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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
 * ## 1. 기존 사용자: 기기 내부 파일 우선 (0바이트 · 0초 대국 기동)
 * 1.3.0 이하에서 설치되어 기기의 `filesDir/katago/`에 이미 저장된 `model.bin.gz` 또는
 * 비압축 `model.bin`이 있으면 그것을 그대로 사용하여 즉시 엔진을 기동한다.
 * 사람 모델이 없더라도 주 모델로 즉시 대국을 시작할 수 있으며, 사람 모델 팩(99MB)은
 * 백그라운드에서 비동기로 수신한다.
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

    // 3단계: 주 모델 유무에 따른 분기
    // - 기존 유저 (주 모델이 이미 있음):
    //   주 모델이 있으므로 메인 로컬 엔진을 즉시 생성하여 대국 시작을 1초도 막지 않는다!
    //   사람 모델이 없는 경우, 백그라운드 비동기로 사람 모델 팩(99MB) 다운로드를 개시하여
    //   로비 가이드 카드에 진행률을 띄우되, 대국 시작 버튼은 열려 있고 언제든 닫을 수 있다.
    if (localModel != null && localModel.isFile && executable.canExecute() && config.isFile) {
        if (localHumanModel == null && assetPackManager != null) {
            startBackgroundHumanModelDownload(assetPackManager)
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
                    ),
                ),
            ),
            mode = EngineMode.LocalProcess,
            displayName = "KataGo (local)",
            diagnostic = buildString {
                append("Local KataGo ready (model: ${localModel.name})")
                if (localHumanModel != null) {
                    append(", human model: ${localHumanModel.name}")
                }
            },
        )
    }

    // - 신규 유저 (주 모델이 없음):
    //   주 모델과 사람 모델 다운로드를 요청하고 대기한다.
    //   실패나 타임아웃 시 즉시 스텁으로 종료하지 않고, 가이드 카드의 [다시 시도]를 기다리며
    //   엔진 상태를 '준비 중(Preparing)'으로 유지한다 (스텁 팝업 방지).
    val packMessages = mutableListOf<String>()
    if (assetPackManager != null) {
        val neededPacks = buildList {
            add(KatagoModelPackName)
            if (localHumanModel == null) add(KatagoHumanPackName)
        }
        val downloadMessages = fetchAndAwaitAssetPacksWithRetry(
            manager = assetPackManager,
            neededPacks = neededPacks,
        )
        packMessages += downloadMessages
        // 다운로드 성공 후 에셋 팩 경로 재확인
        localModel = resolvePackAssetFile(assetPackManager, KatagoModelPackName, "katago/model.bin.gz")
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
                ),
            ),
        ),
        mode = EngineMode.LocalProcess,
        displayName = "KataGo (local)",
        diagnostic = buildString {
            append("Local KataGo ready (model: ${localModel.name})")
            if (localHumanModel != null) {
                append(", human model: ${localHumanModel.name}")
            }
        },
    )
}

internal const val KatagoModelPackName = "katago_model_pack"
internal const val KatagoHumanPackName = "katago_human_pack"

private const val KatagoModelPackEstimatedBytes = 97_900_000L
private const val KatagoHumanPackEstimatedBytes = 99_100_000L

private val backgroundDownloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * 주 모델이 이미 있는 사용자를 위해 사람 모델 팩(99MB)을 백그라운드 비동기로 요청한다.
 */
private fun startBackgroundHumanModelDownload(manager: AssetPackManager) {
    backgroundDownloadScope.launch {
        fetchAndAwaitAssetPacksWithRetry(
            manager = manager,
            neededPacks = listOf(KatagoHumanPackName),
            isMandatory = false,
        )
    }
}

/**
 * PAD on-demand 에셋 팩을 비동기 요청하고 다운로드 완료까지 대기한다.
 *
 * 실패 또는 타임아웃 시 스텁으로 바로 종료하지 않고, [EngineDownloadStatus.Failed] 상태로
 * UI의 [currentEngineDownloadTracker().retry] 신호를 suspend 대기한다.
 */
private suspend fun fetchAndAwaitAssetPacksWithRetry(
    manager: AssetPackManager,
    neededPacks: List<String>,
    timeoutMillis: Long = 300_000L,
    isMandatory: Boolean = true,
): List<String> {
    if (neededPacks.isEmpty()) return emptyList()

    val messages = mutableListOf<String>()
    val tracker = currentEngineDownloadTracker()

    while (currentCoroutineContext().isActive) {
        val (completed, attemptMessages) = fetchAndAwaitAssetPacksAttempt(
            manager = manager,
            neededPacks = neededPacks,
            timeoutMillis = timeoutMillis,
            isMandatory = isMandatory,
        )
        messages += attemptMessages
        if (completed) {
            tracker.updateStatus(EngineDownloadStatus.Completed)
            break
        }

        // 선택적 팩(사람 모델 단독)이 실패한 경우 무한 루프 돌지 않고 종료
        if (!isMandatory) {
            break
        }

        // 주 모델이 필수인데 실패한 경우: 재시도 핸들러를 등록하고 사용자의 [다시 시도] 클릭을 대기
        val retryDeferred = CompletableDeferred<Unit>()
        tracker.registerRetryHandler {
            if (!retryDeferred.isCompleted) {
                retryDeferred.complete(Unit)
            }
        }

        try {
            retryDeferred.await()
        } catch (e: CancellationException) {
            break
        }
    }

    return messages
}

private data class FetchAttemptResult(
    val completed: Boolean,
    val messages: List<String>,
)

