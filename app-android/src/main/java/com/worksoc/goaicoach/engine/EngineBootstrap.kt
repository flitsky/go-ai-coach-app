package com.worksoc.goaicoach.engine

import android.content.Context
import com.google.android.play.core.assetpacks.AssetPackException
import com.google.android.play.core.assetpacks.AssetPackManager
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackState
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackErrorCode
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import com.worksoc.goaicoach.BuildConfig
import com.worksoc.goaicoach.engine.android.EngineCoreApiFactory
import com.worksoc.goaicoach.engine.android.KataGoProcessConfig
import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi
import com.worksoc.goaicoach.shared.enginecontract.EngineMode
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

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

    // 3단계: PAD 에셋 팩 다운로드 처리
    // - 기존 유저 (주 모델이 이미 있음):
    //   주 모델이 있으므로 메인 부트스트랩을 블로킹하지 않고 즉시 기동한다 (대국 차단 0초).
    //   사람 모델이 없는 경우, 백그라운드 코루틴에서 비동기로 수신하고 가이드 카드를 띄운다.
    // - 신규 유저 (주 모델이 없음):
    //   주 모델과 사람 모델 다운로드를 요청하고 완료/재시도를 대기한다.
    val packMessages = mutableListOf<String>()
    if (assetPackManager != null) {
        if (localModel != null) {
            // 주 모델이 이미 있는 사용자: 사람 모델만 백그라운드 비동기로 요청 (대국 시작 차단 0초)
            if (localHumanModel == null) {
                startBackgroundHumanModelDownload(assetPackManager)
            }
        } else {
            // 신규 사용자: 주 모델(+사람 모델) 다운로드 수행
            val neededPacks = buildList {
                add(KatagoModelPackName)
                if (localHumanModel == null) add(KatagoHumanPackName)
            }
            val downloadMessages = fetchAndAwaitAssetPacksWithRetry(
                manager = assetPackManager,
                neededPacks = neededPacks,
                isMandatory = true,
            )
            packMessages += downloadMessages
            // 다운로드 후 경로 재확인
            localModel = resolvePackAssetFile(assetPackManager, KatagoModelPackName, "katago/model.bin.gz")
            if (localHumanModel == null) {
                localHumanModel = resolvePackAssetFile(assetPackManager, KatagoHumanPackName, "katago/human.bin.gz")
            }
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
        try {
            fetchAndAwaitAssetPacksWithRetry(
                manager = manager,
                neededPacks = listOf(KatagoHumanPackName),
                isMandatory = false,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // 백그라운드 사람 모델 다운로드 중 예외 발생 시 크래시 방지 및 기록
            android.util.Log.w("EngineBootstrap", "Background human model download failed", e)
            val tracker = currentEngineDownloadTracker()
            val currentStatus = tracker.status.value
            // 사람 모델 수신 상태일 때만 안전하게 Idle로 리셋 (다른 다운로드 덮어쓰기 방지)
            if (currentStatus is EngineDownloadStatus.Downloading && currentStatus.isHumanModelOnly) {
                tracker.updateStatus(EngineDownloadStatus.Idle)
            }
        }
    }
}

internal const val CustomErrorCodeStalled = 1001
internal const val CustomErrorCodeImmediateFailure = 1002

internal sealed interface AttemptOutcome {
    object Success : AttemptOutcome
    data class RecoverableFailure(val message: String, val errorCode: Int) : AttemptOutcome
    object UserTriggeredRetry : AttemptOutcome
    data class PermanentFailure(val message: String, val errorCode: Int) : AttemptOutcome
}

/**
 * Play Core 공식 오류 코드 또는 자체 코드에서 영구 실패 여부를 판정한다.
 * API_NOT_AVAILABLE(-5), APP_UNAVAILABLE(-1), PACK_UNAVAILABLE(-2), INVALID_REQUEST(-3),
 * ACCESS_DENIED(-7), DOWNLOAD_NOT_FOUND(-4), APP_NOT_OWNED(-13), UNRECOGNIZED_INSTALLATION(-15)는
 * 재시도해도 극복할 수 없으므로 즉시 스텁 모드로 전환해야 한다.
 * ⚠️ INTERNAL_ERROR(-100)는 Play Core의 일시적인 다운로드 장애일 수 있으므로 영구 실패가 아닌 복구 가능 오류로 취급한다.
 */
internal fun isPermanentAssetPackErrorCode(errorCode: Int): Boolean =
    when (errorCode) {
        AssetPackErrorCode.API_NOT_AVAILABLE,
        AssetPackErrorCode.APP_UNAVAILABLE,
        AssetPackErrorCode.PACK_UNAVAILABLE,
        AssetPackErrorCode.INVALID_REQUEST,
        AssetPackErrorCode.ACCESS_DENIED,
        AssetPackErrorCode.DOWNLOAD_NOT_FOUND,
        AssetPackErrorCode.APP_NOT_OWNED,
        AssetPackErrorCode.UNRECOGNIZED_INSTALLATION,
        -11 -> true // PLAY_STORE_NOT_FOUND
        else -> false
    }

/**
 * 네트워크 오류(-6), 스토리지 부족(-10), 내부 오류(-100), 진행 정체(1001) 등 사용자가 해결 후 재시도 가능한 오류인지 판정한다.
 */
internal fun isRecoverableAssetPackErrorCode(errorCode: Int): Boolean =
    when (errorCode) {
        AssetPackErrorCode.NETWORK_ERROR,
        AssetPackErrorCode.INSUFFICIENT_STORAGE,
        AssetPackErrorCode.INTERNAL_ERROR,
        CustomErrorCodeStalled,
        CustomErrorCodeImmediateFailure -> true
        else -> false
    }

internal fun extractAssetPackErrorCode(throwable: Throwable?): Int? {
    var current: Throwable? = throwable
    while (current != null) {
        if (current is AssetPackException) {
            return current.errorCode
        }
        current = current.cause
    }
    return null
}

internal fun determineErrorClassification(exception: Throwable): Pair<Int, Boolean> {
    val playErrorCode = extractAssetPackErrorCode(exception)
    if (playErrorCode != null) {
        val isPermanent = isPermanentAssetPackErrorCode(playErrorCode)
        return Pair(playErrorCode, isPermanent)
    }
    val msg = exception.message.orEmpty()
    val isPermanent = msg.contains("API_NOT_AVAILABLE", ignoreCase = true) ||
        msg.contains("PLAY_STORE_NOT_FOUND", ignoreCase = true) ||
        msg.contains("Play Store", ignoreCase = true) ||
        msg.contains("APP_UNAVAILABLE", ignoreCase = true) ||
        msg.contains("PACK_UNAVAILABLE", ignoreCase = true)
    return Pair(CustomErrorCodeImmediateFailure, isPermanent)
}

/**
 * PAD on-demand 에셋 팩을 요청하고 완료까지 대기한다.
 *
 * 네트워크 일시 오류나 정체 시 [EngineDownloadStatus.Failed] 상태로 재시도 신호를 대기하고,
 * Play Store 밖 설치나 복구 불가능한 오류 시에는 즉시 스텁 fallback으로 진행한다.
 */
internal suspend fun fetchAndAwaitAssetPacksWithRetry(
    manager: AssetPackManager,
    neededPacks: List<String>,
    isMandatory: Boolean,
    tracker: EngineDownloadTracker = currentEngineDownloadTracker(),
): List<String> {
    if (neededPacks.isEmpty()) return emptyList()

    val messages = mutableListOf<String>()
    tracker.startNewDownload()

    while (currentCoroutineContext().isActive) {
        val (outcome, attemptMessages) = fetchAndAwaitAssetPacksAttempt(
            manager = manager,
            neededPacks = neededPacks,
            isMandatory = isMandatory,
            tracker = tracker,
        )
        messages += attemptMessages

        when (outcome) {
            is AttemptOutcome.Success -> {
                tracker.updateStatus(EngineDownloadStatus.Completed(isHumanModelOnly = !isMandatory))
                break
            }

            is AttemptOutcome.PermanentFailure -> {
                // Play가 모르는 설치(make dev-stub, 디버그 APK 등) 또는 스토어 영구 실패이므로 스텁으로 전환.
                // 부트스트랩 루프가 즉시 종료되고 스텁 모드로 진입하므로, 반응 없는 죽은 재시도 버튼을
                // 남기지 않고 카드를 Idle로 닫는다. 스텁 안내는 부트스트랩 완료 후 다이얼로그가 맡는다.
                messages += "Asset pack permanent failure: ${outcome.message} (code: ${outcome.errorCode})"
                tracker.updateStatus(EngineDownloadStatus.Idle)
                break
            }

            is AttemptOutcome.UserTriggeredRetry -> {
                // 사용자가 다운로드 중 또는 Wi-Fi 대기 중에 [다시 시도]를 눌러 중단한 경우:
                // 추가 대기 없이 즉시 새 attempt 시작 (2회 탭 버그 방지)
                continue
            }

            is AttemptOutcome.RecoverableFailure -> {
                // 사용자가 [다시 시도]를 누를 때까지 suspend 대기
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
        }
    }

    return messages
}

internal data class FetchAttemptResult(
    val outcome: AttemptOutcome,
    val messages: List<String>,
)

private const val PendingStallTimeoutMs = 300_000L // 5분

internal suspend fun fetchAndAwaitAssetPacksAttempt(
    manager: AssetPackManager,
    neededPacks: List<String>,
    isMandatory: Boolean,
    tracker: EngineDownloadTracker = currentEngineDownloadTracker(),
): FetchAttemptResult {
    val downloadedBytesMap = mutableMapOf<String, Long>()
    val totalBytesMap = mutableMapOf<String, Long>()
    val failedPacks = mutableSetOf<String>()
    val packStatusMap = java.util.concurrent.ConcurrentHashMap<String, Int>()
    val pendingStartTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()

    val isHumanModelOnly = !isMandatory && neededPacks == listOf(KatagoHumanPackName)

    neededPacks.forEach { pack ->
        downloadedBytesMap[pack] = 0L
        packStatusMap[pack] = AssetPackStatus.UNKNOWN
        totalBytesMap[pack] = if (pack == KatagoModelPackName) {
            KatagoModelPackEstimatedBytes
        } else {
            KatagoHumanPackEstimatedBytes
        }
    }

    fun updateProgressOrWifiState() {
        val anyWaitingForWifi = neededPacks.any { pack ->
            val st = packStatusMap[pack]
            st == AssetPackStatus.WAITING_FOR_WIFI || st == AssetPackStatus.REQUIRES_USER_CONFIRMATION
        }
        val currentDownloaded = downloadedBytesMap.values.sum()
        val currentTotal = totalBytesMap.values.sum().coerceAtLeast(1L)
        val percentage = ((currentDownloaded * 100) / currentTotal).toInt().coerceIn(0, 100)

        if (anyWaitingForWifi) {
            tracker.updateStatus(
                EngineDownloadStatus.WaitingForWifi(
                    bytesDownloaded = currentDownloaded,
                    totalBytesToDownload = currentTotal,
                    isHumanModelOnly = isHumanModelOnly,
                )
            )
        } else {
            tracker.updateStatus(
                EngineDownloadStatus.Downloading(
                    bytesDownloaded = currentDownloaded,
                    totalBytesToDownload = currentTotal,
                    percentage = percentage,
                    isHumanModelOnly = isHumanModelOnly,
                )
            )
        }
    }

    updateProgressOrWifiState()

    val messages = mutableListOf<String>()

    val outcome = suspendCancellableCoroutine<AttemptOutcome> { continuation ->
        val pendingPacks = neededPacks.toMutableSet()
        val isResumed = AtomicBoolean(false)
        val lastProgressTimestamp = AtomicLong(System.currentTimeMillis())

        lateinit var listener: AssetPackStateUpdateListener
        var stallMonitorJob: Job? = null

        fun safeResume(result: AttemptOutcome) {
            if (isResumed.compareAndSet(false, true)) {
                try {
                    manager.unregisterListener(listener)
                } catch (_: Exception) {
                }
                stallMonitorJob?.cancel()
                continuation.resume(result)
            }
        }

        // ⚠️ 단일 invokeOnCancellation 등록 (코루틴은 핸들러 이중 등록 시 IllegalStateException 발생)
        continuation.invokeOnCancellation {
            try {
                manager.unregisterListener(listener)
            } catch (_: Exception) {
            }
            stallMonitorJob?.cancel()
        }

        // 사용자가 진행 중/대기 중 [다시 시도]를 누르면 즉시 재시도 outcome으로 중단 및 전환
        tracker.registerRetryHandler {
            safeResume(AttemptOutcome.UserTriggeredRetry)
        }

        fun checkCompletion() {
            if (isResumed.get()) return
            if (pendingPacks.isEmpty()) {
                if (failedPacks.isEmpty()) {
                    safeResume(AttemptOutcome.Success)
                } else {
                    safeResume(
                        AttemptOutcome.RecoverableFailure(
                            message = "Some packs failed: ${failedPacks.joinToString()}",
                            errorCode = AssetPackErrorCode.NETWORK_ERROR,
                        )
                    )
                }
            }
        }

        listener = object : AssetPackStateUpdateListener {
            override fun onStateUpdate(state: AssetPackState) {
                val name = state.name()
                val status = state.status()
                packStatusMap[name] = status

                when (status) {
                    AssetPackStatus.COMPLETED -> {
                        pendingStartTimestamps.remove(name)
                        val total = totalBytesMap[name] ?: 0L
                        downloadedBytesMap[name] = total
                        pendingPacks.remove(name)
                        updateProgressOrWifiState()
                        checkCompletion()
                    }

                    AssetPackStatus.PENDING -> {
                        pendingStartTimestamps.putIfAbsent(name, System.currentTimeMillis())
                        val bytes = state.bytesDownloaded()
                        val total = state.totalBytesToDownload()
                        if (total > 0L) totalBytesMap[name] = total
                        downloadedBytesMap[name] = bytes
                        updateProgressOrWifiState()
                    }

                    AssetPackStatus.WAITING_FOR_WIFI, AssetPackStatus.REQUIRES_USER_CONFIRMATION -> {
                        pendingStartTimestamps.remove(name)
                        messages += "Asset pack ($name) is waiting for Wi-Fi or user confirmation (status: $status)."
                        val bytes = state.bytesDownloaded()
                        val total = state.totalBytesToDownload()
                        if (total > 0L) totalBytesMap[name] = total
                        downloadedBytesMap[name] = bytes
                        updateProgressOrWifiState()
                    }

                    AssetPackStatus.DOWNLOADING, AssetPackStatus.TRANSFERRING -> {
                        pendingStartTimestamps.remove(name)
                        val bytes = state.bytesDownloaded()
                        val total = state.totalBytesToDownload()
                        if (bytes > (downloadedBytesMap[name] ?: 0L)) {
                            lastProgressTimestamp.set(System.currentTimeMillis())
                        }
                        if (total > 0L) totalBytesMap[name] = total
                        downloadedBytesMap[name] = bytes
                        updateProgressOrWifiState()
                    }

                    AssetPackStatus.FAILED, AssetPackStatus.CANCELED -> {
                        pendingStartTimestamps.remove(name)
                        val err = state.errorCode()
                        messages += "Asset pack ($name) failed or canceled (code: $err)."
                        failedPacks.add(name)
                        pendingPacks.remove(name)

                        val isPermanent = isPermanentAssetPackErrorCode(err)
                        if (isPermanent) {
                            // 영구 실패 시 트래커 상태는 바깥 루프 한 곳에서 Idle로 정리
                            safeResume(
                                AttemptOutcome.PermanentFailure(
                                    message = "Asset pack ($name) permanent failure (code: $err)",
                                    errorCode = err,
                                )
                            )
                            return
                        }

                        tracker.updateStatus(
                            EngineDownloadStatus.Failed(
                                errorCode = err,
                                message = "Download failed for $name",
                                isHumanModelOnly = isHumanModelOnly,
                            )
                        )

                        // 주 모델 실패 시 즉시 중단 및 재시도 대기
                        if (name == KatagoModelPackName && isMandatory) {
                            safeResume(
                                AttemptOutcome.RecoverableFailure(
                                    message = "Mandatory model pack ($name) failed (code: $err)",
                                    errorCode = err,
                                )
                            )
                        } else {
                            checkCompletion()
                        }
                    }
                }
            }
        }

        manager.registerListener(listener)

        // 진행 정체(stall) 감지 코루틴:
        // Wi-Fi 대기 중이거나 사용자 확인 대기 중에는 타이머 유예.
        // PENDING 상태는 최대 5분(PendingStallTimeoutMs)까지만 유예하고 초과 시 정체로 처리.
        stallMonitorJob = backgroundDownloadScope.launch {
            while (isActive && !isResumed.get()) {
                delay(5_000L)
                val anyWaitingForWifi = neededPacks.any { pack ->
                    val st = packStatusMap[pack]
                    st == AssetPackStatus.WAITING_FOR_WIFI || st == AssetPackStatus.REQUIRES_USER_CONFIRMATION
                }
                if (anyWaitingForWifi) {
                    lastProgressTimestamp.set(System.currentTimeMillis())
                    continue
                }

                val now = System.currentTimeMillis()
                val pendingPacksList = neededPacks.filter { packStatusMap[it] == AssetPackStatus.PENDING }
                val pendingExceeded = pendingPacksList.any { pack ->
                    val start = pendingStartTimestamps[pack] ?: now
                    now - start > PendingStallTimeoutMs
                }

                if (pendingPacksList.isNotEmpty() && !pendingExceeded) {
                    lastProgressTimestamp.set(now)
                    continue
                }

                val elapsed = now - lastProgressTimestamp.get()
                if (elapsed > 120_000L && !isResumed.get()) {
                    messages += "Asset pack download stalled for ${elapsed / 1000}s."
                    tracker.updateStatus(
                        EngineDownloadStatus.Failed(
                            errorCode = CustomErrorCodeStalled,
                            message = "Download stalled",
                            isHumanModelOnly = isHumanModelOnly,
                        )
                    )
                    safeResume(
                        AttemptOutcome.RecoverableFailure(
                            message = "Download stalled for ${elapsed / 1000}s",
                            errorCode = CustomErrorCodeStalled,
                        )
                    )
                    break
                }
            }
        }

        manager.fetch(neededPacks)
            .addOnSuccessListener { states ->
                val packStates = states.packStates()
                neededPacks.forEach { pack ->
                    val packState = packStates[pack] ?: return@forEach
                    val st = packState.status()
                    packStatusMap[pack] = st
                    val total = packState.totalBytesToDownload()
                    if (total > 0L) totalBytesMap[pack] = total

                    if (st == AssetPackStatus.COMPLETED) {
                        downloadedBytesMap[pack] = totalBytesMap[pack] ?: 0L
                        pendingPacks.remove(pack)
                    } else if (st == AssetPackStatus.PENDING) {
                        pendingStartTimestamps.putIfAbsent(pack, System.currentTimeMillis())
                    } else if (st == AssetPackStatus.FAILED || st == AssetPackStatus.CANCELED) {
                        val err = packState.errorCode()
                        messages += "Asset pack ($pack) initial state failed (code: $err)."
                        failedPacks.add(pack)
                        pendingPacks.remove(pack)
                        if (isPermanentAssetPackErrorCode(err)) {
                            safeResume(
                                AttemptOutcome.PermanentFailure(
                                    message = "Asset pack ($pack) permanent failure: $err",
                                    errorCode = err,
                                )
                            )
                            return@addOnSuccessListener
                        }
                    }
                }
                updateProgressOrWifiState()
                checkCompletion()
            }
            .addOnFailureListener { exception ->
                messages += "Asset pack fetch failed immediately: ${exception.message}"
                val (errorCode, isPermanent) = determineErrorClassification(exception)
                if (isPermanent) {
                    safeResume(
                        AttemptOutcome.PermanentFailure(
                            message = "Fetch failed: ${exception.message}",
                            errorCode = errorCode,
                        )
                    )
                } else {
                    tracker.updateStatus(
                        EngineDownloadStatus.Failed(
                            errorCode = errorCode,
                            message = exception.message ?: "Fetch failed",
                            isHumanModelOnly = isHumanModelOnly,
                        )
                    )
                    safeResume(
                        AttemptOutcome.RecoverableFailure(
                            message = "Fetch failed: ${exception.message}",
                            errorCode = errorCode,
                        )
                    )
                }
            }
    }

    return FetchAttemptResult(outcome = outcome, messages = messages)
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
        "Failed to seed bundled asset $assetPath: ${e.message}"
    }
}
