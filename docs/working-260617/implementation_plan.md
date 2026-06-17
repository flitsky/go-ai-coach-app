# Implementation Plan - Board Size Multi-Size (9x9, 13x13, 19x19) Support Extension

## 1. Goal Description
현재 9x9 크기로 하드코딩되어 있는 대국 환경을 확장하여, 사용자가 설정 메뉴에서 **9x9, 13x13, 19x19**의 3가지 바둑판 크기를 유연하게 선택하고 이에 맞춘 새 게임 기동 및 환경 설정의 영속 저장을 지원하도록 고도화합니다.

> [!NOTE]
> **모바일 UX 대응 방향성 기록**
> - 작은 스마트폰 화면에서 **19x19** 대국을 진행할 경우 격자가 매우 좁아져 착수가 불편할 수 있습니다.
> - 본 고도화 단계에서는 우선 3가지 보드 크기의 기동 및 AI 대국 기능을 완전하게 확장하는 데 집중합니다.
> - 작은 스마트폰 화면에서의 19x19 착수 편의성 보완(돋보기 줌, 터치 정밀 타겟팅 등)은 **추후 UI/UX단에서의 추가 고도화 개발을 통해 점진적으로 확인 및 반영**할 예정입니다. (13x13 크기까지는 현재 화면 사이즈에서 안정적으로 동작 가능함을 확인했습니다.)

---

## 2. Proposed Changes

### 2.1. 사용자 환경설정(UserPreferences) 내 BoardSize 지원 및 영속화
- **[MODIFY] [UserPreferencesSnapshot.kt](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/application/preferences/UserPreferencesSnapshot.kt)**
  - `UserPreferencesSnapshot` 데이터 클래스에 `val boardSize: BoardSize = BoardSize.Nine` 속성을 기본값과 함께 추가합니다.
- **[MODIFY] [UserPreferencesStore.kt](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/persistence/UserPreferencesStore.kt)**
  - `UserPreferencesCodec.encode` 및 `decode` 메소드 내에 `boardSize` 필드의 JSON 직렬화/역직렬화 변환 로직을 추가합니다:
    - encode: `put("boardSize", snapshot.boardSize.value)`
    - decode: `boardSize = BoardSize(json.optInt("boardSize", BoardSize.Nine.value))`

### 2.2. 게임 설정 상태 및 초기 진입 보드 크기 적용
- **[MODIFY] [GameSessionSettingsState.kt](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/application/session/GameSessionSettingsState.kt)**
  - `GameSessionSettingsState` 데이터 클래스에 `val boardSize: BoardSize` 속성을 추가합니다.
  - 보드 크기 변경 상태를 반영하는 `fun applyBoardSize(size: BoardSize): GameSessionSettingsState` 리듀서 메소드를 추가합니다.
  - `InitialUserPreferencesPlan.toGameSessionSettingsState()` 익스텐션에서 `boardSize = settings.boardSize` 매핑을 지원합니다.
- **[MODIFY] [UserPreferencesApplication.kt](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/application/preferences/UserPreferencesApplication.kt)**
  - `GameSettings` 데이터 클래스에 `val boardSize: BoardSize` 속성을 추가합니다.
  - `buildInitialUserPreferencesPlan` 내에서 초기 `GameState.empty` 생성 시 하드코딩된 `BoardSize.Nine` 대신 `preferences.boardSize`를 적용하도록 수정합니다.
  - `buildGameSettings` 및 매핑 메서드(`toGameSettings`, `toUserPreferencesSnapshot`)에서 `boardSize` 관련 대입 처리를 보강합니다.

### 2.3. 새 게임 시작/기동 시 BoardSize 동적 연결
- **[MODIFY] [StartGameApplication.kt](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/application/startgame/StartGameApplication.kt)**
  - `StartConfiguredGamePlan.ResetLocalGame` 및 `StartConfiguredGamePlan.StartEngineGame` 데이터 클래스 각각에 `val boardSize: BoardSize` 속성을 추가합니다.
  - `buildNewLocalGameSessionPlan` 함수 시그니처에 `boardSize: BoardSize` 파라미터를 추가하고 `GameState.empty(boardSize, ruleset)` 으로 전달합니다.
  - `buildStartConfiguredGamePlan` 함수 시그니처에 `boardSize: BoardSize` 파라미터를 추가하고, 각 Plan 생성 인스턴스에 이를 동적으로 패스합니다.
