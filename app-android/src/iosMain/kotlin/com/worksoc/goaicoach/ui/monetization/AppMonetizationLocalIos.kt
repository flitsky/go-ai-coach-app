package com.worksoc.goaicoach.ui.monetization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.premium.app.PremiumAdGrantRunRequest
import com.worksoc.goaicoach.application.premium.app.runPremiumAdGrantApplication
import com.worksoc.goaicoach.application.premium.port.AdRewardFailureReason
import com.worksoc.goaicoach.application.premium.port.AdRewardOutcome
import com.worksoc.goaicoach.application.premium.port.PremiumProductInfo
import com.worksoc.goaicoach.application.premium.port.PurchaseFailureReason
import com.worksoc.goaicoach.application.premium.port.PurchaseOutcome
import com.worksoc.goaicoach.application.premium.state.PremiumState
import com.worksoc.goaicoach.application.time.currentEpochMillis
import com.worksoc.goaicoach.platform.AppMonetization
import com.worksoc.goaicoach.platform.isDebugBuild
import kotlinx.coroutines.delay

@Composable
internal actual fun rememberAppMonetization(): AppMonetization = remember { IosAppMonetization }

/**
 * iOS의 결제 · 광고(백로그 #294) — StoreKit · 광고 SDK가 붙기 전이다(App Store Connect 등록 · 계정이 필요한 사용자 몫).
 * 결제는 늘 「쓸 수 없음」. 보상형 광고는 **디버그 빌드에서만** 1초 뒤 보상을 준다(안드로이드 디버그의 시험 광고 자리) —
 * 출시 빌드는 「쓸 수 없음」이다.
 */
internal object IosAppMonetization : AppMonetization {
    private val unavailable = PurchaseOutcome.NotPurchased(PurchaseFailureReason.Unavailable)

    override suspend fun performPremiumPurchase(diagnosticEventLog: DiagnosticEventLogPort) = unavailable to null

    override suspend fun performPremiumPurchaseRestore(diagnosticEventLog: DiagnosticEventLogPort, currentState: PremiumState) = unavailable to null

    override suspend fun performPremiumAdGrant(diagnosticEventLog: DiagnosticEventLogPort, currentState: PremiumState): Pair<AdRewardOutcome, PremiumState?> {
        val outcome = showRewardedAdOnce()
        val result = runPremiumAdGrantApplication(
            PremiumAdGrantRunRequest(outcome = outcome, nowMillis = currentEpochMillis(), currentState = currentState),
        )
        diagnosticEventLog.append(result.diagnosticEvent)
        return outcome to result.nextState
    }

    override suspend fun queryPremiumProductInfo(): PremiumProductInfo? = null

    override suspend fun performBotCharacterPurchase(): PurchaseOutcome = unavailable

    override suspend fun performBotCharacterPurchaseRestore(): PurchaseOutcome = unavailable

    override suspend fun showRewardedAdOnce(): AdRewardOutcome {
        if (!isDebugBuild) return AdRewardOutcome.NotRewarded(AdRewardFailureReason.Unavailable)
        delay(1_000)
        return AdRewardOutcome.RewardEarned()
    }

    override fun openSubscriptionManagement() {}

    override fun isPrivacyOptionsRequired(): Boolean = false

    override suspend fun showPrivacyOptionsForm() {}

    override fun canRequestAds(): Boolean = false

    override fun resetAdConsentForDebug() {}
}
