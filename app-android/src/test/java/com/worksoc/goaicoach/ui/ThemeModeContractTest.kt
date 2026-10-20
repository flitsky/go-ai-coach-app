package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import com.worksoc.goaicoach.shared.policy.ThemeMode
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.themeModeLabelFor
import com.worksoc.goaicoach.ui.l10n.themeModeNightHoursHintFor
import com.worksoc.goaicoach.ui.l10n.themeModeTitleFor
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 야간 모드(백로그 #272)가 지켜야 할 것들 — 어겨도 컴파일은 되고 화면만 조용히 틀어지는 자리라 소스 계약으로 든다.
 */
class ThemeModeContractTest {

    private fun source(path: String): String = File(path).readContractSource()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .lines()
        .filterNot { it.trimStart().startsWith("import ") }
        .joinToString("\n") { it.substringBefore("//") }

    /** 리플렉션 그물 밖의 곁표라(함정 10) 네 언어 × 넷 갈래가 다 찼는지 손으로 본다. */
    @Test
    fun everyLanguageNamesEveryMode() {
        UiLanguage.entries.forEach { language ->
            assertTrue(themeModeTitleFor(language).isNotBlank())
            assertTrue(themeModeNightHoursHintFor(language).isNotBlank())
            val labels = ThemeMode.entries.map { themeModeLabelFor(language, it) }
            assertTrue("$language 에 빈 이름이 있다: $labels", labels.all { it.isNotBlank() })
            assertEquals("$language 에 같은 이름이 둘 있다: $labels", labels.size, labels.toSet().size)
        }
    }

    /** 색은 앱의 맨 바깥(`AppTheme`)에서 한 번 고른다 — 셸이 밝은 색을 다시 박으면 야간 모드가 통째로 덮인다. */
    @Test
    fun theThemeIsChosenOnceAtTheRootAndTheShellDoesNotOverrideIt() {
        val main = source("src/main/java/com/worksoc/goaicoach/MainActivity.kt")
        val app = source(RepoPaths.goCoachApp.path)
        assertTrue("`MainActivity`가 홈 · 스플래시를 `AppTheme`으로 감싸지 않는다.", main.indexOf("AppTheme {") in 0 until main.indexOf("AppSplash()"))
        assertFalse("`GoCoachApp`이 `AppLightColorScheme`을 박는다 — 야간 모드를 덮는다.", app.contains("AppLightColorScheme"))
        listOf("AppSplash.kt", "SplashVariants.kt").forEach { name ->
            assertFalse("$name 이 `AppLightColorScheme`을 직접 쓴다 — 야간 모드에서 크림색이 비친다.", source(RepoPaths.uiFile(name).path).contains("AppLightColorScheme"))
        }
    }

    /** 다크 모드 폰의 기동 바탕(`values-night`)은 어두운 색 구성의 바탕과 같은 값이어야 기동 순간 다른 색이 번쩍이지 않는다. */
    @Test
    fun theNightWindowBackgroundMatchesTheDarkScheme() {
        val night = RepoPaths.appAndroidMain("res/values-night/colors.xml").readText()
        val windowColor = Regex("""name="app_window_background">#([0-9A-Fa-f]{6})<""").find(night)?.groupValues?.get(1)
        assertTrue("values-night에 `app_window_background`가 없다.", windowColor != null)
        val scheme = source(RepoPaths.uiFile("AppColorScheme.kt").path)
        val darkBackground = Regex("""AppDarkColorScheme[\s\S]*?background = Color\(0xFF([0-9A-Fa-f]{6})\)""").find(scheme)?.groupValues?.get(1)
        assertEquals("기동 바탕(values-night)과 `AppDarkColorScheme.background`가 다르다.", darkBackground?.uppercase(), windowColor?.uppercase())
    }
}
