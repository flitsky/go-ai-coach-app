package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 대국 종료 상태 화면의 **결과 배지**가 지켜야 할 것(백로그 #187).
 *
 * ⚠️ **팝업을 닫으면 결과를 볼 곳이 없었다**(2026-09-22 사용자 제보) — 이 배지가 그 자리다.
 */
class FinalResultBadgeContractTest {

    private fun source(path: String): String =
        File(path).readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { it.trimStart().startsWith("import ") }
            .joinToString("\n") { it.substringBefore("//") }

    private val panel = source("src/main/java/com/worksoc/goaicoach/ui/GameStatusPanel.kt")
    private val badge = source("src/main/java/com/worksoc/goaicoach/ui/FinalResultBadge.kt")

    /**
     * ⚠️ **대국 중에는 절대 뜨면 안 된다** — 좌석 카드 가운데를 가리는데 거기에 시계와 사석이 있다.
     * 게이트가 빠져도 컴파일은 되고, 대국 내내 결과 배지가 떠 있게 된다.
     */
    @Test
    fun theBadgeOnlyAppearsOnceTheGameHasEnded() {
        assertTrue(
            "결과 배지가 `isGameEnded` 게이트 없이 그려진다 — 대국 중에 시계·사석을 가린다.",
            panel.contains("if (screenState.isGameEnded)") && panel.contains("FinalResultBadge("),
        )
    }

    /**
     * ⚠️ **좌석 카드 「사이」에 칸을 만들지 않는다.** 칸을 하나 더 두면 흑·백이 폭을 반씩 갖는
     * 배분(#143)이 깨지고, 그 폭은 1.3배에서 이미 빠듯하다 — #107이 같은 자리에서
     * `Captures: 0`을 잘라 먹었다. **배분은 그대로 두고 겹쳐** 그린다.
     */
    @Test
    fun theBadgeOverlaysTheSeatRowInsteadOfTakingWidthFromIt() {
        assertTrue(
            "배지가 `Box` 위에 겹쳐 그려지지 않는다 — 좌석 카드의 폭 배분을 훔치고 있다(#143·#107).",
            panel.contains("Modifier.align(Alignment.Center)"),
        )
        assertEquals(
            "좌석 카드 **호출**이 둘이 아니다 — 가운데에 칸이 생겼다면 폭 배분이 바뀐 것이다. " +
                "(선언 `private fun PlayerSeatCard(`은 세지 않는다 — 들여쓰기로 가른다.)",
            2,
            Regex("""^\s+PlayerSeatCard\(""", RegexOption.MULTILINE).findAll(panel).count(),
        )
    }

    /**
     * ⚠️ **기권으로 끝난 판에는 `FinalScoreJudgement`가 아예 `null`이다.** 승자를 판정에서만
     * 찾으면 기권 판에서 배지가 비거나 무승부로 보인다. `Move.Resign`은 **던진 쪽**을 들고
     * 있으므로 승자는 그 반대편이다(대국 기록이 쓰는 것과 같은 규칙).
     */
    @Test
    fun theBadgeFindsTheWinnerOfAResignedGameToo() {
        assertTrue(
            "기권 판의 승자를 마지막 수에서 찾지 않는다 — 판정이 `null`이라 배지가 빈다.",
            badge.contains("as? Move.Resign"),
        )
        assertTrue(
            "`Move.Resign`의 반대편을 승자로 삼지 않는다 — 진 쪽이 이긴 것으로 뜬다.",
            badge.contains("resignMove?.player?.opponent"),
        )
    }

    /** ⚠️ 아무도 이기지 않았는데 트로피를 달면 조용히 틀린다. */
    @Test
    fun theTrophyIsOnlyDrawnWhenSomeoneActuallyWon() {
        assertTrue(
            "무승부에도 트로피가 그려진다.",
            badge.contains("if (winner != null)"),
        )
    }

    /**
     * ⚠️ **셋째 줄이 둘째 줄과 같은 말을 하면 두 번 이긴다.** 둘째 줄이 이미 *"백 승"* 이므로
     * 괄호 안에는 **「어떻게」만** 남아야 한다. 그리고 기권이면 집수가 없어 **빈 괄호가 되면
     * 고장으로 읽힌다.**
     */
    @Test
    fun theDetailLineSaysOnlyHowAndNeverComesOutEmpty() {
        UiLanguage.entries.forEach { language ->
            val strings = UiStrings.forLanguage(language)

            val scored = strings.finalResultDetailLabel(margin = 11.5, isResign = false)
            assertTrue("$language: 집수 줄이 비었다.", !scored.isNullOrBlank())
            assertTrue("$language: 집수 줄이 괄호로 닫히지 않았다: \"$scored\"", scored!!.startsWith("(") && scored.endsWith(")"))
            assertTrue("$language: 집수 줄에 수치가 없다: \"$scored\"", scored.contains("11.5"))
            assertFalse(
                "$language: 집수 줄이 「승」을 다시 말한다 — 둘째 줄과 겹친다: \"$scored\"",
                scored.contains(strings.winnerWithoutMarginLabel("").trim()) && strings.winnerWithoutMarginLabel("").isNotBlank(),
            )

            val resigned = strings.finalResultDetailLabel(margin = null, isResign = true)
            assertTrue("$language: 기권 줄이 비었다 — 빈 괄호는 고장으로 읽힌다.", !resigned.isNullOrBlank())

            assertNull(
                "$language: 무승부인데 괄호 줄이 붙었다 — 둘째 줄이 이미 「무승부」다.",
                strings.finalResultDetailLabel(margin = null, isResign = false),
            )
        }
    }

    /**
     * ⚠️ **팝업과 같은 낱말을 써야 한다**(함정 39) — 둘이 다른 말을 하면 어느 쪽이 맞는지
     * 알 수 없다. 배지는 새 문구를 만들지 않고 `winnerWithoutMarginLabel`을 그대로 쓴다.
     */
    @Test
    fun theBadgeReusesTheExistingWinnerWording() {
        assertTrue(
            "배지가 승자 문구를 새로 만든다 — 팝업과 어긋날 수 있다(함정 39).",
            badge.contains("strings.winnerWithoutMarginLabel("),
        )
    }

    /**
     * ⚠️⚠️ **금색은 이 앱에서 이미 「프리미엄 기능이다」라는 뜻이다**(2026-09-18 결정 ⓐ안,
     * `PremiumTheme.kt`). 트로피 옆이라 **가장 고르기 쉬운 색이면서 고르면 안 되는 색**이고,
     * 두르는 순간 같은 화면에서 금색이 두 가지를 뜻한다 — 대국 메뉴의 프리미엄 옵션 셋이
     * 바로 옆에서 그 규칙을 쓰고 있다.
     *
     * ⚠️ **프라이머리(초록)도 막는다** — `ActiveStateBorder`가 **「지금 차례」**다. 종국에는
     * 아무도 차례가 아니라 색이 비어 보이지만, 같은 화면에서 같은 초록이 그 뜻으로 읽히던 자리다.
     *
     * 둘 다 *"어울려 보여서"* 바뀌기 쉬운 자리라 **코드로 고정한다**(백로그 #190).
     */
    @Test
    fun theWinnerBorderUsesNeitherThePremiumGoldNorTheYourTurnGreen() {
        assertFalse(
            "승자 테두리가 금색을 쓴다 — 금색은 「프리미엄 기능」이라는 뜻이라 같은 화면에서 " +
                "두 가지를 말하게 된다(#190).",
            badge.contains("PremiumGold"),
        )
        assertFalse(
            "승자 테두리가 프라이머리(초록)를 쓴다 — 그 색은 「지금 차례」다(`ActiveStateBorder`).",
            badge.contains("colorScheme.primary"),
        )
    }

    /**
     * ⚠️ **백은 `Color.White`가 아니라 `Color.Gray`다** — 배지 바탕이 밝은 `surfaceVariant`라
     * 흰 테두리는 **있으나 마나**가 된다. 좌석 카드(`PlayerSeatCard`)가 백을 회색으로 그리는
     * 그 규칙과 같은 값이어야 하고, **한쪽만 고치면 같은 화면에서 백이 두 색이 된다.**
     */
    @Test
    fun theWinnerBorderDrawsWhiteAsGreyJustLikeTheSeatCardDoes() {
        assertTrue(
            "배지가 백 승자를 회색으로 그리지 않는다 — 밝은 바탕에서 테두리가 사라진다(#190).",
            badge.contains("StoneColor.White -> Color.Gray"),
        )
        assertTrue(
            "좌석 카드가 백을 회색으로 그리지 않는다 — 배지와 진영색이 갈라진다.",
            panel.contains("stoneGlyphColor = Color.Gray"),
        )
    }

    /**
     * ⚠️ **무승부에는 승자색이 붙으면 안 된다** — 아무도 이기지 않았다. 덤이 모두 반집이라
     * 실제로 나올 수 없지만, **「없는 경우」를 그리면 조용히 틀린다**(트로피가 같은 이유로 빠진다).
     */
    @Test
    fun aDrawWearsNoWinnerColourAtAll() {
        assertTrue(
            "무승부(`null`)에 테두리 색이 붙는다 — 아무도 안 이겼는데 이긴 것처럼 보인다(#190).",
            badge.contains("null -> null"),
        )
    }
}
