package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 앱 이름이 **세 곳에서 같은 값**인지 못박는 계약(백로그 #97).
 *
 * ⚠️ **이 그물이 막는 것은 오타가 아니라 표류다.** 이름은 서로 다른 세 체계에 산다 —
 * 안드로이드 리소스(런처), `UiStrings`(앱 안), 그리고 스토어 등록정보(사람이 손으로 쓰는 텍스트).
 * 한 곳만 고치면 나머지가 조용히 어긋나고, **어긋난 것을 알아채는 경로가 없다.**
 *
 * ⚠️ 실제로 같은 종류의 사고가 있었다 — #87에서 스토어 등록정보가 **앱이 이미 없앤 프로모션**을
 * 계속 광고하고 있었다. 코드와 등록정보를 잇는 그물이 없어서 #67의 12곳 훑기도 그 파일을 지나쳤다.
 */
class AppNameContractTest {

    private val repoRoot = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val resourceName = Regex("""<string name="app_name"[^>]*>([^<]+)</string>""")
        .find(File(repoRoot, "app-android/src/main/res/values/strings.xml").readText())
        ?.groupValues?.get(1)

    private val inAppName = Regex("""appTitle = "([^"]+)"""")
        .find(File(repoRoot, "app-android/src/main/java/com/worksoc/goaicoach/ui/UiStringsKo.kt").readText())
        ?.groupValues?.get(1)

    /**
     * 스토어 등록정보의 `[앱 이름]` 바로 다음 줄.
     *
     * ⚠️ **`dist/`가 아니라 `design-handoff/export/`를 읽는다** — `dist/`는 **gitignore 대상**이라
     * (`.gitignore:8`) 새로 클론한 저장소나 CI에는 **그 파일이 없다.** 거기를 읽으면 이 테스트가
     * 내 기계에서만 통과한다. 두 사본은 같은 내용이고, 추적되는 쪽이 정본이다.
     * · 같은 이유로 ⚠️ **`dist/` 안의 등록정보만 고치고 끝내지 말 것** — 커밋되지 않는다.
     */
    private val storeName = File(repoRoot, "design-handoff/export/2026-09-01-play-store-listing-and-screenshots/store_listing.txt")
        .readLines()
        .let { lines -> lines.getOrNull(lines.indexOfFirst { it.startsWith("[앱 이름]") } + 1)?.trim() }

    @Test
    fun theLauncherTheAppAndTheStoreAgreeOnTheName() {
        assertTrue("리소스에서 app_name을 찾지 못했다.", resourceName != null)
        assertTrue("`UiStringsKo.kt`에서 appTitle을 찾지 못했다.", inAppName != null)
        assertTrue("스토어 등록정보에서 [앱 이름]을 찾지 못했다.", !storeName.isNullOrBlank())
        assertEquals(
            "런처 라벨과 스토어 등록정보의 앱 이름이 다르다 — 설치 화면과 홈 화면이 서로 다른 이름을 " +
                "말하게 된다(#97).",
            storeName,
            resourceName,
        )
        assertEquals(
            "앱 안 타이틀과 스토어 등록정보의 앱 이름이 다르다(#97).",
            storeName,
            inAppName,
        )
    }

    /**
     * ⚠️ **'POC'가 출시 빌드의 첫 프레임에 찍혀 있었다**(2026-09-05 발견) — 엔진 준비 화면이
     * `"Go AI Coach POC"`를 하드코딩하고 있었다. 그 화면은 **모든 사용자가 보는 첫 화면**이다.
     */
    @Test
    fun noPlaceholderWordingSurvivesInUserFacingNames() {
        val mainActivity = File(repoRoot, "app-android/src/main/java/com/worksoc/goaicoach/MainActivity.kt")
            .readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.substringBefore("//") }
        assertFalse(
            "엔진 준비 화면에 'POC'가 남아 있다 — 출시 앱의 첫 프레임이다(#97).",
            mainActivity.contains("POC"),
        )
        listOf(resourceName, inAppName, storeName).forEach { name ->
            assertFalse(
                "앱 이름에 자리표시 문구가 들어 있다: $name",
                name!!.contains("POC", ignoreCase = true) || name.contains("TODO"),
            )
        }
    }

    /**
     * ⚠️ **"코치"는 아직 넣지 않는다**(2026-09-05 사용자 결정) — 코칭 기능이 실제로 없기 때문이다.
     * 기능이 나오면 그때 세 곳을 함께 바꾼다. 이 단언은 그 약속이 잊히지 않게 한다.
     */
    @Test
    fun theNameDoesNotPromiseCoachingThatDoesNotExistYet() {
        assertFalse(
            "앱 이름이 '코치'를 약속한다 — 코칭 기능이 들어간 뒤에 바꾸기로 했다(#97). " +
                "기능이 생겼다면 이 테스트를 지우고 세 곳을 함께 바꿀 것.",
            inAppName!!.contains("코치"),
        )
    }
}
