package com.worksoc.goaicoach.application.premium

/**
 * Play가 말해 주는 **구독 상품의 고지 재료**(백로그 #159).
 *
 * ⚠️ **가격을 문구에 박지 않기 위해 존재한다.** `UiStrings`가 *"Play가 지역/통화별로 다른 값을
 * 보여주는데 앱이 하나를 적어 두면 어긋난다"* 며 금액을 문구에 쓰는 것을 금지해 뒀다(#18).
 * 그래서 구글 정책이 요구하는 **가격·결제 주기** 고지는 런타임 조회로만 채울 수 있다.
 *
 * ⚠️ **만료일은 여기 없다.** Play Billing 클라이언트의 `Purchase`가 주는 것은
 * `purchaseTime`·`purchaseToken`·`isAutoRenewing`·`purchaseState`뿐이고 `expiryTimeMillis`는
 * **Play Developer API(서버)에만** 있다 — 그 사실이 #158이 자체 만료 시계를 접은 직접 이유이고,
 * 다시 여는 것이 #174의 U-44다. 이 타입에 만료일을 **지어내 넣지 말 것.**
 *
 * @param formattedPrice Play가 그 사용자의 지역·통화로 이미 포맷한 금액 문자열(`"₩3,900"` 등).
 *   앱이 숫자를 다시 만지지 않는다 — 통화 기호 위치와 자릿수 구분은 나라마다 다르다.
 * @param period 결제 주기. Play는 ISO-8601 기간 문자열로 주므로 [billingPeriodFromIso8601]이 옮긴다.
 */
data class PremiumProductInfo(
    val formattedPrice: String,
    val period: BillingPeriod,
)

/**
 * 구독 결제 주기 — 문구가 *"매월"* 이라고 말할 수 있으려면 **Play가 답한 것**이어야 한다.
 *
 * ⚠️ **"어차피 월 구독이니 매월로 박자"를 하지 않는 이유**: 콘솔에서 기본 요금제의 주기를 바꾸거나
 * 연간 요금제를 더하는 날, 앱만 *"매월"* 이라고 말하는 **거짓 고지**가 남는다. 그것이 정확히
 * 구글 정책이 막으려는 것이다.
 */
enum class BillingPeriod {
    Weekly,
    Monthly,
    Quarterly,
    SemiAnnual,
    Yearly,

    /**
     * 우리가 옮길 줄 모르는 주기. ⚠️ **"매월"로 접지 말 것** — 모르면 모른다고 말하고,
     * 문구는 주기를 주장하지 않는 쪽("정기 결제")으로 물러난다.
     */
    Unknown,
}

/**
 * Play의 `ProductDetails.PricingPhase.billingPeriod`(ISO-8601 기간)를 [BillingPeriod]로 옮긴다.
 *
 * ⚠️ **일반 ISO-8601 파서를 쓰지 않는다.** Play 콘솔이 만들 수 있는 주기는 정해진 몇 가지뿐이라
 * 표 하나가 더 정확하고, 무엇보다 **모르는 값을 [BillingPeriod.Unknown]으로 남기는 것이
 * 이 함수의 요점**이다 — 억지로 개월 수로 환산하면 `P45D` 같은 값이 조용히 "매월"이 된다.
 */
fun billingPeriodFromIso8601(raw: String?): BillingPeriod =
    when (raw?.trim()?.uppercase()) {
        "P1W", "P7D" -> BillingPeriod.Weekly
        "P1M" -> BillingPeriod.Monthly
        "P3M" -> BillingPeriod.Quarterly
        "P6M" -> BillingPeriod.SemiAnnual
        "P1Y", "P12M" -> BillingPeriod.Yearly
        else -> BillingPeriod.Unknown
    }
