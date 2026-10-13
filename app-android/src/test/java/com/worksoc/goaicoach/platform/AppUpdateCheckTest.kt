package com.worksoc.goaicoach.platform

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 백로그 #255 — 「새 버전이 있는가」는 **프로세스마다 한 번** 묻고 엔진 받기 · 홈 · 설정이 그 답을 나눠 쓴다.
 * 홈 알림이 「콜드 스타트마다 다시 뜬다」(사용자 2026-10-13)는 단위와 같다.
 */
class AppUpdateCheckTest {

    @Test
    fun asksOnceAndSharesTheAnswer() = runBlocking(Dispatchers.Default) {
        val calls = AtomicInteger()
        val check = AppUpdateCheck {
            calls.incrementAndGet()
            delay(20)
            AppUpdateStatus.Available
        }
        val answers = List(5) { async { check.ensureChecked() } }.awaitAll()
        assertEquals(List(5) { AppUpdateStatus.Available }, answers)
        assertEquals("동시에 물어도 Play에는 한 번만 묻는다", 1, calls.get())
        assertEquals(AppUpdateStatus.Available, check.status.value)
    }

    @Test
    fun aThrowingRequestBecomesUnknownNotAnError() = runBlocking(Dispatchers.Default) {
        val check = AppUpdateCheck { error("Play services missing") }
        assertEquals(AppUpdateStatus.Unknown, check.ensureChecked())
        assertEquals(AppUpdateStatus.Unknown, check.status.value)
    }

    @Test
    fun aCancelledCheckIsAskedAgainNextTime() = runBlocking(Dispatchers.Default) {
        val calls = AtomicInteger()
        val check = AppUpdateCheck {
            if (calls.incrementAndGet() == 1) awaitCancellation() else AppUpdateStatus.UpToDate
        }
        assertEquals(null, withTimeoutOrNull(50) { check.ensureChecked() })
        assertEquals("시간 제한으로 취소된 조회는 답으로 적지 않는다", AppUpdateStatus.Checking, check.status.value)
        assertEquals(AppUpdateStatus.UpToDate, check.ensureChecked())
    }

    @Test
    fun dismissingTheNoticeLastsOnlyAsLongAsTheProcess() {
        val check = AppUpdateCheck { AppUpdateStatus.Available }
        assertEquals(false, check.noticeDismissed.value)
        check.dismissNotice()
        assertEquals(true, check.noticeDismissed.value)
        assertEquals("새 프로세스(콜드 스타트)의 객체는 닫힌 적이 없다", false, AppUpdateCheck { AppUpdateStatus.Available }.noticeDismissed.value)
    }
}
