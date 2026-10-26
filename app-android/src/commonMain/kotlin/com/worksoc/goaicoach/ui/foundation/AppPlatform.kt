package com.worksoc.goaicoach.ui.foundation

import androidx.compose.runtime.staticCompositionLocalOf
import com.worksoc.goaicoach.application.debugreport.ClipboardPort
import com.worksoc.goaicoach.platform.AppNotice

/**
 * 화면이 쓰는 플랫폼 기능의 묶음(백로그 #294 — 이식 계획 S1). 화면은 `LocalContext` 대신 [LocalAppPlatform]에서 꺼낸다 —
 * 안드로이드는 `AndroidAppPlatform`(루트, 액티비티가 만든다), iOS는 `IosAppPlatform`이다. 웨이브마다 화면이 옮겨 오며 필요한
 * 기능이 여기에 하나씩 는다 — 멤버의 타입은 공용(`:shared` · 이 모듈 공용) 타입만 쓴다.
 */
interface AppPlatform {
    val notice: AppNotice
    val clipboard: ClipboardPort
    val stores: AppStores
}

/** 루트가 한 번 넣는다(안드로이드 `MainActivity`, iOS `MainViewController`). 넣지 않고 읽으면 바로 터진다 — 조용한 무동작보다 낫다. */
val LocalAppPlatform = staticCompositionLocalOf<AppPlatform> {
    error("AppPlatform not provided — the root provides LocalAppPlatform (MainActivity / MainViewController)")
}
