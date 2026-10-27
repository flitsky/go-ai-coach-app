package com.worksoc.goaicoach.ios

import com.worksoc.goaicoach.application.premium.port.AdRewardFailureReason
import com.worksoc.goaicoach.application.premium.port.AdRewardOutcome
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UIKit.UIView

/**
 * 광고(백로그 #296 — U-86 사용자 2026-10-27: 안드로이드와 똑같이 · ATT 창 없이 비맞춤만). Swift `GoogleAdsSource`가 Google Mobile Ads · UMP의
 * iOS SDK로 구현해 [IosAppRoot]에 넘긴다(두 SDK는 Swift 패키지라 Kotlin이 직접 부르지 못한다 — [IosStoreSource]와 같은 이유).
 * 안드로이드 `AndroidRewardedInterstitialAdClient` · `AdsConsentManager` · `BannerAdViewAndroid`의 자리다. 답은 리스너로 — 한 번만 온다.
 */
interface IosAdsSource {
    /** 동의 정보를 새로 묻고, 필요하면 동의 창을 띄운 뒤 광고를 요청해도 되는지 답한다(안드로이드 `refresh` + `gatherConsentIfRequired`). */
    fun gatherConsent(listener: IosAdsConsentListener)

    /** 보상형 전면 광고 한 편 — 불러와 띄우고, 닫힐 때 결과를 준다. */
    fun showRewardedAd(listener: IosRewardedAdListener)

    fun canRequestAds(): Boolean

    /** 설정의 「광고 개인정보 옵션」 줄을 그릴지(UMP). */
    fun isPrivacyOptionsRequired(): Boolean

    fun showPrivacyOptions(listener: IosAdsDoneListener)

    /** 개발자 메뉴 — 동의를 처음 상태로. */
    fun resetConsent()

    /** 그 폭의 세로 고정 적응형 배너 높이(pt) — 안드로이드 `getPortraitAnchoredAdaptiveBannerAdSize`와 같은 크기. */
    fun bannerHeight(widthPoints: Double): Double

    /** 배너 하나 — 동의가 있을 때만 광고를 요청한다(배너는 보상형 광고의 동의 게이트를 지나지 않는다). */
    fun makeBanner(widthPoints: Double): UIView
}

interface IosAdsConsentListener {
    fun onDone(canRequestAds: Boolean)
}

interface IosAdsDoneListener {
    fun onDone()
}

interface IosRewardedAdListener {
    fun onRewarded(type: String, amount: Int)

    fun onDismissedWithoutReward()

    fun onLoadFailed(detail: String)

    fun onShowFailed(detail: String)
}

internal suspend fun IosAdsSource.consentAllowsAds(): Boolean = suspendCancellableCoroutine { continuation ->
    gatherConsent(
        object : IosAdsConsentListener {
            override fun onDone(canRequestAds: Boolean) {
                if (continuation.isActive) continuation.resume(canRequestAds)
            }
        },
    )
}

internal suspend fun IosAdsSource.rewardedOutcome(): AdRewardOutcome = suspendCancellableCoroutine { continuation ->
    fun finish(outcome: AdRewardOutcome) {
        if (continuation.isActive) continuation.resume(outcome)
    }
    showRewardedAd(
        object : IosRewardedAdListener {
            override fun onRewarded(type: String, amount: Int) = finish(AdRewardOutcome.RewardEarned(type = type, amount = amount))

            override fun onDismissedWithoutReward() = finish(AdRewardOutcome.NotRewarded(AdRewardFailureReason.DismissedWithoutReward))

            override fun onLoadFailed(detail: String) = finish(AdRewardOutcome.NotRewarded(AdRewardFailureReason.LoadFailed, detail))

            override fun onShowFailed(detail: String) = finish(AdRewardOutcome.NotRewarded(AdRewardFailureReason.ShowFailed, detail))
        },
    )
}

internal suspend fun IosAdsSource.privacyOptionsShown(): Unit = suspendCancellableCoroutine { continuation ->
    showPrivacyOptions(
        object : IosAdsDoneListener {
            override fun onDone() {
                if (continuation.isActive) continuation.resume(Unit)
            }
        },
    )
}
