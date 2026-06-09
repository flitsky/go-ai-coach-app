# Go AI Coach 리팩토링 전략 보고

작성일: 2026-06-08
최근 갱신: 2026-06-09

## 요약

최근 기능 추가로 앱은 POC를 넘어 실제 플레이 가능한 구조에 가까워졌다. 동시에 Android 화면 파일 하나가 너무 많은 책임을 갖게 되었다. 현재 가장 큰 리스크는 도메인 룰이나 엔진 경계가 무너진 것이 아니라, `GoCoachApp.kt`가 UI 상태, 엔진 호출 순서, 자동 AI 턴, Top Moves 분석, 종국 처리, 저장/복원, 디버그 리포트 생성을 모두 직접 조율한다는 점이다.

따라서 다음 리팩토링의 목표는 기능 동작을 바꾸는 것이 아니라, 상위 계층으로 갈수록 추상화 레이어를 두고 “화면은 상태를 표시하고 이벤트를 전달만 한다”는 구조로 옮기는 것이다.

## 2026-06-09 판단 업데이트

리팩토링은 현재 프로젝트에 권고된다. 이유는 “코드를 예쁘게 만들기 위해서”가 아니라, 엔진 분석, 대국 진행, 저장/복원, UI 표시가 계속 고도화되는 상황에서 변경 영향 범위를 줄여야 하기 때문이다.

장점:

- 기능 추가 속도가 장기적으로 빨라진다. 예를 들어 Top Moves, 착수 리뷰, 계가, Player Setup이 서로 덜 얽히면 한 기능 수정이 다른 기능을 깨뜨릴 가능성이 낮아진다.
- 테스트 가능한 단위가 늘어난다. 엔진 호출 없이도 후보수 표시 정책, 종국 처리, 저장 복원, 화면 상태 조립을 검증할 수 있다.
- 향후 JNI/process/native engine/remote server 교체가 쉬워진다. 상위 계층은 `EngineAdapter`와 application service 결과만 보면 된다.
- UX 실험 비용이 줄어든다. 화면은 `GameScreenState`를 그리기만 하면 되고, 분석 데이터 구조는 application/presentation 계층에서 안정적으로 유지된다.

단점:

- 단기 개발 속도는 느려진다. 기능 하나를 추가하기 전 “어느 계층 책임인가”를 먼저 판단해야 한다.
- 파일 수와 타입 수가 늘어난다. 작은 기능도 DTO/helper가 생기면 처음 보는 개발자에게는 진입 비용이 있다.
- 과한 추상화 위험이 있다. 아직 변하지 않은 요구사항까지 예측해 interface를 만들면 오히려 수정이 어려워진다.
- Compose 상태를 controller/ViewModel로 옮기는 단계는 timing regression이 생기기 쉽다. 특히 엔진 busy, 자동 AI 턴, 이어하기 팝업, 자동 Top Moves 분석은 순서 보장이 중요하다.

냉정한 현재 평가:

- `shared`, `engine-android`, `application`, `presentation`, `ui`의 큰 경계는 이미 좋아졌다.
- `GoCoachApp.kt`는 1,957줄에서 1,102줄까지 줄었지만, 아직 화면 상태 보관과 엔진 orchestration을 모두 가진다. 이 파일은 여전히 가장 큰 기술 부채다.
- 지금 당장 대규모 ViewModel 전환을 한 번에 수행하는 것은 권장하지 않는다. 먼저 화면 렌더링, 상태 조립, 저장/복원, 자동 분석 trigger를 더 작은 단위로 빼면서 회귀 테스트를 유지해야 한다.
- 다음 권고 순서는 `GoCoachApp.kt` 내부의 상태 전이 helper를 controller 후보로 묶고, 그 다음에 Compose 상태를 ViewModel 또는 controller state holder로 이전하는 것이다.

## 2026-06-09 리팩토링 완성도 평가

현재 전체 리팩토링 완성도는 약 68%로 본다. POC 앱을 계속 고도화할 수 있는 기반은 확보됐지만, 첫 마켓 릴리즈 이후 유지보수까지 안정적으로 보장하는 수준은 아직 아니다.

세부 평가:

| 영역 | 완성도 | 평가 |
| --- | ---: | --- |
| 도메인/엔진 경계 | 82% | `shared` 룰/DTO와 `EngineAdapter` 경계는 안정적이다. |
| application service 분리 | 72% | 점수, 종국, Top Moves, 저장, 무르기, 사람 착수 로직이 많이 분리됐다. |
| presentation 상태/이벤트 계약 | 70% | `GameScreenState`, `GameUiEvent`가 생겨 UI 계약은 명확해졌다. |
| UI/UX 파일 분리 | 74% | 메뉴, 플레이 화면, Player Setup, 응답 패널이 분리됐다. |
| 상태 소유권/controller 전환 | 38% | `GoCoachApp.kt`가 아직 많은 `remember` 상태와 엔진 orchestration을 직접 가진다. |
| 테스트 기반 | 78% | 핵심 application helper 테스트는 좋지만 UI 상태 전이/controller 테스트는 더 필요하다. |
| 패키지 구조 정리 | 62% | 큰 방향은 잡혔지만 `match`/`shared` 일부 재배치 후보가 남아 있다. |

