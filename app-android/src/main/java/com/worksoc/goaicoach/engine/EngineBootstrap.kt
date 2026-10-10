package com.worksoc.goaicoach.engine

import android.content.Context
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
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
        fetchAndAwaitAssetPacksWithRetry(
            manager = manager,
            neededPacks = listOf(KatagoHumanPackName),
            isMandatory = false,
        )
    }
}

private sealed interface AttemptOutcome {
    object Success : AttemptOutcome
    data class RecoverableFailure(val message: String) : AttemptOutcome
    data class PermanentFailure(val message: String) : AttemptOutcome
}

/**
 * 네트워크 오류(-6)나 타임아웃(-2), 스토리지 부족(-10) 등 사용자가 조치 후 재시도 가능한 오류인지 판정한다.
 * Play Store가 없는 환경(make dev-stub, debug 빌드)의 오류(-5, -11 등)는 영구 실패로 취급하여 즉시 스텁으로 넘긴다.
 */
private fun isRecoverableAssetPackError(errorCode: Int): Boolean =
    when (errorCode) {
        AssetPackErrorCode.NETWORK_ERROR -> true
        AssetPackErrorCode.INSUFFICIENT_STORAGE -> true
        -2 -> true // 진행 정체 타임아웃
        else -> false
    }

/**
 * PAD on-demand 에셋 팩을 요청하고 완료까지 대기한다.
 *
 * 네트워크 일시 오류나 정체 시 [EngineDownloadStatus.Failed] 상태로 재시도 신호를 대기하고,
 * Play Store 밖 설치나 복구 불가능한 오류 시에는 즉시 스텁 fallback으로 진행한다.
 */
