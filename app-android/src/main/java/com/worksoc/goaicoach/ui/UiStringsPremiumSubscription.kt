package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.premium.app.PurchaseFailureNotice
import com.worksoc.goaicoach.application.premium.app.failureNotice
import com.worksoc.goaicoach.application.premium.port.BillingPeriod
import com.worksoc.goaicoach.application.premium.port.PurchaseOutcome

/**
 * 구독 고지가 쓰는 문구(백로그 #159). 구조는 `UiStringsAppUpdate.kt`와 같다 — 화면 한 조각이
 * 쓰는 문구를 한 파일에 모아 네 언어 파일을 건드리지 않는다.
 *
 * ## ⚠️ 이 문구들은 **구글 결제 정책이 요구하는 고지**다
 * 구독 상품은 결제 지점에서 **가격 · 결제 주기 · 자동 갱신 · 해지 경로**를 알려야 한다.
 * 그래서 이 파일의 문구는 마케팅 문구가 아니라 **고지**다 — 줄이거나 부드럽게 다듬을 때
 * 네 가지 중 무엇이 사라지는지 먼저 볼 것.
 *
 * ## ⚠️ 금액은 여기 없다
 * 가격은 [premiumSubscriptionPriceLineFor]가 **Play가 준 문자열을 그대로** 끼워 넣는다
 * (`PremiumProductInfo.formattedPrice`). 지역·통화마다 다르므로 앱이 숫자를 적어 두면 거짓이 된다.
 *
 * ## ⚠️ 만료일·다음 결제일 문구가 없는 것은 **의도다**
 * Play Billing 클라이언트는 만료 시각을 주지 않는다(#174의 U-44). 날짜를 말하는 문구를
 * 여기 추가하려면 **그 값을 어디서 얻을지부터** 정해야 한다 — `purchaseTime + 한 달`로
 * 지어내지 말 것.
 */
/**
 * ⚠️ **짧아야 한다 — 같은 줄을 해지 버튼과 나눠 쓴다.**
 * 2026-09-18 실기(에뮬레이터, 글꼴 배율 1.3)에서 *"프리미엄 구독 이용 중"* 이
 * *"프리미엄 구독 이용…"* 으로 잘렸다(함정 9·21). 길이를 되돌리려면 카드 폭 계산부터 다시 할 것.
 */
private val ActiveLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "프리미엄 구독 중",
    UiLanguage.English to "Premium active",
    UiLanguage.Japanese to "プレミアム登録中",
    UiLanguage.ChineseSimplified to "高级订阅使用中",
)

/** 자동 갱신 고지의 **짧은 판**(카드 부제). 온전한 문장은 [AutoRenewNotices]가 갖는다. */
private val ActiveTaglines: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "자동 갱신",
    UiLanguage.English to "Auto-renews",
    UiLanguage.Japanese to "自動更新",
    UiLanguage.ChineseSimplified to "自动续订",
)

private val InactiveLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "프리미엄 구독",
    UiLanguage.English to "Premium plan",
    UiLanguage.Japanese to "プレミアム登録",
    UiLanguage.ChineseSimplified to "高级订阅计划",
)

/**
 * ⚠️ **파는 것을 한 줄로 말한다 — 기능을 나열하지 않는다.** 카드가 한 줄뿐이라 나열하면
 * 말줄임으로 잘리고, 잘린 목록은 있는 기능을 없는 것처럼 보이게 한다. 상세는 업셀 팝업의 몫이다.
 */
private val InactiveTaglines: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "모든 캐릭터·모든 기능",
    UiLanguage.English to "All characters & features",
    UiLanguage.Japanese to "全キャラクター・全機能",
    UiLanguage.ChineseSimplified to "解锁所有角色和全部功能",
)

/**
 * 구독 다이얼로그의 특전 목록 제목(2026-09-20 사용자 요청) — 고지(가격·주기·자동갱신·해지)
 * 앞에 붙는 **마케팅 문구**다. 위 [InactiveTaglines]의 KDoc이 "상세는 업셀 팝업의 몫"이라고
 * 적어 뒀지만, 실제로 구독을 누르는 자리(마이페이지 → [PremiumSubscribeDialog])에는 그 상세가
 * 하나도 없어 사용자가 "너무 단조롭다"고 지적했다 — 그래서 여기 새로 만든다.
 */
private val BenefitsTitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "프리미엄 구독 특전",
    UiLanguage.English to "Premium subscription benefits",
    UiLanguage.Japanese to "プレミアム登録の特典",
    UiLanguage.ChineseSimplified to "高级订阅特权",
)

/**
 * 특전 ⓐ(`FEATURE_ACCESS_PRINCIPLES.md` 8.1) — **구독 전용**이다. 광고 1시간은 이걸 안 준다
 * ([[premium-character-unlock-policy]] 메모리, 2026-09-20 재확인).
 */
