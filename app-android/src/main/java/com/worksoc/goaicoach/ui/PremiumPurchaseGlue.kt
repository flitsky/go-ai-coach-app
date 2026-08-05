package com.worksoc.goaicoach.ui

import android.app.Activity
import android.content.Context
import com.worksoc.goaicoach.BuildConfig
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.premium.PremiumPurchaseRunRequest
import com.worksoc.goaicoach.application.premium.PremiumState
import com.worksoc.goaicoach.application.premium.PurchaseFailureReason
import com.worksoc.goaicoach.application.premium.PurchaseOutcome
import com.worksoc.goaicoach.application.premium.PurchaseTrigger
import com.worksoc.goaicoach.application.premium.runPremiumPurchaseApplication

/**
 * `GoCoachApp.kt`가 상태 훅/라인 예산이 빠듯해(880줄/47훅, 여유 12줄·0훅 — premium-mode/README.md
 * Step 4 참고) 어댑터 생성 + 순수 판정 함수 호출 + 진단 로그 기록을 이 얇은 헬퍼로 모아, 호출부는
 * 반환된 [PremiumState]를 자신의 `premiumState`에 반영하는 한 줄만 남기면 되게 한다 — Step 3의
 * `activateAdGrant`처럼 이 시퀀스를 GoCoachApp.kt에 그대로 인라인할 여유가 이번엔 없었다.
 */
internal suspend fun performPremiumPurchase(
    context: Context,
    diagnosticEventLog: DiagnosticEventLogPort,
): Pair<PurchaseOutcome, PremiumState?> =
    resolvePremiumPurchase(context, diagnosticEventLog, PurchaseTrigger.Explicit) { activity ->
        AndroidBillingClient(activity, BuildConfig.PREMIUM_PRODUCT_ID).purchasePremium()
    }

/** 앱 시작 시 복원 조회 버전 — [performPremiumPurchase]와 판정 로직(상태 전이/로그)을 그대로 공유한다. */
internal suspend fun performPremiumPurchaseRestore(
    context: Context,
    diagnosticEventLog: DiagnosticEventLogPort,
): Pair<PurchaseOutcome, PremiumState?> =
    resolvePremiumPurchase(context, diagnosticEventLog, PurchaseTrigger.Restore) { activity ->
        AndroidBillingClient(activity, BuildConfig.PREMIUM_PRODUCT_ID).restorePurchases()
    }

private suspend fun resolvePremiumPurchase(
    context: Context,
    diagnosticEventLog: DiagnosticEventLogPort,
    trigger: PurchaseTrigger,
    callBillingClient: suspend (Activity) -> PurchaseOutcome,
): Pair<PurchaseOutcome, PremiumState?> {
    val activity = context as? Activity
    val outcome = if (activity != null) {
        callBillingClient(activity)
    } else {
        PurchaseOutcome.NotPurchased(PurchaseFailureReason.Unavailable)
    }
    val result = runPremiumPurchaseApplication(
        PremiumPurchaseRunRequest(outcome = outcome, trigger = trigger, nowMillis = System.currentTimeMillis()),
    )
    diagnosticEventLog.append(result.diagnosticEvent)
    return outcome to result.nextState
}
