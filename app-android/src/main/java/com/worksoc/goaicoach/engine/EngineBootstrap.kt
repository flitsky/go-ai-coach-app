package com.worksoc.goaicoach.engine

import com.worksoc.goaicoach.engine.android.KataGoProcessConfig
import com.worksoc.goaicoach.engine.android.KataGoProcessEngineAdapter
import com.worksoc.goaicoach.engine.android.StubEngineAdapter
import com.worksoc.goaicoach.shared.EngineAdapter
import java.io.File

data class EngineBootstrap(
    val adapter: EngineAdapter,
    val displayName: String,
    val diagnostic: String,
)

fun createEngineBootstrap(
    filesDir: File,
    nativeLibraryDir: String,
): EngineBootstrap {
    val katagoDir = File(filesDir, "katago")
    val executable = File(nativeLibraryDir, "libkatago.so")
    val model = File(katagoDir, "model.bin.gz")
    val config = File(katagoDir, "gtp_learning.cfg")

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
            diagnostic = "Stub fallback: missing ${missing.joinToString()}. Run make install-dev-engine or make seed-engine, then restart the app.",
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
        diagnostic = "KataGo assets found. Using local process engine.",
    )
}