private val BenefitRosterLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "모든 캐릭터 해금",
    UiLanguage.English to "Unlock every character",
    UiLanguage.Japanese to "全キャラクターを解放",
    UiLanguage.ChineseSimplified to "解锁所有角色",
)

private val BenefitFeaturesLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "프리미엄 기능 무제한 사용",
    UiLanguage.English to "Unlimited premium features",
    UiLanguage.Japanese to "プレミアム機能を使い放題",
    UiLanguage.ChineseSimplified to "无限使用高级功能",
)

/**
 * ⚠️ **[UiStrings.premiumModeFeatureList]와 같은 넷을 같은 순서로 나열한다** — 형세보기·추천수·
 * 착수평가·무르기(대국 설정 화면 프리미엄 카드 부제, 2026-09-20). 하나를 고치면 다른 쪽도 볼 것.
 * "기능:" 접두어가 없는 것은 이 문구가 [BenefitFeaturesLabels] 아래 괄호로 들어가 이미 문맥이
 * "무엇을 무제한으로 쓰는가"이기 때문이다 — 접두어를 또 붙이면 같은 말이 중복된다.
 */
private val BenefitFeatureNames: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "형세보기, 추천수, 착수 평가, 무르기",
    UiLanguage.English to "Eval, Top Moves, Move Review, Undo",
    UiLanguage.Japanese to "形勢判断、候補手、着手評価、待った",
    UiLanguage.ChineseSimplified to "形势判断、推荐手、着法评价、悔棋",
)

internal fun premiumSubscriptionBenefitsTitleFor(language: UiLanguage): String =
    BenefitsTitles.getValue(language)

internal fun premiumSubscriptionBenefitRosterFor(language: UiLanguage): String =
    BenefitRosterLabels.getValue(language)

internal fun premiumSubscriptionBenefitFeaturesFor(language: UiLanguage): String =
    BenefitFeaturesLabels.getValue(language)

internal fun premiumSubscriptionBenefitFeatureNamesFor(language: UiLanguage): String =
    BenefitFeatureNames.getValue(language)

/**
 * ⚠️ **"해지"라는 말을 빼지 말 것.** 이 버튼이 앱 안에서 해지 경로를 알리는 **유일한 자리**다
 * (구독 중에는 업셀 팝업이 뜨지 않아 고지 블록도 안 보인다). 폭이 모자라면 상태 라벨
 * ([ActiveLabels])을 줄이지, 이쪽을 줄이지 않는다.
 */
private val ManageActions: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "구독 관리·해지",
    UiLanguage.English to "Manage or cancel",
    UiLanguage.Japanese to "管理・解約",
    UiLanguage.ChineseSimplified to "管理·取消订阅",
)

private val SubscribeActions: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "구독하기",
    UiLanguage.English to "Subscribe",
    UiLanguage.Japanese to "登録する",
    UiLanguage.ChineseSimplified to "立即订阅",
)

private val AutoRenewNotices: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "해지하기 전까지 자동 갱신됩니다.",
    UiLanguage.English to "Renews automatically until cancelled.",
    UiLanguage.Japanese to "解約するまで自動的に更新されます。",
    UiLanguage.ChineseSimplified to "在您取消订阅之前将自动续订。",
)

/**
 * ⚠️ **해지 경로를 "어디에 있다"가 아니라 "무엇을 하면 된다"로 쓴다**(함정 39). 바로 아래
 * [ManageActions] 버튼이 그 자리를 실제로 열어 주므로, 문구는 그 버튼을 가리킨다.
 */
private val CancelNotices: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "해지는 Play 스토어 구독 관리에서 합니다.",
    UiLanguage.English to "Cancel any time in Play Store subscriptions.",
    UiLanguage.Japanese to "解約はPlayストアの定期購入からできます。",
    UiLanguage.ChineseSimplified to "您可随时在 Play 商店的订阅管理中取消。",
)

private val PriceLoadingLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "가격을 불러오는 중…",
    UiLanguage.English to "Loading the price…",
    UiLanguage.Japanese to "価格を読み込み中…",
    UiLanguage.ChineseSimplified to "正在加载价格…",
)

/**
 * ⚠️ **조회 실패를 "실패"라고 쓰지 않는다**(`UiStringsAppUpdate.kt`의 같은 판단). 사용자가
 * 고칠 수 없는 사정이고, 실제 금액은 Play 결제 시트가 **반드시** 보여준다 — 할 수 있는 일만 남긴다.
 */
private val PriceUnavailableLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "가격은 결제 화면에서 확인할 수 있어요.",
    UiLanguage.English to "Check the price on the payment screen.",
    UiLanguage.Japanese to "価格は決済画面でご確認いただけます。",
    UiLanguage.ChineseSimplified to "具体价格可在付款页面查看确认。",
)

