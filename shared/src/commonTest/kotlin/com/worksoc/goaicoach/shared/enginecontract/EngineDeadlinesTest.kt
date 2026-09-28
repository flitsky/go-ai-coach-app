package com.worksoc.goaicoach.shared.enginecontract

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 엔진 호출의 마감 — 로컬 어댑터가 `engine-android`에서 쓰던 값 그대로여야 한다(refactor backlog #17은 함수를
 * `:shared`로 옮겨 원격·진단이 같이 쓰게 할 뿐, 로컬의 대기 시간은 바꾸지 않는다).
 */
class EngineDeadlinesTest {
    @Test
    fun aCappedSearchWaitsForItsCapPlusTwentySeconds() {
        assertEquals(30_000L, searchTimeoutMillisFor(10_000L))
        assertEquals(20_250L, searchTimeoutMillisFor(250L))
    }

    @Test
    fun anUncappedSearchWaitsTwoMinutes() {
        assertEquals(120_000L, searchTimeoutMillisFor(null))
    }

    @Test
    fun aCommandWithoutASearchBudgetWaitsThirtySeconds() {
        assertEquals(30_000L, DefaultCommandTimeoutMillis)
    }

    /** `analyze()`는 `minTimeMillis`를 바닥으로 캡을 올린다 — 캡이 없고 바닥만 있으면 바닥이 캡이 된다. */
    @Test
    fun anAnalysisCapIsRaisedToItsMinimumTime() {
        assertEquals(2_000L, AnalysisLimit(timeMillis = 500L, minTimeMillis = 2_000L).analysisSearchTimeMillis())
        assertEquals(5_000L, AnalysisLimit(timeMillis = 5_000L, minTimeMillis = 2_000L).analysisSearchTimeMillis())
        assertEquals(2_000L, AnalysisLimit(timeMillis = null, minTimeMillis = 2_000L).analysisSearchTimeMillis())
        assertEquals(null, AnalysisLimit(timeMillis = null, minTimeMillis = null).analysisSearchTimeMillis())
        assertEquals(22_000L, AnalysisLimit(timeMillis = null, minTimeMillis = 2_000L).analysisSearchTimeoutMillis())
        assertEquals(120_000L, AnalysisLimit(timeMillis = null, minTimeMillis = null).analysisSearchTimeoutMillis())
    }
}
