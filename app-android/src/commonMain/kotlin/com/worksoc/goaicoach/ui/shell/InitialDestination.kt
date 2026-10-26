package com.worksoc.goaicoach.ui.shell

import com.worksoc.goaicoach.application.device.DeviceIdentityStorePort
import com.worksoc.goaicoach.ui.foundation.FeatureFlags

/**
 * 앱 시작 시 첫 화면을 계산한다. [GoCoachApp.kt]가 상태 훅/라인 예산이 빠듯해(state-holder-refactor
 * 메모리 참고. ⚠️ 이 주석은 2026-09-08까지 *"849/849·47/47"* 이라고 적고 있었다 — #102가 예산을
 * 역할 단위로 바꾼 뒤 2026-09-23 기준 계약은 **코드 777줄 / 상태 훅 42(여유 0)** 다
 * (`LayeringContractTest.kt`의 `goCoachAppStaysWithinShrinkingUiShellBudget()`가 선언하는
 * `lineBudget`/`stateHookBudget` 참고 — 줄 번호는 밀리니 심볼로 찾을 것) 이 판단을 별도 파일로 뺐다 — 호출부는 기존
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
