package com.worksoc.goaicoach.ui

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
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
 * SDK 초기화([MobileAds.initialize])는 앱 기동 시점이 아니라 [showRewardedAd]가 처음
 * 호출되는 시점(= 사용자가 실제로 "광고 시청" 버튼을 눌렀을 때)에만 지연 수행한다 — 대부분의
 * 세션에서 한 번도 쓰이지 않을 수 있는 기능이 앱 초기 기동 시간에 영향을 주지 않도록 하기
 * 위함이다. 반복 호출은 SDK가 자체적으로 멱등 처리한다(Google 공식 문서 — 최초 1회만 실제
 * 초기화하고 이후 호출은 즉시 콜백).
 */
internal class AndroidRewardedAdClient(
    private val activity: Activity,
    private val adUnitId: String,
) : AdRewardPort {
    override suspend fun showRewardedAd(): AdRewardOutcome {
        initializeOnce()
        val ad = loadRewardedAdOnce().getOrElse { error ->
            return AdRewardOutcome.NotRewarded(AdRewardFailureReason.LoadFailed, error.message)
        }
        return showLoadedAdOnce(ad)
    }

    private suspend fun initializeOnce(): Unit =
        suspendCancellableCoroutine { continuation ->
            MobileAds.initialize(activity) { continuation.resume(Unit) }
        }

    private suspend fun loadRewardedAdOnce(): Result<RewardedAd> =
        suspendCancellableCoroutine { continuation ->
            RewardedAd.load(
                activity,
                adUnitId,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(rewardedAd: RewardedAd) {
                        continuation.resume(Result.success(rewardedAd))
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        continuation.resume(Result.failure(RuntimeException(error.message)))
                    }
                },
            )
        }

    // RewardedAd.show()의 OnUserEarnedRewardListener는 사용자가 보상 지점까지 시청했을
    // 때만 불린다. FullScreenContentCallback.onAdDismissedFullScreenContent()는 성공/중단과
    // 무관하게 광고 화면이 닫힐 때 항상 불리므로, 그 시점에 rewardEarned 플래그를 확인해
    // 최종 결과를 판정한다(Google 공식 가이드가 권장하는 순서).
    private suspend fun showLoadedAdOnce(ad: RewardedAd): AdRewardOutcome =
        suspendCancellableCoroutine { continuation ->
            var rewardEarned = false
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    val outcome = if (rewardEarned) {
                        AdRewardOutcome.RewardEarned
                    } else {
                        AdRewardOutcome.NotRewarded(AdRewardFailureReason.DismissedWithoutReward)
                    }
                    continuation.resume(outcome)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    continuation.resume(AdRewardOutcome.NotRewarded(AdRewardFailureReason.ShowFailed, error.message))
                }
            }
            ad.show(activity) { rewardEarned = true }
        }
}
