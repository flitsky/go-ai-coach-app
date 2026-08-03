# 플레이 흐름 UX 개편: 로그인 반복 버그 수정 + 프리미엄 팝업 타이밍 정리

## 배경

사용자가 앞으로 고도화할 3대 우선순위(플레이 경험 다듬기 / 로그인 인증 붙이기 / 광고·상품 추가)를
착수하기 전에, 직접 플레이 흐름을 코드 레벨로 리뷰하며 페인포인트를 찾고 정리해 달라는 요청을 받았다.
아래 3가지 구체 항목이 함께 주어졌다.

1. **로그인(계정 없이 시작하기)**
   1. `google-services.json`이 없어 Firebase 익명 로그인이 항상 실패 → 매 실행마다 온보딩이
      반복되는 현상이 실제로 존재하는지 확인
   2. 로컬 기반 익명 ID로 전환하고, Firebase 연결은 실패한 상태로 두되 로그인 UI를 더 이상
      메인 흐름에 노출하지 않기
   3. 홈 화면 상단에 설정 진입점을 추가해, 그 안에서 로그인을 원할 때 강화할 수 있는 단계 제공
2. **대국하기 진입 시 프리미엄 팝업이 UX를 해침**
   1. 홈 → 대국 설정 화면으로 바로 이동
   2. 대국 설정 화면 안에서 프리미엄 모드 활성화를 사용자가 선택할 수 있는 UX 추가
3. **대국 시작 시 프리미엄 활성화를 깜빡한 경우**
   1. 인게임에서 비활성화된 프리미엄 버튼을 누르면 활성화 팝업이 뜨는 기존 폴백이 유지되는지 확인

## 코드 리뷰로 확인한 사실

- `OnboardingScreen.kt`는 `didSignIn`이 false면 `hasSeenOnboarding`을 저장하지 않도록
  설계돼 있음(문서화된 의도) → google-services.json 부재 환경에서는 매 실행마다 온보딩이
  다시 뜨는 것이 확인된 사실(1.1 확인됨).
- `GoCoachHomeScreen.kt`의 `proceedToStartMatch()`가 "대국 하기" 클릭 시 프리미엄
  비활성이면 `GameSetupLobby` 진입보다 먼저 업셀 팝업을 띄움(2번 항목 확인됨).
- `GamePlaySection.kt`(`GameActionButtons`)와 `KaTrainUxPanels.kt`는 이미 프리미엄
  전용 버튼 탭 시 `PremiumUpsellDialogHost`를 띄우는 폴백을 갖고 있음 → **3번 항목은 이미
  구현되어 있어 추가 구현이 필요 없고, 2번 개편 이후에도 그대로 동작함**을 코드로 확인.
- `DeviceIdentity`/`DeviceIdentityStorePort`/`DeviceIdentityStore`는 이전 단계(Stage C-2)에서
  이미 만들어졌으나 소비자가 없던 인프라 — 이번 온보딩 개편의 실제 소비처가 된다.

## 설계 결정

- **온보딩**: `DeviceIdentityStore.loadOrCreate()`는 네트워크 없이 항상 즉시 성공하므로,
  이 호출 성공을 온보딩 완료 조건으로 삼는다. Firebase 익명 로그인은 완전히 제거하지 않고
  버튼 클릭 시 fire-and-forget으로 백그라운드 시도만 남긴다 — 화면은 결과를 기다리지도,
  실패를 알리지도 않는다. 이렇게 하면 (a) 반복 온보딩 버그가 없어지고 (b) 나중에
  google-services.json이 추가되는 순간 별도 코드 변경 없이 조용히 동작하기 시작한다
  (이미 만들어진 재시도 로직을 폐기하지 않음 — 우선순위 #2 "로그인 인증 붙이기"에서 재사용 예정).
- **Google/Email 스텁 버튼**은 온보딩에서 제거하고 신규 `SettingsScreen`으로 이전한다.
- **설정 진입점**: `GoCoachApp.kt`의 상태 훅 예산이 47/47로 소진돼 있어(LayeringContractTest
  budget), 새 `remember`/`mutableStateOf`를 추가하지 않는다. 대신 기존 `currentDestination`
  상태를 재사용해 `ScreenDestination.Settings`를 추가하는 방식으로 구현한다.
- **프리미엄 팝업**: 홈 화면에서는 팝업 없이 바로 `GameSetupLobby`로 이동시키고, 대신
  `GameSetupLobby` 내부에 프리미엄 모드 카드(비활성 시 탭하면 기존 `PremiumUpsellDialogHost`
  재사용)를 추가한다. 인게임 폴백은 코드 변경 없이 그대로 유지된다.

## 진행 로그

- (작성 시작) 계획서 초안 작성.
- Item 1: `OnboardingScreen.kt`를 `DeviceIdentityStorePort.loadOrCreate()` 기반 즉시 완료 흐름으로 재작성. `signInAnonymously()`는 fire-and-forget으로 남김. Google/Email 스텁 버튼 제거.
- Item 1.3: `SettingsScreen.kt` 신규 작성(Google/Email 로그인 강화 버튼 이전), `ScreenDestination.Settings` 추가, `GoCoachHomeScreen.kt`에 `HomeSettingsButton`(⚙) 좌상단 추가. `GoCoachApp.kt` 상태 훅 개수 증가 없이(기존 `currentDestination` 재사용) 구현.
- Item 2: `GoCoachHomeScreen.kt`의 `proceedToStartMatch()`/`showPremiumUpsellDialog`/`PremiumUpsellDialogHost` 제거, "대국 하기" 클릭 시 프리미엄 상태 무관하게 바로 `onStartMatchClick()` 호출.
- Item 2.2: `GameSetupLobby.kt`에 `PremiumModeCard` 신규 추가 — 비활성 시 탭하면 기존 `PremiumUpsellDialogHost` 재사용해서 연다.
- Item 3: `GamePlaySection.kt`/`KaTrainUxPanels.kt`의 인게임 잠긴 버튼 업셀 폴백은 코드 변경 없이 그대로 유지됨을 코드 리뷰로 재확인 — 추가 구현 불필요.
- UiStrings 4개 언어 파일(Ko/En/Ja/Zh)에 `getStarted`/`settingsTitle`/`settingsAccountSectionTitle`/`settingsGuestStatusMessage`/`premiumModeTitle`/`premiumModeActiveSubtitle`/`premiumModeInactiveSubtitle` 추가, 더 이상 쓰이지 않는 `continueWithoutAccount`/`guestConnectionFailedMessage` 제거.
- `auth-onboarding/README.md`, `premium-mode/README.md`에 이번 개편에 맞춰 개정 이력 섹션 추가(각 문서의 "완료 단계도 지우지 않고 이력으로 남긴다" 관례를 따름).
- `make test` (`:shared:check :engine-android:testDebugUnitTest :app-android:assembleDebug :app-android:testDebugUnitTest`) 전체 통과 확인 (`LayeringContractTest`의 `GoCoachApp.kt` 라인/상태 훅 예산 포함).
