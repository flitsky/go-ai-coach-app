package com.worksoc.goaicoach.ui.monetization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.premium.app.PremiumAdGrantRunRequest
import com.worksoc.goaicoach.application.premium.app.PremiumPurchaseRunRequest
import com.worksoc.goaicoach.application.premium.app.PurchaseTrigger
import com.worksoc.goaicoach.application.premium.app.runPremiumAdGrantApplication
import com.worksoc.goaicoach.application.premium.app.runPremiumPurchaseApplication
import com.worksoc.goaicoach.application.premium.port.AdRewardFailureReason
import com.worksoc.goaicoach.application.premium.port.AdRewardOutcome
import com.worksoc.goaicoach.application.premium.port.PremiumProductInfo
import com.worksoc.goaicoach.application.premium.port.PurchaseFailureReason
import com.worksoc.goaicoach.application.premium.port.PurchaseOutcome
import com.worksoc.goaicoach.application.premium.state.PremiumState
import com.worksoc.goaicoach.application.time.currentEpochMillis
import com.worksoc.goaicoach.ios.IosPremiumProductId
import com.worksoc.goaicoach.ios.IosStoreSource
import com.worksoc.goaicoach.ios.entitlementOutcome
import com.worksoc.goaicoach.ios.premiumProductInfo
import com.worksoc.goaicoach.ios.purchaseOutcome
import com.worksoc.goaicoach.ios.restoreOutcome
import com.worksoc.goaicoach.platform.AppMonetization
import com.worksoc.goaicoach.platform.isDebugBuild
import kotlinx.coroutines.delay

@Composable
internal actual fun rememberAppMonetization(): AppMonetization = LocalIosAppMonetization.current

/** 앱 뿌리([com.worksoc.goaicoach.ios.IosAppRoot])가 프로세스에 하나 만들어 내려 준다 — 기본값은 스토어 없는 묶음(카탈로그 · 미리보기). */
internal val LocalIosAppMonetization = staticCompositionLocalOf<AppMonetization> { IosAppMonetization(store = null) }

/**
 * iOS의 결제 · 광고(백로그 #294 · #296). 구독은 [store](StoreKit 2 — Swift `AppStoreSubscriptionSource`)로 사고 복원한다 — 판정 · 진단 기록은
 * 안드로이드(`PremiumPurchaseGlue.kt`의 `resolvePremiumPurchase`)와 같은 `runPremiumPurchaseApplication`이 한다. [store]가 없으면 「쓸 수 없음」.
 * 광고 SDK는 아직이다(U-86) — 보상형 광고는 **디버그 빌드에서만** 1초 뒤 보상을 준다(안드로이드 디버그의 시험 광고 자리), 출시 빌드는 「쓸 수 없음」.
 * 캐릭터 낱개 판매는 안드로이드에서도 접었다(2026-09-18) — 여기서는 늘 「쓸 수 없음」.
 */
internal class IosAppMonetization(private val store: IosStoreSource?) : AppMonetization {
    private val unavailable = PurchaseOutcome.NotPurchased(PurchaseFailureReason.Unavailable)

    override suspend fun performPremiumPurchase(diagnosticEventLog: DiagnosticEventLogPort) =
        resolve(diagnosticEventLog, PurchaseTrigger.Explicit, PremiumState()) { it.purchaseOutcome(IosPremiumProductId) }

    override suspend fun performPremiumPurchaseRestore(diagnosticEventLog: DiagnosticEventLogPort, currentState: PremiumState) =
        resolve(diagnosticEventLog, PurchaseTrigger.Restore, currentState) { it.entitlementOutcome(IosPremiumProductId) }

    override suspend fun performPremiumPurchaseUserRestore(diagnosticEventLog: DiagnosticEventLogPort, currentState: PremiumState) =
        resolve(diagnosticEventLog, PurchaseTrigger.Restore, currentState) { it.restoreOutcome(IosPremiumProductId) }

    private suspend fun resolve(
        diagnosticEventLog: DiagnosticEventLogPort,
        trigger: PurchaseTrigger,
        currentState: PremiumState,
        call: suspend (IosStoreSource) -> PurchaseOutcome,
    ): Pair<PurchaseOutcome, PremiumState?> {
        val outcome = store?.let { call(it) } ?: unavailable
        val result = runPremiumPurchaseApplication(
            PremiumPurchaseRunRequest(outcome = outcome, trigger = trigger, nowMillis = currentEpochMillis(), currentState = currentState),
        )
        diagnosticEventLog.append(result.diagnosticEvent)
        return outcome to result.nextState
    }

    override suspend fun performPremiumAdGrant(diagnosticEventLog: DiagnosticEventLogPort, currentState: PremiumState): Pair<AdRewardOutcome, PremiumState?> {
        val outcome = showRewardedAdOnce()
        val result = runPremiumAdGrantApplication(
            PremiumAdGrantRunRequest(outcome = outcome, nowMillis = currentEpochMillis(), currentState = currentState),
        )
        diagnosticEventLog.append(result.diagnosticEvent)
        return outcome to result.nextState
    }

    override suspend fun queryPremiumProductInfo(): PremiumProductInfo? = store?.premiumProductInfo(IosPremiumProductId)

    override suspend fun performBotCharacterPurchase(): PurchaseOutcome = unavailable

    override suspend fun performBotCharacterPurchaseRestore(): PurchaseOutcome = unavailable

    override suspend fun showRewardedAdOnce(): AdRewardOutcome {
        if (!isDebugBuild) return AdRewardOutcome.NotRewarded(AdRewardFailureReason.Unavailable)
        delay(1_000)
        return AdRewardOutcome.RewardEarned()
    }

    override fun openSubscriptionManagement() {
        store?.showManageSubscriptions()
    }

    override fun isPrivacyOptionsRequired(): Boolean = false

    override suspend fun showPrivacyOptionsForm() {}

    override fun canRequestAds(): Boolean = false

    override fun resetAdConsentForDebug() {}
}
