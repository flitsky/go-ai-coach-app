package com.worksoc.goaicoach.application.dailyfortune

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val Today = 20_749L
private const val Pool = 368
private const val Tap = 1_793_000_000_123L

/** 백로그 #276 — 오늘의 운세 규칙(`DailyFortune.kt`): 누른 순간으로 뽑는 랜덤 운세 · 하루 3번 갱신. */
class DailyFortuneTest {
    @Test
    fun theFirstDrawOfTheDayUsesTheTapTimestamp() {
        val drawn = assertNotNull(drawDailyFortune(DailyFortuneProgress(), Today, Tap, Pool))
        assertEquals(Today, drawn.day)
        assertEquals(fortuneIndexAt(Tap, Pool, excluding = null), drawn.fortuneIndex)
        assertEquals(0, drawn.refreshesUsed)
        assertEquals(drawn.fortuneIndex, drawn.todaysIndex(Today, Pool))
    }

    @Test
    fun differentMomentsDrawAcrossThePool() {
        val picks = (0L until 2_000L).map { fortuneIndexAt(Tap + it * 997, Pool, excluding = null) }.toSet()
        assertTrue(picks.size > Pool / 2, "누른 순간이 달라도 고르는 글이 몇 개에 몰린다: ${picks.size}")
        assertTrue(picks.all { it in 0 until Pool })
    }

    @Test
    fun theSameMomentDrawsTheSameFortune() {
        assertEquals(fortuneIndexAt(Tap, Pool, null), fortuneIndexAt(Tap, Pool, null))
    }

    @Test
    fun aDrawnFortuneStaysForTheRestOfTheDay() {
        val drawn = drawDailyFortune(DailyFortuneProgress(), Today, Tap, Pool)!!
        assertNull(drawDailyFortune(drawn, Today, Tap + 60_000, Pool), "같은 날 다시 누르면 새로 뽑지 않는다")
        assertEquals(drawn.fortuneIndex, drawn.todaysIndex(Today, Pool))
    }

    @Test
    fun aNewDayNeedsANewDrawAndResetsTheRefreshes() {
        val spent = DailyFortuneProgress(day = Today, fortuneIndex = 7, refreshesUsed = DailyFortuneMaxRefreshes)
        assertNull(spent.todaysIndex(Today + 1, Pool))
        assertNull(spent.todaysIndex(Today - 1, Pool), "시계를 되감아도 새 날로 본다")
        assertEquals(DailyFortuneMaxRefreshes, spent.refreshesLeftOn(Today + 1))
        val drawn = drawDailyFortune(spent, Today + 1, Tap, Pool)!!
        assertEquals(0, drawn.refreshesUsed)
    }

    @Test
    fun threeRefreshesADayEachShowingSomethingNew() {
        var progress = drawDailyFortune(DailyFortuneProgress(), Today, Tap, Pool)!!
        repeat(DailyFortuneMaxRefreshes) { time ->
            assertEquals(DailyFortuneMaxRefreshes - time, progress.refreshesLeftOn(Today))
            val before = progress.fortuneIndex
            progress = refreshDailyFortune(progress, Today, Tap + (time + 1) * 1_000L, Pool)!!
            assertNotEquals(before, progress.fortuneIndex)
        }
        assertEquals(0, progress.refreshesLeftOn(Today))
        assertNull(refreshDailyFortune(progress, Today, Tap + 9_000, Pool))
    }

    @Test
    fun aRefreshThatLandsOnTheSameFortuneMovesOn() {
        val same = fortuneIndexAt(Tap, Pool, excluding = null)
        assertEquals((same + 1) % Pool, fortuneIndexAt(Tap, Pool, excluding = same))
        assertEquals(0, fortuneIndexAt(Tap, 1, excluding = 0), "글이 하나뿐이면 그대로")
    }

    @Test
    fun nothingToRefreshBeforeTheDaysDraw() {
        assertNull(refreshDailyFortune(DailyFortuneProgress(), Today, Tap, Pool))
        val yesterday = DailyFortuneProgress(day = Today - 1, fortuneIndex = 3, refreshesUsed = 0)
        assertNull(refreshDailyFortune(yesterday, Today, Tap, Pool))
    }

    @Test
    fun aFortuneThatNoLongerExistsIsDrawnAgainKeepingTheRefreshesSpent() {
        val shrunk = DailyFortuneProgress(day = Today, fortuneIndex = Pool + 5, refreshesUsed = 2)
        assertNull(shrunk.todaysIndex(Today, Pool))
        val redrawn = drawDailyFortune(shrunk, Today, Tap, Pool)!!
        assertEquals(2, redrawn.refreshesUsed)
    }

    @Test
    fun storedRefreshCountsOutsideTheRangeAreClamped() {
        val tampered = DailyFortuneProgress(day = Today, fortuneIndex = 1, refreshesUsed = 99)
        assertEquals(0, tampered.refreshesLeftOn(Today))
        assertNull(refreshDailyFortune(tampered, Today, Tap, Pool))
    }
}
