package com.worksoc.goaicoach

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `MainActivity.onCreate`는 **창을 먼저 세우고 SDK를 나중에 깨운다**(백로그 #123).
 *
 * ## 무엇을 막는가 — 실측으로 값을 치른 계약이다
 * `AdsConsentManager.refresh`가 `setContent`보다 **앞에** 있던 동안, 앱은 기동 10회 중 1회꼴로
 * `MainActivity.onCreate`에서 죽었다. 얼굴은 둘이지만
 * (`RuntimeException: Window couldn't find content container view` / `setContent` 안의
 * `ViewGroup.getChildAt` NPE) **retrace하면 둘 다 같은 한 줄**이다 —
 * `androidx.activity.compose.ComponentActivityKt.setContent(ComponentActivity.kt:55)`,
 * 즉 `window.decorView.findViewById(android.R.id.content)`가 **null**을 돌려준 것이다.
 *
 * 원인은 순서다. UMP(`requestConsentInfoUpdate`)는 **자기 백그라운드 스레드**에서 WebView
 * 프로바이더를 로드하고(`WebViewFactory.getProvider`), 그 과정이 앱 프로세스의
 * `Resources`/`AssetManager`를 통째로 갈아끼운다. 그것을 `setContent` **앞에서** 시작하면
 * 그 스레드가 **decor를 인플레이트하는 바로 그 순간**의 메인 스레드와 겹친다.
 *
 * ## 실측(2026-09-06, Pixel 7 / API 35 에뮬레이터, `playInternal` = R8·비디버거블)
 * | 배치 | 크래시 |
 * | --- | --- |
 * | `refresh`가 `setContent` **앞** | 4 / 40 |
 * | `refresh`가 `setContent` **뒤** | **0 / 120** |
 *
 * ⚠️ **이 계약이 없으면 되돌리기가 너무 쉽다.** 한 줄을 위로 올리는 것은 리뷰에서
 * *"의미 없는 이동"* 으로 보이고, **컴파일도 되고 테스트도 전부 초록이며**, 크래시는 10%라
 * 손으로 몇 번 켜 보는 것으로는 드러나지 않는다.
 *
 * ⚠️ **이 계약은 "광고 SDK"가 아니라 "순서"를 지킨다.** 나중에 다른 SDK를 `onCreate`에
 * 붙일 때도 같은 규칙이다 — **`setContent` 뒤**에 붙일 것.
 */
class StartupOrderContractTest {

    private val onCreateBody: String = run {
        val source = File("src/main/java/com/worksoc/goaicoach/MainActivity.kt").readText()
        // 주석은 지운다 — 이 계약은 서술이 아니라 **호출 순서**를 잰다.
        source
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }
    }

    @Test
    fun setContentComesBeforeAnyConsentSdkCall() {
        val setContent = onCreateBody.indexOf("setContent {")
        val consent = onCreateBody.indexOf("AdsConsentManager.refresh")
        assertTrue(
            "MainActivity에서 `setContent {`를 찾지 못했다 — 이 계약의 전제가 무너졌다.",
            setContent >= 0,
        )
        assertTrue(
            "MainActivity에서 `AdsConsentManager.refresh` 호출을 찾지 못했다. 지운 것이라면 이 " +
                "계약도 함께 다시 볼 것 — UMP는 기동마다 한 번 조회하는 것이 규격이다(백로그 #89).",
            consent >= 0,
        )
        assertTrue(
            "동의 조회가 `setContent`보다 **앞**에 있다 — UMP가 백그라운드에서 WebView " +
                "프로바이더를 로드하며 앱의 Resources/AssetManager를 갈아끼우는데, 그 순간 메인 " +
                "스레드는 창의 decor를 인플레이트하고 있다. 기동 10회 중 1회꼴로 " +
                "`Window couldn't find content container view`로 죽는다(백로그 #123).",
            consent > setContent,
        )
    }

    /**
     * ⚠️ **`onCreate` 안에서 `super.onCreate` 다음 첫 문장이 `setContent`여야 한다.**
     * 위 테스트는 *"동의 조회보다 앞"* 만 재므로, 다른 SDK를 그 사이에 끼워 넣으면 통과한다.
     */
    @Test
    fun nothingRunsBetweenSuperOnCreateAndSetContent() {
        val superCall = onCreateBody.indexOf("super.onCreate(savedInstanceState)")
        val setContent = onCreateBody.indexOf("setContent {")
        assertTrue("`super.onCreate`를 찾지 못했다.", superCall >= 0)
        assertTrue("`setContent {`를 찾지 못했다.", setContent > superCall)
        val between = onCreateBody.substring(superCall + "super.onCreate(savedInstanceState)".length, setContent)
        assertTrue(
            "`super.onCreate`와 `setContent` 사이에 무언가가 들어왔다: ${between.trim()}\n" +
                "창이 세워지기 전에 도는 코드는 decor 인플레이트와 겹친다(백로그 #123). " +
                "정말 그 자리여야 한다면 이 계약을 고치기 전에 #123의 실측 표를 다시 읽을 것.",
            between.isBlank(),
        )
    }
}
