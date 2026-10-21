package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.ui.l10n.DailyQuoteCatalog
import com.worksoc.goaicoach.ui.l10n.DailyQuoteChapter
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #273 — 오늘의 한 줄 명언 목록. id는 저장값이고(바꾸면 본 기록이 끊긴다), 4개 언어가 다 있어야 하며(사용자 2026-10-21),
 * 폰 홈 한 줄 자리에서 두 줄 안에 들어가야 한다(사용자 2026-10-21).
 */
class DailyQuoteCatalogTest {
    private val quotes = DailyQuoteCatalog.quotes

    @Test
    fun idsAreUniqueAndSafeToStore() {
        val ids = quotes.map { it.id }
        assertEquals("id가 겹친다", ids.size, ids.toSet().size)
        // 저장소는 쉼표로 이어 적는다(`DailyQuoteStore`) — id에 쉼표·공백이 들어가면 차례가 깨진다.
        ids.forEach { assertTrue("저장할 수 없는 id: $it", it.matches(Regex("[a-z0-9-]+"))) }
        ids.forEach { assertEquals(it, DailyQuoteCatalog.quote(it)?.id) }
    }

    @Test
    fun everyQuoteHasAllFourLanguagesAndASource() {
        quotes.forEach { quote ->
            assertTrue("${quote.id}: 출처가 비었다", quote.source.isNotBlank())
            UiLanguage.entries.forEach { language ->
                val line = quote.lineFor(language)
                assertTrue("${quote.id}/$language: 명언이 비었다", line.text.isNotBlank())
                assertTrue("${quote.id}/$language: 말한 이가 비었다", line.by.isNotBlank())
                if (language != UiLanguage.Korean) {
                    assertTrue("${quote.id}/$language: 한글이 섞였다", (line.text + line.by).none { it in '가'..'힣' })
                }
            }
        }
    }

    /**
     * 폰(360dp) 홈 한 줄 자리의 글자 폭 어림 — 한·중·일 글자는 2, 그 밖은 1(「명언  — 말한 이」 전체). 언어마다 기준은 에뮬레이터(360dp · 기본 글꼴)에서
     * 두 줄에 든 가장 긴 것이다(2026-10-21). 일본어가 짧은 것은 구절 단위로 줄을 바꾸기 때문이다(`HomeDailyQuote.kt`).
     * ⚠️ **그물일 뿐 보증이 아니다** — 긴 낱말 때문에 어림 80칸인 영어가 세 줄이 되기도 했다. 확정은 실제 배치로 잰다: 디버그 빌드에서
     * `files/debug-quote-sweep`을 만들고 홈을 열면 지금 언어의 명언 전부의 줄 수가 로그로 나온다(`adb logcat -s DailyQuoteSweep`) — 2026-10-21 네 언어 696줄 모두 두 줄 이하.
     * 글꼴을 크게(1.3배) 하면 세 줄이 될 수 있다 — 자르지 않고 줄을 늘린다.
     */
    @Test
    fun everyQuoteFitsInTwoLinesOnAPhone() {
        val tooLong = quotes.flatMap { quote ->
            UiLanguage.entries.mapNotNull { language ->
                val line = quote.lineFor(language)
                val units = displayUnits("${line.text}  — ${line.by}")
                if (units > twoLineUnits.getValue(language)) "${quote.id}/$language: $units" else null
            }
        }
        assertEquals("두 줄을 넘는다($twoLineUnits)", emptyList<String>(), tooLong)
    }

    @Test
    fun everyChapterHasQuotes() {
        // 장별 고르기(아이디어 I-6)가 생기면 빈 장은 고를 것이 없다 — 지금 실린 다섯 장이 다 차 있는지 본다.
        val empty = DailyQuoteChapter.entries.filter { chapter -> quotes.none { it.chapter == chapter } }
        assertEquals(emptyList<DailyQuoteChapter>(), empty)
    }

    private fun displayUnits(text: String): Int = text.codePoints().toArray().sumOf { if (it > 0x2E80) 2 else 1 }

    private val twoLineUnits = mapOf(
        UiLanguage.Korean to 91,
        UiLanguage.English to 85,
        UiLanguage.Japanese to 77,
        UiLanguage.ChineseSimplified to 84,
    )
}
