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
                setAdSize(AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, adWidthDp))
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { adView -> adView.destroy() },
    )
}
