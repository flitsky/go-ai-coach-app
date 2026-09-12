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
}
