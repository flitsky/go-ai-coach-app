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
- 커밋/푸시 완료 (5fa83f0).

## 2차 개정 (사용자 피드백 반영, 2026-08-04)

1차 구현 이후 사용자 리뷰를 받아 온보딩/프리미엄 카드 방향을 일부 재조정했다.

- **온보딩**: "설치 후 첫 화면은 로그인 화면이어야 한다, 그냥 패스시키는 건 의미 없다"는 피드백에 따라 `OnboardingScreen.kt`를 다시 Google/Apple/이메일/게스트 4버튼("얕은 허들") 구성으로 되돌림. Apple 버튼은 이번에 UI 스텁으로 신규 추가(iOS 대응 시 App Store 정책상 Apple 로그인이 사실상 필수라는 점 고려). 반복 온보딩 버그 수정(로컬 `DeviceIdentityStore` 기반 게스트 완료 조건)은 유지. 게스트 선택 시 별도 확인 팝업은 추가하지 않고, 홈 진입 직후 가벼운 토스트 1회만 안내(사용자와 상의 후 결정).
- **SettingsScreen**: Google/Apple/이메일 3버튼으로 온보딩과 맞춤.
- **프리미엄 카드**: `ui/PremiumTheme.kt` 신규(금색 계열 상수 — `PremiumGold`/`PremiumGoldLight`/`PremiumGoldDeep`/`PremiumGoldGradient`/`PremiumCardShape`/`PremiumLockedBorder`). `GameSetupLobby.kt`의 프리미엄 카드를 금색 그라디언트 디자인으로 고급화하고, 스크롤 영역에서 하단 고정 바("대국 시작하기" 버튼 바로 위)로 이동.
- **인게임 버튼**: `GameActionButtons.kt`의 `ActionButton`/`SingleActionButton`/`ToggleActionButton`에 `premiumLocked` 파라미터 추가, `GamePlaySection.kt`의 4개 게이팅 지점(분석/형세보기/추천수/무르기)에 스레딩 — 잠긴 상태일 때 `PremiumLockedBorder`(금색 테두리)로 "프리미엄 버튼"임을 시각적으로 표시.
- `auth-onboarding/README.md`/`premium-mode/README.md`에 "재개정"/"추가 개정" 섹션으로 이력 추가.
- `make test` 재통과 확인.

## 3차 개정 (로그인 버튼 브랜드 아이덴티티, 2026-08-04)

사용자와 "브랜드 컬러를 버튼에 입힐지" 토론 후 결론: Google/Apple 모두 실제로는 버튼 전체를
브랜드색으로 채우는 걸 권장하지 않는다(Google은 무채색 배경 + 멀티컬러 로고, Apple은 흑/백만
허용) — 그래서 배경은 계속 무채색(OutlinedButton)으로 통일하고, 브랜드 구분은 왼쪽 아이콘
글리프 색상에만 두기로 결정.

- `ui/SocialLoginButton.kt` 신규 — Google/Apple/이메일 로그인 버튼 공통 컴포저블. 왼쪽에
  고정폭 글리프(placeholder — 실제 벡터 로고 에셋 없음), 오른쪽에 라벨. `GoogleBrandBlue`
  상수(단색 근사치)를 Google 글리프에만 적용, Apple/이메일은 기본 색 유지.
  - Google: "G" (파란색), Apple: 🍎, 이메일: ✉ — 모두 텍스트/이모지 기반 placeholder다.
    실제 SDK 연동(Step 2/3) 시 공식 벡터 에셋으로 교체 필요.
- **게스트 버튼 무채색화**: "계정 없이 시작하기"가 진한 배경(Button, primary color)이라
  마치 권장 경로처럼 보인다는 피드백 → 다른 3개 버튼과 동일하게 `SocialLoginButton`(=
  OutlinedButton) 사용으로 변경. 얕은 허들이라는 취지에 맞게 4개 버튼이 동일한 시각적
  무게를 갖도록 함.
- `OnboardingScreen.kt`/`SettingsScreen.kt` 양쪽 모두 `SocialLoginButton`으로 통일.
- `make test` 재통과 확인.

## 4차 개정 (프리미엄 카드 텍스트 간결화, 2026-08-04)

