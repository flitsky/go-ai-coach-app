# 화면 UX 구성 개편 및 고도화 태스크

## 1단계: 설계 및 네비게이션 구조화 [완료]
- `[x]` **1. Navigation Destination 정의**
  - `GoCoachApp.kt` 에 `ScreenDestination` Enum 선언 및 세션 홀더 하위에 `currentDestination` 상태 연동.
- `[x]` **2. 홈 화면 (0뎁스) 구현**
  - `GoCoachHomeScreen.kt` 컴포저블 신규 작성 ("대국 하기", "학습 하기").
- `[x]` **3. 대국 설정 로비 화면 (1뎁스) 구현**
  - `GameSetupLobby.kt` 컴포저블 신규 작성 (설정 항목 및 50% 비율 축소 `GoBoard` 프리뷰 배치).
  - "대국 시작하기" 버튼 연동 (대국 시작 후 `InGame` 전환).
- `[x]` **4. 저장된 대국 복원(Resume) 연동**
  - 복원 다이얼로그 수락 시 `InGame`으로 다이렉트 전환하도록 흐름 보완.

## 2단계: 인게임 메뉴 고도화 및 컴포넌트 정리 [완료]
- `[x]` **1. ExpandedGameMenuSection 파라미터 분리**
  - `GameMenuSection.kt` 의 `ExpandedGameMenuSection` 컴포저블에 1뎁스 노출 전용 모드 또는 파라미터 추가.
  - 인게임 우측 상단 메뉴 노출 시 "플레이어 설정" 및 "대국 설정(룰셋/바둑판크기/접바둑)" 영역은 숨김 처리.
- `[x]` **2. GoCoachApp 전체 화면 흐름 연동**
  - `GoCoachScreen`에서 `currentDestination` 분기 처리하여 화면 렌더링.

## 3단계: 빌드 및 검증 테스트 [완료]
- `[x]` **1. 레이어 의존성 및 컴파일 검증**
  - `:app-android:testDebugUnitTest` 테스트가 깨지지 않는지 컴파일 및 빌드 검증.
- `[x]` **2. 수동 UX 동작 시나리오 검증**
  - 0뎁스 -> 1뎁스 -> 2뎁스 전환 및 미니 프리뷰 변경 확인.
  - 인게임 진입 시 메뉴가 접힌 채 시작하고, 메뉴 오픈 시 불필요 항목 제외 여부 확인.

## 4단계: 이전 대국 복원(Resume) 팝업 최상위(홈 화면) 호이스팅 [완료]
- `[x]` **1. GoCoachContent에서 복원 다이얼로그 렌더링 제거**
  - `GoCoachContent.kt`에서 `ResumeSavedSessionDialog` 코드 제거.
- `[x]` **2. GoCoachApp에 복원 다이얼로그 이식 및 연동**
  - `GoCoachApp.kt`의 `GoCoachScreen` 내부 최상위 렌더링 트리로 다이얼로그 이동.
  - `savedSessionToPrompt` 조건 체크 후 메인 홈 화면 등에서도 복원 다이얼로그가 즉시 뜨도록 연동.
- `[x]` **3. 유닛 테스트 및 빌드 검증**
  - 변경 후 전체 빌드 및 회귀 방지 테스트 통과 여부 검증.

## 5단계: Back 키 기권 연동 및 홈 퇴장 시 보드 리셋 [완료]
- `[x]` **1. GoCoachApp에 AlertDialog 및 TextButton 임포트 추가**
  - `GoCoachApp.kt`에 다이얼로그 조작에 필요한 컴포저블 임포트.
- `[x]` **2. exitToHome() 유틸 함수 정의**
  - 대국을 리셋하고 홈으로 복귀하는 공통 로직 이식.
- `[x]` **3. BackHandler 연동 및 기권 팝업 처리**
  - 2뎁스 진행 중 Back 누를 시 기권 유도 다이얼로그 조건부 렌더링.
  - 1뎁스 및 기권 승인 시 `exitToHome()` 호출 연동.
- `[x]` **4. 유닛 테스트 빌드 검증**
  - 리팩토링 후 전체 유닛 테스트 빌드 검증 및 통과 확인.

## 6단계: 대국 종료 시 바둑판 및 돌 색상 어둡기/밝기 보정 이펙트 적용 [완료]
- `[x]` **1. GoBoard.kt에 isGameEnded 파라미터 연동**
  - 바둑판 및 돌 색상 변경 상태 플래그 셋업.
- `[x]` **2. 바둑판 RGB 20% 어둡게 보정**
  - `Color(0xFFD7A85E)` 에서 20% 어두워진 `Color(0xFFAC864B)`로 분기 렌더링.
- `[x]` **3. 백돌 20% 어둡게, 흑돌 20% 밝게 보정**
  - 백돌과 흑돌의 그라디언트 브러시 색상 및 테두리 명도 보정 코드 작성.
- `[x]` **4. 빌드 컴파일 및 유닛 테스트 확인**
  - 변경 코드 빌드 검증 완료.

