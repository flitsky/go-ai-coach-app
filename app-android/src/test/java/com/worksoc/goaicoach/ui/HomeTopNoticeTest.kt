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
}
