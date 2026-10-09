package com.worksoc.goaicoach.engine

import android.content.Context
import com.worksoc.goaicoach.BuildConfig
import com.worksoc.goaicoach.engine.android.EngineCoreApiFactory
import com.worksoc.goaicoach.engine.android.KataGoProcessConfig
import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi
import com.worksoc.goaicoach.shared.enginecontract.EngineMode
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
    // 사람 모델(백로그 #215) — 있으면 급수 캐릭터가 그것으로 둔다. 없는 것은 사고가 아니다: 그 기기는 지금 방식으로 둔다.
    // 2026-10-06부터 **주 모델과 함께 번들에 싣는다**(사용자 결정 — 두 모델). 위 씨딩이 번들에서 풀어 두고, 개발용 debug 빌드는
    // 번들에 에셋이 없으므로 `make seed-engine`이 이 자리에 넣는다. 주 모델과 같은 두 이름을 본다.
    val humanModel = listOf(File(katagoDir, HumanModelCompressedName), File(katagoDir, HumanModelName))
        .firstOrNull { it.isFile && it.length() > 0L }

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
                humanModelPath = humanModel?.absolutePath,
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
        ).withDebugStallInjector(filesDir),
        mode = EngineMode.LocalProcess,
        displayName = "KataGo",
        diagnostic = buildString {
            append("KataGo assets found. Using local process engine.")
            append(if (humanModel != null) " Human model found: ${humanModel.name}." else " Human model absent.")
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
 * 디버그 빌드에서만 다음 분석 한 번을 일부러 멈출 수 있게 감싼다(refactor backlog #74 실기 확인 —
 * [DebugEngineStallInjector]). 릴리스 빌드는 그대로 돌려준다 — 파일이 있어도 아무 일도 없다.
 */
private fun EngineCoreApi.withDebugStallInjector(filesDir: File): EngineCoreApi =
    if (BuildConfig.DEBUG) {
        DebugEngineStallInjector(delegate = this, armFile = File(filesDir, DebugEngineStallInjector.ArmFileName))
    } else {
        this
    }

/**
 * 번들에 실린 에셋을 `filesDir/katago`로 푼다 — **앱 데이터를 지워도 다시 풀리는 것이 요점이다.**
 * 네이티브 바이너리는 앱 데이터가 아니라 설치 디렉터리에 있어 애초에 지워지지 않으므로,
 * 이 함수가 도는 한 "앱 데이터 삭제 → 재실행"은 스스로 복구된다.
 *
 * ## ⚠️ 빌드가 넣는 이름과 앱이 여는 이름이 **다르다 — 그래야 맞다**
 * `make prepare-friend-assets`는 `katago/model.bin.gz`를 넣는데, 앱은 `katago/model.bin`을 연다.
 * **AGP가 에셋의 `.gz`를 패키징하면서 풀고 확장자를 뗀다** — 2026-09-05 실측으로 확인했다:
 * 소스에 `model.bin.gz`(97,898,094B) 하나뿐인데 병합 결과는 `model.bin`(105,532,578B)이고,
 * 그 값은 `gzip -dc | wc -c`와 **정확히 같다.**
 *
 * ⚠️ **이것 때문에 한 번 잘못 고쳤다**(2026-09-05). 두 이름이 어긋난 것을 결함으로 보고
 * 여는 쪽을 `.gz`로 바꿨는데, 그 이름은 **APK 안에 존재하지 않아** 오히려 스텁으로 떨어졌다.
 * `assets.open()`의 실패는 [seedAssetIfMissing]이 **삼키므로**(stderr 한 줄) 조용히 그렇게 된다.
 * ⚠️ **두 이름을 "같게" 만들려 하지 말 것** — `BundledEngineAssetContractTest`가 그 **변환**까지
 * 포함해 묶어 둔다(빌드가 넣는 이름에서 `.gz`를 뗀 것 == 앱이 여는 이름).
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
            // ⚠️ `.gz`가 아니다 — 위 머리말 참고. AGP가 이미 풀어서 넣었으므로 APK 안의 이름은
            // 확장자가 떨어진 `model.bin`이고, 푸는 결과도 비압축본(약 100MB)이다.
            assetPath = "katago/model.bin",
            destination = bundledModel,
        )?.let { messages += it }
    }

    // 사람 모델(백로그 #215)도 같은 규칙이다 — 둘 중 하나라도 있으면 풀지 않는다(이름의 `.gz` 사정도 주 모델과 같다).
    // 번들에 없는 빌드에서는 여는 데 실패하고 넘어간다 — 그 기기의 급수 캐릭터는 지금 방식으로 두고 승급 대국 카드는 안 보인다.
    val humanModel = File(katagoDir, HumanModelName)
    if (!File(katagoDir, HumanModelCompressedName).isFile && !humanModel.isFile) {
        seedAssetIfMissing(
            context = context,
            assetPath = "katago/human.bin",
            destination = humanModel,
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

/** 기기의 `files/katago/`에서 찾는 사람 모델 파일 이름(백로그 #215) — KataGo는 `.bin.gz`를 풀지 않고 읽는다. */
internal const val HumanModelCompressedName = "human.bin.gz"
internal const val HumanModelName = "human.bin"
