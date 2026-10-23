package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.dailyfortune.DailyFortuneSlotsPerDay
import com.worksoc.goaicoach.application.dailyfortune.FortuneSign
import com.worksoc.goaicoach.application.dailyfortune.FortuneStrand
import com.worksoc.goaicoach.application.dailyfortune.StarSign
import com.worksoc.goaicoach.application.dailyfortune.ZodiacAnimal
import com.worksoc.goaicoach.ui.l10n.DailyFortuneCatalog
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.fortuneSignNameFor
import com.worksoc.goaicoach.ui.l10n.starSignDatesFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #276 — 오늘의 운세 글. 갈래마다 글 수가 하루의 칸 수(4)의 배수여야 갱신이 늘 다른 글을 꺼내고(`DailyFortune.kt`),
 * 4개 언어가 다 있어야 하며, 영 · 일 · 중에 한글이 섞이지 않아야 한다.
 */
class DailyFortuneCatalogTest {
    private val pools = FortuneStrand.entries.associateWith(DailyFortuneCatalog::pool)

    @Test
    fun everyPoolSizeIsAMultipleOfTheSlotsInADay() {
        pools.forEach { (strand, pool) ->
            assertTrue("${strand.key}: 글이 없다", pool.isNotEmpty())
            assertEquals("${strand.key}: ${pool.size}개는 $DailyFortuneSlotsPerDay 의 배수가 아니다", 0, pool.size % DailyFortuneSlotsPerDay)
            assertEquals(pool.size, DailyFortuneCatalog.sizes.of(strand))
        }
    }

    @Test
    fun idsAreUnique() {
        val ids = pools.values.flatten().map { it.id }
        assertEquals("id가 겹친다", ids.size, ids.toSet().size)
    }

    @Test
    fun everyTextHasAllFourLanguagesWithoutHangulOutsideKorean() {
        pools.values.flatten().forEach { text ->
            UiLanguage.entries.forEach { language ->
                val line = text.textFor(language)
                assertTrue("${text.id}/$language: 비었다", line.isNotBlank())
                if (language != UiLanguage.Korean) assertTrue("${text.id}/$language: 한글이 섞였다", line.none { it in '가'..'힣' })
            }
            assertTrue("${text.id}: 한국어에 한글이 없다", text.textFor(UiLanguage.Korean).any { it in '가'..'힣' })
            assertTrue("${text.id}: 중국어에 가나가 섞였다", text.textFor(UiLanguage.ChineseSimplified).none { it in '぀'..'ヿ' })
        }
    }

    /** 사용자: 점수 · 등급이 드러나지 않게 — 글에 등급 낱말(길흉 · 吉凶 포함 — 2차 검수가 「길해요」 · 「更吉」을 등급으로 읽었다)이 없는지 본다. 그물일 뿐, 검수는 LLM 두 번이 했다. */
    @Test
    fun noGradeWords() {
        val gradeWords = listOf("대길", "소길", "길해", "길하", "흉", "점수", "등급", "吉", "凶", "★")
        pools.values.flatten().forEach { text ->
            UiLanguage.entries.forEach { language ->
                val line = text.textFor(language)
                gradeWords.forEach { word -> assertTrue("${text.id}/$language: 「$word」", word !in line) }
            }
        }
    }

    @Test
    fun everySignHasANameInEveryLanguage() {
        UiLanguage.entries.forEach { language ->
            val names = FortuneSign.all.map { fortuneSignNameFor(language, it) }
            assertEquals("$language: 이름이 겹친다", names.size, names.toSet().size)
            StarSign.entries.forEach { assertTrue(starSignDatesFor(language, it).isNotBlank()) }
        }
        assertEquals("용띠", fortuneSignNameFor(UiLanguage.Korean, FortuneSign.Zodiac(ZodiacAnimal.Dragon)))
        assertEquals("3.21~4.19", starSignDatesFor(UiLanguage.Korean, StarSign.Aries))
    }
}
