package com.worksoc.goaicoach.platform

/**
 * 앱을 파는 스토어(백로그 #296). 구독 문구가 해지 · 연결 경로를 말할 때 스토어 이름을 고르고, App Store 심사가 결제 화면에 요구하는 것
 * (「구매 복원」 · 이용약관 · 개인정보처리방침 링크 — `PremiumSubscriptionNotice.kt`)을 그릴지 정한다.
 */
enum class AppStoreKind {
    GooglePlay,
    AppleAppStore,
}