private suspend fun fetchAndAwaitAssetPacksAttempt(
    manager: AssetPackManager,
    neededPacks: List<String>,
    timeoutMillis: Long,
    isMandatory: Boolean,
): FetchAttemptResult {
    val downloadedBytesMap = mutableMapOf<String, Long>()
    val totalBytesMap = mutableMapOf<String, Long>()

    neededPacks.forEach { pack ->
        downloadedBytesMap[pack] = 0L
        totalBytesMap[pack] = if (pack == KatagoModelPackName) {
            KatagoModelPackEstimatedBytes
        } else {
            KatagoHumanPackEstimatedBytes
        }
    }

    val tracker = currentEngineDownloadTracker()

    fun updateDownloadProgress() {
        val currentDownloaded = downloadedBytesMap.values.sum()
        val currentTotal = totalBytesMap.values.sum().coerceAtLeast(1L)
        val percentage = ((currentDownloaded * 100) / currentTotal).toInt().coerceIn(0, 100)
        tracker.updateStatus(
            EngineDownloadStatus.Downloading(
                bytesDownloaded = currentDownloaded,
                totalBytesToDownload = currentTotal,
                percentage = percentage,
            )
        )
    }

    // 초기 상태 통보
    updateDownloadProgress()

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
                            val total = totalBytesMap[name] ?: 0L
                            downloadedBytesMap[name] = total
                            pendingPacks.remove(name)
                            updateDownloadProgress()
                            checkCompletion()
                        }
                        AssetPackStatus.FAILED, AssetPackStatus.CANCELED -> {
                            messages += "Asset pack ($name) failed or canceled (code: ${state.errorCode()})."
                            tracker.updateStatus(
                                EngineDownloadStatus.Failed(state.errorCode(), "Download failed for $name")
                            )
                            if (name == KatagoModelPackName && isMandatory && continuation.isActive) {
                                manager.unregisterListener(this)
                                continuation.resume(false)
                            } else {
                                pendingPacks.remove(name)
                                checkCompletion()
                            }
                        }
                        AssetPackStatus.WAITING_FOR_WIFI -> {
                            messages += "Asset pack ($name) is waiting for Wi-Fi."
                            val bytes = state.bytesDownloaded()
                            val total = state.totalBytesToDownload()
                            if (total > 0L) totalBytesMap[name] = total
                            downloadedBytesMap[name] = bytes
                            tracker.updateStatus(
                                EngineDownloadStatus.WaitingForWifi(
                                    bytesDownloaded = downloadedBytesMap.values.sum(),
                                    totalBytesToDownload = totalBytesMap.values.sum().coerceAtLeast(1L),
                                )
                            )
                        }
                        AssetPackStatus.DOWNLOADING, AssetPackStatus.TRANSFERRING -> {
                            val bytes = state.bytesDownloaded()
                            val total = state.totalBytesToDownload()
                            if (total > 0L) totalBytesMap[name] = total
                            downloadedBytesMap[name] = bytes
                            updateDownloadProgress()
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
                        val packState = packStates[pack] ?: return@forEach
                        val st = packState.status()
                        val total = packState.totalBytesToDownload()
                        if (total > 0L) totalBytesMap[pack] = total
                        if (st == AssetPackStatus.COMPLETED) {
                            downloadedBytesMap[pack] = totalBytesMap[pack] ?: 0L
                            pendingPacks.remove(pack)
                        } else if (st == AssetPackStatus.FAILED || st == AssetPackStatus.CANCELED) {
                            messages += "Asset pack ($pack) initial state failed (code: ${packState.errorCode()})."
                            if (pack == KatagoModelPackName && isMandatory && continuation.isActive) {
                                tracker.updateStatus(
                                    EngineDownloadStatus.Failed(
                                        packState.errorCode(),
                                        "Initial state failed for $pack",
                                    )
                                )
                                manager.unregisterListener(listener)
                                continuation.resume(false)
                                return@addOnSuccessListener
                            } else {
                                pendingPacks.remove(pack)
                            }
                        }
                    }
                    updateDownloadProgress()
                    checkCompletion()
                }
                .addOnFailureListener { exception ->
                    messages += "Asset pack fetch failed immediately: ${exception.message}"
                    tracker.updateStatus(
                        EngineDownloadStatus.Failed(-1, exception.message ?: "Fetch failed")
                    )
                    if (continuation.isActive) {
                        manager.unregisterListener(listener)
                        continuation.resume(false)
                    }
                }
        }
    }

    val completed = result == true
    if (!completed) {
        messages += "Asset pack download timed out or failed."
        tracker.updateStatus(
            EngineDownloadStatus.Failed(-2, "Download timed out or failed")
        )
    }

    return FetchAttemptResult(completed = completed, messages = messages)
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
        sourceFile.takeIf { it.isFile && it.length() > 0L }
    } catch (e: Exception) {
        null
    }
}

private const val HumanModelName = "human.bin"
private const val HumanModelCompressedName = "human.bin.gz"

/**
 * Base 에셋에 보존된 소형 설정 파일(`gtp_learning.cfg` 등)을 기기 저장소로 복사한다.
 */
private fun seedAssetIfMissing(
    context: Context,
    assetPath: String,
    destination: File,
): String? {
    if (destination.isFile && destination.length() > 0L) {
        return null
    }
    return try {
        context.assets.open(assetPath).use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        "Seeded $assetPath to ${destination.absolutePath}"
    } catch (e: IOException) {
        "Failed to seed $assetPath: ${e.message}"
    }
}
