package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.enginecontract.EngineNetwork
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.HumanNetworkJudgeProfile

data class KataGoProcessConfig(
    val executablePath: String,
    val modelPath: String,
    val configPath: String,
    val analysisConfigPath: String? = null,
    val startupOverrides: Map<String, String> = emptyMap(),
    /**
     * 사람 모델(KataGo Human SL) 파일. 없으면 `null` — 그 기기에서는 급수 캐릭터가 지금 방식으로 둔다(백로그 #215).
     * ⚠️ 주 모델과 **같이 올리지 않는다** — 올릴 때는 이 파일이 [modelPath] 자리(`-model`)에 간다([buildGtpCommand]).
     */
    val humanModelPath: String? = null,
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
    /**
     * GTP 엔진을 [network]를 올려 띄운다. 실행 파일·모델·설정 파일이 없으면 [IllegalArgumentException].
     * [boardSize]를 알면 그 크기로 띄운다 — 모르면(`null`) KataGo 기본(19줄)이다.
     */
    fun startGtp(
        profile: EngineProfile,
        network: EngineNetwork = EngineNetwork.Main,
        boardSize: BoardSize? = null,
    ): EngineProcessPipes

    /** 사람 모델 파일이 있어 [EngineNetwork.Human]으로 띄울 수 있는가. */
    val humanNetworkAvailable: Boolean
        get() = false

    /** JSON analysis 엔진의 설정 파일 경로. 없으면 `null` — JSON 경로가 구성되지 않은 빌드다(사고가 아니다). */
    fun analysisConfigPathOrNull(): String?

    /** JSON analysis 엔진을 띄운다. */
    fun startAnalysis(analysisConfigPath: String): EngineProcessPipes

    /**
     * 정적 국면 SGF([sgf])를 KataGo가 읽을 파일에 쓰고 그 경로를 돌려준다(백로그 #264 — `loadsgf`). 앱이 쓰고 KataGo가 읽는다 —
     * 안드로이드는 같은 앱 프로세스의 자식이라, iOS는 같은 앱 안이라 앱 파일 폴더를 읽을 수 있다. 하나를 덮어 쓴다(GTP 명령은 한 번에 하나다).
     * 파일 쓰기는 플랫폼마다 다르다 — 그래서 경로가 아니라 「쓰고 경로를 받는」 한 함수다(백로그 #293).
     */
    fun writeStaticPosition(sgf: String): String
}

/**
 * 엔진의 stdin 쪽 — 줄 하나를 **한 번의 쓰기로** 보내고 비운다(줄바꿈은 구현이 붙인다). 실패하면 플랫폼의 입출력 예외([isEnginePipeBroken]).
 * 한 번에 쓰는 이유: 배수가 따로 보내는 `terminate`와 다음 명령의 줄이 줄 중간에서 섞이지 않게(refactor backlog #17).
 */
interface EngineLineWriter {
    fun writeLine(line: String)
}

/** 엔진의 stdout 쪽 — 줄 하나(줄바꿈 빼고), 스트림이 끝났으면 `null`. 막히면 그대로 기다린다. 실패하면 플랫폼의 입출력 예외([isEnginePipeBroken]). */
interface EngineLineReader {
    fun readLine(): String?
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
    val writer: EngineLineWriter
    val reader: EngineLineReader
    val isAlive: Boolean

    /** SIGTERM — 정상 종료(`quit` 뒤). */
    fun destroy()

    /** SIGKILL — 멈춘 프로세스, SIGTERM을 붙잡아 두는 프로세스(SIGSTOP된 것 포함)도 내린다. */
    fun destroyForcibly()
}

/** [network]로 띄울 때 `-model`에 갈 파일 — 주 모델이거나, 사람 모델(없으면 [IllegalArgumentException]). */
internal fun KataGoProcessConfig.modelPathFor(network: EngineNetwork): String =
    when (network) {
        EngineNetwork.Main -> modelPath
        EngineNetwork.Human -> requireNotNull(humanModelPath) { "KataGo human model is not configured." }
    }

/**
 * GTP 프로세스의 명령줄. [network]가 사람 모델이면 그 파일이 **`-model` 자리**에 간다 — `-human-model`로 주 모델 옆에
 * 얹지 않는다(백로그 #215: 신경망은 한 번에 하나만. 둘을 같이 올리면 메모리가 0.5 → 1.0GB다, 실험실 E5).
 * 사람 모델만 올린 KataGo는 프로필이 있어야 답한다 — 띄울 때 [HumanNetworkJudgeProfile]을 주고, 뒤에는 요청마다 바꾼다.
 *
 * [boardSize]를 알면 `defaultBoardSize`로 준다 — KataGo는 19줄로 떠서 다른 크기의 **첫** `boardsize`에 약 1.2초를 쓴다
 * (S23: 13줄 판의 첫 평가까지 2.98 → 1.64초). 갈아 올릴 때마다 내던 값이라, 대국 중인 판 크기로 띄운다.
 */
internal fun KataGoProcessConfig.buildGtpCommand(
    profile: EngineProfile,
    network: EngineNetwork = EngineNetwork.Main,
    boardSize: BoardSize? = null,
): KataGoProcessCommand {
    val overrides = startupOverrides +
        EngineBehaviorOverrides +
        mapOf(
            "maxVisits" to profile.analysisLimit.visits.toString(),
            "logToStderr" to "false",
        ) +
        when (network) {
            EngineNetwork.Main -> emptyMap()
            EngineNetwork.Human -> mapOf("humanSLProfile" to HumanNetworkJudgeProfile)
        } +
        (boardSize?.let { size -> mapOf("defaultBoardSize" to size.value.toString()) } ?: emptyMap())
    return KataGoProcessCommand(
        executablePath = executablePath,
        arguments = listOf(
            "gtp",
            "-model",
            modelPathFor(network),
            "-config",
            configPath,
            "-override-config",
            overrides.toOverrideText(),
        ),
    )
}

/**
 * JSON analysis 엔진의 탐색 스레드 수 — 안드로이드의 프로세스와 iOS의 앱 안 엔진이 같은 값을 쓴다(백로그 #293 — 한 곳).
 * 실험실 대조(`engine-lab/lab/app_parity.py`)가 이 줄을 읽는다.
 */
internal const val DefaultAnalysisSearchThreads = 4

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

internal fun Map<String, String>.toOverrideText(): String =
    entries.joinToString(",") { (key, value) -> "$key=$value" }

private val AnalysisStartupOverrideAllowList = setOf(
    "logDir",
    "homeDataDir",
    "logToStderr",
)
