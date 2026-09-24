package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import java.io.File

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
