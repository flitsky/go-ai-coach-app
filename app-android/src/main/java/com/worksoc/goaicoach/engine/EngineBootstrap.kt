package com.worksoc.goaicoach.engine

import android.content.Context
import com.worksoc.goaicoach.engine.android.EngineCoreApiFactory
import com.worksoc.goaicoach.engine.android.KataGoProcessConfig
import com.worksoc.goaicoach.shared.EngineCoreApi
import com.worksoc.goaicoach.shared.EngineMode
import java.io.File
import java.io.IOException

data class EngineBootstrap(
    val coreApi: EngineCoreApi,
    val mode: EngineMode,
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
        compressedModel = compressedModel,
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
            coreApi = EngineCoreApiFactory.stub(),
            mode = EngineMode.Stub,
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
        coreApi = EngineCoreApiFactory.local(
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
        mode = EngineMode.LocalProcess,
        displayName = "KataGo",
        diagnostic = buildString {
            append("KataGo assets found. Using local process engine.")
            if (!analysisConfig.isFile) {
                append("\n")
                append("KataGo JSON analysis config missing. Broad study analysis will fall back to GTP search analysis.")
            }
            if (bundleSeedMessages.isNotEmpty()) {
                append("\n")
                append(bundleSeedMessages.joinToString("\n"))
            }
        },
    )
}

/**
 * 번들에 실린 에셋을 `filesDir/katago`로 푼다 — **앱 데이터를 지워도 다시 풀리는 것이 요점이다.**
 * 네이티브 바이너리는 앱 데이터가 아니라 설치 디렉터리에 있어 애초에 지워지지 않으므로,
 * 이 함수가 도는 한 "앱 데이터 삭제 → 재실행"은 스스로 복구된다.
 *
 * ## ⚠️ 에셋 **이름**이 이 함수의 유일한 실패 지점이다
 * `assets.open()`은 이름이 틀리면 `IOException`을 던지고 [seedAssetIfMissing]이 그것을 **삼킨다**
 * (stderr 한 줄). 그래서 이름이 어긋나면 **조용히** 모델 없이 부팅되고 스텁으로 강등된다.
 * 실제로 그렇게 어긋나 있었다: 코드는 `katago/model.bin`을 열고 있었는데
 * `make prepare-friend-assets`는 **`model.bin.gz`** 로 넣고 있었다(2026-06-05 `628e2e8`부터).
 * 그 디렉터리가 gitignore라 **지난 빌드는 손으로 넣어둔 로컬 파일 덕에 통과했다.**
 * ⚠️ 이제 `BundledEngineAssetContractTest`가 두 이름을 묶어 둔다 — 한쪽만 바꾸면 `make test`가 깨진다.
 */
private fun seedBundledKataGoAssetsIfNeeded(
    context: Context,
    katagoDir: File,
    compressedModel: File,
    bundledModel: File,
    config: File,
    analysisConfig: File,
): List<String> {
    katagoDir.mkdirs()
    val messages = mutableListOf<String>()

    // ⚠️ **둘 중 하나라도 이미 있으면 풀지 않는다.** 예전 빌드가 풀어둔 비압축 `model.bin`이
    // 있는 기기에 `.gz`를 또 풀면 200MB를 쓴다 — 둘 다 쓸 수 있으므로 있는 쪽을 그대로 둔다
    // (고르는 것은 호출부의 `compressedModel ?: bundledModel`이다).
    if (!compressedModel.isFile && !bundledModel.isFile) {
        seedAssetIfMissing(
            context = context,
            assetPath = "katago/model.bin.gz",
            destination = compressedModel,
        )?.let { messages += it }
    }

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
    } catch (e: IOException) {
        System.err.println("Failed to seed bundled asset $assetPath: ${e.message}")
        null
    }
}
