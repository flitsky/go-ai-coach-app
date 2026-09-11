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

    /**
     * ⚠️ **2026-09-11에 "같다"에서 "시작한다"로 바꿨다 — 되돌리지 말 것.**
     *
     * 스토어 제목은 이제 **브랜드 + 부제**(`포켓 바둑 코치 - 오프라인 AI 대국·복기`)이고
     * 런처·앱 안은 **브랜드만**(`포켓 바둑 코치`)이다. 부제는 검색에서 일하고 런처에는 들어갈
     * 자리가 없으므로 **일부러 다르다**(사용자 결정).
     *
     * ⚠️ **그래도 그물의 뜻은 그대로다** — 이 계약이 막는 것은 오타가 아니라 **브랜드 표류**다.
     * `startsWith`는 *"스토어가 파는 이름과 기기에 찍히는 이름이 같은 브랜드인가"* 를 계속 묻는다.
     * 한쪽만 고치면 여전히 빨개진다.
     *
     * ⚠️ **`assertEquals`로 되돌리지 말 것** — 되돌리는 순간 런처 라벨에 부제까지 들어가거나
     * 스토어 제목에서 검색어가 통째로 빠진다. 둘 다 이번에 일부러 피한 것이다.
     */
    @Test
    fun theLauncherTheAppAndTheStoreShareOneBrand() {
        assertTrue("리소스에서 app_name을 찾지 못했다.", resourceName != null)
        assertTrue("`UiStringsKo.kt`에서 appTitle을 찾지 못했다.", inAppName != null)
        assertTrue("스토어 등록정보에서 [앱 이름]을 찾지 못했다.", !storeName.isNullOrBlank())

        assertEquals(
            "런처 라벨과 앱 안 타이틀이 다르다 — 홈 화면과 앱 안이 서로 다른 이름을 말하게 된다(#97).",
            resourceName,
            inAppName,
        )
        assertTrue(
            "스토어 제목($storeName)이 런처 이름($resourceName)으로 시작하지 않는다 — 설치 화면에서 " +
                "본 이름과 홈 화면에 찍히는 이름이 다른 브랜드가 된다(#97). 부제는 붙여도 되지만 " +
                "**앞부분은 런처 이름 그대로**여야 한다.",
            storeName!!.startsWith(resourceName!!),
        )
    }

    /**
     * 스토어 제목은 **30자**를 넘을 수 없다(Play 제한). 넘으면 콘솔이 저장 자체를 거부한다.
     *
     * ⚠️ 런처 이름은 이 제한과 별개로 **짧아야 한다** — 홈 화면 아이콘 아래는 두 줄에서 잘리고,
     * 잘린 이름은 브랜드가 되지 못한다.
     */
    @Test
    fun theStoreTitleFitsAndTheLauncherNameStaysShort() {
        assertTrue(
            "스토어 제목이 ${storeName!!.length}자다 — Play 제목 한도는 30자다.",
            storeName.length <= 30,
        )
        assertTrue(
            "런처 이름이 ${resourceName!!.length}자다 — 홈 화면에서 잘린다. 부제는 스토어 제목에만 둘 것.",
            resourceName.length <= 12,
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

}
