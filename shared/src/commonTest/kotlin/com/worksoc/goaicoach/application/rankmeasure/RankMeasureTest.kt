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
 *
 * 여기서 고정하는 것은 **사용자가 정한 규칙**이다(2026-10-07):
 * - 급 구간: 이길 때마다 오른다 — 이긴 집 수 차이를 10으로 나눈 몫, 최소 1단계. 1단에서 멈춘다.
 * - 단 구간: 그 단에서 둔 최근 5판 중 3판을 이기면 한 단 오른다.
 * - 어느 구간이든 2연패하면 한 단계 내린다.
 * 그리고 급수대별 판 크기 · 최초 1회의 기력 선택(2026-10-06).
 */
class RankMeasureTest {
    /** 한 판의 결과 — 이겼으면 집 수 차이, 졌으면 `null`. */
    private fun afterResults(
        vararg wonBy: Double?,
        start: RankMeasureState = RankMeasureState(rank = KgsRank.kyu(10), hasStarted = true),
    ): List<RankMeasureAdjustment> {
        var state = start
        return wonBy.map { margin ->
            // 방금 둔 판의 급수는 직전까지의 기력이다 — 새 대국을 시작할 때 좌석에 옮겨 적기 때문이다.
            adjustRankAfterResult(state, playedRank = state.rank, margin = margin ?: 12.5, userWon = margin != null).also { state = it.state }
        }
    }

    private val loss: Double? = null

    private fun dan(number: Int) = RankMeasureState(rank = KgsRank.dan(number), hasStarted = true)

    private fun List<RankMeasureAdjustment>.ranks(): List<KgsRank> = map { it.state.rank }

    /** 사용자가 든 예 그대로다 — 15급에서 61집 차로 이기면 6단계 올라 9급. */
    @Test
    fun sixtyOnePointsAtFifteenKyuIsSixStepsToNineKyu() {
        val result = afterResults(61.0, start = RankMeasureState(rank = KgsRank.kyu(15), hasStarted = true)).single()

        assertEquals(KgsRank.kyu(9), result.state.rank)
        assertEquals(RankMeasureChange.Promoted(from = KgsRank.kyu(15), to = KgsRank.kyu(9), margin = 61.0), result.change)
        assertEquals(6, (result.change as RankMeasureChange.Promoted).steps)
    }

    /**
     * 오르는 폭은 이긴 집 수 차이를 10으로 나눈 **몫, 최소 1**이다(사용자 2026-10-07 — 식과 예시가 한 단계 달라 물었고 예시 쪽으로 정했다).
     * 한 판만 이겨도 1단계는 기본이고, 20집부터 한 단계씩 더 오른다. 집 차이가 없는 승리(상대 기권)는 1단계로 친다.
     */
    @Test
    fun thePromotionStepsAreTheMarginDividedByTenAtLeastOne() {
        assertEquals(
            listOf(1, 1, 1, 1, 2, 2, 6, 6, 8),
            listOf(0.5, 6.5, 10.0, 19.5, 20.0, 29.5, 61.0, 69.5, 87.5).map { margin -> rankMeasurePromotionSteps(margin) },
        )
        assertEquals(1, rankMeasurePromotionSteps(null))
    }

    /** 급 구간에서는 **이길 때마다** 오른다 — 연승을 기다리지 않는다. */
    @Test
    fun everyWinInTheKyuRangePromotes() {
        val results = afterResults(3.5, 25.0, 0.5)

        assertEquals(listOf(KgsRank.kyu(9), KgsRank.kyu(7), KgsRank.kyu(6)), results.ranks())
        assertTrue(results.all { it.change is RankMeasureChange.Promoted })
    }

    /** 판 크기는 폭을 바꾸지 않는다 — 어느 판이든 같은 식이다(예전의 판 크기별 폭은 스레드가 넣었던 것이고 사용자가 거뒀다). */
    @Test
    fun theRuleDoesNotAskWhichBoardWasPlayed() {
        val fifteenKyu = RankMeasureState(rank = KgsRank.kyu(15), hasStarted = true)

        assertEquals(KgsRank.kyu(14), afterResults(6.5, start = fifteenKyu).single().state.rank)
    }

    /**
     * 급 구간의 빠른 승급은 **1단에서 멈춘다**(사용자 2026-10-07) — 3급에서 61집 차로 이겨도 4단이 아니라 1단이다.
     * 알림의 단계 수는 실제로 오른 만큼이다.
     */
    @Test
    fun aBigWinInTheKyuRangeStopsAtOneDan() {
        val result = afterResults(61.0, start = RankMeasureState(rank = KgsRank.kyu(3), hasStarted = true)).single()

        assertEquals(KgsRank.dan(1), result.state.rank)
        assertEquals(3, (result.change as RankMeasureChange.Promoted).steps)
        assertEquals(KgsRank.dan(1), afterResults(0.5, start = RankMeasureState(rank = KgsRank.kyu(1), hasStarted = true)).single().state.rank)
    }

