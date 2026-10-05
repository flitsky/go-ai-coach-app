package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.OwnershipEstimate
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.ui.history.ReplayAnalysisTap
import com.worksoc.goaicoach.ui.history.ReplayFeatureLedger
import com.worksoc.goaicoach.ui.history.analysablePosition
import com.worksoc.goaicoach.ui.history.hasSomethingToShow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 다시보기 분석 버튼(형세 보기·추천 수)의 원장(백로그 #218). 결함은 대개 **순서**에서 난다 — 켜기 → 옮기기 → 돌아오기 →
 * 끄기 → 켜기 — 그래서 순서를 그대로 적는다(`OneShotLedger`가 #44에서 배운 것).
 */
class ReplayAnalysisLedgerTest {

    private val fresh = ReplayFeatureLedger()

    // ── 차감 없는 접근(구독·광고 1시간) — 켜 두면 따라간다(U-60) ─────────────────────────

    @Test
    fun anUnlimitedUserTurnsItOnForFreeAndItFollowsEveryMove() {
        val (on, outcome) = fresh.tap(moveNumber = 10, unlimited = true, hasTicket = false)

        assertEquals(ReplayAnalysisTap.TurnedOn, outcome)
        assertNull("차감 없는 접근은 1회권을 걸지 않는다", on.awaitingTicketAt)
        listOf(10, 11, 57, 0).forEach { move ->
            val moved = on.onMoved(move)
            assertTrue("${move}수에서도 켜진 채 따라가야 한다", moved.isShownAt(move, unlimited = true))
            assertTrue("${move}수의 결과를 엔진에 물어야 한다", moved.wantsResultAt(move, unlimited = true))
        }
    }

    @Test
    fun anUnlimitedUserNeverSpendsATicketEvenWhenHoldingSome() {
        val (on, outcome) = fresh.tap(moveNumber = 10, unlimited = true, hasTicket = true)

        assertEquals(ReplayAnalysisTap.TurnedOn, outcome)
        assertNull(on.awaitingTicketAt)
        assertTrue(on.paidMoves.isEmpty())
    }

    @Test
    fun tappingAShownFeatureTurnsItOffEverywhere() {
        val on = fresh.tap(10, unlimited = true, hasTicket = false).first

        val (off, outcome) = on.tap(12, unlimited = true, hasTicket = false)

        assertEquals(ReplayAnalysisTap.TurnedOff, outcome)
        assertFalse(off.isShownAt(12, unlimited = true))
        assertFalse(off.isShownAt(10, unlimited = true))
    }

    // ── 1회권 — 한 장이 국면 하나(U-61), 차감은 결과가 나온 뒤 ──────────────────────────

    @Test
    fun aTicketIsOnlyStakedOnTheTapAndNothingShowsUntilItIsCharged() {
        val (staked, outcome) = fresh.tap(moveNumber = 10, unlimited = false, hasTicket = true)

        assertEquals(ReplayAnalysisTap.AwaitingTicket, outcome)
        assertEquals(10, staked.awaitingTicketAt)
        assertTrue("걸어 둔 동안 엔진에는 물어야 한다", staked.wantsResultAt(10, unlimited = false))
        assertFalse("아직 값을 치르지 않았다 — 판에 보이면 공짜다", staked.isShownAt(10, unlimited = false))
        assertTrue("누른 것만으로 값을 치른 것이 되면 안 된다", staked.paidMoves.isEmpty())
    }

    @Test
    fun aChargedTicketOpensThatOnePositionOnly() {
        val paid = fresh.tap(10, unlimited = false, hasTicket = true).first.onTicketCharged(10)

        assertTrue(paid.isShownAt(10, unlimited = false))
        val moved = paid.onMoved(11)
        assertFalse("한 장은 국면 하나다 — 다음 수순은 열리지 않는다", moved.isShownAt(11, unlimited = false))
        assertFalse("치르지 않은 수순은 엔진에 묻지도 않는다", moved.wantsResultAt(11, unlimited = false))
    }

    @Test
    fun comingBackToAPaidPositionShowsItAgainWithoutAnotherTicket() {
        val paid = fresh.tap(10, unlimited = false, hasTicket = true).first.onTicketCharged(10)

        val back = paid.onMoved(11).onMoved(30).onMoved(10)

        assertTrue("값을 치른 국면으로 돌아오면 다시 보인다", back.isShownAt(10, unlimited = false))
        assertEquals(setOf(10), back.paidMoves)
    }

    @Test
    fun turningAPaidPositionOffAndOnAgainIsFree() {
        val paid = fresh.tap(10, unlimited = false, hasTicket = true).first.onTicketCharged(10)

        val (off, offOutcome) = paid.tap(10, unlimited = false, hasTicket = true)
        val (onAgain, onOutcome) = off.tap(10, unlimited = false, hasTicket = true)

        assertEquals(ReplayAnalysisTap.TurnedOff, offOutcome)
        assertEquals("끄는 데 값을 받으면 1회가 반 번이 된다(#44)", setOf(10), off.paidMoves)
        assertEquals(ReplayAnalysisTap.TurnedOn, onOutcome)
        assertNull("이미 값을 치른 국면이다 — 또 걸면 한 장이 더 나간다", onAgain.awaitingTicketAt)
        assertTrue(onAgain.isShownAt(10, unlimited = false))
    }

    @Test
    fun anotherPositionCostsAnotherTicket() {
        val paid = fresh.tap(10, unlimited = false, hasTicket = true).first.onTicketCharged(10).onMoved(11)

        val (staked, outcome) = paid.tap(11, unlimited = false, hasTicket = true)

        assertEquals(ReplayAnalysisTap.AwaitingTicket, outcome)
        assertEquals(11, staked.awaitingTicketAt)
        assertEquals(setOf(10, 11), staked.onTicketCharged(11).paidMoves)
    }

    @Test
    fun movingAwayBeforeTheResultArrivesWithdrawsTheStake() {
        val staked = fresh.tap(10, unlimited = false, hasTicket = true).first

        val moved = staked.onMoved(11)

        assertNull("결과를 못 봤다 — 값을 치르지 않는다", moved.awaitingTicketAt)
        assertFalse(moved.wantsResultAt(11, unlimited = false))
        val back = moved.onMoved(10)
        assertFalse("돌아왔다고 걸어 둔 표가 되살아나면 누르지도 않았는데 한 장이 나간다", back.wantsResultAt(10, unlimited = false))
    }

    @Test
    fun aStakeThatGotNoResultIsReleasedWithoutPaying() {
        val staked = fresh.tap(10, unlimited = false, hasTicket = true).first

        val released = staked.onTicketReleased()

        assertNull(released.awaitingTicketAt)
        assertTrue("엔진이 바빠 못 봤다 — 값을 치른 국면이 되면 안 된다", released.paidMoves.isEmpty())
        assertFalse(released.isShownAt(10, unlimited = false))
        // 다시 누르면 다시 건다 — 켜져 있던 것을 "끄는" 탭으로 읽으면 두 번 눌러야 한다.
        assertEquals(ReplayAnalysisTap.AwaitingTicket, released.tap(10, unlimited = false, hasTicket = true).second)
    }

    @Test
    fun tappingWhileTheStakeIsPendingCancelsItForFree() {
        val staked = fresh.tap(10, unlimited = false, hasTicket = true).first

        val (off, outcome) = staked.tap(10, unlimited = false, hasTicket = true)

        assertEquals(ReplayAnalysisTap.TurnedOff, outcome)
        assertNull(off.awaitingTicketAt)
        assertTrue(off.paidMoves.isEmpty())
    }

    // ── 열 길이 없다 ─────────────────────────────────────────────────────────────────

    @Test
    fun withNoAccessAndNoTicketTheTapOnlyAsksForTheUpsell() {
        val (same, outcome) = fresh.tap(moveNumber = 10, unlimited = false, hasTicket = false)

        assertEquals(ReplayAnalysisTap.NeedsUpsell, outcome)
        assertEquals("업셀로 보낼 뿐 원장은 그대로다 — 켜 두면 광고를 보고 온 순간 누르지도 않은 분석이 돈다", fresh, same)
    }

    // ── 접근이 바뀐다 ────────────────────────────────────────────────────────────────

    @Test
    fun whenTheAdHourRunsOutOnlyThePaidPositionsStayOpen() {
        val paidThenUnlimited = fresh.tap(10, unlimited = false, hasTicket = true).first.onTicketCharged(10)

        assertTrue(paidThenUnlimited.isShownAt(25, unlimited = true))
        assertFalse("광고 1시간이 끝났다 — 값을 치르지 않은 수순은 닫힌다", paidThenUnlimited.isShownAt(25, unlimited = false))
        assertTrue("값을 치른 수순은 남는다", paidThenUnlimited.isShownAt(10, unlimited = false))
    }

    @Test
    fun aStakeIsNotNeededOnceUnlimitedAccessOpens() {
        val staked = fresh.tap(10, unlimited = false, hasTicket = true).first

        assertTrue("그 사이 프리미엄이 켜졌다 — 걸어 둔 표와 무관하게 보인다", staked.isShownAt(10, unlimited = true))
    }

    // ── 보여 줄 것이 있는가 — 없으면 차감하지 않는다 ──────────────────────────────────

    @Test
    fun anEstimateWithNeitherOwnershipNorScoreHasNothingToShow() {
        val status = EngineStatus.ready("estimated")

        assertFalse(ScoreEstimate(status = status, summary = "empty").hasSomethingToShow())
        assertTrue(ScoreEstimate(status = status, whiteScoreLead = 0.0, summary = "even").hasSomethingToShow())
        assertTrue(
            ScoreEstimate(
                status = status,
                ownership = OwnershipEstimate(blackLikelyPoints = 1, whiteLikelyPoints = 1, neutralOrUnclearPoints = 0, threshold = 0.5),
                summary = "ownership only",
            ).hasSomethingToShow(),
        )
    }

    // ── 엔진에 물을 국면 ─────────────────────────────────────────────────────────────

    @Test
    fun aResignedGameIsAnalysedAtThePositionBeforeTheResignation() {
        val empty = GameState.empty()
        val afterBlack = empty.play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val resigned = afterBlack.play(Move.Resign(StoneColor.White))
        val states = listOf(empty, afterBlack, resigned)

        val asked = analysablePosition(positionAt = states::get, moveNumber = 2)

        assertEquals("기권 수를 엔진에 두게 하지 않는다 — 판의 돌은 그 앞 국면과 같다", afterBlack, asked)
        assertEquals(afterBlack, analysablePosition(states::get, 1))
        assertEquals(empty, analysablePosition(states::get, 0))
    }
}
