package com.worksoc.goaicoach.application.rankmeasure

import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.application.gamehistory.GameReplayData
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
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.shared.policy.PlayLevelGroup
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.shared.policy.toPlayLevelSetting
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshotSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 승급 대국(백로그 #219) — 자기 기력과 같은 급수의 AI와 두고, 결과가 기력을 옮긴다.
 *
 * 여기서 고정하는 것은 **사용자가 정한 규칙**이다(2026-10-07 → 2026-10-13 백로그 #259):
 * - 급 구간: 이긴 집 수 차이를 10으로 나눈 몫만큼 오른다 — 10집 미만이면 그대로, 한 판에 최대 7단계. 1단에서 멈춘다.
 * - 단 구간: 10집 이상 차이로 이기면 한 단 오른다(크게 이겨도 한 단).
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

    /**
     * 사용자가 든 예 그대로다(2026-10-13 최종, #259): 20급 11집 승 → 1단계(19급) · 20급 90집 승 → 9이지만 최대 7(13급) ·
     * 5급 60집 승 → 6이지만 급에서는 1단까지(1단).
     */
    @Test
    fun theUsersThreeExamples() {
        val twentyKyu = RankMeasureState(rank = KgsRank.kyu(20), hasStarted = true)

        assertEquals(KgsRank.kyu(19), afterResults(11.0, start = twentyKyu).single().state.rank)
        val bigWin = afterResults(90.0, start = twentyKyu).single()
        assertEquals(KgsRank.kyu(13), bigWin.state.rank)
        assertEquals(7, (bigWin.change as RankMeasureChange.Promoted).steps)
        assertEquals(KgsRank.dan(1), afterResults(60.0, start = RankMeasureState(rank = KgsRank.kyu(5), hasStarted = true)).single().state.rank)
    }

    /**
     * 오르는 폭은 이긴 집 수 차이를 10으로 나눈 **몫, 최대 7**이다(사용자 2026-10-13 최종 — 「몫, 최소 1」 → 「몫 + 1」을 거쳐 +1을 뺐다:
     * *"현재 급수가 유지되는 것도 재밌을 것 같다"*). 10집 미만은 0단계(그대로). 집 차이를 모르는 승리도 0이다(10집 이상인지 알 수 없다).
     */
    @Test
    fun thePromotionStepsAreTheMarginDividedByTenAtMostSeven() {
        assertEquals(
            listOf(0, 0, 1, 1, 2, 2, 6, 6, 7, 7, 7),
            listOf(0.5, 9.5, 10.0, 19.5, 20.0, 29.5, 61.0, 69.5, 70.0, 79.5, 87.5).map { margin -> rankMeasurePromotionSteps(margin) },
        )
        assertEquals(0, rankMeasurePromotionSteps(null))
        assertEquals(RankMeasureMaxPromotionSteps, 7)
        // 팝업이 「한 판에 최대 7단계」를 덧붙이는 경계 — 79.5집은 정확히 7이라 걸리지 않고, 80집부터 걸린다.
        assertEquals(listOf(false, true, true), listOf(79.5, 80.0, 87.5).map(::rankMeasureHitsStepCap))
    }

    /** 급 구간에서 **10집 미만의 승리는 그대로**다 — 연패만 끊는다. 10집 이상이면 그 판으로 오른다(연승을 기다리지 않는다). */
    @Test
    fun aKyuWinUnderTenPointsKeepsTheRank() {
        val results = afterResults(3.5, 25.0, 0.5)

        assertEquals(listOf(KgsRank.kyu(10), KgsRank.kyu(8), KgsRank.kyu(8)), results.ranks())
        assertEquals(listOf(false, true, false), results.map { it.change is RankMeasureChange.Promoted })
        assertEquals(RankMeasureChange.None, results.first().change)
    }

    /** 판 크기는 폭을 바꾸지 않는다 — 어느 판이든 같은 식이다(예전의 판 크기별 폭은 스레드가 넣었던 것이고 사용자가 거뒀다). */
    @Test
    fun theRuleDoesNotAskWhichBoardWasPlayed() {
        val fifteenKyu = RankMeasureState(rank = KgsRank.kyu(15), hasStarted = true)

        assertEquals(KgsRank.kyu(14), afterResults(16.5, start = fifteenKyu).single().state.rank)
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
        assertEquals(KgsRank.dan(1), afterResults(10.0, start = RankMeasureState(rank = KgsRank.kyu(1), hasStarted = true)).single().state.rank)
    }

    /**
     * **단 구간은 10집 이상 차이로 이기면 한 단 오른다**(사용자 2026-10-13, #259 — 「최근 5판 중 3판」을 갈아 끼웠다).
     * 크게 이겨도 한 단이고(같은 날 사용자 확인), 10집 미만의 승리는 그대로다. 집 차이를 모르는 승리는 올리지 않는다.
     */
    @Test
    fun aDanRankIsPromotedByAWinOfTenPointsOrMore() {
        val big = afterResults(80.0, start = dan(1)).single()
        assertEquals(KgsRank.dan(2), big.state.rank)
        assertEquals(RankMeasureChange.Promoted(from = KgsRank.dan(1), to = KgsRank.dan(2), margin = 80.0), big.change)

        assertEquals(listOf(KgsRank.dan(3), KgsRank.dan(4)), afterResults(9.5, 10.0, start = dan(3)).ranks())
        assertEquals(RankMeasureChange.None, afterResults(9.5, start = dan(3)).single().change)

        val unknownMargin = adjustRankAfterResult(dan(3), playedRank = KgsRank.dan(3), margin = null, userWon = true, byResignation = true)
        assertEquals(KgsRank.dan(3), unknownMargin.state.rank)
        assertEquals(RankMeasureChange.None, unknownMargin.change)
    }

    /** 단 구간의 작은 승리도 연패는 끊는다 — 패·(5집 승)·패는 2연패가 아니다. */
    @Test
    fun aSmallDanWinStillBreaksTheLosingStreak() {
        val results = afterResults(loss, 5.0, loss, start = dan(4))

        assertEquals(KgsRank.dan(4), results.last().state.rank)
        assertEquals(1, results.last().state.consecutiveLosses)
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

        assertEquals(KgsRank.kyu(10), results.last().state.rank)
        assertEquals(1, results.last().state.consecutiveLosses)
    }

    /** 끝을 넘지 않는다 — 20급에서 2연패해도 20급, 9단에서 승단 조건(10집 이상 승리)을 채우면 「이미 가장 높다」. */
    @Test
    fun theLadderStopsAtBothEnds() {
        val bottom = afterResults(loss, loss, start = RankMeasureState(hasStarted = true)).last()
        assertEquals(KgsRank.kyu(20), bottom.state.rank)
        assertEquals(RankMeasureChange.None, bottom.change)

        val top = afterResults(12.0, start = dan(9)).single()
        assertEquals(KgsRank.dan(9), top.state.rank)
        assertEquals(RankMeasureChange.AtTheTop, top.change)
        assertEquals(RankMeasureChange.None, afterResults(5.0, start = dan(9)).single().change, "a small win at the top says nothing")
    }

    /**
     * 「최고 기력」은 **이긴 적이 있거나 승급으로 닿은** 가장 높은 급수다. 스스로 고른 시작 급수는 한 판 이기기 전에는 기록이 아니고
     * (고른 것이지 잰 것이 아니다), 내려가도 최고 기력은 남는다.
     */
    @Test
    fun thePeakRankIsWhatWasBeatenOrReachedAndSurvivesADemotion() {
        val chosenFiveKyu = RankMeasureState(rank = KgsRank.kyu(5), hasStarted = true)
        assertNull(afterResults(loss, start = chosenFiveKyu).last().state.peakRank, "a self-chosen rank is not a record until it is won at")
        assertEquals(KgsRank.kyu(5), afterResults(3.5, start = chosenFiveKyu).last().state.peakRank, "a small win does not move the rank but is a record")
        assertEquals(KgsRank.kyu(4), afterResults(13.5, start = chosenFiveKyu).last().state.peakRank)

        val climbedThenFell = afterResults(13.5, loss, loss, start = chosenFiveKyu).last().state
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
     * 승급 대국은 **사람 좌석이 그렇게 표시된** 판이다 — 급수만 직접 정한 AI와 두는 판(나중의 인공지능 캐릭터, #220)이나
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

    /** 새 대국을 시작할 때 상대를 내 기력으로 맞춘다 — 승급 대국이 아니면 좌석 그대로다. */
    @Test
    fun theNextGameIsAgainstTheCurrentRank() {
        val setup = rankMeasurePlayerSetup(StoneColor.White, KgsRank.kyu(10))

        assertEquals(rankMeasurePlayerSetup(StoneColor.White, KgsRank.kyu(9)), setup.withRankForNextMeasureGame(RankMeasureState(rank = KgsRank.kyu(9))))
        assertEquals(setup, setup.withRankForNextMeasureGame(RankMeasureState(rank = KgsRank.kyu(10))))
        val regular = PlayerSetup()
        assertEquals(regular, regular.withRankForNextMeasureGame(RankMeasureState(rank = KgsRank.kyu(9))))
    }

    /**
     * 끝난 승급 대국을 되살릴 때는 그 판의 좌석·판 크기·호선을 설정에 다시 올린다 — 일반 설정에는 저장돼 있지 않아서,
     * 그대로 두면 되살아난 판이 캐릭터와 둔 일반 대국으로 보이고 「재 대국」도 일반 대국으로 시작한다.
     * 일반 대국의 끝난 판, 아직 두고 있는 승급 대국은 건드리지 않는다(뒤쪽은 이어하기가 제 길로 되살린다).
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
     * 승급 대국이 아닌 판은 저장소를 건드리지 않는다.
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

    /**
     * 상대(AI)가 기권한 판에는 계가한 집 수 차이가 없다 — **기권한 순간의 형세 점수차**를 집 수 차이로 친다(backlog #213).
     * AI의 기권 제안을 받아들인 사람이, 거절하고 끝까지 둔 사람보다 덜 오르지 않게 하려는 것이다.
     */
    @Test
    fun aWinByTheOpponentsResignationCountsTheScoreLeadAtThatMoment() {
        val store = InMemoryRankMeasureStore(RankMeasureState(rank = KgsRank.kyu(15), hasStarted = true))
        val aiResigned = entry(rankMeasurePlayerSetup(StoneColor.Black, KgsRank.kyu(15)), winner = StoneColor.Black, isResign = true)
        val replay = GameReplayData(
            moves = listOf(Move.Resign(StoneColor.White)),
            scoreSnapshots = listOf(
                ScoreSnapshot(moveNumber = 40, whiteScoreLead = -12.0, source = ScoreSnapshotSource.HumanNetworkEstimate),
                ScoreSnapshot(moveNumber = 46, whiteScoreLead = -41.7, whiteWinRate = 0.0, source = ScoreSnapshotSource.HumanNetworkEstimate),
            ),
        )

        val adjustment = runRankMeasureAdjustment(aiResigned, store, replay)

        assertEquals(
            RankMeasureChange.Promoted(from = KgsRank.kyu(15), to = KgsRank.kyu(11), margin = 41.0, byResignation = true),
            adjustment?.change,
        )
        assertEquals(KgsRank.kyu(11), store.load().rank)
    }

    /**
     * 형세 기록이 없는 기권승은 **오르지 않는다**(#259 — 10집 이상인지 알 수 없다, 단 구간과 같은 판단). 이긴 판이라 연패는 끊고 최고 기력에 든다.
     * (2026-10-07 규칙에서는 1단계였다 — 「최소 1단계」가 빠지며 같이 바뀌었다.)
     */
    @Test
    fun aResignedWinWithoutAScoreRecordKeepsTheRank() {
        val store = InMemoryRankMeasureStore(RankMeasureState(rank = KgsRank.kyu(15), hasStarted = true, consecutiveLosses = 1))
        val aiResigned = entry(rankMeasurePlayerSetup(StoneColor.Black, KgsRank.kyu(15)), winner = StoneColor.Black, isResign = true)

        val adjustment = runRankMeasureAdjustment(aiResigned, store)

        assertEquals(RankMeasureChange.None, adjustment?.change)
        assertEquals(KgsRank.kyu(15), store.load().rank)
        assertEquals(0, store.load().consecutiveLosses)
        assertEquals(KgsRank.kyu(15), store.load().peakRank)
    }

    @Test
    fun theResignationMarginIsTheLastNetworkEstimateSeenFromTheWinner() {
        fun replay(vararg snapshots: ScoreSnapshot) = GameReplayData(moves = listOf(Move.Pass(StoneColor.Black)), scoreSnapshots = snapshots.toList())
        val whiteAheadBy30 = ScoreSnapshot(moveNumber = 50, whiteScoreLead = 30.0, source = ScoreSnapshotSource.EngineEstimate)

        assertEquals(30.0, resignationMarginFor(replay(whiteAheadBy30), winner = StoneColor.White))
        assertNull(resignationMarginFor(replay(whiteAheadBy30), winner = StoneColor.Black), "이긴 쪽이 형세로는 지고 있었다면 집 수 차이로 치지 않는다")
        assertNull(resignationMarginFor(replay(), winner = StoneColor.White))
        // 어림값의 소수는 버린다 — 39.6집은 39집(3단계)이지 40집(4단계)이 아니다. 한 집도 안 되면 집 수 차이로 치지 않는다.
        assertEquals(39.0, resignationMarginFor(replay(whiteAheadBy30.copy(whiteScoreLead = 39.6)), winner = StoneColor.White))
        assertNull(resignationMarginFor(replay(whiteAheadBy30.copy(whiteScoreLead = 0.4)), winner = StoneColor.White))
        assertNull(resignationMarginFor(null, winner = StoneColor.White))
        // 돌 수만 센 국소 계가는 형세가 아니다 — 그 앞의 신경망 평가를 읽는다.
        val localCount = ScoreSnapshot(moveNumber = 52, whiteScoreLead = 3.0, source = ScoreSnapshotSource.LocalAreaEstimate)
        assertEquals(30.0, resignationMarginFor(replay(whiteAheadBy30, localCount), winner = StoneColor.White))
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

    private fun entry(playerSetup: PlayerSetup, winner: StoneColor?, margin: Double? = null, isResign: Boolean = false) = GameHistoryEntry(
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
        isResign = isResign,
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
