package com.worksoc.goaicoach.ui

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.worksoc.goaicoach.application.premium.AdRewardFailureReason
import com.worksoc.goaicoach.application.premium.AdRewardOutcome
import com.worksoc.goaicoach.application.premium.AdRewardPort
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 4계층(External Integration) Extended API 본체 — [AdRewardPort]를 실제 Google Mobile Ads
 * (AdMob) SDK에 연결하는 어댑터. `AndroidAuthClient`와 동일하게 내부 상태가 없는 얇은
 * 래퍼라, 광고를 보여줄 [activity]와 [adUnitId]를 생성자로 받아 호출부(`GoCoachApp.kt`)가
 * 매번 새로 만들어도 비용이 없다.
 *
 * **왜 `RewardedAd`가 아니라 `RewardedInterstitialAd`인가**: AdMob 콘솔에서 실제로 발급받은
 * 광고 단위가 "리워드(Rewarded)"가 아니라 "리워드 전면(Rewarded Interstitial)" 포맷이다
 * (2026-08-05, 사용자가 콘솔에서 생성). 두 포맷은 광고 단위 ID 발급 시점에 고정되어 서로
 * 바꿔 쓸 수 없다(포맷이 다른 SDK 클래스로 로드를 시도하면 실패한다) — 그래서 어댑터가
 * `RewardedInterstitialAd`를 감싼다. 정책상 차이는 "Rewarded는 사용자가 명시적으로 옵트인해야
 * 하고, Rewarded Interstitial은 옵트인 없이도 노출 가능하되 광고 시작 전 보상 고지 화면이
 * 필요하다"는 점인데, 이 앱은 이미 [PremiumUpsellDialog]에서 명시적 옵트인 버튼("광고 시청으로
 * 1시간 활성화")을 거치므로 어느 포맷이든 정책 요건을 충족한다.
 *
 * SDK 초기화([MobileAds.initialize])는 앱 기동 시점이 아니라 [showRewardedAd]가 처음
 * 호출되는 시점(= 사용자가 실제로 "광고 시청" 버튼을 눌렀을 때)에만 지연 수행한다 — 대부분의
 * 세션에서 한 번도 쓰이지 않을 수 있는 기능이 앱 초기 기동 시간에 영향을 주지 않도록 하기
 * 위함이다. 반복 호출은 SDK가 자체적으로 멱등 처리한다(Google 공식 문서 — 최초 1회만 실제
 * 초기화하고 이후 호출은 즉시 콜백).
 */
internal class AndroidRewardedInterstitialAdClient(
    private val activity: Activity,
    private val adUnitId: String,
) : AdRewardPort {
    override suspend fun showRewardedAd(): AdRewardOutcome {
        initializeOnce()
        val ad = loadRewardedInterstitialAdOnce().getOrElse { error ->
            return AdRewardOutcome.NotRewarded(AdRewardFailureReason.LoadFailed, error.message)
        }
        return showLoadedAdOnce(ad)
    }

    private suspend fun initializeOnce(): Unit =
        suspendCancellableCoroutine { continuation ->
            MobileAds.initialize(activity) { continuation.resume(Unit) }
        }

    private suspend fun loadRewardedInterstitialAdOnce(): Result<RewardedInterstitialAd> =
        suspendCancellableCoroutine { continuation ->
            RewardedInterstitialAd.load(
                activity,
                adUnitId,
                AdRequest.Builder().build(),
                object : RewardedInterstitialAdLoadCallback() {
                    override fun onAdLoaded(rewardedAd: RewardedInterstitialAd) {
                        continuation.resume(Result.success(rewardedAd))
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        continuation.resume(Result.failure(RuntimeException(error.message)))
                    }
                },
            )
        }

    // show()의 OnUserEarnedRewardListener는 사용자가 보상 지점까지 시청했을 때만 불린다.
    // FullScreenContentCallback.onAdDismissedFullScreenContent()는 성공/중단과 무관하게 광고
    // 화면이 닫힐 때 항상 불리므로, 그 시점에 rewardEarned 플래그를 확인해 최종 결과를
    // 판정한다(Google 공식 가이드가 권장하는 순서 — RewardedAd와 동일).
    private suspend fun showLoadedAdOnce(ad: RewardedInterstitialAd): AdRewardOutcome =
        suspendCancellableCoroutine { continuation ->
            var reward: AdRewardOutcome.RewardEarned? = null
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    val outcome = reward
                        ?: AdRewardOutcome.NotRewarded(AdRewardFailureReason.DismissedWithoutReward)
                    continuation.resume(outcome)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    continuation.resume(AdRewardOutcome.NotRewarded(AdRewardFailureReason.ShowFailed, error.message))
                }
            }
            // 콜백 인자(RewardItem)는 콘솔에 설정한 보상 종류/수량이다. 앱은 이 값으로 보상을
            // 계산하지 않지만(AdRewardOutcome.RewardEarned KDoc 참고), 진단에서 콘솔과 앱 해석이
            // 어긋난 것을 알아채려면 버리지 않고 실어 보내야 한다.
            ad.show(activity) { item ->
                reward = AdRewardOutcome.RewardEarned(type = item.type, amount = item.amount)
            }
        }
}
