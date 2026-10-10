package com.worksoc.goaicoach.engine

import android.content.Context
import com.google.android.play.core.assetpacks.AssetPackManager
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackState
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import com.worksoc.goaicoach.BuildConfig
import com.worksoc.goaicoach.engine.android.EngineCoreApiFactory
import com.worksoc.goaicoach.engine.android.KataGoProcessConfig
import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi
import com.worksoc.goaicoach.shared.enginecontract.EngineMode
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

data class EngineBootstrap(
    val coreApi: EngineCoreApi,
    val mode: EngineMode,
    val displayName: String,
    val diagnostic: String,
)

/**
 * KataGo 엔진 모델을 찾고 부트스트랩을 구성한다 (백로그 #245 — PAD on-demand).
 *
 * ## 1. 기존 사용자: 기기 내부 파일 우선 (0바이트 · 0초)
 * 1.3.0 이하에서 설치되어 기기의 `filesDir/katago/`에 이미 저장된 `model.bin.gz` 또는
 * 비압축 `model.bin`이 있으면 그것을 그대로 사용한다. 에셋 팩을 조회하거나 다운로드하지 않는다.
 *
 * ## 2. 신규 사용자: PAD on-demand 에셋 팩 직접 참조 (Direct Path)
 * 기기에 모델이 없으면 Play Asset Delivery의 on-demand 팩(`katago_model_pack`, `katago_human_pack`)을
 * 다운로드한다. 팩이 도착하면 파일을 `filesDir`로 복사하지 않고 `AssetPackLocation.assetsPath()`의
 * 경로를 KataGo에 직접 전달한다:
 *   · 디스크 용량 약 197MB 절약 (팩 + 복사본 중복 방지)
 *   · 100MB 복사 I/O 시간 0초 단축
 *
 * ## 3. 에셋 팩 안의 이름은 `.gz`가 보존된다 (2026-10-10 실측)
 * Base APK 에셋과 달리 AGP 에셋 팩(`com.android.asset-pack`)은 `.gz` 압축을 풀지 않고
 * 원본 파일명 그대로(`katago/model.bin.gz`, `katago/human.bin.gz`) 패키징한다.
 * KataGo는 `.bin.gz`를 압축 해제 없이 직접 읽는다.
 */
