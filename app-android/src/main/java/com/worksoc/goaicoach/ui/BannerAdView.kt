package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.worksoc.goaicoach.platform.AdUnitIds
import com.worksoc.goaicoach.platform.AdsConsentManager

/**
 * 4계층(External Integration) — 배너 광고. 결정 로직이 없는 순수 표시 컴포저블이라(로드 성공/실패에
 * 따른 상태 전이가 없음, 실패해도 그냥 빈 배너로 남는 SDK 기본 동작을 그대로 둠) [AdRewardPort]류의
 * 포트/순수 함수 분리를 두지 않았다 — `AdView`를 감싸는 것 자체가 이 컴포저블의 전부다.
 *
 * Compose는 `AdView`(View 기반)를 직접 지원하지 않아 [AndroidView]로 감싼다. `onRelease`에서
 * [AdView.destroy]를 호출해, 이 컴포저블이 컴포지션에서 완전히 빠질 때(예: 다른 화면으로 이동)
 * 광고 리소스를 정리한다 — Compose 프리뷰([LocalInspectionMode])에서는 실제 SDK 호출 없이
 * 아무것도 그리지 않는다.
 */
@Composable
internal fun BannerAdView(modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) return

    val adWidthDp = LocalConfiguration.current.screenWidthDp
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                adUnitId = AdUnitIds.bannerAdUnitId
                // ⚠️ **`getLargeAnchoredAdaptiveBannerAdSize`가 아니다**(2026-09-19 실기). 그것은
                // 411dp 폭에서 **약 129dp**를 예약하는데, 이 화면은 판이 남는 높이를 받는 구조라
                // 그만큼 판이 줄어든다. 여기는 보는 화면이지 광고 면이 아니다.
                // ⚠️ **`getCurrentOrientation…`는 쓰지 않는다** — deprecated다(2026-08-05 기록).
                // 이 앱은 `screenOrientation="portrait"`로 세로 고정이라 세로용을 직접 고르는 것이
                // 정확하다. ⚠️ 단 **큰 화면(sw≥600dp)에서는 세로 고정이 무시된다**(함정 41) —
                // 그때도 크기 요청으로서 유효하고 다만 최적은 아니다.
                setAdSize(AdSize.getPortraitAnchoredAdaptiveBannerAdSize(context, adWidthDp))
                // ⚠️ **배경을 비워 둔다 — 기본값이 검정이다.** 적응형 배너는 레이아웃이 틀어지지
                // 않게 자리를 먼저 마련해 두는데, 그보다 작은 소재가 내려오면(예: 320x50) 남는 위아래가
                // **검은 띄로** 남는다 — 2026-09-19 실기에서 바로 드러났다. 비워 두면 그 자리에 화면
                // 배경이 비친다. ⚠️ 높이 예약 자체를 줄이려 들지 말 것 — 소재가 들어올 때마다 아래
                // 내용이 튀는 것이 검은 띄보다 나쁘다.
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                // ⚠️ **동의 없이는 요청하지 않는다**(백로그 #89). 배너는 `showRewardedAdOnce`의
                // 게이트를 지나지 않으므로 이 게이트가 **배너의 유일한 관문**이다.
                // ⚠️ 2026-08-08에 호출부를 떼 두었던 동안에도(`59d880c`) 이 줄을 남겨 둔 덕에,
                // 2026-09-19에 되살릴 때 동의 배선을 다시 밟을 일이 없었다.
                // ⚠️ 콘솔에 메시지가 없으면 `canRequestAds`는 true다(`AdsConsentManager` 머리말).
                if (AdsConsentManager.canRequestAds(context)) {
                    loadAd(AdRequest.Builder().build())
                }
            }
        },
        onRelease = { adView -> adView.destroy() },
    )
}

/**
 * **구독자에게는 배너를 띄우지 않는다** — 배너를 쓰는 화면은 [BannerAdView]가 아니라 이것을 부른다.
 *
 * ## 왜 게이트가 배너 쪽에 있는가
 * 광고를 보일지 말지는 **광고의 정책**이지 화면의 사정이 아니다. 화면마다 `if (!premium…)`를
 * 적어 두면 배너를 붙이는 다음 화면이 그 줄을 빠뜨려도 **컴파일도 되고 테스트도 초록이다.**
 *
 * ## ⚠️ 왜 [PremiumUiState.isActive]가 아니라 [PremiumUiState.isPurchased]인가
 * 등재문이 약속한 것은 **구독**이다 — *"광고 없이 편하게 쓰고 싶다면 프리미엄 구독"*
 * (`store_listing.txt` [요금 안내]). `isActive`는 **광고를 봐서 얻은 1시간**도 포함하는데,
 * 그 1시간은 광고를 본 대가로 받은 것이라 "광고를 없애 준다"는 약속과 무관하다.
 * ⚠️ `isActive`로 바꾸면 **광고를 한 번 보면 한 시간 동안 배너가 사라지는** 앱이 된다.
 *
 * ⚠️ **누르는 것 바로 옆에 붙이지 말 것.** AdMob의 「실수 클릭 유도 배치 금지」가 이 앱에서
 * 배너를 대국 화면에 넣지 않기로 한 이유였다(`PREMIUM_MODE.md` Step 3 후속). 조작부와는
 * 눈에 보이는 여백을 둔다.
 */
@Composable
internal fun SubscriptionAwareBannerAd(modifier: Modifier = Modifier) {
    if (LocalPremiumUiState.current.isPurchased) return
    BannerAdView(modifier = modifier)
}
