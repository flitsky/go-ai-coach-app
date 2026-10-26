package com.worksoc.goaicoach.platform

/**
 * 이 빌드가 디버그 빌드인가(백로그 #294 — 이식 계획 S2). 안드로이드는 `BuildConfig.DEBUG`, iOS는 디버그 바이너리인가.
 * 디버그 전용 장치(미리보기 · 무장 파일 · 개발자 메뉴)를 가르는 유일한 기준이다 — 공용 코드는 `BuildConfig`를 볼 수 없다.
 */
expect val isDebugBuild: Boolean

/** 빌드의 이름표 — 설정 「빌드 정보」 · 진단 리포트 · 새 소식이 읽는다(S2). 안드로이드는 `BuildConfig`, iOS는 번들 정보. */
data class AppBuildInfo(
    val versionName: String,
    val versionCode: Int,
    val buildTime: String,
    val buildType: String,
    val useTestAds: Boolean,
    /** 디버그 빌드에서 EEA 동의 창을 억지로 띄우는가(개발자 메뉴 「광고 동의 초기화」의 부제). */
    val forceEeaConsentDebug: Boolean = false,
)

expect val appBuildInfo: AppBuildInfo