남은 큰 리팩토링 추천 항목은 8개 정도다.

1. `GameSessionController` 또는 일반 Kotlin state holder 도입
2. `LaunchedEffect` trigger를 startup/resume/auto-ai/auto-analysis 단위로 분리
3. 엔진 orchestration 상태 전이를 UI 밖으로 이전
4. Top Moves/analysis cache lifecycle을 controller 또는 store로 이전
5. `GoBoard`를 board base, stones, candidate overlay, ownership overlay, input 처리로 분리
6. `ScoreGraphPanel`의 graph data model과 drawing을 분리
7. 메뉴/UX 옵션을 data-driven menu schema로 정리
8. `match` 정책 중 shared/application으로 내려갈 항목을 선별

## 현재 구조 진단

현재 주요 파일 규모:

| 파일 | 라인 수 | 역할 |
| --- | ---: | --- |
| `app-android/.../ui/GoCoachApp.kt` | 1,102 | 화면 상태, 엔진 orchestration, 자동 분석/AI 턴, 저장/복원 trigger |
| `app-android/.../ui/GoCoachContent.kt` | 300 | 화면 렌더링 조립, 메뉴, 보드, 액션 버튼, 이어하기 다이얼로그 |
| `engine-android/.../KataGoProcessEngineAdapter.kt` | 666 | KataGo process/JNI 경계 |
| `app-android/.../ui/GoBoard.kt` | 575 | 바둑판 drawing/input |
| `app-android/.../ui/EnginePanels.kt` | 418 | Player Setup, 메뉴, 로그 패널 |
| `engine-android/.../KataGoAnalysisParser.kt` | 291 | GTP/분석 파싱 |
| `app-android/.../match/MatchPolicy.kt` | 283 | Player Setup, AI 착수 선택 정책 |
| `app-android/.../persistence/GameSessionStore.kt` | 196 | 로컬 저장/복원 codec |
| `shared/.../PlayLevel.kt` | 188 | 레벨링, 분석 예산, 후보 선택 정책 |

긍정적인 점:

- `shared`에는 바둑 룰, 수순 재생, 계가, 사석 정리, 엔진 DTO/interface가 비교적 잘 분리되어 있다.
- `engine-android`는 실제 KataGo process 경계를 `EngineAdapter` 뒤에 숨기고 있다.
- `match`, `persistence`, `application`, `presentation`이 생기면서 Android UI 밖으로 상당수 책임이 빠져나갔다.
- Debug report, 종국 처리, Top Moves 분석 계획, AI 턴 실행, 점수 표시 계획, 사람 착수 로컬 처리, 무르기 계획은 application 계층에서 테스트 가능한 상태가 되었다.

문제점:

- `GoCoachApp.kt`가 화면 컴포저블이면서 동시에 application service처럼 동작한다.
- `LaunchedEffect`가 여러 개이고, 각 effect가 서로 다른 상태 플래그에 반응한다. 이어하기 팝업처럼 timing 문제가 생기기 쉽다.
- 엔진 분석 cache, Top Moves 자동 분석, AI 자동 착수, 종국 cleanup이 한 파일 안에서 직접 상태를 변경한다.
- `match` 패키지는 현재 Android app 계층에 있지만, 일부 정책은 shared/application 계층으로 내려갈 수 있다.
- `GoCoachApp.kt`의 `remember` 상태가 많아 상태 전이의 소유권이 아직 명확하지 않다.

## 목표 아키텍처

권장 방향은 “Clean Architecture를 과하게 도입”하는 것이 아니라, 현재 프로젝트 크기에 맞는 얇은 계층 분리다.

```text
app-android
  ui
    Compose 화면, drawing, 사용자 이벤트 전달
  presentation
    ViewState, UiEvent, ViewModel 또는 Controller
  application
    GameSessionController, AnalysisController, EndgameController
  persistence
    SavedGameSessionStore

engine-android
  EngineAdapter 구현체
  KataGo process/assets/config 관리

shared
  domain
    GameState, Move, BoardRules, Scorer, DeadStoneCleaner
  analysis
    CandidateMove, MoveAnalysisSnapshot, ScoreTimeline, PlayLevel
  engine
    EngineAdapter interface, Engine DTO
```

상위 계층일수록 구체 구현을 모르게 해야 한다.

