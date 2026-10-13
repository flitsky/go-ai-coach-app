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
import com.worksoc.goaicoach.platform.processAppUpdateCheck
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
 * KataGo 엔진 모델을 찾고 부트스트랩을 구성한다 (백로그 #245 — PAD on-demand, #249 — 두 모델 필수).
 *
 * ## 1. 모델은 둘 다 있어야 엔진을 띄운다 (#249 U-78, 사용자 2026-10-11)
 * 캐릭터 다섯이 **모두 사람 모델로 둔다**(`HumanPlayStyle`). 사람 모델 없이 엔진을 띄우면 캐릭터가 주 모델 탐색으로
 * 조용히 바뀌어 「15급 판다」가 거짓이 된다. 그래서 「주 모델로 먼저 띄우고 사람 모델은 뒤에서」 경로를 없앴고,
 * 없는 팩은 **전부 필수**로 받는다. 받지 못하면 스텁이다. 주 모델은 착수 분석·형세처럼 정교한 분석에 쓴다.
 *
 * ## 2. 찾는 순서 — [locateEngineModels] 한 곳
 * ① 1.3.0 이하에서 기기의 `filesDir/katago/`에 저장된 파일(`.bin.gz` 또는 비압축) → ② 이미 받은 PAD 팩.
 * 실행 게이트(엔진 준비 화면)도 같은 함수로 판정한다 — 둘이 갈리면 화면은 「준비됨」인데 엔진은 받는 중이 된다.
 *
 * ## 3. PAD 팩은 복사하지 않는다 (Direct Path)
 * 팩이 도착하면 `AssetPackLocation.assetsPath()`의 경로를 KataGo에 그대로 넘긴다 — 디스크 약 197MB · 복사 I/O 0초.
 * 에셋 팩 안의 이름은 `.gz`가 보존된다(2026-10-10 실측) — KataGo는 `.bin.gz`를 그대로 읽는다.
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

    // 1·2단계: 기기 파일 → 이미 받은 팩(실행 게이트와 같은 판정).
    var models = locateEngineModels(katagoDir, assetPackManager)

    // 3단계: 없는 팩을 받는다 — 둘 다 필수다(위 KDoc 1).
    val packMessages = mutableListOf<String>()
    if (assetPackManager != null && models.missingPacks.isNotEmpty()) {
        // 새 버전이 있으면 업데이트를 먼저 권한다(#255) — 확인을 못 하면 기다리지 않고 받는다.
        awaitUpdateBeforeEngineDownload(
            tracker = currentEngineDownloadTracker(),
            checkUpdate = { processAppUpdateCheck(context).ensureChecked() },
        )
        packMessages += fetchAndAwaitAssetPacksWithRetry(
            manager = assetPackManager,
            neededPacks = models.missingPacks,
        )
        models = locateEngineModels(katagoDir, assetPackManager)
    }
    val localModel = models.main
    val localHumanModel = models.human

    val missing = buildList {
        if (!executable.canExecute()) {
            add("native lib")
        }
        if (localModel == null) {
            add("model.bin.gz")
        }
        if (localHumanModel == null) {
            add("human.bin.gz")
        }
        if (!config.isFile) {
            add("gtp_learning.cfg")
        }
    }

    if (missing.isNotEmpty() || localModel == null || localHumanModel == null) {
        return EngineBootstrap(
            coreApi = EngineCoreApiFactory.stub(),
            mode = EngineMode.Stub,
            displayName = "stub AI",
            diagnostic = buildString {
                append("Stub fallback: missing ${missing.joinToString()}. ")
                // 프로덕션의 원인은 거의 늘 받기 실패다(#254) — 그 코드를 먼저 적고, 개발용 안내는 그 뒤에 둔다.
                currentEngineDownloadTracker().lastPermanentFailureCode?.let { append("PAD download failed permanently (code $it). ") }
                append("Dev builds: run make install-dev-engine / make seed-engine, then restart the app.")
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
                humanModelPath = localHumanModel.absolutePath,
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
            append(" Human model found: ${localHumanModel.name}.")
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

/** 정체 감지 코루틴이 도는 곳 — 받기 시도와 수명이 같지 않아 따로 둔다. */
private val backgroundDownloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** 로그용 이름 — release 빌드는 난독화돼 `toString()`이 `xc.c@4209b10`처럼 찍힌다(2026-10-11 폰 로그). */
private fun AttemptOutcome.logLabel(): String = when (this) {
    AttemptOutcome.Success -> "Success"
    AttemptOutcome.UserTriggeredRetry -> "UserTriggeredRetry"
    is AttemptOutcome.RecoverableFailure -> "RecoverableFailure(code=$errorCode, $message)"
    is AttemptOutcome.PermanentFailure -> "PermanentFailure(code=$errorCode, $message)"
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
        PlayStoreNotFoundErrorCode -> true
        else -> false
    }

internal const val PlayStoreNotFoundErrorCode = -11 // AssetPackErrorCode.PLAY_STORE_NOT_FOUND

/**
 * Play 스토어 밖 설치처럼 재시작·재설치가 아니라 「Google Play에서 설치」가 답인 영구 실패인지 판정한다.
 * 스텁 팝업의 문구를 이것으로 가른다([EngineDownloadTracker.isOfficialPlatformPermanentFailure]).
 */
internal fun isStoreInstallationErrorCode(errorCode: Int?): Boolean =
    when (errorCode) {
        AssetPackErrorCode.APP_NOT_OWNED,
        AssetPackErrorCode.UNRECOGNIZED_INSTALLATION,
        PlayStoreNotFoundErrorCode -> true
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
    if (msg.contains("PLAY_STORE_NOT_FOUND", ignoreCase = true) || msg.contains("Play Store", ignoreCase = true)) {
        // 공식 코드(-11)와 같게 다뤄 스텁 팝업이 「Google Play에서 설치」 문구를 고르게 한다.
        return Pair(PlayStoreNotFoundErrorCode, true)
    }
    val isPermanent = msg.contains("API_NOT_AVAILABLE", ignoreCase = true) ||
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
    tracker: EngineDownloadTracker = currentEngineDownloadTracker(),
): List<String> {
    if (neededPacks.isEmpty()) return emptyList()

    val messages = mutableListOf<String>()
    tracker.startNewDownload()

    while (currentCoroutineContext().isActive) {
        val (outcome, attemptMessages) = fetchAndAwaitAssetPacksAttempt(
            manager = manager,
            neededPacks = neededPacks,
            tracker = tracker,
        )
        messages += attemptMessages
        padLog("Attempt for $neededPacks ended: ${outcome.logLabel()}")

        when (outcome) {
            is AttemptOutcome.Success -> {
                tracker.updateStatus(EngineDownloadStatus.Completed)
                break
            }

            is AttemptOutcome.PermanentFailure -> {
                // Play가 모르는 설치(make dev-stub, 디버그 APK 등) 또는 스토어 영구 실패이므로 스텁으로 전환.
                // 부트스트랩 루프가 즉시 종료되고 스텁 모드로 진입하므로, 반응 없는 죽은 재시도 버튼을
                // 남기지 않고 카드를 Idle로 닫는다. 스텁 안내는 부트스트랩 완료 후 다이얼로그가 맡는다.
                messages += "Asset pack permanent failure: ${outcome.message} (code: ${outcome.errorCode})"
                tracker.lastPermanentFailureCode = outcome.errorCode
                tracker.updateStatus(EngineDownloadStatus.Idle)
                break
            }

            is AttemptOutcome.UserTriggeredRetry -> {
                // 사용자가 다운로드 중 또는 Wi-Fi 대기 중에 [다시 시도]를 눌러 중단한 경우:
                // 추가 대기 없이 즉시 새 attempt 시작 (2회 탭 버그 방지)
                continue
            }

            is AttemptOutcome.RecoverableFailure -> {
                // ⚠️ 몇 번이 겹쳐도 영구 실패로 바꾸지 않는다(#251) — 예전에는 INTERNAL_ERROR 3회면 스텁으로 갔고, 엔진 준비는
                // 프로세스에 한 번이라 강제 종료 전까지 다시 받을 길이 없었다. 재시도는 사용자가 누를 때만 도니 헛도는 고리도 없다.
                // ⚠️ 재시도 핸들러를 Failed보다 먼저 건다 — 반대 순서면 Failed가 보인 직후의 탭이
                // 끝난 시도의 핸들러로 가서 사라진다.
                val retryDeferred = CompletableDeferred<Unit>()
                tracker.registerRetryHandler {
                    if (!retryDeferred.isCompleted) {
                        retryDeferred.complete(Unit)
                    }
                }

                // 바깥 루프 단 한 곳에서 트래커의 Failed 상태를 설정 ("한 규칙은 한 곳에")
                tracker.updateStatus(
                    EngineDownloadStatus.Failed(
                        errorCode = outcome.errorCode,
                        message = outcome.message,
                    )
                )

                // 사용자가 [다시 시도]를 누를 때까지 suspend 대기
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

/**
 * 정체 타이머를 대기열(PENDING) 때문에 멈춰 둘 것인가(#252) — 아직 안 끝난 팩 가운데 **대기열에 있는 팩이 있고**, 그 대기가 5분을
 * 넘지 않았고, **실제로 받는 팩이 하나도 없을 때**만 그렇다. 하나라도 받고 있으면 정체는 평소대로(어느 팩이든 진행이 있으면 산다) 잰다.
 */
internal fun isStallTimerHeldByQueue(
    statuses: Map<String, Int>,
    unfinishedPacks: Set<String>,
    pendingExceeded: Boolean,
): Boolean {
    if (pendingExceeded) return false
    val queued = unfinishedPacks.filter { statuses[it] == AssetPackStatus.PENDING }
    val receiving = unfinishedPacks.filter { statuses[it] != AssetPackStatus.PENDING }
    return queued.isNotEmpty() && receiving.isEmpty()
}

internal data class FetchAttemptResult(
    val outcome: AttemptOutcome,
    val messages: List<String>,
)

private const val PendingStallTimeoutMs = 300_000L // 5분

internal suspend fun fetchAndAwaitAssetPacksAttempt(
    manager: AssetPackManager,
    neededPacks: List<String>,
    tracker: EngineDownloadTracker = currentEngineDownloadTracker(),
): FetchAttemptResult {
    val downloadedBytesMap = mutableMapOf<String, Long>()
    val totalBytesMap = mutableMapOf<String, Long>()
    val packStatusMap = java.util.concurrent.ConcurrentHashMap<String, Int>()
    val pendingStartTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()
    val isResumed = AtomicBoolean(false)

    // ⚠️ 리스너·fetch 콜백(메인)과 정체 감지(IO)가 다른 스레드에서 돈다. 상태 갱신과 종료(safeResume)를 이 락 하나로
    // 묶어야 「isResumed 확인 → 트래커 쓰기」 사이에 시도가 끝나 바깥 루프의 Failed·Completed를 덮어쓰는 일이 없다.
    val stateLock = Any()

    // ⚠️ 받는 팩은 **전부 필수**다(#249 U-78) — 어느 하나가 실패하면 이 시도가 실패한다. 예전의 「선택 팩」(사람 모델이
    // 실패해도 주 모델만으로 성공)은 캐릭터가 사람 모델로 두게 되면서 결함이 됐다.

    neededPacks.forEach { pack ->
        downloadedBytesMap[pack] = 0L
        packStatusMap[pack] = AssetPackStatus.UNKNOWN
        totalBytesMap[pack] = if (pack == KatagoModelPackName) {
            KatagoModelPackEstimatedBytes
        } else {
            KatagoHumanPackEstimatedBytes
        }
    }

    fun updateProgressOrWifiState(): Unit = synchronized(stateLock) {
        // ⚠️ 시도가 끝난 뒤 늦게 온 콜백(fetch 성공 등)이 바깥 루프가 세운 Failed를 덮어쓰면 재시도 버튼이 사라진다.
        if (isResumed.get()) return
        val isConfirmationRequired = neededPacks.any { pack ->
            packStatusMap[pack] == AssetPackStatus.REQUIRES_USER_CONFIRMATION
        }
        val anyWaitingForWifi = isConfirmationRequired || neededPacks.any { pack ->
            packStatusMap[pack] == AssetPackStatus.WAITING_FOR_WIFI
        }
        val currentDownloaded = neededPacks.sumOf { downloadedBytesMap[it] ?: 0L }
        val currentTotal = neededPacks.sumOf { totalBytesMap[it] ?: 0L }.coerceAtLeast(1L)
        val percentage = ((currentDownloaded * 100) / currentTotal).toInt().coerceIn(0, 100)

        if (anyWaitingForWifi) {
            tracker.updateStatus(
                EngineDownloadStatus.WaitingForWifi(
                    bytesDownloaded = currentDownloaded,
                    totalBytesToDownload = currentTotal,
                    isConfirmationRequired = isConfirmationRequired,
                )
            )
        } else {
            tracker.updateStatus(
                EngineDownloadStatus.Downloading(
                    bytesDownloaded = currentDownloaded,
                    totalBytesToDownload = currentTotal,
                    percentage = percentage,
                )
            )
        }
    }

    updateProgressOrWifiState()

    val messages = mutableListOf<String>()

    val outcome = suspendCancellableCoroutine<AttemptOutcome> { continuation ->
        val pendingPacks = neededPacks.toMutableSet()
        val lastProgressTimestamp = AtomicLong(System.currentTimeMillis())

        lateinit var listener: AssetPackStateUpdateListener
        var stallMonitorJob: Job? = null

        fun safeResume(result: AttemptOutcome): Unit = synchronized(stateLock) {
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
            if (isResumed.get() || pendingPacks.isNotEmpty()) return
            // 실패한 팩은 [onPackState]가 그 자리에서 시도를 끝낸다 — 여기 닿았으면 전부 받았다.
            safeResume(AttemptOutcome.Success)
        }

        /**
         * 리스너와 fetch 결과가 함께 쓰는 유일한 상태 처리 — 팩 상태·바이트·종료 판정을 한 곳에서 정한다.
         * ⚠️ 트래커의 Failed/Idle은 여기서 쓰지 않는다 — 바깥 루프 한 곳이 맡는다 ("한 규칙은 한 곳에").
         *
         * @param fromFetchResult fetch()의 스냅샷이면 true. 리스너가 이미 더 새로운 상태를 전달했으면
         *   스냅샷은 바이트만 보태고 상태를 되돌리거나 분기를 다시 타지 않는다.
         */
        fun onPackState(
            name: String,
            status: Int,
            errorCode: Int,
            bytes: Long,
            total: Long,
            fromFetchResult: Boolean,
        ): Unit = synchronized(stateLock) {
            if (isResumed.get() || name !in neededPacks) return
            if (total > 0L) totalBytesMap[name] = total
            if (fromFetchResult && packStatusMap[name] != AssetPackStatus.UNKNOWN) {
                if (bytes > 0L) downloadedBytesMap[name] = maxOf(downloadedBytesMap[name] ?: 0L, bytes)
                updateProgressOrWifiState()
                return
            }
            if (packStatusMap[name] != status) {
                padLog("Pack $name status ${packStatusMap[name]} -> $status (error $errorCode, $bytes/$total bytes)")
            }
            packStatusMap[name] = status

            when (status) {
                AssetPackStatus.COMPLETED -> {
                    pendingStartTimestamps.remove(name)
                    downloadedBytesMap[name] = totalBytesMap[name] ?: 0L
                    pendingPacks.remove(name)
                }

                AssetPackStatus.PENDING -> {
                    pendingStartTimestamps.putIfAbsent(name, System.currentTimeMillis())
                    downloadedBytesMap[name] = bytes
                }

                AssetPackStatus.WAITING_FOR_WIFI, AssetPackStatus.REQUIRES_USER_CONFIRMATION -> {
                    pendingStartTimestamps.remove(name)
                    messages += "Asset pack ($name) is waiting for Wi-Fi or user confirmation (status: $status)."
                    downloadedBytesMap[name] = bytes
                }

                AssetPackStatus.DOWNLOADING, AssetPackStatus.TRANSFERRING -> {
                    pendingStartTimestamps.remove(name)
                    if (bytes > (downloadedBytesMap[name] ?: 0L)) {
                        lastProgressTimestamp.set(System.currentTimeMillis())
                    }
                    downloadedBytesMap[name] = bytes
                }

                AssetPackStatus.FAILED, AssetPackStatus.CANCELED -> {
                    pendingStartTimestamps.remove(name)
                    messages += "Asset pack ($name) failed or canceled (code: $errorCode)."
                    pendingPacks.remove(name)
                    // 받는 팩은 전부 필수다 — 하나의 실패가 곧 이 시도의 실패다. 영구면 스텁, 아니면 재시도 대기.
                    safeResume(
                        if (isPermanentAssetPackErrorCode(errorCode)) {
                            AttemptOutcome.PermanentFailure(
                                message = "Asset pack ($name) permanent failure (code: $errorCode)",
                                errorCode = errorCode,
                            )
                        } else {
                            AttemptOutcome.RecoverableFailure(
                                message = "Asset pack ($name) failed (code: $errorCode)",
                                errorCode = errorCode,
                            )
                        }
                    )
                    return
                }
            }
            updateProgressOrWifiState()
            checkCompletion()
        }

        listener = object : AssetPackStateUpdateListener {
            override fun onStateUpdate(state: AssetPackState) {
                onPackState(
                    name = state.name(),
                    status = state.status(),
                    errorCode = state.errorCode(),
                    bytes = state.bytesDownloaded(),
                    total = state.totalBytesToDownload(),
                    fromFetchResult = false,
                )
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

                // 대기열(PENDING)의 유예는 **실제로 받는 팩이 하나도 없을 때만** 준다(#252). 예전에는 아무 팩이나 대기 중이면 타이머를
                // 되돌려, 두 팩을 함께 받다 한 팩이 대기열에 있는 동안 다른 팩이 바이트 없이 멈춰도 최대 5분을 놓쳤다.
                // 팩별로 따로 재지는 않는다 — Play가 팩을 차례로 받는 느린 망에서 뒤의 팩을 정체로 잘못 볼 수 있다. 진행은 어느 팩이든 센다.
                val unfinished = synchronized(stateLock) { pendingPacks.toSet() }
                if (isStallTimerHeldByQueue(packStatusMap, unfinished, pendingExceeded)) {
                    lastProgressTimestamp.set(now)
                    continue
                }

                val elapsed = now - lastProgressTimestamp.get()
                if (elapsed > 120_000L && !isResumed.get()) {
                    // ⚠️ 트래커 상태는 바깥 루프 한 곳이 맡는다 ("한 규칙은 한 곳에").
                    synchronized(stateLock) {
                        if (!isResumed.get()) messages += "Asset pack download stalled for ${elapsed / 1000}s."
                        safeResume(
                            AttemptOutcome.RecoverableFailure(
                                message = "Download stalled for ${elapsed / 1000}s",
                                errorCode = CustomErrorCodeStalled,
                            )
                        )
                    }
                    break
                }
            }
        }

        padLog("fetch($neededPacks)")
        manager.fetch(neededPacks)
            .addOnSuccessListener { states ->
                synchronized(stateLock) {
                    val packStates = states.packStates()
                    neededPacks.forEach { pack ->
                        val packState = packStates[pack] ?: return@forEach
                        onPackState(
                            name = pack,
                            status = packState.status(),
                            errorCode = packState.errorCode(),
                            bytes = packState.bytesDownloaded(),
                            total = packState.totalBytesToDownload(),
                            fromFetchResult = true,
                        )
                    }
                    updateProgressOrWifiState()
                    checkCompletion()
                }
            }
            .addOnFailureListener { exception ->
                padLog("fetch($neededPacks) failed immediately", exception)
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
                    // ⚠️ 트래커 상태는 바깥 루프 한 곳이 맡는다 ("한 규칙은 한 곳에").
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

/** 받기 경로의 로그 태그 — `adb logcat -s EnginePad`로 받기 한 번의 흐름을 본다(#249, #246 실기 확인용). */
internal const val PadLogTag = "EnginePad"

/**
 * 받기 경로의 로그 한 줄. 2026-10-11 폰 재현 때 앱 쪽 로그가 없어 Play 스토어 로그로만 흐름을 읽었다 — 그 빈칸을 메운다.
 * ⚠️ JVM 단위 테스트에서는 `android.util.Log`가 구현이 없어 던진다(`not mocked`) — 그 예외만 삼킨다.
 */
internal fun padLog(message: String, error: Throwable? = null) {
    try {
        if (error == null) android.util.Log.i(PadLogTag, message) else android.util.Log.w(PadLogTag, message, error)
    } catch (_: RuntimeException) {
    }
}

/**
 * 엔진 모델 두 파일의 자리. 없으면 `null`이다.
 *
 * @property missingPacks 받아야 할 PAD 팩 — 비어 있어야 엔진을 띄울 수 있다(#249 U-78: 둘 다 필수).
 */
internal data class EngineModelFiles(
    val main: File?,
    val human: File?,
) {
    val missingPacks: List<String>
        get() = buildList {
            if (main == null) add(KatagoModelPackName)
            if (human == null) add(KatagoHumanPackName)
        }
}

/**
 * 엔진 모델을 찾는 **유일한** 판정 — 부트스트랩과 실행 게이트(엔진 준비 화면)가 함께 쓴다(#249).
 * ① `filesDir/katago/`의 파일(1.3.0 이하 설치) → ② 이미 받은 PAD 팩. 디스크 존재 확인과 `getPackLocation`뿐이라
 * 메인 스레드에서 불러도 된다(스플래시 안에서 판정을 끝내야 홈이 먼저 보였다 가이드로 바뀌지 않는다).
 */
internal fun locateEngineModels(
    katagoDir: File,
    assetPackManager: AssetPackManager?,
): EngineModelFiles {
    fun localFile(compressedName: String, plainName: String): File? =
        File(katagoDir, compressedName).takeIf { it.isFile && it.length() > 0L }
            ?: File(katagoDir, plainName).takeIf { it.isFile && it.length() > 0L }

    val main = localFile("model.bin.gz", "model.bin")
        ?: assetPackManager?.let { resolvePackAssetFile(it, KatagoModelPackName, "katago/model.bin.gz") }
    val human = localFile(HumanModelCompressedName, HumanModelName)
        ?: assetPackManager?.let { resolvePackAssetFile(it, KatagoHumanPackName, "katago/human.bin.gz") }
    return EngineModelFiles(main = main, human = human)
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
