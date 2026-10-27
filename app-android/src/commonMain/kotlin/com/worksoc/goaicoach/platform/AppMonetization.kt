package com.worksoc.goaicoach.platform

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.premium.port.AdRewardOutcome
import com.worksoc.goaicoach.application.premium.port.PremiumProductInfo
import com.worksoc.goaicoach.application.premium.port.PurchaseOutcome
import com.worksoc.goaicoach.application.premium.state.PremiumState

/**
 * 결제 · 광고 · 광고 동의(백로그 #294 — 이식 계획 S12). 화면은 이 묶음만 부른다 — 안드로이드는 오늘의 Play Billing · AdMob · UMP
 * 경로(`AndroidAppMonetization` → `PremiumPurchaseGlue.kt`)를 **화면의 액티비티 문맥 그대로** 지나고, iOS는 StoreKit · 광고가
 * 붙기 전까지 「쓸 수 없음」으로 답한다(디버그 빌드의 보상형 광고만 시험 광고처럼 보상을 준다 — 시뮬레이터에서 보상 흐름을 보려고).
 *
 * 함수 이름과 반환은 옛 전역 함수(`performPremiumPurchase(context, …)` 등)와 같다 — 첫 인자(문맥)만 빠졌다.
 */
interface AppMonetization {
    suspend fun performPremiumPurchase(diagnosticEventLog: DiagnosticEventLogPort): Pair<PurchaseOutcome, PremiumState?>

    suspend fun performPremiumPurchaseRestore(
        diagnosticEventLog: DiagnosticEventLogPort,
        currentState: PremiumState = PremiumState(),
    ): Pair<PurchaseOutcome, PremiumState?>

    /**
     * 사용자가 누른 「구매 복원」(#296 — App Store 결제 화면에만 있다). iOS는 App Store와 맞춘 뒤(`AppStore.sync` — 암호를 물을 수 있다) 묻고,
     * 안드로이드는 그 단추가 없어 [performPremiumPurchaseRestore]와 같다.
     */
    suspend fun performPremiumPurchaseUserRestore(
        diagnosticEventLog: DiagnosticEventLogPort,
        currentState: PremiumState,
    ): Pair<PurchaseOutcome, PremiumState?>

    suspend fun performPremiumAdGrant(
        diagnosticEventLog: DiagnosticEventLogPort,
        currentState: PremiumState = PremiumState(),
    ): Pair<AdRewardOutcome, PremiumState?>

    suspend fun queryPremiumProductInfo(): PremiumProductInfo?

    suspend fun performBotCharacterPurchase(): PurchaseOutcome

    suspend fun performBotCharacterPurchaseRestore(): PurchaseOutcome

    suspend fun showRewardedAdOnce(): AdRewardOutcome

    /** 스토어의 구독 관리 화면을 연다. */
    fun openSubscriptionManagement()

    /** 광고 동의(UMP) — 설정의 「광고 개인정보 옵션」 줄을 그릴지. */
    fun isPrivacyOptionsRequired(): Boolean

    suspend fun showPrivacyOptionsForm()

    fun canRequestAds(): Boolean

    /** 개발자 메뉴 — 광고 동의를 처음 상태로. */
    fun resetAdConsentForDebug()
}
