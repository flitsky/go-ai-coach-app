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

    /** #273 — 오늘의 한 줄 명언은 맨 끝이다(사용자 2026-10-20: 업데이트 알림이 사라진 뒤에). 알릴 것이 하나라도 있으면 기다린다. */
    @Test
    fun theDailyQuoteTakesTheSlotOnlyAfterEveryNoticeIsGone() {
        fun slot(engine: EngineDownloadStatus, update: AppUpdateStatus, updateDismissed: Boolean, whatsNew: Boolean) =
            homeTopNoticeOf(engine, update, updateDismissed, whatsNewPending = whatsNew, dailyQuoteWanted = true)

        assertEquals(HomeTopNoticeKind.EngineDownload, slot(EngineDownloadStatus.Downloading(1L, 2L, 50), AppUpdateStatus.UpToDate, false, whatsNew = false))
        assertEquals(HomeTopNoticeKind.WhatsNew, slot(EngineDownloadStatus.Completed, AppUpdateStatus.UpToDate, false, whatsNew = true))
        assertEquals(HomeTopNoticeKind.AppUpdate, slot(EngineDownloadStatus.Idle, AppUpdateStatus.Available, false, whatsNew = false))
        assertEquals("업데이트 알림을 ✕로 닫으면 그 자리에 뜬다", HomeTopNoticeKind.DailyQuote, slot(EngineDownloadStatus.Idle, AppUpdateStatus.Available, true, whatsNew = false))
        assertEquals(HomeTopNoticeKind.DailyQuote, slot(EngineDownloadStatus.Completed, AppUpdateStatus.UpToDate, false, whatsNew = false))
        assertEquals(
            "명언을 원하지 않으면(출석 팝업 전 · 오늘 닫음 · 실을 명언 없음) 자리는 빈다",
            HomeTopNoticeKind.None,
            homeTopNoticeOf(EngineDownloadStatus.Completed, AppUpdateStatus.UpToDate, updateDismissed = false, dailyQuoteWanted = false),
        )
    }
}
