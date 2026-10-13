package com.worksoc.goaicoach.engine

import com.worksoc.goaicoach.platform.AppUpdateStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #255 — 엔진을 받기 전에 업데이트를 먼저 권하는 문. 사용자 결정(2026-10-13): 새 버전이 있으면 업데이트가 먼저,
 * 「이대로 받기」를 고르면 받기가 먼저. **확인을 못 하면 기다리지 않고 받는다** — 그 성질이 이 문에서 가장 중요하다.
 */
class UpdateBeforeEngineDownloadTest {

    @Test
    fun newVersionHoldsTheDownloadUntilTheUserChoosesToDownloadAnyway() = runBlocking(Dispatchers.Default) {
        val tracker = EngineDownloadTracker()
        val gate = async { awaitUpdateBeforeEngineDownload(tracker, checkUpdate = { AppUpdateStatus.Available }) }

        withTimeout(2_000) { while (tracker.status.value != EngineDownloadStatus.UpdateFirst) delay(5) }
        delay(50)
        assertFalse("고르기 전에는 받기로 넘어가지 않는다", gate.isCompleted)

        tracker.skipUpdate()
        withTimeout(2_000) { gate.await() }
        assertEquals("받기가 Downloading을 적기 전까지는 「준비하고 있어요」", EngineDownloadStatus.Idle, tracker.status.value)
    }

    @Test
    fun upToDateOrUnknownDownloadsRightAwayWithoutTouchingTheStatus() = runBlocking(Dispatchers.Default) {
        for (answer in listOf(AppUpdateStatus.UpToDate, AppUpdateStatus.Unknown, AppUpdateStatus.Checking)) {
            val tracker = EngineDownloadTracker()
            withTimeout(2_000) { awaitUpdateBeforeEngineDownload(tracker, checkUpdate = { answer }) }
            assertEquals("$answer 이면 묻지 않고 받는다", EngineDownloadStatus.Idle, tracker.status.value)
        }
    }

    @Test
    fun aSlowCheckDoesNotHoldTheDownloadPastTheLimit() = runBlocking(Dispatchers.Default) {
        val tracker = EngineDownloadTracker()
        withTimeout(2_000) {
            awaitUpdateBeforeEngineDownload(tracker, checkUpdate = { awaitCancellation() }, timeoutMillis = 50)
        }
        assertEquals(EngineDownloadStatus.Idle, tracker.status.value)
    }

    @Test
    fun downloadAnywayAlsoReopensADismissedCard() = runBlocking(Dispatchers.Default) {
        val tracker = EngineDownloadTracker()
        val chosen = CompletableDeferred<Unit>()
        tracker.registerSkipUpdateHandler { chosen.complete(Unit) }
        tracker.dismiss()
        tracker.skipUpdate()
        assertTrue(chosen.isCompleted)
        assertFalse("「이대로 받기」를 고른 뒤 받기 카드가 닫힌 채로 남지 않는다", tracker.isDismissed.value)
    }
}
