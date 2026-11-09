package com.worksoc.goaicoach.ui.monetization

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import com.worksoc.goaicoach.ui.foundation.screenWidthDp
import platform.UIKit.UIColor

/**
 * iOS 배너 한 줄(백로그 #296) — 안드로이드 `BannerAdViewAndroid`의 짝. 크기도 같다(세로 고정 적응형 — 화면 폭에 맞춘 50~90pt).
 * 배너는 Swift(`GoogleAdsSource.makeBanner`)가 만들고 동의가 있을 때만 광고를 요청한다. 광고 SDK가 없는 묶음(카탈로그)은 그리지 않는다.
 * 구독자 숨김은 이 위의 [SubscriptionAwareBannerAd]가 한다.
 */
@Composable
internal actual fun BannerAdView(modifier: Modifier) {
    val ads = LocalIosAppMonetization.current.ads ?: return
    val width = screenWidthDp().toDouble()
    val height = remember(ads, width) { ads.bannerHeight(width) }
    // 자리의 바탕은 화면 바탕색이다 — 투명으로 두면 Compose가 뚫어 둔 자리 아래의 흰 창이 비쳐, 광고가 오기 전(또는 오지 않으면 계속)
    // 흰 띠로 남는다(2026-11-10 시뮬레이터). 안드로이드는 같은 자리에 화면 배경이 비친다(`BannerAdViewAndroid`의 TRANSPARENT). 어두운 모드도 따라간다.
    val background = MaterialTheme.colorScheme.background
    UIKitView(
        factory = { ads.makeBanner(width) },
        modifier = modifier.fillMaxWidth().height(height.dp),
        update = { view ->
            view.backgroundColor = UIColor.colorWithRed(
                red = background.red.toDouble(),
                green = background.green.toDouble(),
                blue = background.blue.toDouble(),
                alpha = background.alpha.toDouble(),
            )
        },
    )
}