internal fun premiumSubscriptionActiveLabelFor(language: UiLanguage): String =
    ActiveLabels.getValue(language)

internal fun premiumSubscriptionActiveTaglineFor(language: UiLanguage): String =
    ActiveTaglines.getValue(language)

internal fun premiumSubscriptionInactiveLabelFor(language: UiLanguage): String =
    InactiveLabels.getValue(language)

internal fun premiumSubscriptionInactiveTaglineFor(language: UiLanguage): String =
    InactiveTaglines.getValue(language)

internal fun premiumSubscriptionManageActionFor(language: UiLanguage): String =
    ManageActions.getValue(language)

internal fun premiumSubscriptionSubscribeActionFor(language: UiLanguage): String =
    SubscribeActions.getValue(language)

internal fun premiumSubscriptionAutoRenewNoticeFor(language: UiLanguage): String =
    AutoRenewNotices.getValue(language)

internal fun premiumSubscriptionCancelNoticeFor(language: UiLanguage): String =
    CancelNotices.getValue(language)

internal fun premiumSubscriptionPriceLoadingFor(language: UiLanguage): String =
    PriceLoadingLabels.getValue(language)

internal fun premiumSubscriptionPriceUnavailableFor(language: UiLanguage): String =
    PriceUnavailableLabels.getValue(language)

/**
 * *"매월 ₩3,900"* 한 줄 — **가격과 주기를 한 번에** 말한다(구글 정책은 둘을 함께 요구한다).
 *
 * ⚠️ **[BillingPeriod.Unknown]일 때 "매월"로 물러나지 않는다.** 모르는 주기를 월로 접으면
 * 거짓 고지가 된다 — 주기를 주장하지 않는 표현("정기 결제")으로 물러난다.
 *
 * ⚠️ 언어마다 **금액과 주기의 순서가 다르다**(한국어·일본어·중국어는 주기가 앞, 영어는 뒤).
 * 그래서 문자열을 이어 붙이지 않고 언어별로 완성형을 쓴다.
 */
internal fun premiumSubscriptionPriceLineFor(
    language: UiLanguage,
    formattedPrice: String,
    period: BillingPeriod,
): String = when (language) {
    UiLanguage.Korean -> when (period) {
        BillingPeriod.Weekly -> "매주 $formattedPrice"
        BillingPeriod.Monthly -> "매월 $formattedPrice"
        BillingPeriod.Quarterly -> "3개월마다 $formattedPrice"
        BillingPeriod.SemiAnnual -> "6개월마다 $formattedPrice"
        BillingPeriod.Yearly -> "매년 $formattedPrice"
        BillingPeriod.Unknown -> "정기 결제 $formattedPrice"
    }
    UiLanguage.English -> when (period) {
        BillingPeriod.Weekly -> "$formattedPrice / week"
        BillingPeriod.Monthly -> "$formattedPrice / month"
        BillingPeriod.Quarterly -> "$formattedPrice / 3 months"
        BillingPeriod.SemiAnnual -> "$formattedPrice / 6 months"
        BillingPeriod.Yearly -> "$formattedPrice / year"
        BillingPeriod.Unknown -> "$formattedPrice per billing period"
    }
    UiLanguage.Japanese -> when (period) {
        BillingPeriod.Weekly -> "毎週 $formattedPrice"
        BillingPeriod.Monthly -> "毎月 $formattedPrice"
        BillingPeriod.Quarterly -> "3か月ごと $formattedPrice"
        BillingPeriod.SemiAnnual -> "6か月ごと $formattedPrice"
        BillingPeriod.Yearly -> "毎年 $formattedPrice"
        BillingPeriod.Unknown -> "定期購入 $formattedPrice"
    }
    UiLanguage.ChineseSimplified -> when (period) {
        BillingPeriod.Weekly -> "每周 $formattedPrice"
        BillingPeriod.Monthly -> "每月 $formattedPrice"
        BillingPeriod.Quarterly -> "每3个月 $formattedPrice"
        BillingPeriod.SemiAnnual -> "每6个月 $formattedPrice"
        BillingPeriod.Yearly -> "每年 $formattedPrice"
        BillingPeriod.Unknown -> "定期 $formattedPrice"
    }
}

/**
 * ⚠️ **72시간 동안 확인에 실패해 프리미엄을 내렸을 때의 안내**(백로그 #174).
 *
 * 문구가 말해야 하는 것은 셋이다 — **무슨 일이 있었는지**(Play에 못 닿았다) · **그래서 지금
 * 어떤 상태인지**(프리미엄이 꺼졌다) · **무엇을 하면 되는지**(연결하고 다시 열기). 함정 39가
 * *"무엇이 있다 말고 무엇을 하면 된다"* 라고 한 그 형태다.
 *
 * ⚠️ **"오류"라고 쓰지 않는다** — 사용자가 잘못한 것이 아니고, 실제로 할 수 있는 일이 있다.
 * ⚠️ **"해지되었습니다"라고 쓰지 않는다** — 앱은 해지 여부를 **모른다.** 아는 것은 *"사흘 넘게
 * 확인하지 못했다"* 뿐이다. 없는 사실을 말하면 구독이 살아 있는 사용자에게 거짓이 된다.
 */