    /**
     * **단 구간은 최근 5판 중 3판을 이겨야 오른다**(사용자 2026-10-07) — 한 판 이긴 것으로는 오르지 않고, 크게 이겨도 한 단씩이다.
     * 지고 이기기를 섞어도 5판 안에 3승이면 오른다.
     */
    @Test
    fun aDanRankIsPromotedByThreeWinsInTheLastFiveGames() {
        val straight = afterResults(80.0, 80.0, 80.0, start = dan(1))
        assertEquals(listOf(KgsRank.dan(1), KgsRank.dan(1), KgsRank.dan(2)), straight.ranks())
        assertEquals(RankMeasureChange.Promoted(from = KgsRank.dan(1), to = KgsRank.dan(2), margin = null), straight.last().change)

        val mixed = afterResults(5.0, loss, 5.0, loss, 5.0, start = dan(3))
        assertEquals(KgsRank.dan(4), mixed.last().state.rank)
        assertTrue(mixed.dropLast(1).all { it.change == RankMeasureChange.None })
    }

    /** 최근 전적은 **5판까지만** 본다 — 여섯 판 전에 이긴 것은 세지 않는다. */
    @Test
    fun onlyTheLastFiveDanGamesCount() {
        // 승 패 승 패 | 패는 2연패가 아니게 배치: 승, 패, 승, 패, 승이면 오르므로, 오래된 승이 밀려나는 수순을 본다.
        val state = dan(2).copy(recentDanResults = listOf(true, false, false, true, false))

        val afterWin = adjustRankAfterResult(state, playedRank = KgsRank.dan(2), margin = 3.5, userWon = true)

        // 창은 [패, 패, 승, 패, 승] — 2승뿐이라 그대로다. 맨 앞의 승이 밀려났다.
        assertEquals(KgsRank.dan(2), afterWin.state.rank)
        assertEquals(listOf(false, false, true, false, true), afterWin.state.recentDanResults)
    }

    /** 단이 바뀌면 전적을 비우고 새로 센다 — 1단에서 이긴 판을 2단의 전적으로 치지 않는다. */
    @Test
    fun theDanRecordStartsOverAfterTheRankChanges() {
        val results = afterResults(5.0, 5.0, 5.0, 5.0, 5.0, start = dan(1))

        assertEquals(listOf(KgsRank.dan(1), KgsRank.dan(1), KgsRank.dan(2), KgsRank.dan(2), KgsRank.dan(2)), results.ranks())
        assertEquals(listOf(true, true), results.last().state.recentDanResults)
        assertTrue(afterResults(5.0, loss, loss, start = dan(2)).last().state.recentDanResults.isEmpty(), "a demotion clears the record too")
    }

    /**
     * **강급·강단은 2연패**다(사용자 2026-10-07) — 급이든 단이든 2연패하면 한 단계 내리고 연패 수를 다시 0부터 센다.
     * 1단에서 내리면 1급이다. 한 판 진 것으로는 내리지 않는다.
     */
    @Test
    fun twoLossesInARowDemoteOneStepInBothRanges() {
        val kyu = afterResults(loss, loss, loss, loss)
        assertEquals(listOf(KgsRank.kyu(10), KgsRank.kyu(11), KgsRank.kyu(11), KgsRank.kyu(12)), kyu.ranks())
        assertEquals(RankMeasureChange.Demoted(from = KgsRank.kyu(10), to = KgsRank.kyu(11)), kyu[1].change)
        assertEquals(0, kyu[1].state.consecutiveLosses)

        val oneDan = afterResults(loss, loss, start = dan(1))
        assertEquals(KgsRank.kyu(1), oneDan.last().state.rank)
        assertEquals(RankMeasureChange.Demoted(from = KgsRank.dan(1), to = KgsRank.kyu(1)), oneDan.last().change)
    }

    /** 한 판 이기면 연패가 끊긴다 — 패·승·패는 2연패가 아니다. */
    @Test
    fun aWinBreaksTheLosingStreak() {
        val results = afterResults(loss, 3.5, loss)

        assertEquals(KgsRank.kyu(9), results.last().state.rank)
        assertEquals(1, results.last().state.consecutiveLosses)
    }

    /** 끝을 넘지 않는다 — 20급에서 2연패해도 20급, 9단에서 승단 조건을 채우면 「이미 가장 높다」. */
    @Test
    fun theLadderStopsAtBothEnds() {
        val bottom = afterResults(loss, loss, start = RankMeasureState(hasStarted = true)).last()
        assertEquals(KgsRank.kyu(20), bottom.state.rank)
        assertEquals(RankMeasureChange.None, bottom.change)

        val top = afterResults(5.0, 5.0, 5.0, start = dan(9)).last()
        assertEquals(KgsRank.dan(9), top.state.rank)
        assertEquals(RankMeasureChange.AtTheTop, top.change)
        assertTrue(top.state.recentDanResults.isEmpty(), "the notice must not repeat on every game after it")
    }

