package com.worksoc.goaicoach.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 구독 문구의 **표시폭 예산**(백로그 #159, 2026-09-18 사용자 지시).
 *
 * ## 왜 "언어별로 눈으로 확인"이 아니라 계약인가
 * 사용자 지시 그대로다 — *"1.3배 UX를 위해 항상 간결한 표현으로 모든 언어가 최적화되도록 해야 함.
 * 언어별로 일일이 체크할 게 아니라 **물리적으로 각 언어별 문장 길이가 유사하게** 만드는 게 핵심"*.
 *
 * 눈으로 하는 확인은 네 언어 × 두 배율 = 여덟 번을 매번 다시 해야 하고, 실제로 그렇게 해서
 * 새지 않은 적이 없다(함정 21·#29·#137). 그래서 *"어느 언어가 유독 길다"* 를 **숫자로** 막는다.
 *
 * ## 두 가지 예산
 * ⓐ **상한** — 그 칸에 들어갈 수 있는 최대 폭. 넘으면 말줄임된다.
 * ⓑ **편차** — 네 언어의 최대-최소 차. **이쪽이 사용자가 말한 "유사한 길이"다.** 상한만 보면
 *    한 언어가 절반 길이여도 통과하는데, 그러면 같은 자리가 언어마다 다른 무게로 읽힌다.
 *
 * ## ⚠️ 한 줄짜리와 여러 줄짜리는 예산이 다르다
 * 카드의 라벨·부제·버튼은 **한 줄에 갇혀** 있어 넘치면 곧바로 잘린다. 다이얼로그의 고지 문장은
 * 접히므로 절대 폭보다 분량의 균형이 문제다. 그래서 띠를 둘로 나눈다.
 *
 * ⚠️ **예산을 넘겼을 때 영어만 깎지 말 것** — [displayWidth]는 라틴 문자를 전각의 절반으로 세어
 * 영어에 불리하다. 넘쳤다면 **네 언어를 함께** 다시 쓴다.
 */
class PremiumSubscriptionCopyWidthTest {

    /** 카드 한 줄에 갇히는 조각들 — 잘림이 곧 정보 손실이다. */
    private val oneLineBudget = mapOf<String, (UiLanguage) -> String>(
        "ActiveLabels" to ::premiumSubscriptionActiveLabelFor,
        "ActiveTaglines" to ::premiumSubscriptionActiveTaglineFor,
        "InactiveLabels" to ::premiumSubscriptionInactiveLabelFor,
        "InactiveTaglines" to ::premiumSubscriptionInactiveTaglineFor,
        "ManageActions" to ::premiumSubscriptionManageActionFor,
        "SubscribeActions" to ::premiumSubscriptionSubscribeActionFor,
        "PriceLoadingLabels" to ::premiumSubscriptionPriceLoadingFor,
    )

    /** 다이얼로그에서 접히는 고지 문장들 — 상한은 느슨하고, 균형만 본다. */
    private val wrappingBudget = mapOf<String, (UiLanguage) -> String>(
        "AutoRenewNotices" to ::premiumSubscriptionAutoRenewNoticeFor,
        "CancelNotices" to ::premiumSubscriptionCancelNoticeFor,
        "PriceUnavailableLabels" to ::premiumSubscriptionPriceUnavailableFor,
    )

    /**
     * ## ⚠️ 25칸이라는 숫자는 **실기에서 잰 값이다** (2026-09-18, Pixel 8 에뮬레이터, 배율 1.3)
     *
     * 카드 한 줄은 👑(20sp)과 오른쪽 버튼이 먼저 가져가고 남는다. 네 언어를 모두 밟아 확인했다 —
     * 한국어 `모든 캐릭터·모든 기능`(21) · 일본어 `全キャラクター・全機能`(22) ·
     * 중국어 `解锁所有角色和全部功能`(22) · 영어 `All characters & features`(**25, 최대**) 전부
     * 잘림 없이 들어갔다.
     *
     * ⚠️ **다만 그 통과는 버튼 여백을 줄인 뒤에야 나왔다.** 같은 영어 문구가 기본 여백에서는
     * *"All characters & featur…"* 로 잘렸다 — `Subscribe`가 `구독하기`보다 픽셀 폭이 넓어 왼쪽
     * 문구 몫을 빼앗았기 때문이다(`PremiumSubscriptionCardFrame`의 `contentPadding`).
     * **그 여백을 되돌리면 이 상한도 함께 내려간다.**
     *
     * ⚠️ **늘리려면 실기로 다시 재고 이 주석을 고칠 것** — 계산으로 올리지 말 것.
     */
    @Test
    fun everyOneLineLabelStaysWithinTheCardsWidthBudget() {
        val cap = 25
        oneLineBudget.forEach { (name, getter) ->
            UiLanguage.entries.forEach { language ->
                val text = getter(language)
                assertTrue(
                    "$name(${language.name})이 한 줄 예산 ${cap}칸을 넘는다(${text.displayWidth()}칸): \"$text\"\n" +
                        "→ 네 언어를 **함께** 줄일 것. 영어만 깎으면 균형이 깨진다(#159).",
                    text.displayWidth() <= cap,
                )
            }
        }
    }

    /**
     * ⚠️ **이것이 사용자가 말한 "물리적으로 유사한 길이"다.** 상한만으로는 한 언어가 절반 길이여도
     * 통과하는데, 그러면 같은 자리가 언어마다 다른 무게로 읽히고 레이아웃도 언어마다 다르게 접힌다.
     */
    @Test
    fun theFourLanguagesStayWithinSixColumnsOfEachOtherOnEveryOneLineLabel() {
        assertUniform(oneLineBudget, maxSpread = 6)
    }

    /** 접히는 문장은 상한 대신 균형만 본다 — 다만 띠는 한 줄짜리보다 넓게 잡는다. */
    @Test
    fun theFourLanguagesStayWithinTenColumnsOfEachOtherOnEveryWrappingNotice() {
        assertUniform(wrappingBudget, maxSpread = 10)
    }

    /**
     * 자기검증. 폭 계산이 **실제로 전각을 2로 세고 있는지** 못 박는다 — 이 함수가 모든 문자를
     * 1로 세면 위 두 검사는 아무것도 안 보면서 통과한다(함정 24).
     */
    @Test
    fun theWidthMetreActuallyCountsFullWidthCharactersAsTwo() {
        assertTrue("한글을 2로 세지 않는다", "구독".displayWidth() == 4)
        assertTrue("한자를 2로 세지 않는다", "订阅".displayWidth() == 4)
        assertTrue("가나를 2로 세지 않는다", "登録".displayWidth() == 4)
        assertTrue("라틴을 1로 세지 않는다", "Subscribe".displayWidth() == 9)
        assertTrue("전각 가운뎃점을 2로 세지 않는다", "・".displayWidth() == 2)
        assertTrue("반각 가운뎃점을 1로 세지 않는다", "·".displayWidth() == 1)
    }

    private fun assertUniform(budget: Map<String, (UiLanguage) -> String>, maxSpread: Int) {
        budget.forEach { (name, getter) ->
            val measured = UiLanguage.entries.associateWith { getter(it).displayWidth() }
            val spread = measured.values.max() - measured.values.min()
            assertTrue(
                "$name 의 네 언어 길이가 ${spread}칸 벌어졌다(허용 ${maxSpread}칸) — " +
                    measured.entries.joinToString(" · ") { "${it.key.name}=${it.value}" } + "\n" +
                    UiLanguage.entries.joinToString("\n") { "    ${it.name}: \"${getter(it)}\"" } + "\n" +
                    "→ 긴 쪽을 줄이거나 짧은 쪽을 늘려 **비슷한 분량**으로 맞출 것(#159 사용자 지시).",
                spread <= maxSpread,
            )
        }
    }
}