- **[MODIFY] [StartEngineBackedGameRunnerApplication.kt](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/application/startgame/StartEngineBackedGameRunnerApplication.kt)**
  - `runStartEngineBackedGameApplication` 내에서 `request.engineClient.runEngineBackedNewGameWorkflowResult`를 호출할 때 하드코딩된 `BoardSize.Nine` 대신 `boardSize = request.plan.boardSize`를 동적으로 전달하도록 수정합니다.
- **[MODIFY] [NewGameController.kt](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/application/startgame/NewGameController.kt)**
  - `NewGameController` 생성자에 `private val currentBoardSize: () -> BoardSize` 의존성을 주입합니다.
  - `resetLocalGame` 시그니처에 `boardSize: BoardSize`를 추가해 `buildNewLocalGameSessionPlan`에 연동합니다.
  - `startConfiguredGame` 내에서 `buildStartConfiguredGamePlan` 호출 시 `boardSize = currentBoardSize()`를 패스하도록 수정합니다.

### 2.4. UI 및 이벤트 파이프라인 확장
- **[MODIFY] [GameUiEvent.kt](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/presentation/GameUiEvent.kt)**
  - `data class ChangeBoardSize(val boardSize: BoardSize) : GameUiEvent` 신규 이벤트를 선언합니다.
  - `GameUiEventHandlers`, `buildGameUiEventHandlers`, `dispatchGameUiEvent`에 보드 크기 변경 액션 콜백 바인딩을 추가합니다.
- **[MODIFY] [GoCoachApp.kt](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt)**
  - `newGameController` 초기화 시 `currentBoardSize = { settingsState.boardSize }` 람다를 인자로 넘겨줍니다.
  - `buildGameUiEventHandlers` 바인딩 시 `changeBoardSize` 이벤트를 처리할 수 있도록 람다를 설계합니다:
    ```kotlin
    changeBoardSize = { size ->
        settingsState = settingsState.applyBoardSize(size)
        newGameController.startConfiguredGame() // 보드 크기 변경 시 즉시 새 대국을 기동
    }
    ```
- **[MODIFY] [GameMenuSection.kt](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/ui/GameMenuSection.kt)**
  - `GameMenuActionsPanel` 호출부에 `boardSize = screenState.gameState.boardSize` 및 `onBoardSizeChange = { size -> onEvent(GameUiEvent.ChangeBoardSize(size)) }` 인자를 주입합니다.
- **[MODIFY] [GameMenuActionsPanel.kt](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/ui/GameMenuActionsPanel.kt)**
  - 컴포저블의 파라미터로 `boardSize: BoardSize` 및 `onBoardSizeChange: (BoardSize) -> Unit`를 추가합니다.
  - 계가 룰 드롭다운 하단에 "Board size" 드롭다운(`SetupDropdown`)을 수평 정렬되게 렌더링하고, **`listOf(BoardSize.Nine, BoardSize.Thirteen, BoardSize.Nineteen)`**를 선택 옵션으로 매핑합니다.

---

## 3. Verification Plan

### Automated Tests
- `UserPreferencesApplicationTest`, `GameSessionSettingsStateTest`, `NewGameControllerTest` 등 기존 하드코딩 테스트들의 컴파일 에러 대응 및 13x13/19x19 보드 사이즈 변경에 대한 신규 유닛 테스트 케이스를 구축합니다.
- 아래 명령으로 전체 테스트가 성공적으로 빌드되는지 확인합니다:
  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app-android:testDebugUnitTest
  ```

### Manual Verification
- 설정 메뉴의 '계가 규칙' 드롭다운 바로 밑에 'Board size' 드롭다운이 **9x9, 13x13, 19x19** 옵션과 함께 아름답게 노출되는지 확인합니다.
- 보드 크기 드롭다운을 13x13 이나 19x19로 변경할 때 즉시 새 대국 리셋 및 KataGo 엔진 기동이 이루어지며, 바둑판 렌더링(GoBoard) 격자가 해당 스케일로 올바르게 확장되어 그려지는지 확인합니다.
- 대국 종료 후 앱을 재기동하거나 신규 대국을 시작할 때, 이전 설정한 보드 크기 정보가 온전히 복원되는지 영속성을 검증합니다.
