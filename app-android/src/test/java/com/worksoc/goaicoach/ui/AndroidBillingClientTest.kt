package com.worksoc.goaicoach.ui

import com.android.billingclient.api.BillingClient
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #26 착수 순서 — 프리미엄을 구독(SUBS)으로 바꾸기 전에 반드시 서 있어야 하는 두 그물.
 *
 * ⚠️ **콘솔이 막혀 실기 검증이 불가능하다**(수익 창출 잠금). 구독 SKU를 만들 수 없으니 구매
 * 플로우를 한 번도 밟아 볼 수 없고, 그래서 **잘못될 수 있는 지점을 순수 규칙으로 떼어 내
 * 테스트로 고정하는 것**이 지금 할 수 있는 최선이다.
 */
class AndroidBillingClientTest {

    /**
     * ⚠️ **구독에서 `oneTimePurchaseOfferDetails`는 언제나 `null`이다.** 종류만 SUBS로 바꾸고
     * 이 자리를 그대로 두면 구독이 **"상품 없음"으로 조용히 실패한다** — 오류 메시지가
     * "지금은 구매할 수 없어요"라 원인이 드러나지도 않는다.
     */
    @Test
    fun aSubscriptionTakesItsTokenFromTheSubscriptionOffersNotTheOneTimeOne() {
        assertEquals(
            "sub-offer",
            billingOfferToken(
                productType = BillingClient.ProductType.SUBS,
                oneTimeOfferToken = null,
                subscriptionOfferTokens = listOf("sub-offer", "another"),
            ),
        )
        // 단발 구매(#18 캐릭터)는 반대쪽에서 가져온다 — 이 둘이 섞이면 한쪽이 조용히 깨진다.
        assertEquals(
            "one-time-offer",
            billingOfferToken(
                productType = BillingClient.ProductType.INAPP,
                oneTimeOfferToken = "one-time-offer",
                subscriptionOfferTokens = listOf("sub-offer"),
            ),
        )
    }

    @Test
    fun aMissingOfferYieldsNullSoTheCallerCanReportProductUnavailable() {
        assertNull(
            billingOfferToken(
                productType = BillingClient.ProductType.SUBS,
                oneTimeOfferToken = "one-time-offer",
                subscriptionOfferTokens = emptyList(),
            ),
        )
        assertNull(
            billingOfferToken(
                productType = BillingClient.ProductType.INAPP,
                oneTimeOfferToken = null,
                subscriptionOfferTokens = listOf("sub-offer"),
            ),
        )
    }

    /**
     * ⚠️ **이 그물이 #26의 숨은 함정을 막는다.** 어댑터를 프리미엄(SUBS 예정)과 봇 캐릭터
     * (INAPP, #18)가 **공유한다** — 클래스 안에서 종류를 상수로 치환하면 캐릭터 구매가 조용히
     * 깨지고, `isBotCharacterPurchaseEnabled`가 꺼져 있어 어떤 실행 테스트에도 걸리지 않는다.
     * 그래서 **소스에 `ProductType`이 박혀 있지 않은지** 직접 확인한다.
     */
    @Test
    fun theProductTypeIsAConstructorParameterNotAHardcodedConstant() {
        val source = File("src/main/java/com/worksoc/goaicoach/ui/AndroidBillingClient.kt").readText()
        assertTrue(
            "생성자에 productType이 없다 — 종류가 다시 클래스 안으로 박혔다",
            source.contains("private val productType: String"),
        )
        // 유일하게 허용되는 상수 언급은 생성자 기본값과 위 순수 함수의 비교뿐이다.
        val hardcoded = Regex("""\.setProductType\(BillingClient\.ProductType\.\w+\)""").findAll(source).toList()
        assertTrue(
            "질의가 ProductType 상수를 직접 박아 쓴다: ${hardcoded.map { it.value }}",
            hardcoded.isEmpty(),
        )
    }

    /**
     * 프리미엄은 `SUBS`, 봇 캐릭터는 `INAPP` — **호출부가 직접 말해야 한다**(#26 ⓕ).
     *
     * ⚠️ **이 그물이 없어서 구독은 조회조차 되지 않았다.** 어댑터의 `productType` 기본값이
     * `INAPP`이었고 `SUBS`를 넘기는 호출부가 저장소에 하나도 없었는데, 플래그가 꺼져 있어
     * **실행 테스트로는 영원히 드러나지 않는다.** 그래서 소스를 읽는다.
     *
     * ⚠️ **기본값을 되살리지 말 것** — 그 순간 다음 호출부가 다시 조용히 틀린 종류를 쓴다.
     */
    @Test
    fun everyBillingCallSiteNamesTheProductTypeItSells() {
        val glue = File("src/main/java/com/worksoc/goaicoach/ui/PremiumPurchaseGlue.kt").readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { line -> line.substringBefore("//") }

        assertTrue(
            "프리미엄이 SUBS를 선언하지 않는다 — 구독 복원이 오류가 아니라 조용한 미소유로 끝난다(#26 ⓕ).",
            glue.contains("BillingClient.ProductType.SUBS"),
        )
        assertTrue(
            "봇 캐릭터가 INAPP를 선언하지 않는다 — 단발 구매가 구독으로 조회되면 조용히 깨진다(#18).",
            glue.contains("BillingClient.ProductType.INAPP"),
        )
        Regex("""AndroidBillingClient\(([^)]*)\)""").findAll(glue).forEach { match ->
            assertTrue(
                "`AndroidBillingClient(${match.groupValues[1]})`가 상품 종류를 넘기지 않는다 — " +
                    "기본값에 기대는 순간 무엇을 파는지 코드가 말하지 않게 된다.",
                match.groupValues[1].contains("ProductType"),
            )
        }

        val adapter = File("src/main/java/com/worksoc/goaicoach/ui/AndroidBillingClient.kt").readText()
        assertTrue(
            "`productType`에 기본값이 되살아났다 — 호출부가 다시 조용히 빠뜨릴 수 있다(#26 ⓕ).",
            !adapter.contains("private val productType: String ="),
        )
    }
}
