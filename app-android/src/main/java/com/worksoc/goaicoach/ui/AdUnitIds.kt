package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.BuildConfig

/**
 * 실제 광고 단위 ID를 쓸지, 항상 Google 공식 테스트 ID를 쓸지 이 한 곳에서만 결정한다.
 * [BuildConfig.USE_TEST_ADS]는 `app-android/build.gradle.kts`가 빌드 타입별로 심어주는 값이다
 * (debug/friend=항상 true, release=local.properties에 실제 값이 있을 때만 false) — 정식 출시
 * 전(디버그/친구 배포 빌드)에는 로컬 설정과 무관하게 항상 테스트 광고만 나가도록, "빌드 종류"
 * 자체가 안전장치가 되게 한다(사람이 토글을 깜빡해도 안전). AdMob은 실제 광고 단위에 인위적인
 * 트래픽이 쌓이면 계정을 정지시킬 수 있다는 정책이 있어(Google 공식 "Understanding account
 * suspensions due to invalid traffic" 문서) 이 판단을 코드 리뷰로 한눈에 확인 가능한 한 파일에
 * 모아둔다.
 */
internal object AdUnitIds {
    // Google 공식 테스트 ID(https://developers.google.com/admob/android/test-ads) — 항상 공개/
    // 커밋 가능하고, 실제 계정과 무관하다.
    private const val TestRewardedInterstitialAdUnitId = "ca-app-pub-3940256099942544/5354046379"
    private const val TestBannerAdUnitId = "ca-app-pub-3940256099942544/6300978111"

    val rewardedInterstitialAdUnitId: String
        get() = if (BuildConfig.USE_TEST_ADS) TestRewardedInterstitialAdUnitId else BuildConfig.REWARDED_INTERSTITIAL_AD_UNIT_ID

    val bannerAdUnitId: String
        get() = if (BuildConfig.USE_TEST_ADS) TestBannerAdUnitId else BuildConfig.BANNER_AD_UNIT_ID
}
