package com.worksoc.goaicoach

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration

/**
 * App-wide lifecycle hook — registered via `android:name` in
 * AndroidManifest.xml. The only job here is wiring
 * `ProcessLifecycleOwner` to [AppForegroundEvents]; feature-specific
 * logic (attendance check-in, etc.) subscribes to that event stream
 * instead of adding more observers here.
 *
 * ⚠️ **[ReleaseResetCoordinator]만 예외로 이벤트 스트림을 거치지 않고 여기서 직접, 그리고 가장
 * 먼저 돈다**(백로그 #63). 정식 릴리즈 초기화는 **다른 무엇도 읽거나 쓰기 전에** 끝나야 하기
 * 때문이다 — 출석 체크인이 먼저 돌면 그날 기록이 붙었다가 곧바로 지워져 사용자가 출석을 잃는다.
 */
class GoAiCoachApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        applyAdContentRating()
        // 순서가 중요하다 — 아래 둘보다 먼저다(KDoc 참고).
        //
        // ⚠️ **개발자 모드 주기 초기화가 릴리즈 초기화보다도 먼저다**(백로그 #99). 그것이 지우는
        // 것 안에 **릴리즈 초기화 마커도 들어 있어서**, 순서가 뒤집히면 릴리즈 초기화가 방금 지워진
        // 마커를 보고 한 번 더 돌며 **안내까지 띄운다**(그때는 지울 것이 없으니 조용하긴 하다).
        // ⚠️ **debug에서는 돌지 않는다** — 개발자 본인의 실기 테스트가 3시간마다 날아가면 안 된다
        // (2026-09-05 사용자 결정). `BuildConfig.DEBUG`는 **debug에서만** 참이다(friend 빌드타입이 없어진 2026-09-06부터).
        if (!BuildConfig.DEBUG) {
            DeveloperModeResetCoordinator(this).applyIfNeeded(System.currentTimeMillis())
        }
        ReleaseResetCoordinator(this).applyIfNeeded()
        ProcessLifecycleOwner.get().lifecycle.addObserver(ForegroundObserver)
        AttendanceCheckInCoordinator(this).start()
    }

    /**
     * 광고 재고를 **전체이용가(G)** 로 제한한다.
     *
     * **왜 있어야 하는가**: 2026-09-16 Play가 업데이트를 거부했다 — *"Your app contains ads that are
     * not suitable for children... gambling-related content"* 와 *"The ad content in your app is not
     * consistent with the app's content rating."* 이 앱의 IARC 등급은 **전체이용가(3+)** 인데 광고는
     * `AdRequest.Builder().build()`로 아무 필터 없이 나가고 있었고, AdMob 콘솔의 앱 수준 등급 한도도
     * **성인용(MA)** 이었다(계정 기본값을 물려받은 상태).
     *
     * ⚠️ **콘솔 설정만으로는 부족하다.** AdMob 콘솔 값은 Play 심사관에게 보이지 않는다 — 심사관이
     * 확인할 수 있는 물증은 이 코드뿐이다. 콘솔(G)과 여기(G)를 **같은 값으로** 둔다.
     * ⚠️ 우선순위는 **SDK > 앱 > 계정**이라 이 줄이 콘솔 설정을 덮어쓴다. 등급을 바꾸려면
     * **앱을 다시 배포해야 한다** — 콘솔에서 고칠 수 없게 된다는 뜻이다.
     *
     * **왜 여기(Application)인가**: `MobileAdsInitProvider`(initOrder=100)가 ContentProvider라
     * `Application.onCreate()`**보다 먼저** 돈다. 즉 앱 코드로 "초기화 직전"에 끼어들 자리는 없다.
     * 중요한 것은 초기화 시점이 아니라 **광고 요청보다 먼저**인지이고, 이 앱은 사용자가 버튼을
     * 눌러야만 광고를 로드하므로(`AndroidRewardedInterstitialAdClient`) 여기가 충분히 이르다.
     * 여기 두면 아직 호출부가 없는 `BannerAdView`까지 자동으로 덮인다.
     *
     * ⚠️ `setTagForChildDirectedTreatment`·`setTagForUnderAgeOfConsent`는 **넣지 않는다** —
     * SDK 25.4.0에서 deprecated이고, 타겟층을 만 13세 이상으로 선언한 지금은 얻는 것 없이
     * 광고 fill만 잃는다(2026-09-16 Play 콘솔에서 `9~12세` 선언을 해제했다).
     *
     * ⚠️ 저장소를 읽거나 쓰지 않으므로 [onCreate]의 초기화 순서 계약(KDoc)을 건드리지 않는다.
     */
    private fun applyAdContentRating() {
        MobileAds.setRequestConfiguration(
            MobileAds.getRequestConfiguration()
                .toBuilder()
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                .build(),
        )
    }

    private object ForegroundObserver : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            AppForegroundEvents.notifyForegrounded()
        }
    }
}