- UI는 `EngineAdapter`를 직접 호출하지 않는다.
- UI는 `GameSessionController` 또는 `ViewModel`에 이벤트를 보낸다.
- Controller는 `EngineAdapter`, `SessionStore`, domain model을 조합한다.
- shared domain은 Android, Compose, KataGo process를 모른다.

## 우선 분리할 책임

### 1. 화면 상태 모델 분리

현재 `GoCoachScreen` 내부의 `remember { mutableStateOf(...) }` 상태들을 하나의 `GameScreenState` data class로 정리한다.

예상 모델:

```kotlin
data class GameScreenState(
    val gameState: GameState,
    val engine: EngineUiState,
    val analysis: AnalysisUiState,
    val score: ScoreUiState,
    val setup: PlayerSetup,
    val uxOptions: KaTrainUxOptions,
    val resumePrompt: ResumePromptState?,
)
```

기대 효과:

- UI는 단일 상태를 읽고 그린다.
- 저장/복원, 엔진 busy, 팝업 표시 조건이 명시적 상태로 남는다.
- 스냅샷 테스트나 단위 테스트가 쉬워진다.

### 2. 엔진 orchestration 분리

현재 UI 파일 안에 있는 다음 흐름을 `GameEngineCoordinator` 또는 `EngineSessionController`로 옮긴다.

- 앱 시작 시 `initialize -> newGame -> estimateScore`
- 현재 수순을 엔진에 `syncToGameState`
- 사람 착수 후 `estimateScore`
- AI 턴 전 `configure -> sync -> analyze -> playMove`
- Top Moves 분석 요청과 cache hit/miss 처리

권장 인터페이스:

```kotlin
interface EngineSession {
    suspend fun start(profile: EngineProfile, state: GameState): EngineStartupResult
    suspend fun sync(state: GameState, profile: EngineProfile): EngineSyncResult
    suspend fun analyzeTopMoves(state: GameState, request: AnalysisRequest): AnalysisSessionResult
    suspend fun playAiTurn(state: GameState, setup: SidePlayerSetup): AiTurnResult
}
```

이 계층은 Android `Context`나 Compose를 몰라야 한다.

### 3. 게임 진행 use case 분리

사용자 이벤트를 use case로 나눈다.

- `StartNewGameUseCase`
- `SubmitMoveUseCase`
- `UndoTurnUseCase`
- `RequestTopMovesUseCase`
- `RequestScoreEstimateUseCase`
- `ResolveEndgameUseCase`
- `RestoreSavedGameUseCase`

처음부터 모두 클래스로 쪼갤 필요는 없다. 1차로는 `GameSessionController` 안의 메서드로 묶고, 커지는 use case만 파일로 빼는 편이 현실적이다.

### 4. 종국 처리 분리

`resolveAiEndgame`, `AiEndgameResolution`, `buildEndgameLog`는 UI 하단에 있을 이유가 없다.

권장 위치:

- `app-android/application/EndgameResolver.kt`
- 또는 Android 의존이 없다면 `shared/analysis/EndgameResolution.kt`

현재 종국 로직은 엔진 `deadStones`, 로컬 `DeadStoneDetector`, `DeadStoneCleaner`, `BoardScorer`, `EndgameScoreSelector`를 조합한다. 이 조합 자체는 application 계층이 적절하다.

### 5. Debug report 생성 분리

`buildDebugReport`, `toBoardText`, `toStonesText`, `toMovesText`, endgame log formatter는 별도 `DebugReportBuilder`로 분리한다.

이유:

- 사용자 로그 기반 TDD를 계속 만들 것이므로 report format이 테스트 가능한 단위여야 한다.
- UI 파일이 긴 문자열 생성 책임을 갖지 않는다.
- 이후 “로그 파일로 저장”, “친구/QA용 리포트 공유”로 확장하기 쉽다.

### 6. 분석 cache 분리

현재 cache는 UI 상태와 같이 `remember`에 있다. 우선은 app 계층 `AnalysisCacheStore`로 분리한다.

단기 목표:

- 메모리 cache는 유지
- cache key/result 타입은 UI 파일 밖으로 이동
- `Undo` 후 cache 재사용 정책을 테스트 가능하게 만들기

장기 목표:

- 최근 대국 분석 snapshot 일부를 디스크 저장할지 검토
- 단, 디스크 cache는 모델/config/profile 변경 시 무효화 규칙이 필요하므로 당장 도입하지 않는다.

## 권장 작업 순서

### Phase 1. UI 파일 다이어트

목표: `GoCoachApp.kt`를 2,000줄에서 1,000줄 이하로 줄인다.

작업:

1. `DebugReportBuilder` 분리
2. `EndgameResolver` 분리
3. `AnalysisCacheKey`, `CachedAnalysisResult`, analysis limit helper 분리
4. `MoveReviewBuilder` 분리

