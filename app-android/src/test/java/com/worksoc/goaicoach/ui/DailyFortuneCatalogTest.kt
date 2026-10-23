package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.ui.l10n.DailyFortuneCatalog
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #276 — 랜덤 운세의 글. 사용자: 최소 365개(2026-10-23). 4개 언어가 다 있어야 하고, 영 · 일 · 중에 한글이 섞이지 않아야 하며,
 * 설정 없이 누구에게나 보이므로 별자리를 고른 사람을 전제하지 않는다.
 */
class DailyFortuneCatalogTest {
    private val fortunes = DailyFortuneCatalog.fortunes

    @Test
    fun atLeast365Fortunes() {
        assertTrue("운세가 ${fortunes.size}개 — 사용자: 최소 365개", fortunes.size >= 365)
        assertEquals(DailyFortuneCatalog.general + DailyFortuneCatalog.star + DailyFortuneCatalog.go, fortunes)
    }

    @Test
    fun idsAreUnique() {
        val ids = fortunes.map { it.id }
        assertEquals("id가 겹친다", ids.size, ids.toSet().size)
    }

    @Test
    fun everyTextHasAllFourLanguagesWithoutHangulOutsideKorean() {
        fortunes.forEach { text ->
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
        // 「흉」 한 글자는 「흉내」에도 들어 있어 낱말로 막는다(b-77).
        val gradeWords = listOf("대길", "소길", "길해", "길하", "흉하", "흉한", "흉운", "점수", "등급", "吉", "凶", "★")
        fortunes.forEach { text ->
            UiLanguage.entries.forEach { language ->
                val line = text.textFor(language)
                gradeWords.forEach { word -> assertTrue("${text.id}/$language: 「$word」", word !in line) }
            }
        }
    }

    /** 랜덤 운세는 설정 없이 보인다(사용자 2026-10-23) — 「별자리의 기운」처럼 별자리를 고른 사람을 전제하는 글이 없어야 한다. */
    @Test
    fun noTextPresumesAChosenSign() {
        val signWords = mapOf(
            UiLanguage.Korean to listOf("별자리", "당신"),
            UiLanguage.English to listOf("your sign", "Your sign"),
            UiLanguage.Japanese to listOf("星座", "干支"),
            UiLanguage.ChineseSimplified to listOf("星座", "生肖"),
        )
        fortunes.forEach { text ->
            signWords.forEach { (language, words) ->
                val line = text.textFor(language)
                words.forEach { word -> assertTrue("${text.id}/$language: 「$word」", word !in line) }
            }
        }
    }
}
