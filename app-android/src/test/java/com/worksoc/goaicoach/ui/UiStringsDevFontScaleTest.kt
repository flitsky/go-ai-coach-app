package com.worksoc.goaicoach.ui

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 개발자 1차 글꼴 배율 행 부제([UiStrings.settingsDevFontScaleSubtitle])의 **손 그물**(백로그 #81).
 *
 * ⚠️ 이 문구는 상태(배율, 오버라이드 여부)를 말해야 해서 `fun`이고, 따라서 `UiStringsTest`의
 * 리플렉션 그물에 **아예 안 잡힌다**(함정 10번).
 */
class UiStringsDevFontScaleTest {

    private val languages = UiLanguage.entries

    @Test
    fun everyLanguageHasCopyForBothTheSystemAndOverriddenCase() {
        languages.forEach { language ->
            val strings = UiStrings.forLanguage(language)
            listOf(true, false).forEach { overridden ->
                val subtitle = strings.settingsDevFontScaleSubtitle(current = 1.3f, isOverridden = overridden)
                assertTrue("$language / overridden=$overridden 부제가 비었다", subtitle.isNotBlank())
                assertTrue("$language / overridden=$overridden 부제에 배율이 없다", subtitle.contains("1.3"))
            }
        }
    }

    /**
     * ⚠️ **시스템 값과 앱 오버라이드의 문구가 갈려야 한다.** 같으면 오버라이드 중인데도 "시스템
     * 값"으로 읽혀 *"왜 시스템에서 바꿔도 안 변하지"* 로 오진하고, 반대 방향으로도 헷갈린다.
     */
    @Test
    fun theSystemAndOverriddenCasesReadDifferentlyInEveryLanguage() {
        languages.forEach { language ->
            val strings = UiStrings.forLanguage(language)
            assertNotEquals(
                "$language: 시스템 값과 앱 오버라이드의 부제가 같다 — 어느 쪽인지 알 수 없다(#81).",
                strings.settingsDevFontScaleSubtitle(current = 1.3f, isOverridden = false),
                strings.settingsDevFontScaleSubtitle(current = 1.3f, isOverridden = true),
            )
        }
    }

    @Test
    fun nonKoreanCopyDoesNotInheritTheKoreanBaseline() {
        val korean = UiStrings.forLanguage(UiLanguage.Korean)
            .settingsDevFontScaleSubtitle(current = 2.0f, isOverridden = true)
        languages.filter { it != UiLanguage.Korean }.forEach { language ->
            assertNotEquals(
                "$language 부제가 한국어와 같다 — 번역이 빠졌다.",
                korean,
                UiStrings.forLanguage(language).settingsDevFontScaleSubtitle(current = 2.0f, isOverridden = true),
            )
        }
    }

    /**
     * ⚠️ **배율 순환은 #64가 실제로 검증한 세 값을 돈다** — 임의 슬라이더보다 그 회귀를 다시
     * 밟는 데 정확하다. 그리고 **한 바퀴 돌면 시스템 값으로 돌아와야** 한다(꺼짐 상태가 없으면
     * 원래 배율을 잃는다).
     */
    @Test
    fun theOverrideCyclesThroughTheScalesThatSixtyFourVerifiedAndBackToSystem() {
        val seen = mutableListOf<Float?>()
        repeat(4) {
            DevFontScaleOverride.cycle()
            seen += DevFontScaleOverride.scale
        }
        assertTrue(
            "배율 순환이 1.0 → 1.3 → 2.0 → 시스템이 아니다: $seen",
            seen == listOf(1.0f, 1.3f, 2.0f, null),
        )
    }
}
