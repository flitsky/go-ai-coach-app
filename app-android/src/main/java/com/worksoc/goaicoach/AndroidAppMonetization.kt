package com.worksoc.goaicoach

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.premium.state.PremiumState
import com.worksoc.goaicoach.platform.AdsConsentManager
import com.worksoc.goaicoach.platform.AppMonetization

/**
 * 안드로이드의 [AppMonetization](백로그 #294 — S12) — 옛 전역 함수(`PremiumPurchaseGlue.kt`)에 화면의 문맥을 그대로 넘긴다.
 * 액티비티가 아니면 결제 · 광고가 「쓸 수 없음」인 규칙도 그 함수들 안에 그대로 있다.
 */
internal class AndroidAppMonetization(private val context: Context) : AppMonetization {
    override suspend fun performPremiumPurchase(diagnosticEventLog: DiagnosticEventLogPort) =
        performPremiumPurchase(context, diagnosticEventLog)

    override suspend fun performPremiumPurchaseRestore(diagnosticEventLog: DiagnosticEventLogPort, currentState: PremiumState) =
        performPremiumPurchaseRestore(context, diagnosticEventLog, currentState)

    override suspend fun performPremiumPurchaseUserRestore(diagnosticEventLog: DiagnosticEventLogPort, currentState: PremiumState) =
        performPremiumPurchaseRestore(context, diagnosticEventLog, currentState)

    override suspend fun performPremiumAdGrant(diagnosticEventLog: DiagnosticEventLogPort, currentState: PremiumState) =
        performPremiumAdGrant(context, diagnosticEventLog, currentState)

    override suspend fun queryPremiumProductInfo() = queryPremiumProductInfo(context)

    override suspend fun performBotCharacterPurchase() = performBotCharacterPurchase(context)

    override suspend fun performBotCharacterPurchaseRestore() = performBotCharacterPurchaseRestore(context)

    override suspend fun showRewardedAdOnce() = showRewardedAdOnce(context)

    override fun openSubscriptionManagement() = openSubscriptionManagement(context)

    override fun isPrivacyOptionsRequired(): Boolean = AdsConsentManager.isPrivacyOptionsRequired(context)

    override suspend fun showPrivacyOptionsForm() {
        val activity = context as? Activity ?: return
        AdsConsentManager.showPrivacyOptionsForm(activity)
    }

    override fun canRequestAds(): Boolean = AdsConsentManager.canRequestAds(context)

    override fun resetAdConsentForDebug() = AdsConsentManager.resetForDebug(context)
}

/**
 * 이 앱의 **구독 관리 화면**을 연다 — 해지가 실제로 일어나는 자리다.
 *
 * ⚠️ **두 겹으로 시도한다**(`openStoreListing`과 같은 형태). 첫 주소는 이 상품을 바로 펴고,
 * 실패하면 구독 목록으로 떨어진다. 둘 다 실패하는 기기(브라우저조차 없음)에서는
 * **조용히 아무 일도 없다** — 그래도 고지 문구가 경로를 말해 두었다.
 *
 * ⚠️ 패키지 이름은 `BuildConfig`가 아니라 `Context.getPackageName`에서 읽는다 — 이 앱은
 * `applicationId`와 `namespace`가 다르고, Play가 아는 것은 전자다.
 */
internal fun openSubscriptionManagement(context: Context) {
    val packageName = context.packageName
    val sku = BuildConfig.PREMIUM_PRODUCT_ID
    val attempts = listOf(
        "https://play.google.com/store/account/subscriptions?sku=$sku&package=$packageName",
        "https://play.google.com/store/account/subscriptions",
    )
    attempts.firstOrNull { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess
    }
}
