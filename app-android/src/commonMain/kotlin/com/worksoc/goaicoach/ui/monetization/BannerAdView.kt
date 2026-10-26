package com.worksoc.goaicoach.ui.monetization

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** 배너 광고 한 줄(백로그 #294 — S13) — 안드로이드는 AdMob 배너(`BannerAdViewAndroid.kt`), iOS는 광고 SDK 전까지 그리지 않는다. */
@Composable
internal expect fun BannerAdView(modifier: Modifier = Modifier)

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
