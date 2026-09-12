package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 메뉴(☰ 대국 메뉴 · 설정 화면이 공유하는 패널)의 **차례와 프리미엄 표시**(2026-09-12 사용자 지시).
 *
 * ⚠️ **이 차례는 사용자가 직접 정한 것이다.** 위에서부터 판 자체 → 착수하는 동작 → 착수 뒤의 표시 →
 * 프리미엄 순이다. 리팩터링하다 행을 옮기거나, 새 옵션을 아무 데나 끼워 넣으면 **컴파일도 되고 다른
 * 테스트도 초록인 채** 사용자가 정한 배열만 조용히 흐트러진다. 그래서 소스 계약으로 든다.
 *
 * 바꾸려거든 **사용자에게 물을 것** — 여기 적힌 차례가 곧 그 결정이다.
 */
class MenuOptionOrderContractTest {

    private val menu: String =
        File("src/main/java/com/worksoc/goaicoach/ui/KaTrainUxPanels.kt").readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { it.trimStart().startsWith("import ") }
            .joinToString("\n") { it.substringBefore("//") }

    /**
     * 사용자가 지정한 배열:
     * ```
     * 바둑판 최대 | 좌표
     * 착수 돋보기 | 지연 착수
     * 착수 표시   | 착수 진동
     * 착수 이펙트 | 착수 평가
     * 수순 번호   | (빈칸 — '바로 착수'가 플래그 뒤에 숨어 있다)
     * 매 수마다 형세 | 매 수마다 추천
     * ```
     */
    @Test
    fun theOptionsAppearInTheOrderTheUserAskedFor() {
        val expected = listOf(
            "boardSizeToggleLabelFor(strings.language, isMaxSize = true)",
            "strings.coordinates",
            "playMagnifierLabelFor(strings.language)",
            "strings.delayedPlay",
            "strings.lastMoveRing",
            "strings.playHaptic",
            "strings.playEffect",
            "strings.moveReviewToggle",
            "strings.moveNumbers",
            "strings.directPlay",
            "strings.everyMoveEval",
            "strings.everyMoveTopMoves",
        )
        val actual = Regex("""label = (.+),""").findAll(menu)
            .map { it.groupValues[1].trim() }
            .filter { it in expected }
            .toList()
        assertEquals(
            "메뉴 옵션의 차례가 사용자가 정한 배열과 다르다(2026-09-12). 바꾸려면 사용자에게 물을 것.",
            expected,
            actual,
        )
    }

    /**
     * ⚠️ **프리미엄 전용 셋은 라벨이 금색이다**(2026-09-12 사용자 요청).
     *
     * 흐리게(`alpha`)만 두면 *"지금 못 쓴다"* 로는 읽혀도 **"프리미엄 기능이다"로는 읽히지 않는다.**
     * 그래서 색을 따로 준다 — 셋(매 수마다 형세 · 매 수마다 추천 · 착수 평가)뿐이고,
     * 보통 옵션에 금색이 번지면 프리미엄 표시가 뜻을 잃는다.
     */
    @Test
    fun onlyThePremiumOptionsWearTheGoldLabel() {
        assertEquals(
            "프리미엄 색을 쓰는 칸이 셋이 아니다 — 매 수마다 형세·매 수마다 추천·착수 평가뿐이어야 한다.",
            3,
            Regex("""labelColor = PremiumGoldDeep""").findAll(menu).count(),
        )
        // 색은 지어내지 않고 앱이 이미 쓰는 프리미엄 색을 쓴다(`PremiumTheme.kt`).
        assertTrue(
            "프리미엄 색을 `PremiumTheme`의 토큰이 아닌 값으로 적었다 — 다른 프리미엄 표시와 어긋난다.",
            menu.contains("PremiumGoldDeep"),
        )
        // 기본값은 보통 옵션의 색 그대로여야 한다 — 넘기지 않은 칸까지 금색이 되면 안 된다.
        assertTrue(
            "라벨 색의 기본값이 보통 옵션 색이 아니다 — 모든 옵션이 프리미엄처럼 보인다.",
            menu.contains("labelColor: Color? = null") &&
                menu.contains("color = labelColor ?: MaterialTheme.colorScheme.onSurface"),
        )
    }

    /**
     * ⚠️ **잠금 표시(흐림)는 스위치에만 건다 — 라벨은 또렷한 금색으로 남는다**(2026-09-12 사용자 결정 ⓐ안).
     *
     * 처음 구현은 `Modifier.weight(1f).alpha(...)`로 **칸 전체**를 흐렸는데, 그러면 금색이 배경으로 섞여
     * 실측 `(191,175,136)`이 됐다(의도는 `(138,100,22)`). 보통 옵션과 구별은 돼도 *"금색"* 이라기엔 옅고,
     * **프리미엄이라는 신호가 잠김 표시에 먹힌다.** 흐림과 금색은 서로 다른 말을 하므로 겹쳐 걸지 않는다.
     *
     * ⚠️ 되돌리기 쉬운 종류다 — 누가 "잠긴 건 흐리게" 하며 `modifier`에 `.alpha(`를 다시 붙이면
     * **컴파일도 되고 다른 테스트도 초록인 채** 금색만 조용히 죽는다.
     */
    @Test
    fun theLockedLabelStaysGoldAndOnlyTheSwitchDims() {
        assertEquals(
            "프리미엄 칸의 `modifier`에 `.alpha(`가 다시 붙었다 — 라벨까지 흐려져 금색이 배경으로 섞인다.",
            0,
            Regex("""Modifier\.weight\(1f\)\.alpha\(""").findAll(menu).count(),
        )
        assertEquals(
            "흐림을 스위치로 넘기는 칸이 셋이 아니다 — 잠긴 프리미엄 셋만 흐려져야 한다.",
            3,
            Regex("""switchAlpha = if \(""").findAll(menu).count(),
        )
        assertTrue(
            "흐림이 스위치에 걸리지 않는다 — 잠긴 옵션이 열린 것처럼 보인다.",
            menu.contains("modifier = Modifier.alpha(switchAlpha)"),
        )
        assertTrue(
            "`switchAlpha`의 기본값이 1f가 아니다 — 보통 옵션의 스위치까지 흐려진다.",
            menu.contains("switchAlpha: Float = 1f"),
        )
    }
}