private val StaleSubscriptionTitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "구독을 확인하지 못했습니다",
    UiLanguage.English to "Subscription not verified",
    UiLanguage.Japanese to "定期購入を確認できません",
    UiLanguage.ChineseSimplified to "目前无法确认您的订阅信息",
)

private val StaleSubscriptionBodies: Map<UiLanguage, String> = mapOf(
    // ⚠️ **과거형이다** — 팝업과 강등이 같은 순간이라(사용자 확정) *"꺼집니다"* 는 거짓이 된다.
    //   나머지 세 언어도 같은 시제다(`is now off` · `オフにしました` · `已关闭`).
    UiLanguage.Korean to "구글 결제 정보를 사흘 넘게 가져오지 못해 프리미엄 기능이 꺼졌습니다. " +
        "인터넷에 연결한 뒤 앱을 다시 열어 주세요.",
    UiLanguage.English to "We couldn't reach Google Play for over three days, so premium is now off. " +
        "Connect to the internet and open the app again.",
    UiLanguage.Japanese to "3日以上Google Playに接続できなかったため、プレミアムをオフにしました。" +
        "ネットに接続してアプリを開き直してください。",
    UiLanguage.ChineseSimplified to "超过三天未能连接 Google Play，高级功能已关闭。" +
        "请连接网络后重新打开应用以恢复订阅。",
)

internal fun premiumStaleSubscriptionTitleFor(language: UiLanguage): String =
    StaleSubscriptionTitles.getValue(language)

internal fun premiumStaleSubscriptionBodyFor(language: UiLanguage): String =
    StaleSubscriptionBodies.getValue(language)

/**
 * **결제가 끝나지 않은 사유별 안내**(백로그 #178). 무엇을 언제 쓸지는
 * `PurchaseFailureNotice`가 정한다 — 여기는 문구만 갖는다.
 *
 * ⚠️ **취소에는 문구가 없다**(`Silent`) — 의도한 행동에 안내를 붙이면 소음이다.
 * 실패 문구(`premiumPurchaseFailedMessage`)는 [UiStrings]에 이미 있으므로 여기 두지 않는다.
 */
private val PurchaseUnavailableMessages: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "지금은 결제를 이용할 수 없습니다. 잠시 후 다시 시도해 주세요.",
    UiLanguage.English to "Purchases aren't available right now. Please try again later.",
    UiLanguage.Japanese to "現在お支払いをご利用いただけません。しばらくしてからお試しください。",
    UiLanguage.ChineseSimplified to "目前无法使用支付功能，请稍后再试。",
)

/**
 * ⚠️ **"실패"라고 쓰지 않는다** — 계좌이체처럼 나중에 확정되는 결제다. 실패라고 말하면
 * 사용자가 **한 번 더 결제한다.**
 */
private val PurchasePendingMessages: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "결제가 처리 중입니다. 완료되면 프리미엄이 자동으로 켜집니다.",
    UiLanguage.English to "Your payment is processing. Premium turns on once it completes.",
    UiLanguage.Japanese to "お支払いを処理中です。完了するとプレミアムが自動で有効になります。",
    UiLanguage.ChineseSimplified to "正在处理付款，完成后将自动开启高级功能。",
)

internal fun premiumPurchaseUnavailableMessageFor(language: UiLanguage): String =
    PurchaseUnavailableMessages.getValue(language)

internal fun premiumPurchasePendingMessageFor(language: UiLanguage): String =
    PurchasePendingMessages.getValue(language)

/**
 * 결제 결과를 **화면에 적을 문구**로 옮긴다 — `null`이면 **아무 말도 하지 않는다**(취소).
 *
 * ⚠️ 이 한 줄이 두 결제 지점(업셀 팝업 · 마이페이지 구독 다이얼로그)에서 **같게** 쓰여야 한다.
 * 한쪽만 고치면 같은 사유에 다른 말을 하는 앱이 된다.
 */
internal fun purchaseFailureMessageFor(
    outcome: PurchaseOutcome,
    strings: UiStrings,
): String? = when (outcome.failureNotice()) {
    null, PurchaseFailureNotice.Silent -> null
    PurchaseFailureNotice.Pending -> premiumPurchasePendingMessageFor(strings.language)
    PurchaseFailureNotice.Unavailable -> premiumPurchaseUnavailableMessageFor(strings.language)
    PurchaseFailureNotice.Failed -> strings.premiumPurchaseFailedMessage
}