## 7단계: 양 패스 시 계가 연산 로딩 오버레이 추가 [완료]
- `[x]` **1. GoBoard.kt 상단 필요한 레이아웃 임포트 추가**
  - `CircularProgressIndicator`, `Spacer`, `Column` 등 연산 알림에 필요한 컴포저블 임포트.
- `[x]` **2. textAlpha 맥동 애니메이션 선언 및 오버레이 적용**
  - 바둑판 내부 Box 최하단에 `isGameEnded && gameState.hasConsecutivePasses()` 조건부 오버레이 렌더링.
- `[x]` **3. 전체 빌드 및 유닛 테스트 검증**
  - 리액티브 텍스트 렌더링 추가 후 전체 빌드 성공 확인.

## 8단계: 계가 오버레이 잔상 제거 및 무르기 후 팝업 스킵 버그 수정 [완료]
- `[x]` **1. GoBoard.kt 로딩 조건에 engineActivityIndicator 연동**
  - 연산 완료 혹은 팝업 종료 시 로딩 표시가 사라지게 수정.
- `[x]` **2. GoCoachContent.kt 에 판정 팝업 리셋 이펙트 선언**
  - `isGameEnded` 가 풀릴 때 `dismissedFinalJudgementKey` 캐시를 무효화하는 코드 작성.
- `[x]` **3. 회귀 테스트 검증 및 빌드**
  - 리팩토링 후 전체 유닛 테스트 결과 검증 확인.

## 9단계: 계가 로딩 오버레이 isEngineBusy 연동 보완 [완료]
- `[x]` **1. GoBoard.kt 시그니처에 isEngineBusy 추가 및 렌더링 연동**
  - `isEngineBusy` 파라미터를 추가하고, 오버레이 조건문을 `isEngineBusy` 로 갱신.
- `[x]` **2. GamePlaySection.kt 및 GameSetupLobby.kt 내 GoBoard 호출부 갱신**
  - `isEngineBusy`에 알맞은 파라미터를 주입하여 넘김.
- `[x]` **3. 전체 컴파일 및 유닛 테스트 검증**
  - 변경 후 회귀 방지 예산 테스트 완벽 수행 확인.

## 10단계: 로딩 오버레이 isGameEnded 조건 제외 [완료]
- `[x]` **1. GoBoard.kt 내 로딩 조건문 간소화**
  - 오버레이 조건식을 `gameState.hasConsecutivePasses() && isEngineBusy` 로 갱신.
- `[x]` **2. 빌드 컴파일 및 유닛 테스트 확인**
  - 리액터 상태 변경 후 전체 테스트 통과 여부 검증.

## 11단계: 대국 설정 로비 바텀 네비게이션 인셋 고정 [완료]
- `[x]` **1. GameSetupLobby.kt 에 navigationBarsPadding 임포트 추가**
  - `androidx.compose.foundation.layout.navigationBarsPadding` 임포트.
- `[x]` **2. 하단 "대국 시작하기" 버튼 컨테이너에 padding 적용**
  - `navigationBarsPadding()` 적용하여 소프트키에 버튼 잘림 해결.
- `[x]` **3. 전체 컴파일 및 유닛 테스트 확인**
  - 레이아웃 인셋 패치 후 빌드 통과 검증.

## 12단계: 대국 설정 로비 상단 스테이터스바 인셋 적용 [완료]
- `[x]` **1. GameSetupLobby.kt 에 statusBarsPadding 임포트 추가**
  - `androidx.compose.foundation.layout.statusBarsPadding` 임포트.
- `[x]` **2. 로비 상단 뒤로가기 헤더 Row에 padding 적용**
  - `statusBarsPadding()` 적용하여 상태 표시줄에 아이콘이 겹치는 현상 해결.
- `[x]` **3. 전체 컴파일 및 유닛 테스트 확인**
  - 레이아웃 안전 영역 패치 후 빌드 통과 검증.

## 13단계: 대국 화면 헤더 영역 간소화 및 3분할 재배치 [완료]
- `[x]` **1. GameMenuSection.kt 에 Box 및 TextAlign 임포트 추가**
  - `androidx.compose.foundation.layout.Box` 및 `androidx.compose.ui.text.style.TextAlign` 임포트.
- `[x]` **2. formatBuildTime 헬퍼 생성 및 GameHeaderSection 개편**
  - strings.appTitle 텍스트 뷰를 소거하고 1f:2f:1f 비율 of Row 레이아웃으로 빌드타임/정보/메뉴 버튼을 각각 좌/중/우 정렬.
- `[x]` **3. 전체 컴파일 및 유닛 테스트 확인**
  - 헤더 레이아웃 교체 후 유닛 테스트 통과 검증.

## 14단계: 빌드타임 포맷 vyyMMdd.HHmm 변경 및 KataGo 분석 영역 고도화 [완료]
- `[x]` **1. GameMenuSection.kt 내 formatBuildTime 리턴 포맷 변경**
  - `vyyMMdd.HHmm` 형식으로 빌드타임을 리턴하도록 수정.
