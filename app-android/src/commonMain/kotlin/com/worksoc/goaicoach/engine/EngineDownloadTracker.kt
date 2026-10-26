package com.worksoc.goaicoach.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Play Asset Delivery on-demand 에셋 팩 다운로드 진행 상태 (백로그 #245 U-73).
 *
 * 로비의 가이드 카드 컴포저블이 이 상태를 관찰하여 실시간 진행률(MB / 전체 MB) 및
 * Wi-Fi 대기, 실패 시 재시도 버튼, 닫기 기능을 렌더링한다.
 */
sealed interface EngineDownloadStatus {
    /** 팩 다운로드가 필요 없거나 아직 시작되지 않은 상태 */
    object Idle : EngineDownloadStatus

    /** 팩 다운로드 진행 중 */
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytesToDownload: Long,
        val percentage: Int,
    ) : EngineDownloadStatus

    /** 모바일 데이터에서 Wi-Fi 연결 또는 사용자 확인을 대기 중 */
    data class WaitingForWifi(
        val bytesDownloaded: Long,
        val totalBytesToDownload: Long,
        val isConfirmationRequired: Boolean = false,
    ) : EngineDownloadStatus

    /** 다운로드 실패 또는 일시정지 (재시도 가능) */
    data class Failed(
        val errorCode: Int,
        val message: String,
    ) : EngineDownloadStatus

    /** 다운로드 완료 — 받는 팩은 전부 필수라(#249 U-78) 완료는 곧 두 모델이 다 있다는 뜻이다. */
    object Completed : EngineDownloadStatus

    /**
     * 새 버전이 있어 **받기 전에 업데이트를 권하는 중**(#255 — 사용자 2026-10-13: *"업데이트 먼저 해서 최신 앱에서 엔진을 받게"*).
     * 받기는 시작하지 않았다 — 사용자가 「이대로 받기」([EngineDownloadTracker.skipUpdate])를 고르면 받기가 이어진다.
     * 「업데이트」는 스토어로 보낸다. 업데이트가 끝나 새 프로세스가 뜨면 확인이 「최신」이라 바로 받는다.
     */
    object UpdateFirst : EngineDownloadStatus
}

internal class EngineDownloadTracker {
    private val _status = MutableStateFlow<EngineDownloadStatus>(EngineDownloadStatus.Idle)
    val status: StateFlow<EngineDownloadStatus> = _status.asStateFlow()

    private val _isDismissed = MutableStateFlow(false)
    val isDismissed: StateFlow<Boolean> = _isDismissed.asStateFlow()

    private var retryHandler: (() -> Unit)? = null
    private var skipUpdateHandler: (() -> Unit)? = null
    /**
     * 「모바일 데이터로 계속」이 띄울 Play 승인 창 핸들러 — 배너가 떠 있는 **화면마다** 하나씩 건다(Activity가 있어야 창이 열린다).
     * ⚠️ 자리 하나였을 때(#252 전) 홈 배너가 건 뒤 엔진 준비 화면이 덮어쓰고, 준비 화면이 닫히며 지우면 자리가 비어
     *   홈 배너를 눌러도 아무 일이 없었다. 그래서 쌓는다 — 가장 최근에 건 살아 있는 것이 받고, 닫히는 화면은 제 것만 뺀다.
     */
    private val cellularConfirmationHandlers = mutableListOf<() -> Unit>()

    /**
     * 진행 상황을 갱신한다.
     * ⚠️ 사용자가 닫기(X)를 누른 [isDismissed] 상태를 임의로 풀지 않는다 (진행 바이트 수신마다 되살아나는 버그 방지).
     */
    fun updateStatus(newStatus: EngineDownloadStatus) {
        _status.value = newStatus
    }

    /** 새 다운로드를 시작할 때 호출되어 닫힘 상태를 초기화한다. */
    fun startNewDownload() {
        _isDismissed.value = false
        lastPermanentFailureCode = null
    }

    fun dismiss() {
        _isDismissed.value = true
    }

    /** 닫힌 다운로드 카드를 다시 연다. */
    fun reopen() {
        _isDismissed.value = false
    }

    fun registerRetryHandler(handler: () -> Unit) {
        retryHandler = handler
    }

    fun retry() {
        _isDismissed.value = false
        retryHandler?.invoke()
    }

    /** [EngineDownloadStatus.UpdateFirst] 동안 「이대로 받기」를 받을 곳 — 받기를 미뤄 둔 부트스트랩이 건다(#255). */
    fun registerSkipUpdateHandler(handler: () -> Unit) {
        skipUpdateHandler = handler
    }

    /** 「이대로 받기」 — 업데이트하지 않고 지금 버전으로 엔진을 받는다(#255). */
    fun skipUpdate() {
        _isDismissed.value = false
        skipUpdateHandler?.invoke()
    }

    var lastPermanentFailureCode: Int? = null

    /** 판정 목록은 [isStoreInstallationErrorCode] 한 곳에 있다 — 영구 실패 목록과 함께 고친다. */
    fun isOfficialPlatformPermanentFailure(): Boolean = isStoreInstallationErrorCode(lastPermanentFailureCode)

    fun registerCellularConfirmationHandler(handler: () -> Unit) {
        cellularConfirmationHandlers.remove(handler)
        cellularConfirmationHandlers.add(handler)
    }

    fun unregisterCellularConfirmationHandler(handler: () -> Unit) {
        cellularConfirmationHandlers.remove(handler)
    }

    fun requestCellularConfirmation() {
        cellularConfirmationHandlers.lastOrNull()?.invoke()
    }
}

private val globalTrackerInstance = EngineDownloadTracker()

internal fun currentEngineDownloadTracker(): EngineDownloadTracker = globalTrackerInstance
