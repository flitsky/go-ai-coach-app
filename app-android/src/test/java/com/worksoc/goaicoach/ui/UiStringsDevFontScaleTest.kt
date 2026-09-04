package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.preferences.AppFontScales
import com.worksoc.goaicoach.application.preferences.DefaultAppFontScale
import com.worksoc.goaicoach.application.preferences.nextAppFontScale
import com.worksoc.goaicoach.application.preferences.sanitizeAppFontScale
import org.junit.Assert.assertEquals
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
    fun everyLanguageStatesTheScaleItIsShowing() {
        languages.forEach { language ->
            val strings = UiStrings.forLanguage(language)
            AppFontScales.forEach { scale ->
                val subtitle = strings.settingsDevFontScaleSubtitle(scale)
                assertTrue("$language / $scale 부제가 비었다", subtitle.isNotBlank())
                assertTrue("$language / $scale 부제에 배율이 없다", subtitle.contains(scale.toString()))
            }
        }
    }

    /**
     * ⚠️ **배율마다 부제가 달라야 한다** — 같으면 버튼을 눌러도 바뀐 것이 화면에 안 드러나고,
     * 그것이 이 항목을 만든 제보(*"동작하지 않는다"*)와 같은 인상을 준다.
     */
    @Test
    fun eachScaleReadsDifferentlyInEveryLanguage() {
        languages.forEach { language ->
            val strings = UiStrings.forLanguage(language)
            val subtitles = AppFontScales.map { scale -> strings.settingsDevFontScaleSubtitle(scale) }
            assertEquals(
                "$language: 서로 다른 배율의 부제가 겹친다 — 바뀐 것이 화면에 안 드러난다(#81).",
                AppFontScales.size,
                subtitles.toSet().size,
            )
        }
    }

    @Test
    fun nonKoreanCopyDoesNotInheritTheKoreanBaseline() {
        val korean = UiStrings.forLanguage(UiLanguage.Korean).settingsDevFontScaleSubtitle(1.3f)
        languages.filter { it != UiLanguage.Korean }.forEach { language ->
            assertNotEquals(
                "$language 부제가 한국어와 같다 — 번역이 빠졌다.",
                korean,
                UiStrings.forLanguage(language).settingsDevFontScaleSubtitle(1.3f),
            )
        }
    }

    /** 순환이 목록을 한 바퀴 돌아 제자리로 온다 — 어느 값에서도 갇히지 않아야 한다. */
    @Test
    fun theCycleWrapsAroundTheWholeList() {
        var scale = AppFontScales.first()
        val seen = mutableListOf(scale)
        repeat(AppFontScales.size) {
            scale = nextAppFontScale(scale)
            seen += scale
        }
        assertEquals(
            "순환이 목록을 한 바퀴 돌지 않는다: $seen",
            AppFontScales + AppFontScales.first(),
            seen,
        )
    }

    /**
     * ⚠️ **모르는 값은 기본값으로 접힌다.** 저장이 손 편집되거나(개발자 도구가 그 파일을 쓴다)
     * 값 셋이 줄어 옛 저장분이 남으면, 그대로 두면 **순환 버튼이 아무 일도 안 하는 것처럼 보인다.**
     */
    @Test
    fun anUnknownStoredScaleFallsBackRatherThanSticking() {
        assertEquals(DefaultAppFontScale, sanitizeAppFontScale(0f))
        assertEquals(DefaultAppFontScale, sanitizeAppFontScale(-1f))
        assertEquals(DefaultAppFontScale, sanitizeAppFontScale(2.0f))
        assertEquals(AppFontScales.first(), nextAppFontScale(9.9f))
    }
}