- `[x]` **2. EngineResponsePanel.kt 에 정규식 파싱 헬퍼 및 이중 템플릿(인간/AI) 구성**
  - `SeatController` 추가 임포트 및 플레이어 제어 주체에 따른 분석 정보 구조화.
  - 가로 1대1 분석 카드 정보창 내 가독성 높은 레이아웃 텍스트 결합 적용.
- `[x]` **3. 전체 컴파일 및 유닛 테스트 확인**
  - 변경 후 `:app-android:testDebugUnitTest` 유닛 테스트 통과 검증.

## 15단계: 대국 화면 버튼 1/2열 재배치 및 분석 정보창 팝업화 [완료]
- `[x]` **1. UiStrings.kt 에 localized 'analysis' 추가**
  - `analysis` 프로퍼티 추가 및 다국어 맵 연동.
- `[x]` **2. GamePlaySection.kt 에 GameActionButtonState 임포트 추가**
  - `com.worksoc.goaicoach.presentation.GameActionButtonState` 임포트.
- `[x]` **3. GamePlaySection 및 GameActionButtons 개편**
  - `showAnalysisDialog` 상태 선언, 기존 항상 렌더링되던 `EngineResponsePanel` 소거 후 팝업 연동.
  - 1열: [분석 | 형세 보기 | 추천 수 | 무르기], 2열: [기권 | 통과] 레이아웃 재배치 및 `ActionButtonWrapper` 헬퍼 적용.
- `[x]` **4. 전체 컴파일 및 유닛 테스트 확인**
  - 변경 후 `:app-android:testDebugUnitTest` 유닛 테스트 통과 검증.

## 16단계: 버튼 3x2 그리드 정렬 고도화 및 엔진 분석 중 활성화 조건 일원화 [완료]
- `[x]` **1. GamePlaySection.kt 버튼 3x2 로 배분 개편**
  - 1열: [분석 | 형세보기 | 추천수], 2열: [기권 | 통과 | 무르기] 로 분할 배치.
- `[x]` **2. GameScreenState.kt 내 TopMoves/Eval 활성화 조건에서 isBlockingBusy 제거**
  - 엔진 연산 중에도 버튼 활성화 유지하여 깜빡임 제거 및 실시간 On/Off 기능 제공.
  - `GamePlaySection.kt` 분석 버튼에서도 `isBlockingBusy` 검사 소거.
- `[x]` **3. GameScreenStateTest.kt 유닛 테스트 단언문 갱신**
  - `Eval.enabled` 의 `assertFalse` 를 신규 비활성화 일원화 정책에 맞추어 `assertTrue` 로 보완.
- `[x]` **4. 전체 컴파일 및 유닛 테스트 확인**
  - 변경 후 `:app-android:testDebugUnitTest` 유닛 테스트 통과 검증.

## 17단계: 토글(On/Off) 및 단발성 실행 버튼의 UX 시각적 이원화 [완료]
- `[x]` **1. GamePlaySection.kt 헬퍼 컴포저블 분리**
  - `ToggleActionButton` 및 `SingleActionButton` 신규 선언.
- `[x]` **2. 1열 및 2열 버튼에 헬퍼 컴포저블 매핑**
  - 1열: 분석(Single), 형세보기(Toggle), 추천수(Toggle)
  - 2열: 기권(Single), 통과(Single), 무르기(Single)
- `[x]` **3. 전체 컴파일 및 유닛 테스트 확인**
  - 변경 후 `:app-android:testDebugUnitTest` 유닛 테스트 통과 검증.

## 18단계: 바둑판 크기 설정 변경 시 "바로 착수" 옵션 연동 [완료]
- `[x]` **1. GoCoachApp.kt 의 changeBoardSize 콜백 및 팝업 렌더링 갱신**
  - `BoardSize.Nine` 선택 시 `isDirectPlayEnabled = true` 연동.
  - `BoardSize.Nineteen` 선택 시 `isDirectPlayEnabled = false` 연동.
  - `DirectPlayRecommendationDialog.kt` 신규 생성 및 다국어 `UiStrings` 추가 적용하여 아키텍처 제약(Line / Hook Budget) 안전 통과.
- `[x]` **2. 전체 컴파일 및 유닛 테스트 확인**
  - 변경 후 전체 빌드 및 유닛 테스트 성공 검증.

## 19단계: 대국 화면 우상단 메뉴의 팝업 다이얼로그화 [완료]
- `[x]` **1. GoCoachContent.kt 렌더링 분기 개편**
  - `isDisplayMenuExpanded == true` 일 때 `ExpandedGameMenuSection` 을 `AlertDialog`로 래핑하여 렌더링.
  - 수직 스크롤 뷰(`verticalScroll`)가 포함된 Column 본문 셋업.
- `[x]` **2. 전체 컴파일 및 유닛 테스트 확인**
  - 변경 후 전체 빌드 및 유닛 테스트 성공 검증.