    /**
     * 「최고 기력」은 **이긴 적이 있거나 승급으로 닿은** 가장 높은 급수다. 스스로 고른 시작 급수는 한 판 이기기 전에는 기록이 아니고
     * (고른 것이지 잰 것이 아니다), 내려가도 최고 기력은 남는다.
     */
    @Test
    fun thePeakRankIsWhatWasBeatenOrReachedAndSurvivesADemotion() {
        val chosenFiveKyu = RankMeasureState(rank = KgsRank.kyu(5), hasStarted = true)
        assertNull(afterResults(loss, start = chosenFiveKyu).last().state.peakRank, "a self-chosen rank is not a record until it is won at")
        assertEquals(KgsRank.kyu(4), afterResults(3.5, start = chosenFiveKyu).last().state.peakRank)

        val climbedThenFell = afterResults(3.5, loss, loss, start = chosenFiveKyu).last().state
        assertEquals(KgsRank.kyu(5), climbedThenFell.rank)
        assertEquals(KgsRank.kyu(4), climbedThenFell.peakRank)
    }

    /**
     * 기력은 **그 기력으로 둔 판**만 옮긴다. 다른 급수로 둔 판(뒤늦게 반영된 옛 판)은 연패·전적에도 기력에도 넣지 않는다 —
     * 넣으면 20급을 이긴 판이 12급인 사람의 기력을 옮긴다(2026-10-06 에뮬레이터). 이긴 급수는 그래도 잰 급수다 — 최고 기력에는 든다.
     */
    @Test
    fun aGamePlayedAtAnotherRankMovesNothingButCanSetThePeak() {
        val twelveKyu = RankMeasureState(rank = KgsRank.kyu(12), hasStarted = true)

        val lateWin = adjustRankAfterResult(twelveKyu, playedRank = KgsRank.kyu(20), margin = 61.0, userWon = true)

        assertEquals(RankMeasureChange.None, lateWin.change)
        assertEquals(twelveKyu.copy(peakRank = KgsRank.kyu(20)), lateWin.state)

        val oneLossShortOfDemotion = RankMeasureState(rank = KgsRank.kyu(12), hasStarted = true, consecutiveLosses = 1, peakRank = KgsRank.kyu(11))
        val lateLoss = adjustRankAfterResult(oneLossShortOfDemotion, playedRank = KgsRank.kyu(5), margin = null, userWon = false)
        assertEquals(RankMeasureAdjustment(oneLossShortOfDemotion), lateLoss)
    }

    /** 무승부(승자 모름)는 아무것도 바꾸지 않는다. */
    @Test
    fun aGameWithoutAWinnerChangesNothing() {
        val counting = RankMeasureState(rank = KgsRank.kyu(10), hasStarted = true, consecutiveLosses = 1)

        assertEquals(counting, adjustRankAfterResult(counting, KgsRank.kyu(10), margin = null, userWon = null).state)
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

    /**
     * 기록된 판 하나를 한 번만 반영하고 저장한다 — **이긴 집 수 차이는 기록에서 읽는다**(25.5집 차 → 2단계).
     * 기력 측정 대국이 아닌 판은 저장소를 건드리지 않는다.
     */
    @Test
    fun aRecordedGameIsCountedOnceAndSaved() {
        val store = InMemoryRankMeasureStore(RankMeasureState(rank = KgsRank.kyu(10), hasStarted = true))
        val wonByUser = entry(rankMeasurePlayerSetup(StoneColor.Black, KgsRank.kyu(10)), winner = StoneColor.Black, margin = 25.5)

        val adjustment = runRankMeasureAdjustment(wonByUser, store)

        assertEquals(RankMeasureChange.Promoted(from = KgsRank.kyu(10), to = KgsRank.kyu(8), margin = 25.5), adjustment?.change)
        assertEquals(KgsRank.kyu(8), store.load().rank)

        // 끝난 판의 화면은 여러 번 다시 그려지고 계가한 판은 앱을 껐다 켜도 복원된다 — 같은 판을 다시 넘겨도 또 세지 않는다.
        assertNull(runRankMeasureAdjustment(wonByUser, store))
        assertEquals(KgsRank.kyu(8), store.load().rank)

        assertNull(runRankMeasureAdjustment(entry(PlayerSetup(), winner = StoneColor.Black), store))
        assertEquals(1, store.saves, "a game against a character must not touch the rank")
    }

    /** 승자는 기록에서 읽는다 — 사용자가 백이어도, 기권으로 끝났어도. 기록이 붙은 판은 측정을 시작한 것이다. */
    @Test
    fun theWinnerComesFromTheRecordAndARecordedGameMeansMeasuringHasStarted() {
        val store = InMemoryRankMeasureStore(RankMeasureState(rank = KgsRank.kyu(10), consecutiveLosses = 1))
        val userIsWhite = rankMeasurePlayerSetup(StoneColor.White, KgsRank.kyu(10))

        val adjustment = runRankMeasureAdjustment(entry(userIsWhite, winner = StoneColor.Black), store)

        assertEquals(RankMeasureChange.Demoted(from = KgsRank.kyu(10), to = KgsRank.kyu(11)), adjustment?.change)
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

    private fun entry(playerSetup: PlayerSetup, winner: StoneColor?, margin: Double? = null) = GameHistoryEntry(
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
        margin = margin,
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
