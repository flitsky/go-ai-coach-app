package com.worksoc.goaicoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [EngineDownloadTracker]의 상태 머신 및 닫기/재시도 동작 검증 (백로그 #245 U-73).
 */
class EngineDownloadTrackerTest {

    @Test
    fun initialStateIsIdleAndNotDismissed() {
        val tracker = EngineDownloadTracker()
        assertEquals(EngineDownloadStatus.Idle, tracker.status.value)
        assertFalse(tracker.isDismissed.value)
    }

    @Test
    fun updatingStatusDoesNotResetDismissed() {
        val tracker = EngineDownloadTracker()
        tracker.dismiss()
        assertTrue(tracker.isDismissed.value)

        // 다운로드 진행 콜백이 지속적으로 들어와도 사용자가 닫은 상태가 풀리지 않아야 함
        tracker.updateStatus(
            EngineDownloadStatus.Downloading(
                bytesDownloaded = 10_000_000L,
                totalBytesToDownload = 100_000_000L,
                percentage = 10,
            )
        )
        assertTrue("진행 콜백으로 인해 닫힘 상태가 임의로 풀리면 안 된다", tracker.isDismissed.value)

        tracker.updateStatus(
            EngineDownloadStatus.WaitingForWifi(
                bytesDownloaded = 10_000_000L,
                totalBytesToDownload = 100_000_000L,
            )
        )
        assertTrue("Wi-Fi 대기 콜백으로 인해 닫힘 상태가 임의로 풀리면 안 된다", tracker.isDismissed.value)
    }

    @Test
    fun startNewDownloadResetsDismissed() {
        val tracker = EngineDownloadTracker()
        tracker.dismiss()
        assertTrue(tracker.isDismissed.value)

        tracker.startNewDownload()
        assertFalse(tracker.isDismissed.value)
    }

    @Test
    fun retryExecutesHandlerAndResetsDismissed() {
        val tracker = EngineDownloadTracker()
        tracker.dismiss()
        assertTrue(tracker.isDismissed.value)

        var retryCalled = false
        tracker.registerRetryHandler { retryCalled = true }

        tracker.retry()
        assertTrue("retry() 호출 시 등록된 핸들러가 실행되어야 함", retryCalled)
        assertFalse("retry() 호출 시 닫힘 상태가 다시 열려야 함", tracker.isDismissed.value)
    }
}
