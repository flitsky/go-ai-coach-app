package com.worksoc.goaicoach.ui

import android.app.Activity
import android.content.Context
import com.worksoc.goaicoach.BuildConfig
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.premium.AdRewardFailureReason
import com.worksoc.goaicoach.application.premium.AdRewardOutcome
import com.worksoc.goaicoach.application.premium.PremiumAdGrantRunRequest
import com.worksoc.goaicoach.application.premium.PremiumPurchaseRunRequest
import com.worksoc.goaicoach.application.premium.PremiumState
import com.worksoc.goaicoach.application.premium.PurchaseFailureReason
import com.worksoc.goaicoach.application.premium.PurchaseOutcome
import com.worksoc.goaicoach.application.premium.PurchaseTrigger
import com.worksoc.goaicoach.application.premium.runPremiumAdGrantApplication
import com.worksoc.goaicoach.application.premium.runPremiumPurchaseApplication

/**
 * `GoCoachApp.kt`는 상태 훅/라인 예산이 빠듯해(state-holder-refactor 메모리 참고) 어댑터 생성 +
 * 순수 판정 함수 호출 + 진단 로그 기록을 이 얇은 헬퍼로 모아, 호출부는 반환된 [PremiumState]를
 * 자신의 `premiumState`에 반영하는 한 줄만 남기면 되게 한다.
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

/**
 * 실제 AdMob 리워드 광고를 로드/노출하고(premium-mode/README.md Step 3), 시청 완료(보상 획득)
 * 여부는 [runPremiumAdGrantApplication]이 판정해 그때만 상태를 특정 대국(매치)에 묶지 않고
 * 부여한다(부여 시점부터 1시간 동안 몇 판을 새로 시작하든 유효). 로드 실패/중도 이탈 시에는
 * 상태를 바꾸지 않는다. [performPremiumPurchase]와 같은 이유로 이 시퀀스를 GoCoachApp.kt 밖으로 뺐다.
 */
internal suspend fun performPremiumAdGrant(
    context: Context,
    diagnosticEventLog: DiagnosticEventLogPort,
): Pair<AdRewardOutcome, PremiumState?> {
    val outcome = showRewardedAdOnce(context)
    val result = runPremiumAdGrantApplication(
        PremiumAdGrantRunRequest(outcome = outcome, nowMillis = System.currentTimeMillis()),
    )
    diagnosticEventLog.append(result.diagnosticEvent)
    return outcome to result.nextState
}

/**
 * 리워드 광고를 한 번 띄우고 결과만 돌려준다 — **상태는 건드리지 않는다.**
 *
 * 광고 시청은 이제 두 곳에서 쓰인다: 프리미엄 1시간 활성화([performPremiumAdGrant])와 봇 캐릭터
 * 조각 적립(#11, `ui/BotCharacterUiState.kt`). 두 결과의 수명이 정반대라(전자는 1시간 뒤 꺼지고
 * 후자는 영구 소유) **"광고를 보여주는 일"과 "그 결과로 무엇을 바꿀지"를 분리**해 뒀다 — 이 함수는
 * 앞의 절반만 맡는다.
 */
internal suspend fun showRewardedAdOnce(context: Context): AdRewardOutcome {
    val activity = context as? Activity
        ?: return AdRewardOutcome.NotRewarded(AdRewardFailureReason.Unavailable)
    return AndroidRewardedInterstitialAdClient(activity, AdUnitIds.rewardedInterstitialAdUnitId).showRewardedAd()
}
