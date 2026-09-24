package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.premium.port.BillingPeriod
import com.worksoc.goaicoach.application.premium.port.billingPeriodFromIso8601
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 결제 주기 파싱 계약(백로그 #159).
 *
 * `ProductDetails`는 Play SDK 타입이라 단위 테스트에서 만들 수 없어, `billingOfferToken`과 같은
 * 이유로 **고르는 규칙만 떼어 내** 여기서 고정한다.
 */
class PremiumProductInfoTest {

    @Test
    fun playsIso8601BillingPeriodsMapToTheLabelsWeCanSay() {
        assertEquals(BillingPeriod.Monthly, billingPeriodFromIso8601("P1M"))
        assertEquals(BillingPeriod.Yearly, billingPeriodFromIso8601("P1Y"))
        assertEquals(BillingPeriod.Yearly, billingPeriodFromIso8601("P12M"))
        assertEquals(BillingPeriod.Weekly, billingPeriodFromIso8601("P1W"))
        assertEquals(BillingPeriod.Quarterly, billingPeriodFromIso8601("P3M"))
        assertEquals(BillingPeriod.SemiAnnual, billingPeriodFromIso8601("P6M"))
    }

    /**
     * ⚠️ **모르는 주기를 "매월"로 접지 않는다.**
     *
     * 이것이 이 함수의 존재 이유다 — 개월 수로 환산하는 일반 파서를 쓰면 `P45D` 같은 값이
     * 조용히 월로 반올림되고, 앱은 **거짓 고지**를 하게 된다. 구독 고지에서 주기는
     * 가격만큼이나 정책이 요구하는 항목이라(`UiStringsPremiumSubscription.kt`),
     * 모르면 주기를 주장하지 않는 표현으로 물러나야 한다.
     */
    @Test
    fun anUnrecognisedPeriodStaysUnknownInsteadOfCollapsingToMonthly() {
        listOf("P45D", "P2M", "P10D", "", "말도 안 되는 값").forEach { raw ->
            assertEquals(BillingPeriod.Unknown, billingPeriodFromIso8601(raw), "raw=$raw")
        }
        assertEquals(BillingPeriod.Unknown, billingPeriodFromIso8601(null))
    }

    /** Play가 소문자·공백을 섞어 보내도 같은 답이어야 한다. */
    @Test
    fun theParserIsInsensitiveToCaseAndSurroundingSpace() {
        assertEquals(BillingPeriod.Monthly, billingPeriodFromIso8601(" p1m "))
    }
}
