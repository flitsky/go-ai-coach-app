package com.worksoc.goaicoach.application.dailyfortune

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val Today = 20_384L
private val Sizes = FortunePoolSizes(general = 48, star = 48, go = 48, luckyPoint = 8)
private val Dragon = FortuneSign.Zodiac(ZodiacAnimal.Dragon)
private val Leo = FortuneSign.Star(StarSign.Leo)

/** 백로그 #276 — 오늘의 운세 규칙(`DailyFortune.kt`). */
class DailyFortuneTest {
    @Test
    fun sameDaySignAndRefreshGiveTheSameReadingForEveryone() {
        repeat(DailyFortuneSlotsPerDay) { used ->
            assertEquals(dailyFortuneReading(Dragon, Today, used, Sizes), dailyFortuneReading(Dragon, Today, used, Sizes))
        }
    }

    /**
     * 씨앗과 섞기를 바꾸면 모두의 그날 운세가 바뀐다 — 그런 변경을 알아채려고 한 값을 못 박는다. iOS(Kotlin/Native)에서도 같은 답이어야 한다.
     * 일부러 바꾼 것이면 이 값을 새로 적는다.
     */
    @Test
    fun theSeedIsPinned() {
        assertEquals(PinnedDragonReading, dailyFortuneReading(Dragon, Today, 0, Sizes))
    }

    @Test
    fun aZodiacAnimalReadsTheGeneralStrandAndAStarSignTheStarStrand() {
        assertEquals(FortuneStrand.General, dailyFortuneReading(Dragon, Today, 0, Sizes).mainStrand)
        assertEquals(FortuneStrand.Star, dailyFortuneReading(Leo, Today, 0, Sizes).mainStrand)
    }

    @Test
    fun everyRefreshOfADayShowsADifferentFortune() {
        FortuneSign.all.forEach { sign ->
            (Today until Today + 30).forEach { day ->
                val readings = (0 until DailyFortuneSlotsPerDay).map { dailyFortuneReading(sign, day, it, Sizes) }
                assertEquals(DailyFortuneSlotsPerDay, readings.map { it.mainIndex }.toSet().size, "${sign.key} $day 운세")
                assertEquals(DailyFortuneSlotsPerDay, readings.map { it.goIndex }.toSet().size, "${sign.key} $day 바둑 운세")
                assertEquals(DailyFortuneSlotsPerDay, readings.map { it.luckyPointIndex }.toSet().size, "${sign.key} $day 행운의 자리")
            }
        }
    }

    @Test
    fun theFirstFortuneDoesNotRepeatWithinACycle() {
        val daysPerCycle = Sizes.general / DailyFortuneSlotsPerDay
        val firstDay = Today - Today.mod(daysPerCycle.toLong())
        val firsts = (firstDay until firstDay + daysPerCycle).map { dailyFortuneReading(Dragon, it, 0, Sizes).mainIndex }
        assertEquals(daysPerCycle, firsts.toSet().size)
    }

    @Test
    fun indicesStayInsideTheirPools() {
        FortuneSign.all.forEach { sign ->
            (0 until DailyFortuneSlotsPerDay).forEach { used ->
                val reading = dailyFortuneReading(sign, Today, used, Sizes)
                assertTrue(reading.mainIndex in 0 until Sizes.of(reading.mainStrand))
                assertTrue(reading.goIndex in 0 until Sizes.go)
                assertTrue(reading.luckyPointIndex in 0 until Sizes.luckyPoint)
            }
        }
    }

    @Test
    fun differentSignsReadDifferently() {
        val firsts = FortuneSign.all.filterIsInstance<FortuneSign.Zodiac>().map { dailyFortuneReading(it, Today, 0, Sizes).mainIndex }
        assertTrue(firsts.toSet().size > 1, "띠 열둘이 모두 같은 운세다")
    }

    @Test
    fun threeRefreshesADayThenNone() {
        var progress = DailyFortuneProgress().withSign(Dragon)
        repeat(DailyFortuneMaxRefreshes) { time ->
            assertEquals(DailyFortuneMaxRefreshes - time, progress.refreshesLeftOn(Today))
            progress = refreshDailyFortune(progress, Today)!!
        }
        assertEquals(0, progress.refreshesLeftOn(Today))
        assertNull(refreshDailyFortune(progress, Today))
    }

    @Test
    fun aNewDayResetsTheRefreshesEvenWhenTheClockWentBack() {
        val spent = DailyFortuneProgress(signKey = Dragon.key, day = Today, refreshesUsed = DailyFortuneMaxRefreshes)
        assertEquals(DailyFortuneMaxRefreshes, spent.refreshesLeftOn(Today + 1))
        assertEquals(DailyFortuneMaxRefreshes, spent.refreshesLeftOn(Today - 1))
        assertEquals(DailyFortuneProgress(Dragon.key, Today + 1, 1), refreshDailyFortune(spent, Today + 1))
    }

    @Test
    fun changingTheSignKeepsTheRefreshesSpentToday() {
        val progress = refreshDailyFortune(DailyFortuneProgress().withSign(Dragon), Today)!!.withSign(Leo)
        assertEquals(Leo, progress.sign)
        assertEquals(1, progress.refreshesUsedOn(Today))
    }

    @Test
    fun nothingToRefreshBeforeASignIsChosen() {
        assertNull(refreshDailyFortune(DailyFortuneProgress(), Today))
        assertNull(refreshDailyFortune(DailyFortuneProgress(signKey = "zodiac:cat"), Today))
    }

    @Test
    fun storedRefreshCountsOutsideTheRangeAreClamped() {
        val tampered = DailyFortuneProgress(signKey = Dragon.key, day = Today, refreshesUsed = 99)
        assertEquals(0, tampered.refreshesLeftOn(Today))
        assertEquals(dailyFortuneReading(Dragon, Today, DailyFortuneMaxRefreshes, Sizes), dailyFortuneReading(Dragon, Today, 99, Sizes))
    }

    @Test
    fun signKeysAreUniqueAndReadBack() {
        val keys = FortuneSign.all.map { it.key }
        assertEquals(24, keys.toSet().size)
        FortuneSign.all.forEach { assertEquals(it, FortuneSign.fromKey(it.key)) }
        assertNull(FortuneSign.fromKey(null))
        assertNull(FortuneSign.fromKey("dragon"))
        assertNull(FortuneSign.fromKey("zodiac:"))
        assertNotEquals(Dragon.key, Leo.key)
    }

    @Test
    fun starSignDatesCoverTheWholeYearWithoutGaps() {
        // 별자리의 끝 다음 날이 다음 별자리의 첫날이다(물고기자리 다음은 양자리).
        val daysInMonth = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        StarSign.entries.forEachIndexed { index, sign ->
            val next = StarSign.entries[(index + 1) % StarSign.entries.size]
            val endIsMonthEnd = sign.toDay == daysInMonth[sign.toMonth - 1]
            val (month, day) = if (endIsMonthEnd) (sign.toMonth % 12 + 1) to 1 else sign.toMonth to sign.toDay + 1
            assertEquals(month to day, next.fromMonth to next.fromDay, "${sign.key} 다음")
        }
    }
}

/** [DailyFortuneTest.theSeedIsPinned]의 값 — 2026-10-23 처음 적었다. */
private val PinnedDragonReading = DailyFortuneReading(FortuneStrand.General, mainIndex = 12, goIndex = 10, luckyPointIndex = 1)