프리미엄 카드의 부제가 길어 2줄로 줄바꿈되며 하단 고정 영역이 세로로 커지는 문제 피드백.
`premiumModeTitle`(공용) + `premiumModeActiveSubtitle`/`premiumModeInactiveSubtitle`(긴 안내문)
3개 필드를 `premiumModeTitleInactive`/`premiumModeTitleActive`(상태를 담은 짧은 제목) +
`premiumModeFeatureList`(상태 무관, "분석·형세보기·추천수·무르기"만 나열하는 공용 부제) 3개로
재구성 — 상태(활성화하기/활성화됨)는 제목이 담당하고 부제는 순수 기능 나열만 하도록 역할을
분리해 텍스트를 줄였다. `GameSetupLobby.kt`의 `PremiumModeCard`에도 `maxLines = 1` +
`TextOverflow.Ellipsis`를 제목/부제 모두에 추가해, 기기 폭이나 폰트 배율이 작아도 카드가
3줄 이상으로 늘어나지 않도록 안전장치를 뒀다. `make test` 재통과 확인 후 커밋/푸시.

## 5차 개정 (실구매 유저 대비 개발자 테스트 인프라, 2026-08-04)

프리미엄을 이미 구매한 유저 케이스를 테스트하고 향후 여러 설정값을 주입할 수 있는 토대를
마련해 달라는 요청. 상세 배경/설계는 `premium-mode/README.md`의 "2026-08-04 개발자 테스트
인프라 추가" 절 참고. 요약:

- `PremiumUiState.isPurchased`/`setPurchased` 신규 — 업셀 팝업 "구매하기" 버튼과 설정
  화면의 새 "개발자 테스트" 섹션 토글이 이 함수 하나를 공유.
- `SettingsScreen.kt`에 개발자 테스트 섹션 추가, `BuildConfig.DEBUG`로 게이팅(릴리스 유출
  방지 — 중요 안전장치).
- `GameSetupLobby.kt`: `premium.isActive`가 true면 프리미엄 카드를 없애고 "대국 시작하기"
  버튼을 금색+👑로 표현. `PremiumModeCard`는 이제 비활성 상태 하나만 표현하도록 단순화
  (`isActive` 파라미터 제거) — 활성 브랜치가 더 이상 호출되지 않아 죽은 코드가 되는 것을
  방지.
- `GoCoachApp.kt` 상태 훅 예산(추가 훅 없이 기존 `premiumState` 재사용) 및 라인 예산(880줄
  중 876줄 사용, 여유 4줄) 모두 준수.
- `make test` 통과 확인.

## 6차 개정 (광고 시청 활성화 버그 수정, 2026-08-04)

사용자 리포트: 대국 설정 화면에서 "광고 시청으로 1시간 활성화"를 눌러도 아무 반응이 없음.

- **원인**: `PremiumState.adGranted(matchGeneration, ...)`는 아직 매치가 배정되지 않은
  경우(대국 시작 전) `adGrantMatchGeneration = null`("pending")로 저장하고, 실제 대국이
  시작되는 시점에 `bindToMatchIfPending`이 그 매치 번호로 확정하는 2단계 설계다. 기존
  `isActive()`는 이 pending 상태를 항상 `false`로 판정했는데, 이 설계는 "활성화가 항상
  홈 화면에서 일어나고, 그 직후 곧바로 대국 설정→대국 시작까지 진행되어 사용자가 이
  간극(pending인 채 false인 구간)을 볼 일이 없다"는 **2차 개정 이전의 흐름을 전제**로 한
  것이었다. 2차 개정에서 활성화 트리거를 대국 설정 화면으로 옮기면서, 사용자가 대국을
  시작하지 않고 그 화면에 머무는 동안(보드 설정 등) 이 간극이 그대로 노출돼 "눌러도
  반응 없음"으로 보이는 회귀가 생겼다.
- **수정**: `PremiumState.isActive()`가 `adGrantMatchGeneration == null`(아직 매치 미배정)
  인 경우도 즉시 유효로 판정하도록 변경 — 시간 조건(1시간 이내)은 그대로 유지된다. 실제
  매치가 시작되면 여전히 `bindToMatchIfPending`이 그 매치 번호로 확정해, 이후에는 그 한
  판에만 유효한 범위로 좁아진다("대국 1판 한정"이라는 기존 정책은 그대로 유지됨).
  `PremiumStateTest.kt`의 `adGrantedStateWithNullMatchIsNotActiveUntilBound`를
  `adGrantedStateWithNullMatchIsActiveBeforeBinding`(+ 1시간 만료 케이스 분리)로 갱신해
  새 의도를 명시.
- `make test` 통과 확인.

## 7차 개정 ("대국 시작하기" 버튼에 프리미엄 상태 표시, 2026-08-04)

프리미엄 활성화 상태(영구 구매 vs 광고 시청 시간 기반)를 버튼에서 구분해서 보여 달라는 요청.

