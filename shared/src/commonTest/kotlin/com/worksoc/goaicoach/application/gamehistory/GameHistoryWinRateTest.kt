package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.application.rankmeasure.rankMeasurePlayerSetup
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.KgsRank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 대국 기록의 승률(백로그 #227). 사용자가 정한 것(2026-10-08): 사람 대 AI 판만 · 10수 이하 판과 승자를 모르는 옛 기권 기록은 뺀다 ·
 * 기력 측정 대국은 따로.
 */
class GameHistoryWinRateTest {
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000
    private val humanBlackVsAi = PlayerSetup()
    private val aiVsAi = PlayerSetup(black = SidePlayerSetup(SeatController.Ai), white = SidePlayerSetup(SeatController.Ai))
    private val humanVsHuman = PlayerSetup(black = SidePlayerSetup(SeatController.Human), white = SidePlayerSetup(SeatController.Human))

    private var nextId = 0

    private fun game(
        winner: StoneColor?,
        daysAgo: Int = 0,
        setup: PlayerSetup = humanBlackVsAi,
        humanColor: StoneColor? = StoneColor.Black,
        moveCount: Int = 120,
        isResign: Boolean = false,
    ) = GameHistoryEntry(
        id = "game-${nextId++}",
        playedAtMillis = now - daysAgo * day,
        boardSize = 13,
        ruleset = Ruleset.Japanese,
        komi = 6.5,
        handicapCount = 0,
        playerSetup = setup,
        moveCount = moveCount,
        humanColor = humanColor,
        winner = winner,
        isResign = isResign,
    )

    private fun won(daysAgo: Int = 0) = game(winner = StoneColor.Black, daysAgo = daysAgo)

    private fun lost(daysAgo: Int = 0) = game(winner = StoneColor.White, daysAgo = daysAgo)

    @Test
    fun onlyGamesBetweenOnePersonAndAnAiCount() {
        val entries = listOf(
            won(),
            game(winner = StoneColor.Black, setup = aiVsAi, humanColor = null),
            game(winner = StoneColor.White, setup = humanVsHuman, humanColor = null),
        )

        assertEquals(WinRateTally(wins = 1), gameHistoryWinRate(entries, WinRatePeriod.AllTime, now).regular)
    }

    /** 사람이 백이어도 사람의 승패로 센다. */
    @Test
    fun theResultIsCountedFromThePersonsSide() {
        val humanWhite = PlayerSetup(black = SidePlayerSetup(SeatController.Ai), white = SidePlayerSetup(SeatController.Human))
        val entries = listOf(
            game(winner = StoneColor.White, setup = humanWhite, humanColor = StoneColor.White),
            game(winner = StoneColor.Black, setup = humanWhite, humanColor = StoneColor.White),
        )

        assertEquals(WinRateTally(wins = 1, losses = 1), gameHistoryWinRate(entries, WinRatePeriod.AllTime, now).regular)
    }

    /** 10수 이하 판은 세지 않는다(#208의 잣대) — 11수부터 센다. 승자를 모르는 옛 기권 기록도 세지 않는다. 무승부는 센다. */
    @Test
    fun shortGamesAndResignationsWithAnUnknownWinnerAreLeftOut() {
        assertFalse(game(winner = StoneColor.Black, moveCount = 10).countsForWinRate())
        assertTrue(game(winner = StoneColor.Black, moveCount = 11).countsForWinRate())
        assertFalse(game(winner = null, isResign = true).countsForWinRate())
        assertTrue(game(winner = null).countsForWinRate(), "a draw has no winner but a known result")
        assertTrue(game(winner = StoneColor.White, isResign = true).countsForWinRate(), "a resignation with a known winner counts")

        val entries = listOf(won(), game(winner = null), game(winner = null, isResign = true), game(winner = StoneColor.Black, moveCount = 4))
        assertEquals(WinRateTally(wins = 1, draws = 1), gameHistoryWinRate(entries, WinRatePeriod.AllTime, now).regular)
    }

    /** 기력 측정 대국은 따로 센다 — 일반 대국의 승률에 섞이지 않는다. */
    @Test
    fun rankMeasureGamesAreTalliedSeparately() {
        val measured = rankMeasurePlayerSetup(StoneColor.Black, KgsRank.kyu(10))
        val entries = listOf(won(), lost(), game(winner = StoneColor.Black, setup = measured), game(winner = StoneColor.Black, setup = measured))

        val rate = gameHistoryWinRate(entries, WinRatePeriod.AllTime, now)

        assertEquals(WinRateTally(wins = 1, losses = 1), rate.regular)
        assertEquals(WinRateTally(wins = 2), rate.rankMeasure)
    }

    /** 최근 10판은 **센 판** 가운데 가장 최근 10판이다 — 갈래마다 따로, 목록의 순서와 무관하게 둔 시각으로 가린다. */
    @Test
    fun theLastTenAreTheTenMostRecentCountedGamesOfEachKind() {
        val measured = rankMeasurePlayerSetup(StoneColor.Black, KgsRank.kyu(10))
        val recentWins = (1..10).map { won(daysAgo = it) }
        val olderLosses = (11..15).map { lost(daysAgo = it) }
        val tooShort = (0 until 3).map { game(winner = StoneColor.White, moveCount = 5) }
        val measuredLosses = (1..12).map { game(winner = StoneColor.White, setup = measured, daysAgo = it) }

        val rate = gameHistoryWinRate((olderLosses + tooShort + measuredLosses + recentWins).shuffled(kotlin.random.Random(7)), WinRatePeriod.LastTenGames, now)

        assertEquals(WinRateTally(wins = 10), rate.regular)
        assertEquals(WinRateTally(losses = 10), rate.rankMeasure)
    }

    /** 최근 한 달은 지금부터 30일이다. */
    @Test
    fun theLastMonthIsThirtyDaysBackFromNow() {
        val entries = listOf(won(daysAgo = 1), won(daysAgo = 30), lost(daysAgo = 31), lost(daysAgo = 200))

        assertEquals(WinRateTally(wins = 2), gameHistoryWinRate(entries, WinRatePeriod.LastMonth, now).regular)
        assertEquals(WinRateTally(wins = 2, losses = 2), gameHistoryWinRate(entries, WinRatePeriod.AllTime, now).regular)
    }

    /** 승률은 반올림한 정수이고, 센 판이 없으면 값이 없다(0%라고 말하지 않는다). 무승부는 판 수에 든다. */
    @Test
    fun theRateIsARoundedPercentAndAbsentWithoutGames() {
        assertNull(WinRateTally().winRatePercent)
        assertEquals(67, WinRateTally(wins = 2, losses = 1).winRatePercent)
        assertEquals(33, WinRateTally(wins = 1, losses = 2).winRatePercent)
        assertEquals(50, WinRateTally(wins = 1, draws = 1).winRatePercent)
        assertEquals(100, WinRateTally(wins = 3).winRatePercent)
    }
}
