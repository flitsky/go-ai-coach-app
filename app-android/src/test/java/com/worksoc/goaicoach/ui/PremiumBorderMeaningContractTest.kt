package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **금색은 "프리미엄 기능이다"라는 뜻이고, "지금 못 쓴다"가 아니다** (2026-09-18 사용자 결정).
 *
 * ## ⚠️ 무엇이 틀어졌었나
 * 대국 **메뉴**는 #149에서 사용자가 직접 ⓐ안으로 정했다 — 프리미엄 옵션은 **라벨이 항상 금색**,
 * 못 쓸 때는 **스위치만 흐림**. 그런데 인게임 **버튼**은 반대로 말했다: 잠겼을 때만 금색이고
 * 권한이 생기면 금색이 사라졌다. 그래서 **구독을 사는 순간 버튼이 더 평범해졌다.**
 *
 * ⚠️ 되돌리기 쉬운 종류다 — 누가 정리하다 삼항 연산자 하나를 옛 모양으로 되돌려도
 * **컴파일도 되고 다른 테스트도 초록인 채** 그 버튼만 다시 반대말을 한다.
 */
class PremiumBorderMeaningContractTest {

    /**
     * ⚠️ **주석을 걷어내고 본다.** 이 파일의 KDoc과 호출부 주석이 `PremiumLockedBorder`·
     * `premiumFeature` 같은 낱말을 그대로 적고 있어서, 날것으로 세면 **설명이 코드로 오인된다**
     * (`MenuOptionOrderContractTest`가 같은 이유로 같은 손질을 한다).
     */
    private fun source(path: String): String =
        File(path).readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    private val buttons = source("src/main/java/com/worksoc/goaicoach/ui/GameActionButtons.kt")
    private val play = source("src/main/java/com/worksoc/goaicoach/ui/GamePlaySection.kt")
    private val theme = source("src/main/java/com/worksoc/goaicoach/ui/PremiumTheme.kt")

    /** 규칙은 **한 곳**에 있어야 한다 — 흩어 두면 한 버튼만 옛 규칙으로 돌아가도 안 걸린다. */
    @Test
    fun theBorderRuleLivesInExactlyOnePlace() {
        assertTrue(
            "테두리 규칙 함수가 없다 — 호출부마다 삼항 연산자로 흩어졌다(2026-09-18).",
            buttons.contains("private fun premiumBorderOr("),
        )
        assertEquals(
            "`PremiumLockedBorder`를 규칙 함수 밖에서도 쓴다 — 규칙이 두 벌이 됐다.",
            1,
            Regex("""PremiumLockedBorder""").findAll(buttons).count(),
        )
        assertEquals(
            "`PremiumUnlockedBorder`도 규칙 함수 안에서만 쓰여야 한다.",
            1,
            Regex("""PremiumUnlockedBorder""").findAll(buttons).count(),
        )
        assertFalse(
            "옛 규칙(`잠겼을 때만 금색`)이 되살아났다 — 권한이 생기면 금색이 사라진다.",
            buttons.contains("if (premiumLocked) PremiumLockedBorder else"),
        )
    }

    /** 잠김과 열림이 **같은 금색**이되 굵기로 갈린다 — 색이 축, 굵기가 상태다. */
    @Test
    fun goldMarksTheAxisAndThicknessMarksTheState() {
        assertTrue(
            "열린 프리미엄 기능에 줄 금색 테두리가 없다 — 구독을 사면 금색이 사라진다.",
            theme.contains("internal val PremiumUnlockedBorder = BorderStroke(1.dp, PremiumGold"),
        )
        assertTrue(
            "잠김 테두리가 2dp가 아니다 — 열림(1dp)과 굵기로 갈리지 않으면 상태를 못 읽는다.",
            theme.contains("internal val PremiumLockedBorder = BorderStroke(2.dp, PremiumGold"),
        )
        assertTrue(
            "규칙이 잠김 → 열림 → 기본 차례로 갈리지 않는다.",
            buttons.contains("premiumLocked -> PremiumLockedBorder") &&
                buttons.contains("premiumFeature -> PremiumUnlockedBorder"),
        )
    }

    /**
     * ⚠️ 형세·추천만 프리미엄 축이다. **무르기는 일부러 뺐다** — 출석 3일차로 영구 해금되는
     * 무료 경로가 있어, 열린 뒤에도 금색을 두면 "돈을 내야 하는 것"으로 과장된다.
     * 대국 메뉴가 금색을 셋에만 준 것과 같은 선이다(#149).
     */
    @Test
    fun onlyTheTwoInGamePremiumButtonsDeclareTheAxis() {
        assertEquals(
            "`premiumFeature = true`를 선언한 버튼이 둘이 아니다 — 형세 보기·추천 수뿐이어야 한다.",
            2,
            Regex("""premiumFeature = true,""").findAll(play).count(),
        )
        assertFalse(
            "무르기가 프리미엄 축을 선언했다 — 출석으로 공짜로 열리는 기능인데 유료처럼 보인다.",
            play.substringAfter("premiumLocked = undoAccess is FeatureAccess.Locked,")
                .substringBefore(")")
                .contains("premiumFeature"),
        )
    }
}
