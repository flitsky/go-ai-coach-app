package com.worksoc.goaicoach.application.dailyquote

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 백로그 #273 — 오늘의 한 줄 명언: 처음 한 번 섞은 차례대로 하루에 하나, 섞은 차례와 본 것은 따로, 다 돌면 다시 섞는다(사용자 2026-10-20 · 10-21). */
class DailyQuoteRotationTest {
    private val catalog = (1..12).map { "q$it" }

    /** [days]일 동안 하루에 한 번씩 고른다. */
    private fun run(days: IntRange, start: DailyQuoteProgress = DailyQuoteProgress(), ids: List<String> = catalog, seed: Int = 7): Pair<List<String>, DailyQuoteProgress> {
        var progress = start
        val random = Random(seed)
        val picked = days.map { day ->
            val pick = pickDailyQuote(ids, progress, today = day.toLong(), random = random)
            progress = pick.progress
            requireNotNull(pick.quoteId)
        }
        return picked to progress
    }

    @Test
    fun theFirstRunShufflesOnceAndThenFollowsThatOrderOneADay() {
        val (picked, progress) = run(1..5)
        assertEquals(progress.order.take(5), picked, "the days follow the stored order")
        assertNotEquals(catalog, progress.order, "the first run shuffles")
        assertEquals(picked.toSet(), progress.shown)
        assertEquals(5L, progress.todayDay)
    }

    @Test
    fun theSameDayKeepsTheSameQuoteAndWritesNothing() {
        val first = pickDailyQuote(catalog, DailyQuoteProgress(), today = 100, random = Random(1))
        val again = pickDailyQuote(catalog, first.progress, today = 100, random = Random(2))
        assertEquals(first.quoteId, again.quoteId)
        assertFalse(again.changed)
    }

    @Test
    fun aClockTurnedBackDoesNotAdvanceTheQuote() {
        val first = pickDailyQuote(catalog, DailyQuoteProgress(), today = 100, random = Random(1))
        val back = pickDailyQuote(catalog, first.progress, today = 98, random = Random(1))
        assertEquals(first.quoteId, back.quoteId)
        assertFalse(back.changed)
    }

    @Test
    fun skippedDaysDoNotSkipQuotes() {
        val day1 = pickDailyQuote(catalog, DailyQuoteProgress(), today = 10, random = Random(3))
        val day40 = pickDailyQuote(catalog, day1.progress, today = 40, random = Random(3))
        assertEquals(day1.progress.order[1], day40.quoteId, "the next one in order, however long the gap")
    }

    @Test
    fun aFullRoundShowsEveryQuoteOnceThenReshufflesWithoutRepeatingTheLastOne() {
        repeat(30) { seed ->
            val (picked, _) = run(1..catalog.size * 2, seed = seed)
            val firstRound = picked.take(catalog.size)
            val secondRound = picked.drop(catalog.size)
            assertEquals(catalog.toSet(), firstRound.toSet(), "seed $seed: every quote once in a round")
            assertEquals(catalog.toSet(), secondRound.toSet(), "seed $seed: and once again in the next round")
            assertNotEquals(firstRound.last(), secondRound.first(), "seed $seed: the last quote never comes right back")
        }
    }

    @Test
    fun differentInstallsShuffleDifferently() {
        val orders = (0 until 20).map { seed -> run(1..1, seed = seed).second.order }.toSet()
        assertTrue(orders.size > 15, "no fixed seed — installs get their own order (${orders.size}/20 distinct)")
    }

    @Test
    fun aRestoredBackupContinuesWhereItLeftOff() {
        val (before, saved) = run(1..4)
        // 다시 깔고 복원 — 같은 진행도에서 다음 날
        val (after, _) = run(5..6, start = saved, seed = 99)
        assertEquals(saved.order.subList(4, 6), after)
        assertTrue(after.none { it in before })
    }

    @Test
    fun anAppUpdateThatAddsQuotesSlotsThemIntoTheUnseenPartAndKeepsWhatWasSeen() {
        val (seen, saved) = run(1..5)
        val grown = catalog + listOf("n1", "n2", "n3")
        val (rest, after) = run(6..(5 + grown.size - seen.size), start = saved, ids = grown, seed = 5)
        assertEquals(grown.toSet() - seen.toSet(), rest.toSet(), "the round finishes with every unseen quote, new ones included")
        assertEquals(seen, after.order.take(5), "what was seen stays at the front of the order")
    }

    @Test
    fun anAppUpdateThatDropsQuotesForgetsThemAndMovesOn() {
        val first = pickDailyQuote(catalog, DailyQuoteProgress(), today = 1, random = Random(4))
        val shrunk = catalog - requireNotNull(first.quoteId)
        val sameDay = pickDailyQuote(shrunk, first.progress, today = 1, random = Random(4))
        assertTrue(sameDay.quoteId in shrunk, "today's quote was dropped — another one takes its place")
        assertFalse(sameDay.progress.order.contains(first.quoteId))
    }

    @Test
    fun noQuotesMeansNothingToShow() {
        val pick = pickDailyQuote(emptyList(), DailyQuoteProgress(), today = 1, random = Random(1))
        assertNull(pick.quoteId)
        assertFalse(pick.changed)
    }

    @Test
    fun closingItHidesItForThatDayOnly() {
        val picked = pickDailyQuote(catalog, DailyQuoteProgress(), today = 7, random = Random(1)).progress.copy(dismissedDay = 7)
        assertTrue(picked.isDismissedOn(7))
        val nextDay = pickDailyQuote(catalog, picked, today = 8, random = Random(1))
        assertFalse(nextDay.progress.isDismissedOn(8))
        assertNotEquals(picked.todayId, nextDay.quoteId)
    }
}
