package com.worksoc.goaicoach.application.rankmeasure

import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.application.session.GameSessionSettingsState
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.HumanGameType
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.match.isRankMeasure
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.shared.policy.PlayLevelGroup
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.shared.policy.toPlayLevelSetting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 기력 측정 대국(백로그 #219) — 자기 기력과 같은 급수의 AI와 두고, 결과가 기력을 옮긴다.
 * 여기서 고정하는 것은 사용자가 정한 규칙이다(2026-10-06): 올림은 랠리 · 내림은 2연패마다 한 칸 · 판 크기별 간격 ·
 * 급수대별 판 크기 · 최초 1회의 기력 선택.
 */
class RankMeasureTest {
    private fun afterResults(
        vararg userWon: Boolean,
        start: RankMeasureState = RankMeasureState(rank = KgsRank.kyu(10), hasStarted = true),
        boardSize: BoardSize = BoardSize.Nineteen,
    ): List<RankMeasureAdjustment> {
        var state = start
        return userWon.map { won ->
            // 방금 둔 판의 급수는 직전까지의 기력이다 — 새 대국을 시작할 때 좌석에 옮겨 적기 때문이다.
            adjustRankAfterResult(state, playedRank = state.rank, boardSize = boardSize, userWon = won).also { state = it.state }
        }
    }

    private fun List<RankMeasureAdjustment>.ranks(): List<KgsRank> = map { it.state.rank }

    /** 2연승이면 한 칸 올리고 랠리에 들어간다. 한 판 이긴 것으로는 오르지 않는다. */
    @Test
    fun twoWinsInARowPromoteAndStartTheRally() {
        val (first, second) = afterResults(true, true)

        assertEquals(RankMeasureChange.None, first.change)
        assertEquals(RankMeasureChange.Promoted(KgsRank.kyu(9)), second.change)
        assertTrue(second.state.isRally)
    }

    /** 랠리 중에는 **이길 때마다** 또 올린다 — 오른 급수에서도 연승이 이어진 것으로 본다. */
    @Test
    fun duringTheRallyEveryWinPromotesAgain() {
        assertEquals(listOf(KgsRank.kyu(10), KgsRank.kyu(9), KgsRank.kyu(8), KgsRank.kyu(7)), afterResults(true, true, true, true).ranks())
    }

    /** 한 번 지면 랠리가 끝난다 — 그 급수에서 다시 2연승해야 오른다. 한 판 진 것으로는 내리지 않는다. */
    @Test
    fun aLossEndsTheRallyAndTwoMoreWinsAreNeededAgain() {
        val results = afterResults(true, true, true, false, true, true)

        assertEquals(listOf(KgsRank.kyu(10), KgsRank.kyu(9), KgsRank.kyu(8), KgsRank.kyu(8), KgsRank.kyu(8), KgsRank.kyu(7)), results.ranks())
        assertFalse(results[3].state.isRally)
        assertEquals(RankMeasureChange.None, results[3].change)
    }

    /**
     * **내림은 2연패마다 한 칸이다.** 2연패하면 내리고 연패 수를 다시 0부터 센다 — 내림에는 랠리가 없어서, 세 번째 패는
     * 내리지 않고 네 번째 패가 또 한 칸 내린다(오르기는 쉽고 내려가기는 느리다).
     */
    @Test
    fun everyTwoLossesInARowDemoteOneStepWithoutARally() {
        val results = afterResults(false, false, false, false)

        assertEquals(listOf(KgsRank.kyu(10), KgsRank.kyu(11), KgsRank.kyu(11), KgsRank.kyu(12)), results.ranks())
        assertEquals(RankMeasureChange.Demoted(KgsRank.kyu(11)), results[1].change)
        assertEquals(RankMeasureChange.None, results[2].change)
        assertEquals(0, results[1].state.consecutiveLosses)
    }

    /** 이기고 지기를 번갈아 하면 어느 쪽도 2연속이 안 된다 — **그 급수에 머문다.** 그 급수가 실력 구간이다. */
    @Test
    fun alternatingWinsAndLossesStayAtTheSameRank() {
        val results = afterResults(true, false, true, false, false, true, false, true)

        // 다섯째 판(2연패째)에서 한 번 내려갔다가 거기서 다시 번갈아 한다.
        assertEquals(KgsRank.kyu(11), results.last().state.rank)
        assertEquals(1, results.count { it.change != RankMeasureChange.None })
    }

    /** 한 판 이기면 연패가 끊긴다 — 패·승·패는 2연패가 아니다. */
    @Test
    fun aWinBreaksTheLosingStreak() {
        assertEquals(KgsRank.kyu(10), afterResults(false, true, false).last().state.rank)
    }

    /** 판이 작을수록 한 번에 크게 움직인다 — 오를 때도 내릴 때도(실험실 #216). */
    @Test
    fun theStepIsLargerOnSmallerBoardsBothWays() {
        assertEquals(listOf(1, 2, 3), listOf(BoardSize.Nineteen, BoardSize.Thirteen, BoardSize.Nine).map(::rankMeasureStepFor))
        val fifteen = RankMeasureState(rank = KgsRank.kyu(15), hasStarted = true)
        assertEquals(KgsRank.kyu(12), afterResults(true, true, start = fifteen, boardSize = BoardSize.Nine).last().state.rank)
        assertEquals(KgsRank.kyu(18), afterResults(false, false, start = fifteen, boardSize = BoardSize.Nine).last().state.rank)
        assertEquals(KgsRank.kyu(13), afterResults(true, true, start = fifteen, boardSize = BoardSize.Thirteen).last().state.rank)
    }

    /** 끝을 넘지 않는다 — 20급에서 2연패해도 20급, 9단에서 이기면 「이미 가장 높다」. 1급 다음은 1단이다. */
    @Test
    fun theLadderStopsAtBothEndsAndCrossesFromKyuToDan() {
        val bottom = afterResults(false, false, start = RankMeasureState(hasStarted = true)).last()
        assertEquals(KgsRank.kyu(20), bottom.state.rank)
        assertEquals(RankMeasureChange.None, bottom.change)

        val top = afterResults(true, true, start = RankMeasureState(rank = KgsRank.dan(9), hasStarted = true)).last()
        assertEquals(RankMeasureChange.AtTheTop, top.change)

        assertEquals(KgsRank.dan(1), afterResults(true, true, start = RankMeasureState(rank = KgsRank.kyu(1), hasStarted = true)).last().state.rank)
    }

    /**
     * 「최고 기력」은 **이긴 적이 있거나 승급으로 닿은** 가장 높은 급수다. 스스로 고른 시작 급수는 한 판 이기기 전에는 기록이 아니고
     * (고른 것이지 잰 것이 아니다), 내려가도 최고 기력은 남는다.
     */
    @Test
    fun thePeakRankIsWhatWasBeatenOrReachedAndSurvivesADemotion() {
        val chosenFiveKyu = RankMeasureState(rank = KgsRank.kyu(5), hasStarted = true)
        assertNull(afterResults(false, start = chosenFiveKyu).last().state.peakRank, "a self-chosen rank is not a record until it is won at")
        assertEquals(KgsRank.kyu(5), afterResults(true, start = chosenFiveKyu).last().state.peakRank)

        val climbedThenFell = afterResults(true, true, false, false, start = chosenFiveKyu).last().state
        assertEquals(KgsRank.kyu(5), climbedThenFell.rank)
        assertEquals(KgsRank.kyu(4), climbedThenFell.peakRank)
    }

    /**
     * 기력은 **그 기력으로 둔 판**만 옮긴다. 다른 급수로 둔 판(뒤늦게 반영된 옛 판)은 연승·연패에도 기력에도 넣지 않는다 —
     * 넣으면 승급 폭이 그 판의 급수에서 계산되어, 20급을 이긴 판이 12급인 사람을 17급으로 "올린다"(2026-10-06 에뮬레이터).
     * 이긴 급수는 그래도 잰 급수다 — 최고 기력에는 든다.
     */
    @Test
    fun aGamePlayedAtAnotherRankMovesNothingButCanSetThePeak() {
        val oneWinShortOfPromotion = RankMeasureState(rank = KgsRank.kyu(12), hasStarted = true, consecutiveWins = 1)

        val lateWin = adjustRankAfterResult(oneWinShortOfPromotion, playedRank = KgsRank.kyu(20), boardSize = BoardSize.Nine, userWon = true)

        assertEquals(RankMeasureChange.None, lateWin.change)
        assertEquals(oneWinShortOfPromotion.copy(peakRank = KgsRank.kyu(20)), lateWin.state)

        val oneLossShortOfDemotion = RankMeasureState(rank = KgsRank.kyu(12), hasStarted = true, consecutiveLosses = 1, peakRank = KgsRank.kyu(11))
        val lateLoss = adjustRankAfterResult(oneLossShortOfDemotion, playedRank = KgsRank.kyu(5), boardSize = BoardSize.Nine, userWon = false)
        assertEquals(RankMeasureAdjustment(oneLossShortOfDemotion), lateLoss)
    }

    /** 무승부(승자 모름)는 아무것도 바꾸지 않는다. */
    @Test
    fun aGameWithoutAWinnerChangesNothing() {
        val counting = RankMeasureState(rank = KgsRank.kyu(10), hasStarted = true, consecutiveWins = 1)

        assertEquals(counting, adjustRankAfterResult(counting, KgsRank.kyu(10), BoardSize.Nineteen, userWon = null).state)
    }

    /**
     * 기력이 오를수록 큰 판으로 좁힌다 — 20~11급은 전부, 10~1급은 13·19줄, 단은 19줄만.
     * 쓸 수 없는 판을 고르고 있었으면 쓸 수 있는 가장 작은 판으로 옮긴다.
     */
    @Test
    fun strongerRanksAreMeasuredOnLargerBoards() {
        val all = listOf(BoardSize.Nine, BoardSize.Thirteen, BoardSize.Nineteen)
        assertEquals(all, rankMeasureBoardSizesFor(KgsRank.kyu(20)))
        assertEquals(all, rankMeasureBoardSizesFor(KgsRank.kyu(11)))
        assertEquals(listOf(BoardSize.Thirteen, BoardSize.Nineteen), rankMeasureBoardSizesFor(KgsRank.kyu(10)))
        assertEquals(listOf(BoardSize.Thirteen, BoardSize.Nineteen), rankMeasureBoardSizesFor(KgsRank.kyu(1)))
        assertEquals(listOf(BoardSize.Nineteen), rankMeasureBoardSizesFor(KgsRank.dan(1)))
        assertEquals(listOf(BoardSize.Nineteen), rankMeasureBoardSizesFor(KgsRank.dan(9)))

        assertEquals(BoardSize.Nine, rankMeasureBoardSizeFor(KgsRank.kyu(12), preferred = BoardSize.Nine))
        assertEquals(BoardSize.Thirteen, rankMeasureBoardSizeFor(KgsRank.kyu(9), preferred = BoardSize.Nine))
        assertEquals(BoardSize.Nineteen, rankMeasureBoardSizeFor(KgsRank.dan(2), preferred = BoardSize.Thirteen))
        assertEquals(BoardSize.Nineteen, rankMeasureBoardSizeFor(KgsRank.kyu(5), preferred = BoardSize.Nineteen))
    }

    /**
     * 기록이 없으면 20급이다. **최초 1회**에 한해 20급~1급에서 스스로 고를 수 있고, 측정을 시작하면 잠긴다 —
     * 그 뒤로는 이기고 지는 것만이 기력을 옮긴다. 단은 고를 수 없다(이겨서 오른다).
     */
    @Test
    fun theStartingRankCanBeChosenOnlyBeforeTheFirstGame() {
        val fresh = RankMeasureState()
        assertEquals(KgsRank.kyu(20), fresh.rank)
        assertTrue(fresh.canChooseStartingRank)
        assertNull(fresh.peakRank)

        assertEquals(KgsRank.kyu(7), fresh.chooseStartingRank(KgsRank.kyu(7)).rank)
        assertEquals(KgsRank.kyu(1), fresh.chooseStartingRank(KgsRank.dan(3)).rank, "a dan rank is earned, not chosen")

        val measuring = fresh.chooseStartingRank(KgsRank.kyu(7)).started()
        assertFalse(measuring.canChooseStartingRank)
        assertEquals(measuring, measuring.chooseStartingRank(KgsRank.kyu(2)))
    }

    /**
     * 기력 측정 대국은 **사람 좌석이 그렇게 표시된** 판이다 — 급수만 직접 정한 AI와 두는 판(나중의 인공지능 캐릭터, #220)이나
     * AI끼리·캐릭터 상대는 아니다.
     */
    @Test
    fun onlyASeatMarkedAsRankMeasureMakesARankMeasureGame() {
        val asBlack = rankMeasurePlayerSetup(StoneColor.Black, KgsRank.kyu(5))
        val asWhite = rankMeasurePlayerSetup(StoneColor.White, KgsRank.dan(2))

        assertEquals(RankMeasureMatchup(StoneColor.Black, KgsRank.kyu(5)), asBlack.rankMeasureMatchup())
        assertEquals(RankMeasureMatchup(StoneColor.White, KgsRank.dan(2)), asWhite.rankMeasureMatchup())
        assertTrue(asBlack.isRankMeasure())
        assertEquals(SeatController.Ai, asWhite.black.controller)

        val manualRank = PlayerSetup(black = SidePlayerSetup(SeatController.Human), white = custom(KgsRank.kyu(5)))
        assertNull(manualRank.rankMeasureMatchup(), "a hand-picked rank without the marker is not a measurement game")
        assertFalse(manualRank.isRankMeasure())
        assertNull(PlayerSetup(black = custom(KgsRank.kyu(5)), white = custom(KgsRank.kyu(3))).rankMeasureMatchup())
        assertNull(PlayerSetup().rankMeasureMatchup())
        val markedButAgainstACharacter = PlayerSetup(
            black = SidePlayerSetup(SeatController.Human, humanGameType = HumanGameType.RankMeasure),
            white = SidePlayerSetup(SeatController.Ai, playLevel = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 3)),
        )
        assertNull(markedButAgainstACharacter.rankMeasureMatchup())
    }

    /** 새 대국을 시작할 때 상대를 내 기력으로 맞춘다 — 기력 측정 대국이 아니면 좌석 그대로다. */
    @Test
    fun theNextGameIsAgainstTheCurrentRank() {
        val setup = rankMeasurePlayerSetup(StoneColor.White, KgsRank.kyu(10))

        assertEquals(rankMeasurePlayerSetup(StoneColor.White, KgsRank.kyu(9)), setup.withRankForNextMeasureGame(RankMeasureState(rank = KgsRank.kyu(9))))
        assertEquals(setup, setup.withRankForNextMeasureGame(RankMeasureState(rank = KgsRank.kyu(10))))
        val regular = PlayerSetup()
        assertEquals(regular, regular.withRankForNextMeasureGame(RankMeasureState(rank = KgsRank.kyu(9))))
    }

    /**
     * 끝난 기력 측정 대국을 되살릴 때는 그 판의 좌석·판 크기·호선을 설정에 다시 올린다 — 일반 설정에는 저장돼 있지 않아서,
     * 그대로 두면 되살아난 판이 캐릭터와 둔 일반 대국으로 보이고 「재 대국」도 일반 대국으로 시작한다.
     * 일반 대국의 끝난 판, 아직 두고 있는 기력 측정 대국은 건드리지 않는다(뒤쪽은 이어하기가 제 길로 되살린다).
     */
    @Test
    fun restoringAnEndedRankMeasureGameBringsItsSeatsAndBoardBack() {
        val regular = GameSessionSettingsState(
            boardSize = BoardSize.Nineteen,
            playerSetup = PlayerSetup(),
            autoPlayDelaySetting = AutoPlayDelaySetting.Default,
            searchTimeSettings = SearchTimeSettings(),
            topMovesEnabled = true,
            handicapCount = 4,
            komi = 0.5,
        )
        val seats = rankMeasurePlayerSetup(StoneColor.White, KgsRank.kyu(12))
        fun snapshot(playerSetup: PlayerSetup, ended: Boolean) = SavedGameSnapshot(
            gameState = GameState.empty(boardSize = BoardSize.Thirteen),
            playerSetup = playerSetup,
            playLevel = PlayLevelSetting(),
            topMovesEnabled = false,
            savedAtMillis = 1L,
            finalScoreJudgement = if (ended) Judgement else null,
        )

        val restored = regular.withEndedRankMeasureGame(snapshot(seats, ended = true))

        assertEquals(seats, restored.playerSetup)
        assertEquals(BoardSize.Thirteen, restored.boardSize)
        assertEquals(0, restored.handicapCount)
        assertEquals(GameState.empty(boardSize = BoardSize.Thirteen).komi, restored.komi)
        assertTrue(restored.topMovesEnabled, "nothing else changes")
        assertEquals(regular, regular.withEndedRankMeasureGame(snapshot(PlayerSetup(), ended = true)), "a regular game keeps the live settings")
        assertEquals(regular, regular.withEndedRankMeasureGame(snapshot(seats, ended = false)), "a game still in progress is restored by the resume path")
    }

    /** 기록된 판 하나를 한 번만 반영하고 저장한다. 기력 측정 대국이 아닌 판은 저장소를 건드리지 않는다. */
    @Test
    fun aRecordedGameIsCountedOnceAndSaved() {
        val store = InMemoryRankMeasureStore(RankMeasureState(rank = KgsRank.kyu(10), hasStarted = true, consecutiveWins = 1))
        val wonByUser = entry(rankMeasurePlayerSetup(StoneColor.Black, KgsRank.kyu(10)), winner = StoneColor.Black)

        val adjustment = runRankMeasureAdjustment(wonByUser, store)

        assertEquals(RankMeasureChange.Promoted(KgsRank.kyu(9)), adjustment?.change)
        assertEquals(KgsRank.kyu(9), store.load().rank)

        // 끝난 판의 화면은 여러 번 다시 그려지고 계가한 판은 앱을 껐다 켜도 복원된다 — 같은 판을 다시 넘겨도 또 세지 않는다.
        assertNull(runRankMeasureAdjustment(wonByUser, store))
        assertEquals(KgsRank.kyu(9), store.load().rank)

        assertNull(runRankMeasureAdjustment(entry(PlayerSetup(), winner = StoneColor.Black), store))
        assertEquals(1, store.saves, "a game against a character must not touch the rank")
    }

    /** 승자는 기록에서 읽는다 — 사용자가 백이어도, 기권으로 끝났어도. 기록이 붙은 판은 측정을 시작한 것이다. */
    @Test
    fun theWinnerComesFromTheRecordAndARecordedGameMeansMeasuringHasStarted() {
        val store = InMemoryRankMeasureStore(RankMeasureState(rank = KgsRank.kyu(10), consecutiveLosses = 1))
        val userIsWhite = rankMeasurePlayerSetup(StoneColor.White, KgsRank.kyu(10))

        val adjustment = runRankMeasureAdjustment(entry(userIsWhite, winner = StoneColor.Black), store)

        assertEquals(RankMeasureChange.Demoted(KgsRank.kyu(11)), adjustment?.change)
        assertTrue(store.load().hasStarted)
    }

    private val Judgement = FinalScoreJudgement(
        winner = StoneColor.White,
        margin = 3.5,
        ruleset = Ruleset.Japanese,
        isEstimatedDisplay = false,
        removedBlack = 0,
        removedWhite = 0,
        blackArea = null,
        whiteAreaWithKomi = null,
        capturedByBlack = 0,
        capturedByWhite = 0,
        komi = 6.5,
    )

    private fun custom(rank: KgsRank) = SidePlayerSetup(controller = SeatController.Ai, playLevel = rank.toPlayLevelSetting())

    private fun entry(playerSetup: PlayerSetup, winner: StoneColor?) = GameHistoryEntry(
        id = "game",
        playedAtMillis = 1L,
        boardSize = 19,
        ruleset = Ruleset.Japanese,
        komi = 6.5,
        handicapCount = 0,
        playerSetup = playerSetup,
        moveCount = 120,
        humanColor = null,
        winner = winner,
    )

    private class InMemoryRankMeasureStore(private var state: RankMeasureState) : RankMeasureStorePort {
        var saves = 0

        override fun load(): RankMeasureState = state

        override fun save(state: RankMeasureState) {
            saves += 1
            this.state = state
        }
    }
}
