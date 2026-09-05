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

/**
 * 봇 캐릭터 한 종의 구매를 실행한다(백로그 #18).
 *
 * 프리미엄 구매와 **결과를 공유하지 않는다** — 프리미엄은 `PremiumState`를 바꾸지만 캐릭터는
 * `BotCollectionState`에 소유를 기록하므로, 여기서는 결제 결과만 돌려주고 저장은 호출부가
 * 컬렉션 경로(`runBotCharacterUnlock`)로 한다. 같은 `AndroidBillingClient`를 상품 ID만 바꿔
 * 재사용하는 것은 그 어댑터가 처음부터 `productId`를 생성자로 받게 설계돼 있어서다.
 */
internal suspend fun performBotCharacterPurchase(context: Context): PurchaseOutcome {
    val activity = context as? Activity
        ?: return PurchaseOutcome.NotPurchased(PurchaseFailureReason.Unavailable)
    return AndroidBillingClient(activity, BuildConfig.BOT_CHARACTER_PRODUCT_ID).purchasePremium()
}

/**
 * 앱 시작 시 캐릭터 구매를 복원 조회한다 — 재설치로 로컬 컬렉션이 사라져도 산 캐릭터를
 * 되찾아야 한다(프리미엄 쪽 [performPremiumPurchaseRestore]와 같은 이유).
 */
internal suspend fun performBotCharacterPurchaseRestore(context: Context): PurchaseOutcome {
    val activity = context as? Activity
        ?: return PurchaseOutcome.NotPurchased(PurchaseFailureReason.Unavailable)
    return AndroidBillingClient(activity, BuildConfig.BOT_CHARACTER_PRODUCT_ID).restorePurchases()
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
 * 실제 AdMob 리워드 광고를 로드/노출하고(PREMIUM_MODE.md Step 3), 시청 완료(보상 획득)
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
 * **광고를 본 것으로 상정해** 프리미엄 보상 루틴을 그대로 태운다 — 개발자 테스트 2차 전용
 * (백로그 #78).
 *
 * ## 왜 상태를 직접 쓰지 않는가
 * `PremiumState.adGranted(...)`를 여기서 만들어 저장하면 **테스트하려던 그 루틴을 우회한다.**
 * 이 버튼의 목적이 둘이기 때문이다 — ⓐ *광고 보상이 실제로 잘 들어오는지* 확인하고,
 * ⓑ *프리미엄 1시간 활성화·만료* 자체를 확인하는 것. 그래서 [performPremiumAdGrant]와
 * **광고를 띄우는 한 걸음만 다르고** 나머지(상태 전이·진단 로그)는 완전히 같은 경로를 쓴다.
 *
 * ## ✅ 부수 이득 — 실기에서 실제 광고를 밟지 않아도 된다
 * `release`만 `local.properties`의 **실제 AdMob 단위**를 쓰므로, 개발자가 자기 빌드에서 광고를
 * 보거나 누르면 자기 노출·자기 클릭이라 **AdMob 정책 위반**이다(`GOOGLE_PLAY_LAUNCH_PLAN.md` §0 B-3이
 * "광고만 일부러 안 밟았다"고 적은 이유). 이 함수는 광고를 **띄우지 않고** 보상 경로만 밟는다.
 *
 * ⚠️ 그래도 이 버튼은 **debug 전용 2차**에만 둔다(#77) — 프리미엄을 무료로 찍어내는 경로다.
 */
internal fun simulatePremiumAdGrant(
    diagnosticEventLog: DiagnosticEventLogPort,
): PremiumState? {
    val result = runPremiumAdGrantApplication(
        PremiumAdGrantRunRequest(
            // 콘솔 보상값은 앱이 쓰지 않는다(항상 1시간) — 실제 콜백에서도 비어 올 수 있다.
            outcome = AdRewardOutcome.RewardEarned(),
            nowMillis = System.currentTimeMillis(),
        ),
    )
    diagnosticEventLog.append(result.diagnosticEvent)
    return result.nextState
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