리스크가 낮고 기능 변경이 거의 없다. 첫 리팩토링으로 적합하다.

### Phase 2. Controller 도입

목표: Compose 화면에서 엔진 호출 순서와 상태 전이를 직접 관리하지 않게 한다.

작업:

1. `GameScreenState` 정의
2. `GameUiEvent` 정의
3. `GameSessionController` 또는 Android `ViewModel` 도입
4. `LaunchedEffect`는 controller 이벤트 수집/초기화 트리거 정도로 축소

이 단계부터 구조 변경 폭이 커진다. Phase 1 이후 테스트 기반을 늘리고 진행하는 것이 좋다.

### Phase 3. Application service 정리

목표: AI 자동 대국, Top Moves, 종국, 저장/복원이 서로 독립된 service로 관리되게 한다.

작업:

1. `EngineSession` service
2. `AnalysisService`
3. `MatchProgressService`
4. `SavedGameService`

각 service는 `GameState`와 DTO를 입력받고 결과 DTO를 반환한다. Compose 상태를 직접 변경하지 않는다.

### Phase 4. 패키지 구조 재정리

현재 패키지:

```text
app-android/engine
app-android/match
app-android/persistence
app-android/ui
engine-android/engine/android
shared/shared
```

권장 패키지:

```text
app-android/application
app-android/presentation
app-android/persistence
app-android/ui
engine-android/katago
shared/domain
shared/analysis
shared/engine
```

단, shared package rename은 import churn이 크다. 당장 하지 말고 Phase 1~2가 끝난 뒤 진행한다.

## 테스트 전략

리팩토링은 기능 변경이 아니므로 테스트 기준을 분명히 둔다.

기존에 반드시 계속 통과해야 하는 명령:

```bash
make test
```

Phase 1에서 추가할 테스트:

- `DebugReportBuilderTest`: report에 Runtime/GameState/Board/Moves/EndgameLog가 포함되는지
- `EndgameResolverTest`: 사용자가 준 사석/종국 로그 케이스 유지
- `AnalysisCacheStoreTest`: 같은 fingerprint/profile이면 cache hit, limit 변경이면 miss
- `MoveReviewBuilderTest`: 착수 좌표가 snapshot에 있을 때 색상/문구 유지

Phase 2에서 추가할 테스트:

- controller 초기화 상태 전이
- 저장 snapshot이 있으면 startup 완료 후 resume prompt 상태가 되는지
- resume prompt 대기 중 자동 AI 턴/자동 분석이 시작되지 않는지

## 리팩토링 중 지켜야 할 원칙

1. 기능 변경과 리팩토링을 같은 커밋에 섞지 않는다.
2. `shared` domain은 Android/Compose/KataGo process를 몰라야 한다.
3. `engine-android`는 KataGo process와 asset/config 위치만 책임진다.
4. UI는 가능한 한 `state -> 화면`, `event -> controller` 형태로 축소한다.
5. 엔진 호출 순서는 application 계층에서 단일하게 관리한다.
6. 사용자 로그 기반 회귀 테스트를 계속 추가한다.
7. 패키지 rename 같은 큰 이동은 마지막에 한다.

## 다음 권장 착수안

Phase 1의 핵심 분리는 완료되었고, Phase 2/3도 일부 진행되었다. 다음 작업은 `GoCoachApp.kt`를 한 번에 ViewModel로 옮기는 것이 아니라, controller 후보를 안정적으로 만드는 순서가 적절하다.

첫 번째 후속 커밋:

- `GoCoachApp.kt`의 반복 상태 반영 코드를 작은 apply helper로 묶는다.
- 예: Top Moves update 반영, score display plan 반영, session reset/restore plan 반영.
- 목표는 기능 변화 없이 “상태를 어떻게 바꾸는지”를 이름 있는 단위로 만드는 것이다.

두 번째 후속 커밋:

- 자동 trigger를 분리한다.
- 예: 엔진 startup effect, saved session persistence effect, 자동 AI 턴 trigger, 자동 Top Moves trigger.
- 목표는 `LaunchedEffect` 의존성을 더 명확히 하고 startup/resume timing regression을 줄이는 것이다.

세 번째 후속 커밋:

- `GameSessionController` 또는 `GoCoachSessionController`를 도입한다.
- 처음에는 Android `ViewModel`이 아니라 일반 Kotlin controller로 시작하는 편이 안전하다.
- controller가 안정화되면 그 다음에 Compose 상태를 ViewModel로 옮긴다.

이 순서가 좋은 이유는 현재 앱이 실제 엔진과 비동기 분석을 사용하므로, 큰 이동보다 작은 경계 생성이 회귀 위험이 낮기 때문이다.