private suspend fun fetchAndAwaitAssetPacksWithRetry(
    manager: AssetPackManager,
    neededPacks: List<String>,
    isMandatory: Boolean,
): List<String> {
    if (neededPacks.isEmpty()) return emptyList()

    val messages = mutableListOf<String>()
    val tracker = currentEngineDownloadTracker()
    tracker.startNewDownload()

    while (currentCoroutineContext().isActive) {
        val (outcome, attemptMessages) = fetchAndAwaitAssetPacksAttempt(
            manager = manager,
            neededPacks = neededPacks,
            isMandatory = isMandatory,
        )
        messages += attemptMessages

        when (outcome) {
            is AttemptOutcome.Success -> {
                tracker.updateStatus(EngineDownloadStatus.Completed(isHumanModelOnly = !isMandatory))
                break
            }

            is AttemptOutcome.PermanentFailure -> {
                // Play가 모르는 설치(make dev-stub, 디버그 APK 등)이므로 재시도 루프를 탈출하고 스텁으로 전환
                messages += "Asset pack permanent failure: ${outcome.message}"
                if (isMandatory) {
                    tracker.updateStatus(
                        EngineDownloadStatus.Failed(-1, outcome.message, isHumanModelOnly = false)
                    )
                }
                break
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

private data class FetchAttemptResult(
    val outcome: AttemptOutcome,
    val messages: List<String>,
)

private suspend fun fetchAndAwaitAssetPacksAttempt(
    manager: AssetPackManager,
    neededPacks: List<String>,
    isMandatory: Boolean,
): FetchAttemptResult {
    val downloadedBytesMap = mutableMapOf<String, Long>()
    val totalBytesMap = mutableMapOf<String, Long>()
    val failedPacks = mutableSetOf<String>()

    val isHumanModelOnly = !isMandatory && neededPacks == listOf(KatagoHumanPackName)

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
                isHumanModelOnly = isHumanModelOnly,
            )
        )
    }

    updateDownloadProgress()

    val messages = mutableListOf<String>()

    // 진행 정체(stall) 감지: 120초 동안 바이트 수신이 전혀 없을 때만 타임아웃 판정 (느린 회선 보호)
    val lastProgressTimestamp = AtomicLong(System.currentTimeMillis())

    val outcome = suspendCancellableCoroutine<AttemptOutcome> { continuation ->
        val pendingPacks = neededPacks.toMutableSet()
        lateinit var listener: AssetPackStateUpdateListener

        // Wi-Fi 대기 중에도 재시도 핸들러를 배선하여 사용자가 즉시 다시 시도할 수 있게 함
        tracker.registerRetryHandler {
            if (continuation.isActive) {
                manager.unregisterListener(listener)
                continuation.resume(AttemptOutcome.RecoverableFailure("User triggered retry"))
            }
        }

        fun checkCompletion() {
            if (!continuation.isActive) return
            if (pendingPacks.isEmpty()) {
                manager.unregisterListener(listener)
                if (failedPacks.isEmpty()) {
                    continuation.resume(AttemptOutcome.Success)
                } else {
                    continuation.resume(
                        AttemptOutcome.RecoverableFailure("Some packs failed: ${failedPacks.joinToString()}")
                    )
                }
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
                        val err = state.errorCode()
                        messages += "Asset pack ($name) failed or canceled (code: $err)."
                        failedPacks.add(name)
                        pendingPacks.remove(name)

                        if (!isRecoverableAssetPackError(err)) {
                            // 복구 불가능한 영구 실패 (Play Store 없음 등)
                            if (continuation.isActive) {
                                manager.unregisterListener(this)
                                continuation.resume(
                                    AttemptOutcome.PermanentFailure("Asset pack ($name) permanent failure (code: $err)")
                                )
                                return
                            }
                        }

                        tracker.updateStatus(
                            EngineDownloadStatus.Failed(
                                errorCode = err,
                                message = "Download failed for $name",
                                isHumanModelOnly = isHumanModelOnly,
                            )
                        )

                        if (name == KatagoModelPackName && isMandatory && continuation.isActive) {
                            manager.unregisterListener(this)
                            continuation.resume(
                                AttemptOutcome.RecoverableFailure("Mandatory model pack ($name) failed (code: $err)")
                            )
                        } else {
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
                                isHumanModelOnly = isHumanModelOnly,
                            )
                        )
                    }

                    AssetPackStatus.DOWNLOADING, AssetPackStatus.TRANSFERRING -> {
                        val bytes = state.bytesDownloaded()
                        val total = state.totalBytesToDownload()
                        if (bytes > (downloadedBytesMap[name] ?: 0L)) {
                            lastProgressTimestamp.set(System.currentTimeMillis())
                        }
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

        // 진행 정체 모니터링 코루틴
        val stallMonitorJob = backgroundDownloadScope.launch {
            while (isActive && continuation.isActive) {
                delay(5_000L)
                val elapsed = System.currentTimeMillis() - lastProgressTimestamp.get()
                if (elapsed > 120_000L && continuation.isActive) {
                    messages += "Asset pack download stalled for ${elapsed / 1000}s."
                    tracker.updateStatus(
                        EngineDownloadStatus.Failed(
                            errorCode = -2,
                            message = "Download stalled",
                            isHumanModelOnly = isHumanModelOnly,
                        )
                    )
                    manager.unregisterListener(listener)
                    continuation.resume(AttemptOutcome.RecoverableFailure("Download stalled for ${elapsed / 1000}s"))
                    break
                }
            }
        }

        continuation.invokeOnCancellation {
            stallMonitorJob.cancel()
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
                        val err = packState.errorCode()
                        messages += "Asset pack ($pack) initial state failed (code: $err)."
                        failedPacks.add(pack)
                        pendingPacks.remove(pack)
                        if (!isRecoverableAssetPackError(err)) {
                            if (continuation.isActive) {
                                manager.unregisterListener(listener)
                                continuation.resume(
                                    AttemptOutcome.PermanentFailure("Asset pack ($pack) permanent failure: $err")
                                )
                                return@addOnSuccessListener
                            }
                        }
                    }
                }
                updateDownloadProgress()
                checkCompletion()
            }
            .addOnFailureListener { exception ->
                messages += "Asset pack fetch failed immediately: ${exception.message}"
                val isPermanent = exception.message?.contains("API_NOT_AVAILABLE", ignoreCase = true) == true ||
                    exception.message?.contains("Play Store", ignoreCase = true) == true
                val errOutcome = if (isPermanent || !isMandatory) {
                    AttemptOutcome.PermanentFailure("Fetch failed: ${exception.message}")
                } else {
                    AttemptOutcome.RecoverableFailure("Fetch failed: ${exception.message}")
                }
                tracker.updateStatus(
                    EngineDownloadStatus.Failed(
                        errorCode = -1,
                        message = exception.message ?: "Fetch failed",
                        isHumanModelOnly = isHumanModelOnly,
                    )
                )
                if (continuation.isActive) {
                    manager.unregisterListener(listener)
                    continuation.resume(errOutcome)
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
