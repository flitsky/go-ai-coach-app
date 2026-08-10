package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.device.DeviceIdentityStorePort

/**
 * 기능 전체를 껐다 켰다 하는 컴파일타임 스위치 모음. 지금은 로그인(Google/이메일 계정 연동)
 * 하나뿐이다 — 게스트(기기 로컬 ID) + Google Play Billing 구매 복원만으로 이미 크로스
 * 디바이스 구매 복원이 완결되고(2026-08-09 실기 검증, `auth-onboarding/README.md` 참고)
 * 계정 로그인이 실제로 열어줄 다른 기능(Firestore 동기화 등)은 아직 미구현이라, 사용자
 * 결정으로 이번 출시에서는 끈다. 값을 `true`로 되돌리고 다시 빌드하면 온보딩/설정 화면의
 * 기존 로그인 UI가 코드 변경 없이 그대로 되살아난다 — 로그인 관련 코드 자체는 지우지 않았다.
 */
internal object FeatureFlags {
    const val isLoginEnabled = false

    /**
     * Google Play Billing을 통한 프리미엄 영구 구매를 끈다(사용자 결정, 2026-08-09) — 광고 시청
     * 1시간 활성화만 남기고 결제 경로 자체를 스토어 심사/앱에서 제거해 "결제·계정 로그인이
     * 필요 없는 앱"으로 간결하게 출시한다. [PurchasePort]/[AndroidBillingClient] 등 결제
     * 연동 코드는 지우지 않았다 — 이 값을 true로 되돌리면 업셀 팝업의 결제 버튼과 앱 시작 시
     * 구매 복원 조회가 코드 변경 없이 그대로 되살아난다.
     */
    const val isPurchaseEnabled = false
}

/**
 * 앱 시작 시 첫 화면을 계산한다. [GoCoachApp.kt]가 상태 훅/라인 예산이 빠듯해(state-holder-refactor
 * 메모리 참고, 849/849·47/47로 여유 없음) 이 판단을 별도 파일로 뺐다 — 호출부는 기존
 * `remember { mutableStateOf(...) }`의 초기값 계산식 하나만 이 함수 호출로 바꾸면 된다.
 *
 * 로그인이 꺼져 있으면 온보딩 화면 자체를 건너뛰고 항상 홈으로 직행한다 — 이 경우 온보딩의
 * "계정 없이 시작하기"가 하던 [DeviceIdentityStorePort.loadOrCreate] 호출(게스트 ID 생성,
 * 이미 있으면 그대로 반환하는 멱등 호출이라 매번 불러도 안전하다)을 여기서 대신 수행해,
 * 온보딩을 거치지 않아도 프리미엄 구매 복원 등 게스트 기반 기능이 그대로 동작하게 한다.
 */
internal fun initialDestination(
    deviceIdentityStore: DeviceIdentityStorePort,
    hasSeenOnboarding: Boolean,
): ScreenDestination {
    if (!FeatureFlags.isLoginEnabled) {
        deviceIdentityStore.loadOrCreate()
        return ScreenDestination.Home
    }
    return if (hasSeenOnboarding) ScreenDestination.Home else ScreenDestination.Onboarding
}
