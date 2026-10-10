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

    /** 모바일 데이터에서 Wi-Fi 연결을 대기 중 */
    data class WaitingForWifi(
        val bytesDownloaded: Long,
        val totalBytesToDownload: Long,
    ) : EngineDownloadStatus

    /** 다운로드 실패 또는 일시정지 (재시도 가능) */
    data class Failed(
        val errorCode: Int,
        val message: String,
    ) : EngineDownloadStatus

    /** 다운로드 완료 */
    object Completed : EngineDownloadStatus
}

internal class EngineDownloadTracker {
    private val _status = MutableStateFlow<EngineDownloadStatus>(EngineDownloadStatus.Idle)
    val status: StateFlow<EngineDownloadStatus> = _status.asStateFlow()

    private val _isDismissed = MutableStateFlow(false)
    val isDismissed: StateFlow<Boolean> = _isDismissed.asStateFlow()

    private var retryHandler: (() -> Unit)? = null

    fun updateStatus(newStatus: EngineDownloadStatus) {
        if (newStatus is EngineDownloadStatus.Downloading || newStatus is EngineDownloadStatus.WaitingForWifi) {
            _isDismissed.value = false
        }
        _status.value = newStatus
    }

    fun dismiss() {
        _isDismissed.value = true
    }

    fun registerRetryHandler(handler: () -> Unit) {
        retryHandler = handler
    }

    fun retry() {
        _isDismissed.value = false
        retryHandler?.invoke()
    }
}

private val globalTrackerInstance = EngineDownloadTracker()

internal fun currentEngineDownloadTracker(): EngineDownloadTracker = globalTrackerInstance
