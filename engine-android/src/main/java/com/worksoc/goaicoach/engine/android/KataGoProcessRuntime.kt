package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class KataGoProcessConfig(
    val executablePath: String,
    val modelPath: String,
    val configPath: String,
    val analysisConfigPath: String? = null,
    val startupOverrides: Map<String, String> = emptyMap(),
)

internal data class KataGoProcessCommand(
    val executablePath: String,
    val arguments: List<String>,
) {
    val commandLine: List<String> = listOf(executablePath) + arguments
}

/**
 * 1계층 — KataGo 프로세스를 **띄우는** 자리(refactor backlog #14). `KataGoProcessEngineAdapter`는
 * 프로세스를 직접 만들지 않고, 이것이 돌려준 [EngineProcessPipes]의 writer/reader만 쓴다.
 *
 * 세 함수 모두 블로킹이지만 짧다 — 파일 검증과 fork/exec뿐이고, 파이프 입출력(모델 적재를 기다리는
 * 첫 응답 같은 것)은 하지 않는다. 그래서 호출자가 수명 락을 쥔 채 불러도 된다.
 */
internal interface EngineProcessRuntime {
    /** GTP 엔진을 띄운다. 실행 파일·모델·설정 파일이 없으면 [IllegalArgumentException]. */
    fun startGtp(profile: EngineProfile): EngineProcessPipes

    /** JSON analysis 엔진의 설정 파일 경로. 없으면 `null` — JSON 경로가 구성되지 않은 빌드다(사고가 아니다). */
    fun analysisConfigPathOrNull(): String?

    /** JSON analysis 엔진을 띄운다. */
    fun startAnalysis(analysisConfigPath: String): EngineProcessPipes
}

/**
 * 띄운 프로세스 하나의 stdin/stdout과 수명 — `process`/`input`/`output` 셋을 한 값으로 묶었다.
 *
 * ⚠️ [destroy]·[destroyForcibly]는 **[reader]/[writer]를 닫지 않는다.** `BufferedReader.close()`는 막힌
 * `readLine()`이 쥔 것과 **같은 락**을 잡으므로, 멈춘 호출을 풀려고 닫으면 닫는 쪽(메인 스레드의
 * `forceReset`)이 같이 멈춘다. 프로세스를 죽이면 파이프의 쓰는 쪽이 닫혀 막힌 읽기가 EOF로 풀린다 —
 * 그것만이 푸는 방법이다(파이프 읽기는 `Thread.interrupt()`를 무시한다).
 */
internal interface EngineProcessPipes {
    val writer: BufferedWriter
    val reader: BufferedReader
    val isAlive: Boolean

    /** SIGTERM — 정상 종료(`quit` 뒤). */
    fun destroy()

    /** SIGKILL — 멈춘 프로세스, SIGTERM을 붙잡아 두는 프로세스(SIGSTOP된 것 포함)도 내린다. */
    fun destroyForcibly()
}

/** 이 기기에서 `ProcessBuilder`로 띄운다 — 앱에서 KataGo 프로세스를 만드는 **유일한** 코드다. */
internal class LocalKataGoProcessRuntime(
    private val config: KataGoProcessConfig,
    private val analysisSearchThreads: Int = DefaultAnalysisSearchThreads,
) : EngineProcessRuntime {
    override fun startGtp(profile: EngineProfile): EngineProcessPipes {
        config.validateGtpFiles()
        return spawn(config.buildGtpCommand(profile))
    }

    override fun analysisConfigPathOrNull(): String? = config.resolveAnalysisConfigPath()

    override fun startAnalysis(analysisConfigPath: String): EngineProcessPipes =
        spawn(
            config.buildAnalysisCommand(
                analysisConfigPath = analysisConfigPath,
                analysisSearchThreads = analysisSearchThreads,
            ),
        )

    private fun spawn(command: KataGoProcessCommand): EngineProcessPipes =
        LocalProcessPipes(
            ProcessBuilder(command.commandLine)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start(),
        )

    private companion object {
        const val DefaultAnalysisSearchThreads = 4
    }
}

