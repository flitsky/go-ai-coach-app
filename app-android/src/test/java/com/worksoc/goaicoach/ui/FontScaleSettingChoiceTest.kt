package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.preferences.AppFontScales
import com.worksoc.goaicoach.application.preferences.DefaultAppFontScale
import com.worksoc.goaicoach.application.preferences.sanitizeAppFontScale
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 글꼴 크기 **설정**의 그물(백로그 #106) — 개발자 도구에서 승격되면서
 * `UiStringsDevFontScaleTest`를 대체했다.
 *
 * ⚠️ 옛 그물은 부제가 `fun`이라(함정 10번) 리플렉션 번역 그물에 안 잡혀 **손으로** 짜야 했다.
 * 승격하면서 문구를 평범한 `val` 셋으로 바꿨으므로 **번역 누락은 이제 `UiStringsTest`가 잡는다.**
 * 여기 남은 것은 그쪽이 잡지 못하는 것들 — 선택지 수의 짝, 그리고 저장값 좁히기다.
 */
class FontScaleSettingChoiceTest {

    private val languages = UiLanguage.entries

    /**
     * ⚠️ **선택지 라벨은 배율 값이 아니라 뜻이다**("×1.3"이 아니라 "크게"). 그래서 배율을
     * 늘리면서 라벨을 안 늘리면 **새 배율이 조용히 "크게"로 그려진다** — 두 칩이 같은 이름을
     * 달고 나란히 선다. 값을 늘릴 때는 `UiStrings`에 라벨을 더하고 이 수를 함께 고칠 것.
     */
    @Test
    fun everyScaleHasALabelOfItsOwn() {
        assertEquals(
            "배율 수와 라벨 수가 어긋났다 — UiStrings에 라벨을 더하고 이 테스트를 함께 고칠 것.",
            2,
            AppFontScales.size,
        )
    }

    @Test
    fun bothChoicesReadDifferentlyInEveryLanguage() {
        languages.forEach { language ->
            val strings = UiStrings.forLanguage(language)
            assertTrue("$language 제목이 비었다", strings.settingsFontScaleTitle.isNotBlank())
            assertTrue("$language 기본 라벨이 비었다", strings.settingsFontScaleNormal.isNotBlank())
            assertTrue("$language 확대 라벨이 비었다", strings.settingsFontScaleLarge.isNotBlank())
            // 두 칩이 같은 글자를 달면 무엇이 골라졌는지 읽을 수 없다.
            assertNotEquals(
                "$language 두 선택지가 같은 글자다",
                strings.settingsFontScaleNormal,
                strings.settingsFontScaleLarge,
            )
        }
    }

    /**
     * ⚠️ **모르는 값은 기본값으로 접힌다.** 저장이 손 편집되거나 값 셋이 줄어 옛 저장분이 남을 수
     * 있다. 접지 않으면 **어느 칩도 선택 상태로 그려지지 않아**, 사용자는 아무것도 고르지 않은
     * 설정을 보게 된다(순환 버튼 시절에는 *"버튼이 안 먹는다"* 로 나타나던 같은 결함이다).
     */
    @Test
    fun anUnknownStoredScaleFallsBackRatherThanSticking() {
        assertEquals(DefaultAppFontScale, sanitizeAppFontScale(0f))
        assertEquals(DefaultAppFontScale, sanitizeAppFontScale(-1f))
        assertEquals(DefaultAppFontScale, sanitizeAppFontScale(2.0f))
        // ⚠️ **1.5는 실제로 목록에 있었다가 빠진 값이다**(#81이 넣고 2026-09-04에 뺐다).
        // 그 배율을 골라 둔 기기의 저장에는 `"1.5"`가 남아 있으므로, 이것이 **마이그레이션 없이
        // 1.0으로 접히는 경로**다.
        assertEquals(DefaultAppFontScale, sanitizeAppFontScale(1.5f))
    }

    /**
     * ⚠️ **승격의 요점은 "개발자 모드 없이 닿는다"는 것이다.** 설정 화면이 이 패널을 개발자
     * 게이트 **밖**에서 불러야 하고, 개발자 섹션에는 남아 있으면 안 된다(중복이 되고, 둘 중
     * 하나만 고쳐지는 순간 갈린다).
     */
    @Test
    fun theSettingIsReachableWithoutDeveloperMode() {
        val settings = codeOnly("src/main/java/com/worksoc/goaicoach/ui/SettingsScreen.kt")
        val developer = codeOnly("src/main/java/com/worksoc/goaicoach/ui/DeveloperTestSection.kt")

        val call = settings.indexOf("FontScaleSettingsPanel()")
        val developerGate = settings.indexOf("if (isDeveloperModeEnabled) {")

        assertTrue("설정 화면이 글꼴 크기 패널을 부르지 않는다(#106).", call >= 0)
        assertTrue("개발자 게이트를 찾지 못했다 — 이 계약의 전제가 무너졌다.", developerGate >= 0)
        assertTrue(
            "글꼴 크기 설정이 개발자 게이트 **안**에 있다 — 승격되지 않은 것과 같다(#106).",
            call < developerGate,
        )
        assertTrue(
            "개발자 섹션에 글꼴 배율 행이 남아 있다 — 같은 설정이 두 곳에 있으면 갈린다(#106).",
            "AppFontScaleState" !in developer,
        )
    }

    private fun codeOnly(path: String): String =
        File(path).readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.substringBefore("//") }
}
