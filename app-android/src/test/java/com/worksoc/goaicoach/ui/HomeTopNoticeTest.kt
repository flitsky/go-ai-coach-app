package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.engine.EngineDownloadStatus
import com.worksoc.goaicoach.platform.AppUpdateStatus
import com.worksoc.goaicoach.ui.setup.HomeTopNoticeKind
import com.worksoc.goaicoach.ui.setup.homeTopNoticeOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 백로그 #255 — 홈 한 줄 슬롯을 엔진 받기(#248)와 앱 업데이트가 나눠 쓰는 규칙. 사용자 결정(2026-10-13):
 * 엔진이 없고 새 버전이 있으면 업데이트가 먼저(받기 상태가 「업데이트 먼저」), 「이대로 받기」를 고르면 받기가 먼저.
 */
class HomeTopNoticeTest {

    @Test
    fun aMovingEngineDownloadOwnsTheSlotEvenWithANewVersion() {
        val moving = listOf(
            EngineDownloadStatus.UpdateFirst,
            EngineDownloadStatus.Downloading(1L, 2L, 50),
            EngineDownloadStatus.WaitingForWifi(1L, 2L),
            EngineDownloadStatus.Failed(-6, "x"),
        )
        for (engine in moving) {
            assertEquals("$engine", HomeTopNoticeKind.EngineDownload, homeTopNoticeOf(engine, AppUpdateStatus.Available, updateDismissed = false))
        }
    }

    @Test
    fun theUpdateNoticeShowsOnlyForANewVersionThatWasNotDismissedThisRun() {
        for (engine in listOf(EngineDownloadStatus.Idle, EngineDownloadStatus.Completed)) {
            assertEquals(HomeTopNoticeKind.AppUpdate, homeTopNoticeOf(engine, AppUpdateStatus.Available, updateDismissed = false))
            assertEquals("✕로 닫으면 이번 실행 동안은 안 보인다", HomeTopNoticeKind.None, homeTopNoticeOf(engine, AppUpdateStatus.Available, updateDismissed = true))
            for (quiet in listOf(AppUpdateStatus.Checking, AppUpdateStatus.UpToDate, AppUpdateStatus.Unknown)) {
                assertEquals("「$quiet」은 홈에서는 소음이다(설정에만)", HomeTopNoticeKind.None, homeTopNoticeOf(engine, quiet, updateDismissed = false))
            }
        }
    }

    /** #257 — 「새로워진 점」은 받기 다음, 업데이트 알림 앞이다(한 번 보면 끝나는 것이 먼저). */
    @Test
    fun whatsNewComesAfterTheEngineDownloadAndBeforeTheUpdateNotice() {
        assertEquals(
            HomeTopNoticeKind.EngineDownload,
            homeTopNoticeOf(EngineDownloadStatus.Downloading(1L, 2L, 50), AppUpdateStatus.Available, updateDismissed = false, whatsNewPending = true),
        )
        assertEquals(
            HomeTopNoticeKind.WhatsNew,
            homeTopNoticeOf(EngineDownloadStatus.Completed, AppUpdateStatus.Available, updateDismissed = false, whatsNewPending = true),
        )
        assertEquals(
            HomeTopNoticeKind.AppUpdate,
            homeTopNoticeOf(EngineDownloadStatus.Idle, AppUpdateStatus.Available, updateDismissed = false, whatsNewPending = false),
        )
    }

    /** #303 — 오늘의 명언은 이 자리를 떠나 대국 페이지 맨 위에 늘 있다(사용자 2026-11-06). 알릴 것이 없으면 이 자리는 빈다. */
    @Test
    fun theSlotIsEmptyWhenNothingNeedsSaying() {
        assertEquals(HomeTopNoticeKind.None, homeTopNoticeOf(EngineDownloadStatus.Completed, AppUpdateStatus.UpToDate, updateDismissed = false))
        assertEquals("업데이트 알림을 ✕로 닫아도 명언이 들어오지 않는다", HomeTopNoticeKind.None, homeTopNoticeOf(EngineDownloadStatus.Idle, AppUpdateStatus.Available, updateDismissed = true))
    }
}
