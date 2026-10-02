package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.ui.l10n.BoardScanStrings
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.boardScanStringsFor
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 바둑판 사진 분석 화면 문구(백로그 #210) — 리플렉션 그물(`UiStringsTest`) 밖이라 이 파일이 손 그물이다(함정 10).
 * 데이터 클래스의 **모든 문자열 필드**를 컴포넌트로 훑으므로, 필드를 더해도 이 테스트를 고칠 필요가 없다.
 */
class UiStringsBoardScanTest {

    @Test
    fun everyBoardScanStringExistsInEveryLanguage() {
        UiLanguage.entries.forEach { language ->
            val s = boardScanStringsFor(language)
            BoardScanStrings::class.java.declaredFields
                .filter { it.type == String::class.java }
                .forEach { field ->
                    field.isAccessible = true
                    val value = field.get(s) as String
                    assertTrue("$language / ${field.name} 문구가 비었다", value.isNotBlank())
                }
            listOf(
                s.boardSizeChip(19),
                s.analysisFailed("x"),
                s.stoneCounts(3, 4),
                s.noLibertyWarning(2),
                s.komiChip(6.5),
                s.winRate(55.0, 45.0),
                s.scoreLead(true, 3.5),
                s.candidatesFound(5),
            ).forEach { assertTrue("$language 함수 문구가 비었다: $it", it.isNotBlank()) }
        }
    }

    /** 숫자를 받는 문구는 그 숫자를 말해야 한다 — 손으로 박은 값이 남지 않게. */
    @Test
    fun numberedTextsCarryTheirNumbers() {
        UiLanguage.entries.forEach { language ->
            val s = boardScanStringsFor(language)
            assertTrue("$language 판 크기", s.boardSizeChip(13).contains("13"))
            assertTrue("$language 숨 없는 돌", s.noLibertyWarning(7).contains("7"))
            assertTrue("$language 돌 개수", s.stoneCounts(11, 12).let { it.contains("11") && it.contains("12") })
        }
    }
}