suspend fun createEngineBootstrap(
    context: Context,
    nativeLibraryDir: String,
): EngineBootstrap {
    val filesDir = context.filesDir
    val katagoDir = File(filesDir, "katago").apply { mkdirs() }
    val executable = File(nativeLibraryDir, "libkatago.so")
    val config = File(katagoDir, "gtp_learning.cfg")
    val analysisConfig = File(katagoDir, "analysis_learning.cfg")

    // 설정 파일 둘(수십 KB) 씨딩 — base 에셋에 보존됨
    val configSeedMessages = mutableListOf<String>()
    seedAssetIfMissing(context = context, assetPath = "katago/gtp_learning.cfg", destination = config)?.let { configSeedMessages += it }
    seedAssetIfMissing(context = context, assetPath = "katago/analysis_learning.cfg", destination = analysisConfig)?.let { configSeedMessages += it }

    val assetPackManager = try {
        AssetPackManagerFactory.getInstance(context)
    } catch (e: Exception) {
        null
    }

    // 1단계: 기존 기기 내부 모델 파일 우선 (기존 사용자: 0바이트 · 0초)
    val compressedModel = File(katagoDir, "model.bin.gz")
    val bundledModel = File(katagoDir, "model.bin")
    var localModel: File? = compressedModel.takeIf { it.isFile && it.length() > 0L }
        ?: bundledModel.takeIf { it.isFile && it.length() > 0L }

    val humanCompressed = File(katagoDir, HumanModelCompressedName)
    val humanBundled = File(katagoDir, HumanModelName)
    var localHumanModel: File? = humanCompressed.takeIf { it.isFile && it.length() > 0L }
        ?: humanBundled.takeIf { it.isFile && it.length() > 0L }

    // 2단계: 기기 내 모델이 없을 경우 이미 내려받아진 PAD 에셋 팩 확인 (Direct Path)
    if (localModel == null && assetPackManager != null) {
        localModel = resolvePackAssetFile(assetPackManager, KatagoModelPackName, "katago/model.bin.gz")
    }
    if (localHumanModel == null && assetPackManager != null) {
        localHumanModel = resolvePackAssetFile(assetPackManager, KatagoHumanPackName, "katago/human.bin.gz")
    }

    // 3단계: 기기 또는 팩에 없는 모델들을 독립적으로 수집하여 fetch() 요청 및 대기 (Preparing 상태 유지)
    // - 신규 유저: 주 모델과 사람 모델 둘 다 없음 -> 둘 다 요청하고 대기 (경쟁 상태 없이 둘 다 완료 후 기동)
    // - 기존 유저(1.1.0 등): 주 모델은 있으나 사람 모델이 없음 -> 사람 모델 팩만 요청하고 대기 (99MB만 받고 주 모델 98MB는 0바이트)
    // - 기존 유저(1.3.0): 둘 다 있음 -> neededPacks가 비어 0바이트/0초 즉시 기동
    val neededPacks = buildList {
        if (localModel == null) add(KatagoModelPackName)
        if (localHumanModel == null) add(KatagoHumanPackName)
    }

    val packMessages = mutableListOf<String>()
    if (neededPacks.isNotEmpty() && assetPackManager != null) {
        val downloadMessages = fetchAndAwaitAssetPacks(
            manager = assetPackManager,
            neededPacks = neededPacks,
        )
        packMessages += downloadMessages
        // 다운로드 시도 후 에셋 팩 경로 재확인
        if (localModel == null) {
            localModel = resolvePackAssetFile(assetPackManager, KatagoModelPackName, "katago/model.bin.gz")
        }
        if (localHumanModel == null) {
            localHumanModel = resolvePackAssetFile(assetPackManager, KatagoHumanPackName, "katago/human.bin.gz")
        }
    }

    val missing = buildList {
        if (!executable.canExecute()) {
            add("native lib")
        }
        if (localModel == null || !localModel.isFile) {
            add("model.bin.gz")
        }
        if (!config.isFile) {
            add("gtp_learning.cfg")
        }
    }

    if (missing.isNotEmpty() || localModel == null) {
        return EngineBootstrap(
            coreApi = EngineCoreApiFactory.stub(),
            mode = EngineMode.Stub,
            displayName = "stub AI",
            diagnostic = buildString {
                append("Stub fallback: missing ${missing.joinToString()}. ")
                append("Use an engine-bundled APK, or run make install-dev-engine / make seed-engine, then restart the app.")
                if (configSeedMessages.isNotEmpty()) {
                    append("\n")
                    append(configSeedMessages.joinToString("\n"))
                }
                if (packMessages.isNotEmpty()) {
                    append("\n")
                    append(packMessages.joinToString("\n"))
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
                modelPath = localModel.absolutePath,
                configPath = config.absolutePath,
                analysisConfigPath = analysisConfig.takeIf { it.isFile }?.absolutePath,
                humanModelPath = localHumanModel?.absolutePath,
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
            append(if (localHumanModel != null) " Human model found: ${localHumanModel.name}." else " Human model absent.")
            if (!analysisConfig.isFile) {
                append("\n")
                append("KataGo JSON analysis config missing. Broad study analysis will fall back to GTP search analysis.")
            }
            if (configSeedMessages.isNotEmpty()) {
                append("\n")
                append(configSeedMessages.joinToString("\n"))
            }
            if (packMessages.isNotEmpty()) {
                append("\n")
                append(packMessages.joinToString("\n"))
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
 * PAD on-demand 에셋 팩들을 요청(fetch)하고 완료될 때까지 비동기 대기한다 (백로그 #245 U-73 대응).
 *
 * @param neededPacks 기기에 없어 다운로드가 필요한 팩 목록.
 *   - [KatagoModelPackName](주 모델)이 포함되어 있다면 엔진 기동에 필수적인 팩으로 취급된다.
 *   - [KatagoHumanPackName](사람 모델)은 선택적 팩으로 취급되어, 주 모델이 준비되었으나 사람 모델만
 *     명시적으로 실패([AssetPackStatus.FAILED])한 경우에는 엔진 기동을 막지 않고 주 모델 단독으로 진행한다.
 *   - 단, 사람 모델이 아직 다운로드 중([AssetPackStatus.DOWNLOADING] 등)인 동안에는 임의로 조기 종료하지 않고
 *     두 모델이 모두 안전하게 완료될 때까지 기다린다 (신규 유저 첫 세션 사람 모델 누락 경쟁 상태 방지).
 *   - Play 스토어 외부 환경(디버그 APK 등)이거나 필수 팩이 실패한 경우 즉시 실패([false])로 반환한다.
 */
private suspend fun fetchAndAwaitAssetPacks(
    manager: AssetPackManager,
    neededPacks: List<String>,
    timeoutMillis: Long = 300_000L,
): List<String> {
    if (neededPacks.isEmpty()) return emptyList()

    val messages = mutableListOf<String>()
    val result = withTimeoutOrNull(timeoutMillis) {
        suspendCancellableCoroutine<Boolean> { continuation ->
            val pendingPacks = neededPacks.toMutableSet()
            lateinit var listener: AssetPackStateUpdateListener

            fun checkCompletion() {
                if (!continuation.isActive) return
                if (pendingPacks.isEmpty()) {
                    manager.unregisterListener(listener)
                    continuation.resume(true)
                }
            }

            listener = object : AssetPackStateUpdateListener {
                override fun onStateUpdate(state: AssetPackState) {
                    val name = state.name()
                    val status = state.status()
                    when (status) {
                        AssetPackStatus.COMPLETED -> {
                            pendingPacks.remove(name)
                            checkCompletion()
                        }
                        AssetPackStatus.FAILED, AssetPackStatus.CANCELED -> {
                            messages += "Asset pack ($name) failed or canceled (code: ${state.errorCode()})."
                            if (name == KatagoModelPackName && continuation.isActive) {
                                manager.unregisterListener(this)
                                continuation.resume(false)
                            } else {
                                pendingPacks.remove(name)
                                checkCompletion()
                            }
                        }
                        AssetPackStatus.WAITING_FOR_WIFI -> {
                            messages += "Asset pack ($name) is waiting for Wi-Fi."
                        }
                        else -> {
                            // PENDING, DOWNLOADING, TRANSFERRING
                        }
                    }
                }
            }

            manager.registerListener(listener)
            continuation.invokeOnCancellation {
                manager.unregisterListener(listener)
            }

            manager.fetch(neededPacks)
                .addOnSuccessListener { states ->
                    val packStates = states.packStates()
                    neededPacks.forEach { pack ->
                        val st = packStates[pack]?.status()
                        if (st == AssetPackStatus.COMPLETED) {
                            pendingPacks.remove(pack)
                        } else if (st == AssetPackStatus.FAILED || st == AssetPackStatus.CANCELED) {
                            messages += "Asset pack ($pack) initial state failed (code: ${packStates[pack]?.errorCode()})."
                            if (pack == KatagoModelPackName && continuation.isActive) {
                                manager.unregisterListener(listener)
                                continuation.resume(false)
                                return@addOnSuccessListener
                            } else {
                                pendingPacks.remove(pack)
                            }
                        }
                    }
                    checkCompletion()
                }
                .addOnFailureListener { exception ->
                    messages += "Asset pack fetch failed immediately: ${exception.message}"
                    if (continuation.isActive) {
                        manager.unregisterListener(listener)
                        continuation.resume(false)
                    }
                }
        }
    }

    if (result == true) {
        messages += "Asset pack download completed."
    } else {
        messages += "Asset pack download timed out or failed."
    }
    return messages
}

/**
 * PAD 에셋 팩의 설치 디렉터리에서 파일 경로를 찾는다 (복사 없이 직접 참조).
 */
private fun resolvePackAssetFile(
    manager: AssetPackManager,
    packName: String,
    relativeSourcePath: String,
): File? {
    return try {
        val location = manager.getPackLocation(packName) ?: return null
        val assetsPath = location.assetsPath() ?: return null
        val sourceFile = File(assetsPath, relativeSourcePath)
        if (sourceFile.isFile && sourceFile.length() > 0L) {
            sourceFile
        } else {
            null
        }
    } catch (e: Exception) {
        System.err.println("Failed to resolve asset pack $packName ($relativeSourcePath): ${e.message}")
        null
    }
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

/** PAD 에셋 팩 이름 (백로그 #245) */
internal const val KatagoModelPackName = "katago_model_pack"
internal const val KatagoHumanPackName = "katago_human_pack"

/** 기기의 `files/katago/`에서 찾는 사람 모델 파일 이름(백로그 #215) — KataGo는 `.bin.gz`를 풀지 않고 읽는다. */
internal const val HumanModelCompressedName = "human.bin.gz"
internal const val HumanModelName = "human.bin"
