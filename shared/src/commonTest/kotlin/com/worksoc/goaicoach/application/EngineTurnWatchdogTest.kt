package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.safety.EngineEndgameWatchdogTimeoutMillis
import com.worksoc.goaicoach.application.safety.EngineResponseGraceMillis
import com.worksoc.goaicoach.application.safety.EngineStuckWaitAction
import com.worksoc.goaicoach.application.safety.EngineTurnWatchdogAttempt
import com.worksoc.goaicoach.application.safety.engineStuckWaitActionFor
import com.worksoc.goaicoach.application.safety.engineTurnWatchdogBaseMillis
import com.worksoc.goaicoach.application.safety.engineTurnWatchdogTimeoutMillisFor
import com.worksoc.goaicoach.application.safety.isEngineStuckDialogVisible
import com.worksoc.goaicoach.application.safety.isEngineTurnWatchdogTriggered
import com.worksoc.goaicoach.shared.policy.SearchTimeLimit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EngineTurnWatchdogTest {
    /**
     * 설정값 × 1.2 + 부가 지연 3초 + **기본 응답 여유 5초**(2026-09-22).
     * ⚠️ 숫자를 손으로 적는다 — `EngineResponseGraceMillis`로 계산하면 그 상수를 바꿔도
     * 테스트가 함께 따라가서 **아무것도 지키지 않게 된다**(함정 24: 초록 ≠ 안전).
     */
    @Test
    fun timeoutScalesConfiguredLimitByOnePointTwoPlusOverheadPlusGrace() {
        assertEquals(20_000L, engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.WithinTenSeconds))
        assertEquals(11_600L, engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.WithinThreeSeconds))
        assertEquals(9_200L, engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.WithinOneSecond))
    }

    /** 여유가 **세 갈래 전부**에 붙는지 — 한 갈래만 붙는 사고를 막는다. */
    @Test
    fun theResponseGraceIsAddedToEveryBranch() {
        assertEquals(5_000L, EngineResponseGraceMillis)
        assertEquals(
            EngineEndgameWatchdogTimeoutMillis + EngineResponseGraceMillis,
            engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.WithinOneSecond, isResolvingEndgame = true),
        )
        assertEquals(60_000L + EngineResponseGraceMillis, engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.Off))
    }

    @Test
    fun timeoutIsFixedSixtyFiveSecondsWhenLimitIsOff() {
        assertEquals(65_000L, engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.Off))
    }

    @Test
    fun notTriggeredWhenNotAiTurnEvenIfElapsedIsLong() {
        assertFalse(
            isEngineTurnWatchdogTriggered(
                isAiTurn = false,
                elapsedSinceTurnStartMillis = 999_999L,
                searchTimeLimit = SearchTimeLimit.WithinThreeSeconds,
            ),
        )
    }

    @Test
    fun notTriggeredBeforeThresholdIsReached() {
        assertFalse(
            isEngineTurnWatchdogTriggered(
                isAiTurn = true,
                elapsedSinceTurnStartMillis = 11_599L,
                searchTimeLimit = SearchTimeLimit.WithinThreeSeconds,
            ),
        )
    }

    @Test
    fun triggeredOnceThresholdIsReached() {
        assertTrue(
            isEngineTurnWatchdogTriggered(
                isAiTurn = true,
                elapsedSinceTurnStartMillis = 11_600L,
                searchTimeLimit = SearchTimeLimit.WithinThreeSeconds,
            ),
        )
    }

    @Test
    fun endgameTimeoutIgnoresSearchTimeLimitAndUsesFixedBudget() {
        assertEquals(
            25_000L,
            engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.WithinOneSecond, isResolvingEndgame = true),
        )
        assertEquals(
            25_000L,
            engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.Off, isResolvingEndgame = true),
        )
    }

    @Test
    fun notTriggeredDuringEndgameResolutionUnderTheEndgameBudgetEvenPastNormalThreshold() {
        // 일반 착수 기준(1초 제한 -> 9_200ms)은 이미 넘었지만 계가 처리 중이므로 트리거되지 않는다.
        assertFalse(
            isEngineTurnWatchdogTriggered(
                isAiTurn = true,
                elapsedSinceTurnStartMillis = 9_500L,
                searchTimeLimit = SearchTimeLimit.WithinOneSecond,
                isResolvingEndgame = true,
            ),
        )
    }

    @Test
    fun triggeredDuringEndgameResolutionOnceEndgameBudgetIsReached() {
        assertTrue(
            isEngineTurnWatchdogTriggered(
                isAiTurn = true,
                elapsedSinceTurnStartMillis = EngineEndgameWatchdogTimeoutMillis + EngineResponseGraceMillis,
                searchTimeLimit = SearchTimeLimit.WithinOneSecond,
                isResolvingEndgame = true,
            ),
        )
    }

    @Test
    fun triggeredAtSixtyFiveSecondsWhenLimitIsOff() {
        assertFalse(
            isEngineTurnWatchdogTriggered(
                isAiTurn = true,
                elapsedSinceTurnStartMillis = 64_999L,
                searchTimeLimit = SearchTimeLimit.Off,
            ),
        )
        assertTrue(
            isEngineTurnWatchdogTriggered(
                isAiTurn = true,
                elapsedSinceTurnStartMillis = 65_000L,
                searchTimeLimit = SearchTimeLimit.Off,
            ),
        )
    }

    /** refactor backlog #74 — 팝업은 와치독 순간(A)이나 시간 초과 뒤 선택 대기(B) 중 하나면 뜨고, 겹쳐도 한 벌이다. */
    @Test
    fun theEngineStuckDialogShowsForEitherTheWatchdogMomentOrAnAwaitedTimeoutChoice() {
        assertFalse(isEngineStuckDialogVisible(isWatchdogTriggered = false, isAwaitingTimeoutChoice = false))
        assertTrue(isEngineStuckDialogVisible(isWatchdogTriggered = true, isAwaitingTimeoutChoice = false))
        assertTrue(
            isEngineStuckDialogVisible(isWatchdogTriggered = false, isAwaitingTimeoutChoice = true),
            "차례 대기의 완료가 와치독 표시를 닫아도, 시간 초과 뒤 선택 대기는 팝업을 남긴다",
        )
        assertTrue(isEngineStuckDialogVisible(isWatchdogTriggered = true, isAwaitingTimeoutChoice = true))
    }

    /**
     * 「한 번 더 기다리기」 — 탐색이 아직 돌면(A) 기다리기만 하고, 시간 초과로 끝났으면(B) 같은 국면을 다시 요청한다.
     * A에서 다시 요청하면 막힌 GTP 탐색 뒤에 한 번 더 줄을 서게 된다(설계 C-10).
     */
    @Test
    fun theWaitChoiceKeepsWaitingWhileTheSearchRunsAndRetriesOnlyAfterATimeout() {
        assertEquals(EngineStuckWaitAction.KeepWaitingAndRearm, engineStuckWaitActionFor(isAwaitingTimeoutChoice = false))
        assertEquals(EngineStuckWaitAction.RetryTimedOutTurnAndRearm, engineStuckWaitActionFor(isAwaitingTimeoutChoice = true))
    }

    /** 다시 걸면 그 순간부터 한도만큼 더 지나야 다시 뜬다. 새 차례가 오면 차례 시작이 이긴다. */
    @Test
    fun rearmingMovesTheWatchdogBaseForwardUntilTheNextTurnStarts() {
        assertEquals(1_000L, engineTurnWatchdogBaseMillis(turnStartedAtMillis = 1_000L, rearmedAtMillis = null))
        assertEquals(25_000L, engineTurnWatchdogBaseMillis(turnStartedAtMillis = 1_000L, rearmedAtMillis = 25_000L))
        assertEquals(40_000L, engineTurnWatchdogBaseMillis(turnStartedAtMillis = 40_000L, rearmedAtMillis = 25_000L))
        val threshold = engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.WithinTenSeconds)
        val base = engineTurnWatchdogBaseMillis(turnStartedAtMillis = 1_000L, rearmedAtMillis = 25_000L)
        assertFalse(isEngineTurnWatchdogTriggered(true, (25_000L + threshold - 1L) - base, SearchTimeLimit.WithinTenSeconds))
        assertTrue(isEngineTurnWatchdogTriggered(true, (25_000L + threshold) - base, SearchTimeLimit.WithinTenSeconds))
    }

    /**
     * refactor backlog #109 ⓑ — 와치독은 **시도마다** 다시 건다. 첫 시도에서 팝업을 띄운 뒤(보고 표시) 그 시도가 끝나면
     * (완료 순번이 바뀐다) 그 순간부터 새 시도로 다시 재고, 조용한 재시도가 한도를 넘기면 **다시** 뜬다. 예전에는 보고
     * 표시가 차례 내내 남아 재시도가 멎어도 아무것도 뜨지 않았다. 숫자는 손으로 적는다(1초 제한의 한도 9.2초).
     */
    @Test
    fun aFinishedAttemptRearmsTheWatchdogSoAStalledRetryIsReportedAgain() {
        val limit = SearchTimeLimit.WithinOneSecond
        fun fires(attempt: EngineTurnWatchdogAttempt, nowMillis: Long) =
            !attempt.isReported && isEngineTurnWatchdogTriggered(true, attempt.elapsedMillis(nowMillis), limit)

        // 첫 시도: 차례 시작 1초, 완료 순번 3. 한도(9.2초)에서 뜨고 보고 표시가 붙는다.
        var attempt = EngineTurnWatchdogAttempt(baseMillis = 1_000L, completionSeq = 3)
        assertFalse(fires(attempt.observe(nowMillis = 10_199L, completionSeq = 3), 10_199L))
        attempt = attempt.observe(nowMillis = 10_200L, completionSeq = 3)
        assertTrue(fires(attempt, 10_200L))
        attempt = attempt.reported()
        assertFalse(fires(attempt.observe(nowMillis = 60_000L, completionSeq = 3), 60_000L), "같은 시도에서는 한 번만 뜬다")

        // 첫 시도가 13초에 실패로 끝났다(순번 4) → 그 순간부터 새 시도. 재시도가 멎으면 13 + 9.2초에 **다시** 뜬다.
        attempt = attempt.observe(nowMillis = 13_000L, completionSeq = 4)
        assertEquals(EngineTurnWatchdogAttempt(baseMillis = 13_000L, completionSeq = 4), attempt)
        assertFalse(fires(attempt.observe(nowMillis = 22_199L, completionSeq = 4), 22_199L))
        assertTrue(fires(attempt.observe(nowMillis = 22_200L, completionSeq = 4), 22_200L), "실패 뒤 조용히 다시 도는 시도가 멎어도 와치독이 다시 뜬다")

        // 순번은 바뀌었는지만 본다 — 엔진 수명 리셋으로 0으로 돌아가도 새 시도다.
        val afterReset = attempt.reported().observe(nowMillis = 30_000L, completionSeq = 0)
        assertEquals(EngineTurnWatchdogAttempt(baseMillis = 30_000L, completionSeq = 0), afterReset)
    }

    /**
     * backlog #202 — 앱이 백그라운드로 가면 AI 차례가 취소되고(완료 순번이 바뀐다), 돌아오면 새로 요청된다. 와치독 루프는
     * 멈춘 동안 돌지 않으므로 복귀 때 멈춘 순간의 순번과 맞대 **복귀 순간부터** 잰다. 차례 시작부터 재면 나가기 전의
     * 시간이 얹혀 멀쩡한 새 탐색에 팝업이 뜬다(2026-10-01 에뮬레이터 실측: 복귀 2.3초 만에 경과 31.9초). 숫자는 손으로
     * 적는다(1초 제한의 한도 9.2초).
     */
    @Test
    fun anAttemptThatEndedWhileTheClockWasPausedIsMeasuredFromTheResume() {
        val limit = SearchTimeLimit.WithinOneSecond
        // 차례 시작 1초(시계의 resume이 멈춘 시간만큼 옮긴 값), 멈출 때 순번 3, 멈춘 사이 취소로 4가 됐다. 복귀 8초.
        val onResume = EngineTurnWatchdogAttempt(baseMillis = 1_000L, completionSeq = 4)
            .resumedAfterPause(completionSeqAtPause = 3, nowMillis = 8_000L)

        assertEquals(EngineTurnWatchdogAttempt(baseMillis = 8_000L, completionSeq = 4), onResume)
        assertFalse(isEngineTurnWatchdogTriggered(true, onResume.elapsedMillis(17_199L), limit), "새 시도는 복귀부터 9.2초를 기다린다")
        assertTrue(isEngineTurnWatchdogTriggered(true, onResume.elapsedMillis(17_200L), limit), "새 시도가 멎으면 여전히 뜬다")
    }

    /** 멈춘 사이 끝난 시도가 없으면(가려졌을 뿐 탐색은 계속됐다) 차례 기준 그대로 잰다 — 진짜 멈춤을 늦게 알리지 않는다. */
    @Test
    fun aPauseWithoutAFinishedAttemptKeepsTheTurnBase() {
        val attempt = EngineTurnWatchdogAttempt(baseMillis = 1_000L, completionSeq = 3)

        assertEquals(attempt, attempt.resumedAfterPause(completionSeqAtPause = 3, nowMillis = 8_000L))
        assertEquals(attempt, attempt.resumedAfterPause(completionSeqAtPause = null, nowMillis = 8_000L), "멈춘 적이 없으면 그대로")
    }

    /**
     * backlog #204 — 화면에 있는 채로 이 프로세스가 멈췄다가(동결·에뮬레이터 VM 정지 — 수명 콜백이 없어 대국 시계도 안 멈춘다)
     * 풀리면, 풀린 첫 틱이 멈춘 시간까지 경과로 세어 멀쩡한 엔진에 팝업을 띄운다. 틱 사이의 틈이 문턱(10초)을 넘으면 그
     * 틱부터 다시 잰다. 제때 온 틱은 아무것도 바꾸지 않는다 — 진짜 멈춤을 늦게 알리지 않는다. 숫자는 손으로 적는다(틱 200ms,
     * 1초 제한의 한도 9.2초).
     */
    @Test
    fun aTickAfterTheProcessWasPausedOnScreenMeasuresFromThatTick() {
        val limit = SearchTimeLimit.WithinOneSecond
        val attempt = EngineTurnWatchdogAttempt(baseMillis = 1_000L, completionSeq = 3)

        // 3초에 틱, 다음 틱이 73초 — 70초 동안 돌지 못했다.
        val thawed = attempt.resumedAfterProcessPause(previousTickMillis = 3_000L, nowMillis = 73_000L, tickIntervalMillis = 200L)
        assertEquals(EngineTurnWatchdogAttempt(baseMillis = 73_000L, completionSeq = 3), thawed)
        assertFalse(isEngineTurnWatchdogTriggered(true, thawed.elapsedMillis(73_000L), limit), "풀린 순간 팝업이 뜨면 오탐이다")
        assertTrue(isEngineTurnWatchdogTriggered(true, thawed.elapsedMillis(82_200L), limit), "풀린 뒤에도 멎어 있으면 여전히 뜬다")

        assertEquals(attempt, attempt.resumedAfterProcessPause(previousTickMillis = 3_000L, nowMillis = 3_200L, tickIntervalMillis = 200L))
        assertEquals(
            attempt,
            attempt.resumedAfterProcessPause(previousTickMillis = 3_000L, nowMillis = 13_199L, tickIntervalMillis = 200L),
            "문턱 아래의 밀림(GC·부하)은 그대로 잰다",
        )
        assertTrue(
            attempt.reported().resumedAfterProcessPause(previousTickMillis = 3_000L, nowMillis = 73_000L, tickIntervalMillis = 200L).isReported,
            "이미 보고한 시도는 다시 걸어도 보고한 채다 — 한 시도에 팝업은 한 번",
        )
    }
}
