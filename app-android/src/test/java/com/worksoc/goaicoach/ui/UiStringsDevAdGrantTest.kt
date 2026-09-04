package com.worksoc.goaicoach.ui

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 개발자 2차의 프리미엄 부여 부제([UiStrings.settingsDevAdGrantSubtitle])의 **손 그물**(백로그 #78).
 *
 * ⚠️ **`UiStringsTest`의 리플렉션 그물은 String *필드*만 본다** — 이 문구는 남은 시간을 말해야 해서
 * `fun`이고 자동 그물에 안 잡힌다(함정 10번).
 */
class UiStringsDevAdGrantTest {

    private val languages = UiLanguage.entries

    @Test
    fun everyLanguageHasCopyForBothTheActiveAndInactiveCase() {
        languages.forEach { language ->
            val strings = UiStrings.forLanguage(language)
            assertTrue("$language: 꺼진 상태 부제가 비었다", strings.settingsDevAdGrantSubtitle(null).isNotBlank())
            val active = strings.settingsDevAdGrantSubtitle(42)
            assertTrue("$language: 활성 상태 부제가 비었다", active.isNotBlank())
            assertTrue("$language: 활성 부제에 남은 분이 없다", active.contains("42"))
        }
    }

    /**
     * ⚠️ **활성/비활성 문구가 갈려야 한다.** 만료를 눈으로 확인하는 것이 이 버튼의 목적 절반이라
     * (#26 구독 유효기간 판정의 전초전), 두 상태가 같은 문장이면 그 확인이 불가능해진다.
     */
    @Test
    fun theActiveAndInactiveCasesReadDifferentlyInEveryLanguage() {
        languages.forEach { language ->
            val strings = UiStrings.forLanguage(language)
            assertNotEquals(
                "$language: 활성/비활성 부제가 같다 — 만료를 확인할 수 없다(#78).",
                strings.settingsDevAdGrantSubtitle(null),
                strings.settingsDevAdGrantSubtitle(59),
            )
        }
    }

    @Test
    fun nonKoreanCopyDoesNotInheritTheKoreanBaseline() {
        val koreanActive = UiStrings.forLanguage(UiLanguage.Korean).settingsDevAdGrantSubtitle(30)
        val koreanIdle = UiStrings.forLanguage(UiLanguage.Korean).settingsDevAdGrantSubtitle(null)
        languages.filter { it != UiLanguage.Korean }.forEach { language ->
            val strings = UiStrings.forLanguage(language)
            assertNotEquals("$language 활성 부제가 한국어와 같다", koreanActive, strings.settingsDevAdGrantSubtitle(30))
            assertNotEquals("$language 비활성 부제가 한국어와 같다", koreanIdle, strings.settingsDevAdGrantSubtitle(null))
        }
    }
}
