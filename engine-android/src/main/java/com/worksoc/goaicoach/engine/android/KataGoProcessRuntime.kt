package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.EngineProfile
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

private fun Map<String, String>.toOverrideText(): String =
    entries.joinToString(",") { (key, value) -> "$key=$value" }

private val AnalysisStartupOverrideAllowList = setOf(
    "logDir",
    "homeDataDir",
    "logToStderr",
)