private class LocalProcessPipes(
    private val process: Process,
) : EngineProcessPipes {
    override val writer: BufferedWriter = BufferedWriter(OutputStreamWriter(process.outputStream))
    override val reader: BufferedReader = BufferedReader(InputStreamReader(process.inputStream))
    override val isAlive: Boolean
        get() = process.isAlive

    override fun destroy() {
        process.destroy()
    }

    override fun destroyForcibly() {
        process.destroyForcibly()
    }
}

internal fun KataGoProcessConfig.validateGtpFiles() {
    require(File(executablePath).canExecute()) {
        "KataGo executable is not executable: $executablePath"
    }
    require(File(modelPath).isFile) {
        "KataGo model not found: $modelPath"
    }
    require(File(configPath).isFile) {
        "KataGo config not found: $configPath"
    }
}

internal fun KataGoProcessConfig.resolveAnalysisConfigPath(): String? =
    analysisConfigPath?.takeIf { File(it).isFile }

internal fun KataGoProcessConfig.buildGtpCommand(profile: EngineProfile): KataGoProcessCommand {
    val overrides = startupOverrides +
        EngineBehaviorOverrides +
        mapOf(
            "maxVisits" to profile.analysisLimit.visits.toString(),
            "logToStderr" to "false",
        )
    return KataGoProcessCommand(
        executablePath = executablePath,
        arguments = listOf(
            "gtp",
            "-model",
            modelPath,
            "-config",
            configPath,
            "-override-config",
            overrides.toOverrideText(),
        ),
    )
}

internal fun KataGoProcessConfig.buildAnalysisCommand(
    analysisConfigPath: String,
    analysisSearchThreads: Int,
): KataGoProcessCommand {
    val overrides = startupOverrides
        .filterKeys { key ->
            key in AnalysisStartupOverrideAllowList
        } +
        EngineBehaviorOverrides +
        mapOf(
            "numAnalysisThreads" to "1",
            "numSearchThreads" to analysisSearchThreads.toString(),
            "logToStderr" to "false",
            "logAllRequests" to "false",
            "logAllResponses" to "false",
            "logSearchInfo" to "false",
        )
    return KataGoProcessCommand(
        executablePath = executablePath,
        arguments = listOf(
            "analysis",
            "-model",
            modelPath,
            "-config",
            analysisConfigPath,
            "-override-config",
            overrides.toOverrideText(),
        ),
    )
}

/**
 * 호출자와 상관없이 두 엔진(gtp·analysis)에 똑같이 싣는 기동 인자다. 호출자의 `startupOverrides`보다
 * 뒤에 더하므로 호출자가 같은 키에 다른 값을 넘겨도 이 값이 이긴다.
 *
 * - `assumeMultipleStartingBlackMovesAreHandicap=false`(백로그 #92). KataGo 기본값은 true다. true면
 *   백이 아직 돌을 놓지 않은 동안 이어진 흑 수를 접바둑 돌로 센다. 백의 `pass`는 그 연속을 끊지 않고,
 *   한 번 센 수는 백이 돌을 놓은 뒤에도 남는다. 면적계가(chinese, whiteHandicapBonus=N)에서는 그만큼
 *   백의 접바둑 보정이 부푼다. 2점 접바둑에서 백 패스·흑 한 수면 2가 3이 되고, 맞바둑에서 흑·백 패스·흑이면
 *   없던 2가 생긴다. 집계가(japanese)는 보정이 0이라 영향이 없다. 앱은 접바둑 돌을 추정에 맡기지 않고
 *   직접 놓으므로(gtp는 `set_free_handicap`) 이 추정이 필요 없다.
 *
 * ⚠️ cfg에 넣지 말고 여기에 둔다. `EngineBootstrap.seedAssetIfMissing`은 `filesDir/katago/`에 cfg가
 * 이미 있으면 다시 풀지 않는다. 그래서 cfg를 고쳐도 이미 설치한 기기에는 닿지 않는다.
 */
private val EngineBehaviorOverrides = mapOf(
    "assumeMultipleStartingBlackMovesAreHandicap" to "false",
)

private fun Map<String, String>.toOverrideText(): String =
    entries.joinToString(",") { (key, value) -> "$key=$value" }

private val AnalysisStartupOverrideAllowList = setOf(
    "logDir",
    "homeDataDir",
    "logToStderr",
)