- `PremiumUiState.adGrantExpiresAtMillis` 신규 — AdGrant일 때만 만료 시각(부여 시각+1시간)을
  담고, 영구 구매/비활성이면 `null`. `GoCoachApp.kt`는 이 값을 그대로 전달만 하고(1줄 추가,
  라인 예산 877/880), 실제 카운트다운 tick은 예산 제약이 없는 `GameSetupLobby.kt`에서
  1초 주기 `LaunchedEffect`로 자체 처리.
- "대국 시작하기" 버튼 오른쪽 끝에 작은 뱃지 추가: 광고 시청 기반이면 "12:34" 형식의
  mm:ss 카운트다운(언어 무관 표기라 번역 문자열 불필요), 영구 구매면 "∞"로 시간 해제와
  구분되게 표시.
- `make test` 통과 확인.

## 8차 개정 (홈 복귀 시 프리미엄이 조기 만료되던 버그 수정, 2026-08-04)

사용자 리포트: 광고 시청으로 활성화하고 대국 시작 → 대국 종료 후, 시간이 충분히 남았는데도
프리미엄이 사라짐. Explore 서브에이전트로 원인 추적.

- **원인**: `GoCoachApp.kt`의 `exitToHome()`이 항상 `refreshNewGamePreview()` →
  `GameSessionCoreState.applyGameSetupPreview(...)` → `applyGameSessionResetPlan(...)`을
  거치는데, 이 함수가 (실제 "새 대국 시작"과 동일하게) `runtimeState.nextMatchGeneration()`을
  호출하고 있었다. 즉 **실제로 새 대국을 시작하지 않고 단순히 홈으로 돌아가기만 해도**
  매치 제너레이션이 올라가, 그 번호에 묶인 프리미엄 광고 시청 활성화가 무효 판정됐다.
  `exitToHome()`은 대국 종료 후 뒤로가기(`BackHandler`), 기권 후 뒤로가기, `GameSetupLobby`
  뒤로가기 3곳 모두에서 호출되므로 어느 경로로 홈에 돌아가도 재현되는 문제였다.
- **수정**: `GameSessionCoreState.applyGameSessionResetPlan`에 `advanceMatchGeneration:
  Boolean = true` 파라미터를 추가하고, `applyGameSetupPreview`(미리보기 전용, 실제 대국
  시작 아님)만 `false`로 호출하도록 변경. 실제 "새 대국 시작" 경로
  (`GameLifecycleControllerWiring.kt` → `NewGameController`)는 기본값 `true`를 그대로
  써서 기존 "대국 1판 한정" 정책은 유지된다.
- `GameSessionCoreStateTest.kt`에 `applyGameSetupPreviewDoesNotAdvanceMatchGeneration`
  회귀 테스트 추가.
- `make test` 통과 확인.

## 9차 개정 (광고 시청 프리미엄 정책을 순수 1시간 타이머로 변경, 2026-08-04)

8차 개정으로 "홈 복귀 시 조기 만료" 버그는 고쳤지만, 사용자의 실제 의도는 "새 대국을
시작해도 1시간이 남아 있으면 계속 프리미엄으로 동작해야 한다"는 것이었다 — 이는
2026-07-28에 확정했던 "대국 1판 한정" 정책 자체를 뒤집는 요청. 상세 배경/설계는
`premium-mode/README.md`의 "결정 번복: '대국 1판 한정' 정책을 순수 1시간 타이머로 변경"
절 참고. 요약:

- `PremiumState`에서 `adGrantMatchGeneration`/`bindToMatchIfPending` 완전 제거,
  `isActive(nowMillis)`가 매치 정보 없이 순수 시간만으로 판정하도록 단순화.
- `GoCoachApp.kt`의 pending-매치-바인딩용 `LaunchedEffect` 제거(불필요해짐) — 그
  결과 라인 수 848/880으로 여유가 늘어남(직전 876/880 대비).
- `PremiumUiState.activateForMatch` → `activateAdGrant`로 리네이밍.
- `PremiumStateStore` JSON 코덱, `PremiumStateTest.kt`, `PremiumStateStoreTest.kt` 모두
  새 API에 맞게 갱신.
- `GameSessionRuntimeState.matchGeneration` 자체(및 8차 개정에서 고친
  `advanceMatchGeneration` 분기)는 삭제하지 않고 유지 — 프리미엄 게이팅에는 더 이상
  안 쓰이지만 세션 리셋 시점을 구분하는 일반 부기 값으로서 유효.
- `make test` 통과 확인.
