package com.worksoc.goaicoach.engine

import android.content.Context
import com.worksoc.goaicoach.engine.android.KataGoProcessConfig
import com.worksoc.goaicoach.engine.android.KataGoProcessEngineAdapter
import com.worksoc.goaicoach.engine.android.StubEngineAdapter
import com.worksoc.goaicoach.shared.EngineAdapter
import java.io.File
import java.io.IOException

data class EngineBootstrap(
    val adapter: EngineAdapter,
    val displayName: String,
    val diagnostic: String,
)

fun createEngineBootstrap(
    context: Context,
    nativeLibraryDir: String,
): EngineBootstrap {
    val filesDir = context.filesDir
    val katagoDir = File(filesDir, "katago")
    val executable = File(nativeLibraryDir, "libkatago.so")
    val compressedModel = File(katagoDir, "model.bin.gz")
    val bundledModel = File(katagoDir, "model.bin")
    val config = File(katagoDir, "gtp_learning.cfg")
    val analysisConfig = File(katagoDir, "analysis_learning.cfg")
    val bundleSeedMessages = seedBundledKataGoAssetsIfNeeded(
        context = context,
        katagoDir = katagoDir,
        bundledModel = bundledModel,
        config = config,
        analysisConfig = analysisConfig,
    )
    val model = compressedModel.takeIf { it.isFile && it.length() > 0L } ?: bundledModel

    val missing = buildList {
        if (!executable.canExecute()) {
            add("native lib")
        }
        if (!model.isFile) {
            add("model.bin.gz")
        }
        if (!config.isFile) {
            add("gtp_learning.cfg")
        }
    }

    if (missing.isNotEmpty()) {
        return EngineBootstrap(
            adapter = StubEngineAdapter(),
            displayName = "stub AI",
            diagnostic = buildString {
                append("Stub fallback: missing ${missing.joinToString()}. ")
                append("Use an engine-bundled APK, or run make install-dev-engine / make seed-engine, then restart the app.")
                if (bundleSeedMessages.isNotEmpty()) {
                    append("\n")
                    append(bundleSeedMessages.joinToString("\n"))
                }
            },
        )
    }

    val logsDir = File(katagoDir, "logs").apply { mkdirs() }
    val homeDir = File(katagoDir, "home").apply { mkdirs() }
    return EngineBootstrap(
        adapter = KataGoProcessEngineAdapter(
            KataGoProcessConfig(
                executablePath = executable.absolutePath,
                modelPath = model.absolutePath,
                configPath = config.absolutePath,
                analysisConfigPath = analysisConfig.takeIf { it.isFile }?.absolutePath,
                startupOverrides = mapOf(
                    "numSearchThreads" to "1",
                    "logDir" to logsDir.absolutePath,
                    "homeDataDir" to homeDir.absolutePath,
                    "logToStderr" to "false",
                    "logAllGTPCommunication" to "false",
                    "logSearchInfo" to "false",
                    "allowResignation" to "false",
                    "startupPrintMessageToStderr" to "false",
                ),
            ),
        ),
        displayName = "KataGo",
        diagnostic = buildString {
            append("KataGo assets found. Using local process engine.")
            if (!analysisConfig.isFile) {
                append("\n")
                append("KataGo JSON analysis config missing. Top Moves will fall back to GTP search analysis.")
            }
            if (bundleSeedMessages.isNotEmpty()) {
                append("\n")
                append(bundleSeedMessages.joinToString("\n"))
            }
        },
    )
}

private fun seedBundledKataGoAssetsIfNeeded(
    context: Context,
    katagoDir: File,
    bundledModel: File,
    config: File,
    analysisConfig: File,
): List<String> {
    katagoDir.mkdirs()
    val messages = mutableListOf<String>()

    seedAssetIfMissing(
        context = context,
        assetPath = "katago/model.bin",
        destination = bundledModel,
    )?.let { messages += it }

    seedAssetIfMissing(
        context = context,
        assetPath = "katago/gtp_learning.cfg",
        destination = config,
    )?.let { messages += it }

    seedAssetIfMissing(
        context = context,
        assetPath = "katago/analysis_learning.cfg",
        destination = analysisConfig,
    )?.let { messages += it }

    return messages
}

private fun seedAssetIfMissing(
    context: Context,
    assetPath: String,
    destination: File,
): String? {
    if (destination.isFile && destination.length() > 0L) {
        return null
    }

    return try {
        destination.parentFile?.mkdirs()
        val temp = File(destination.parentFile, "${destination.name}.tmp")
        context.assets.open(assetPath).use { input ->
            temp.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (!temp.renameTo(destination)) {
            temp.copyTo(destination, overwrite = true)
            temp.delete()
        }
        "Seeded bundled asset $assetPath."
    } catch (_: IOException) {
        null
    }
}
