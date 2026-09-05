package com.worksoc.goaicoach.ui

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.worksoc.goaicoach.BuildConfig
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 4계층(External Integration) — Google **UMP**(User Messaging Platform) 어댑터. EEA·영국·스위스
 * 사용자에게 광고를 내보내려면 Google 인증 CMP(동의 배너)를 거쳐야 한다(백로그 #89).
 *
 * ⚠️ **새 의존성이 없다.** `com.google.android.ump:user-messaging-platform:4.0.0`이
 * `play-services-ads` → `play-services-ads-api`를 타고 이미 클래스패스에 올라와 있다
 * (`./gradlew :app-android:dependencies`로 확인, `{strictly 4.0.0}` 제약이 걸려 있으므로
 * `libs.versions.toml`에 다른 버전을 박으면 의존성 해석이 깨진다).
 *
 * ## ⚠️ 이 배선의 가장 위험한 성질 — "붙였다"와 "동작한다"가 구분되지 않는다
 * UMP 3.0.0부터 **앱에 구성된 개인정보 메시지가 없으면 [ConsentInformation.canRequestAds]가
 * `true`를 돌려준다.** 즉 AdMob 콘솔에 유럽 규정 메시지를 **게시하지 않은 채** 이 코드만 넣으면
 * 폼이 뜨지 않고, 게이트는 무조건 통과하고, 광고는 평소대로 나가고, 크래시도 로그도 없다.
 * **콘솔 게시가 코드보다 먼저다.** 검증 기준은 "컴파일된다"가 아니라
 * **"EEA 디버그 지오그래피에서 폼이 실제로 뜬다"** 여야 한다.
 *
 * ## 왜 `shared`의 포트가 아닌가
 * 동의 상태를 보고 무언가를 결정하는 **순수 로직이 하나도 없다** — 실패는 이미 있는
 * `AdRewardFailureReason.Unavailable`로 표현된다. `BannerAdView`가 같은 이유로 포트를 두지
 * 않은 선례를 따른다. 포트를 만들면 파일과 KDoc 값만 치르고 얻는 것이 없다.
 */
internal object AdsConsentManager {

    private fun consentInformation(context: Context): ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    /**
     * 동의 상태를 갱신한다. **앱 기동마다 한 번** 부르는 것이 UMP 규격이다.
     *
     * ⚠️ 폼을 띄우지는 **않는다** — 조회만 한다. 폼은 [gatherConsentIfRequired]가 광고 직전에
     * 띄운다. 규격이 요구하는 것은 *"기동마다 `requestConsentInfoUpdate`"* 이지
     * *"기동 즉시 폼 표시"* 가 아니라서 가능한 절충이고, **#101의 "홈이 곧 랜딩"을 지키기 위해
     * 그렇게 했다** — 이 앱의 유일한 광고는 사용자가 명시적으로 옵트인하는 리워드 광고이므로,
     * 동의를 그 옵트인과 같은 순간에 묻는 편이 사용자에게도 이해된다.
     */
    suspend fun refresh(activity: Activity): Unit = suspendCancellableCoroutine { continuation ->
        val information = consentInformation(activity)
        information.requestConsentInfoUpdate(
            activity,
            consentRequestParameters(activity),
            { if (continuation.isActive) continuation.resume(Unit) },
            { if (continuation.isActive) continuation.resume(Unit) },
        )
    }

    /**
     * 필요하면 동의 폼을 띄우고 끝날 때까지 기다린다. **광고를 요청하기 직전에 부른다.**
     *
     * 돌려주는 값은 *"광고를 요청해도 되는가"* 다. ⚠️ 위 머리말대로 **콘솔에 메시지가 없으면
     * 언제나 `true`** 라는 점을 잊지 말 것.
     */
    suspend fun gatherConsentIfRequired(activity: Activity): Boolean {
        suspendCancellableCoroutine { continuation ->
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
        return canRequestAds(activity)
    }

    // ⚠️ `canRequestAds()`는 **함수다** — `getCanRequestAds()`가 아니라서 코틀린 프로퍼티로
    //    안 보인다. UMP 4.0.0 자바 API 그대로다.
    fun canRequestAds(context: Context): Boolean = consentInformation(context).canRequestAds()

    /**
     * 설정 화면에 **동의 철회** 진입점을 그려야 하는가. AdMob 콘솔에서 "개인정보 옵션 링크
     * 포함"을 켰을 때만 `REQUIRED`가 된다 — 켜지 않았다면 행을 그리지 않는 것이 맞다.
     */
    fun isPrivacyOptionsRequired(context: Context): Boolean =
        consentInformation(context).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    suspend fun showPrivacyOptionsForm(activity: Activity): Unit =
        suspendCancellableCoroutine { continuation ->
            UserMessagingPlatform.showPrivacyOptionsForm(activity) {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }

    /**
     * ⚠️ **debug에서만 동작한다.** 한 번 동의하면 그 뒤로는 폼이 다시 뜨지 않으므로, 이것이
     * 없으면 실기 테스트가 *"한 번 보고 다시는 못 보는"* 상태에 갇힌다.
     * ⚠️ 개발자 모드 3시간 초기화로는 대체되지 않는다 — `DeveloperModeResetCoordinator`는
     * `go_ai_coach_` 접두사 prefs만 지우고 UMP는 자기 prefs(`IABTCF_*`)에 저장한다.
     */
    fun resetForDebug(context: Context) {
        if (!BuildConfig.DEBUG) return
        consentInformation(context).reset()
    }

    /**
     * ⚠️ **`USE_TEST_ADS`를 재사용하지 않는다 — 축이 다르다.** 동의 필요 여부는 빌드타입이
     * 아니라 **기기 IP**가 정한다. 게다가 `friend`·`playInternal`은 `USE_TEST_ADS=true`를
     * 하드코딩하면서도 **실제 테스터에게 배포되는** 빌드라, 거기에 EEA를 강제하면 한국
     * 테스터에게 동의 폼이 뜬다. `BuildConfig.DEBUG`도 friend에서 참이므로
     * [BuildConfig.FORCE_EEA_CONSENT_DEBUG]라는 **debug 전용 플래그**를 따로 둔다.
     */
    private fun consentRequestParameters(context: Context): ConsentRequestParameters {
        val builder = ConsentRequestParameters.Builder()
        if (BuildConfig.FORCE_EEA_CONSENT_DEBUG) {
            builder.setConsentDebugSettings(
                ConsentDebugSettings.Builder(context)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .build(),
            )
        }
        return builder.build()
    }
}
