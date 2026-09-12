package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #147 — 회전 정책. **폰은 세로 고정, 큰 화면은 자유**(2026-09-12 사용자 확정).
 *
 * ⚠️ 이 표가 곧 그 결정이다. 기준 상수를 건드리면 어느 기기가 옮겨 가는지 여기서 먼저 보인다.
 */
class RotationPolicyTest {

    @Test
    fun phonesStayLockedAndBigScreensAreFree() {
        assertFalse("Pixel 7급(sw411)", allowsRotation(411))
        assertFalse("작은 폰(sw360)", allowsRotation(360))
        assertFalse("폴드 커버(sw344)", allowsRotation(344))
        assertTrue("폴드 안쪽(sw690)", allowsRotation(690))
        assertTrue("태블릿(sw800)", allowsRotation(800))
    }

    /** 경계는 배치가 갈리는 폭과 **같은 값**이어야 한다 — 어긋나면 "가로인데 폰 배치"가 생긴다. */
    @Test
    fun theBoundaryIsTheSameAsTheWideLayoutBoundary() {
        assertFalse(allowsRotation(WideLayoutMinWidthDp.toInt() - 1))
        assertTrue(allowsRotation(WideLayoutMinWidthDp.toInt()))
    }

    /**
     * ⚠️ **접었다 펴면 폭이 바뀐다** — 액티비티는 `configChanges` 덕분에 살아 있으므로, 정책을 `onCreate`에서만
     * 걸면 **접은 뒤에도 가로로 남는다.** 그래서 구성이 바뀔 때도 다시 건다. 방향과 무관한
     * `smallestScreenWidthDp`를 쓰는 것도 함께 못박는다(`screenWidthDp`를 쓰면 돌릴 때마다 기준이 뒤집힌다).
     */
    @Test
    fun theActivityAppliesThePolicyOnCreateAndOnEveryConfigurationChange() {
        val activity = File("src/main/java/com/worksoc/goaicoach/MainActivity.kt").readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { it.trimStart().startsWith("import ") }
            .joinToString("\n") { it.substringBefore("//") }

        assertTrue(
            "`onCreate`에서 회전 정책을 걸지 않는다(#147).",
            activity.contains("applyRotationPolicy(resources.configuration)"),
        )
        assertTrue(
            "구성이 바뀔 때 정책을 다시 걸지 않는다 — 접은 뒤에도 가로로 남는다(#147).",
            activity.contains("override fun onConfigurationChanged") && activity.contains("applyRotationPolicy(newConfig)"),
        )
        assertTrue(
            "방향과 무관한 `smallestScreenWidthDp`를 보지 않는다 — 돌릴 때마다 기준이 뒤집힌다(#147).",
            activity.contains("allowsRotation(configuration.smallestScreenWidthDp)"),
        )
        assertEquals(
            "세로 고정으로 되돌리는 갈래가 없다 — 폰에서 가로가 열린다(#147).",
            1,
            Regex("SCREEN_ORIENTATION_PORTRAIT").findAll(activity).count(),
        )
    }
}
