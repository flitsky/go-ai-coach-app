package com.worksoc.goaicoach.ui.monetization

import androidx.compose.runtime.Composable
import com.worksoc.goaicoach.platform.AppMonetization

/** 이 화면의 결제 · 광고 묶음(백로그 #294 — S12). 안드로이드는 화면의 액티비티 문맥으로 만든다(결제 창은 액티비티가 띄운다). */
@Composable
internal expect fun rememberAppMonetization(): AppMonetization
