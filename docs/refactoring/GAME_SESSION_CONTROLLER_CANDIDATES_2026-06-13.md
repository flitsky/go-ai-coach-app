# GameSessionController 이전 후보 정리

작성일: 2026-06-13

## 목적

`GoCoachApp.kt`의 남은 큰 책임은 Compose 상태 보관, 화면 상태 적용, coroutine orchestration이다. 다음 큰 리팩토링은 `GameSessionController` 또는 이에 준하는 state holder를 도입해 Game UX가 로컬/서버 엔진 차이를 모르게 만드는 것이다.

이번 문서는 바로 옮길 수 있는 reducer 후보와 아직 UI에 남겨야 할 항목을 구분한다. 무리하게 함수만 밖으로 빼면 Compose `mutableStateOf`를 외부에서 간접 변경하는 구조가 되어 오히려 상태 소유권이 흐려지므로, 이전 순서를 고정한다.

## 현재 관찰

- `GoCoachApp.kt`: 1,632줄. reducer state holder 도입으로 단기적으로 helper가 늘었지만, analysis/score/runtime 변경 규칙은 테스트 가능한 application state로 분리되었다.
- application 계층으로 이동 완료된 주요 영역:
  - 엔진 세션 경계: `EngineSessionClient`
  - Top Moves 분석 실행/완료 update/launch plan
  - 자동 AI 턴 실행 context 및 display plan
  - 점수 추정, 종국, undo, 저장/복원, 시작 plan
  - 런타임 이벤트 로그 formatter
  - 엔진 busy/ready operation guard
  - 분석 상태 reducer: `GameSessionAnalysisState`
  - 점수 상태 reducer: `GameSessionScoreState`
  - runtime 설정 reducer: `GameSessionRuntimeState`
- UI에 남은 큰 책임:
  - Compose 상태 변수 보관
  - plan을 화면 상태 변수에 적용하는 reducer 역할
  - coroutine launch와 busy 플래그 전이
  - Android context/clipboard/session store 호출

## Reducer 후보

| 후보 | 현재 위치 | 성격 | 이전 난이도 | 권장 순서 |
| --- | --- | --- | ---: | ---: |
| `applyScoreEstimateDisplayPlan` | `GoCoachApp.kt` | score 상태 reducer | 낮음 | 1 |
| `applyTopMoveAnalysisUpdate` | `GoCoachApp.kt` | analysis 상태 reducer | 낮음 | 1 |
| `clearTopMoveSpots`, `clearReviewAnalysis` | `GoCoachApp.kt` | analysis reset reducer | 낮음 | 1 |
| `applyFinalScoreDisplayPlan`, `applyEndgameFailureDisplayPlan` | `GoCoachApp.kt` | endgame/score reducer | 중간 | 2 |
| `applyRuntimePlayLevelSelection` | `GoCoachApp.kt` | runtime 설정 reducer | 낮음 | 2 |
| `applyUndoLocalStatePlan` | `GoCoachApp.kt` | board/analysis/score reset reducer | 중간 | 3 |
| `applyGameSessionResetPlan`, `applySavedGameRestorePlan` | `GoCoachApp.kt` | session 전체 reset reducer | 중간 | 3 |
| `applyAutoAiTurnDisplayPlan` | `GoCoachApp.kt` | board/analysis/score/runtime 복합 reducer | 높음 | 4 |
| `applyHumanEngineSyncDisplayPlan`, `applyHumanEngineSyncFailurePlan` | `GoCoachApp.kt` | human move 후 엔진 sync reducer | 높음 | 4 |
| `applyScoringRuleChangePlan` | `GoCoachApp.kt` | ruleset/score/analysis reset reducer | 중간 | 4 |

## Controller 이전 전제

Reducer를 application으로 직접 옮기지 않는다. 먼저 아래 state holder가 필요하다.

```kotlin
data class GameSessionUiState(
    val gameState: GameState,
    val isGameEnded: Boolean,
    val runtime: RuntimePlayLevelSelection,
    val analysis: AnalysisUiModel,
    val score: ScoreUiModel,
    val endgameLog: String,
    val engineMessage: String,
)
```

이후 `GameSessionReducer`가 `현재 state + display plan -> 다음 state`를 반환하게 한다. Compose는 반환된 immutable state를 받아 표시하고, Android context나 clipboard 같은 플랫폼 작업만 UI에 남긴다.

## 권장 리팩토링 순서

1. `GameSessionUiState` 초안 추가
   - 기존 Compose state를 한 번에 옮기지 않고, 먼저 score/analysis 하위 모델부터 만든다.
   - `GoCoachApp.kt`에는 기존 변수와 새 모델을 병행하지 않는다. 중복 source of truth를 만들지 않는 것이 중요하다.
   - 현재 `GameSessionAnalysisState`, `GameSessionScoreState`, `GameSessionRuntimeState`가 선행 구현되어 있다.

2. `AnalysisUiReducer` 도입
   - `TopMoveAnalysisUpdate`, top move clear, review clear를 immutable state reducer로 이전한다.
   - Top Moves는 최근 가장 많이 변경된 영역이므로 작은 reducer부터 테스트한다.
   - 현재 `GameSessionAnalysisState`로 1차 완료했다. 다음 단계는 `GoCoachApp.kt`의 개별 분석 상태 변수를 이 객체 하나로 통합하는 것이다.

3. `ScoreUiReducer` 도입
   - `ScoreEstimateDisplayPlan`, `FinalScoreDisplayPlan`, `EndgameFailureDisplayPlan` 적용을 이전한다.
   - 점수/종국은 오류 탐지 로그와 직결되므로 테스트를 먼저 추가한다.
   - 현재 `GameSessionScoreState`로 1차 완료했다. 다음 단계는 human move/local score 갱신처럼 아직 직접 대입이 남은 경로까지 이 reducer를 확장하는 것이다.

4. Runtime reducer 확장
   - 현재 `GameSessionRuntimeState`로 `playLevel`, `engineProfile`, `analysisPreset` 묶음은 분리했다.
   - 다음 단계는 `searchTimeSettings`, `playerSetup`, `autoPlayDelaySetting`, `topMovesEnabled`까지 포함할지 검토한다.

5. `GameSessionController` 도입
   - coroutine launch, `isEngineBusy`, `isAutoAiTurnPending`, `lastAnalysisKey`를 controller 소유로 이동한다.
   - Compose는 event dispatch와 render에 집중한다.

6. 플랫폼 경계 분리
   - `sessionStore`, `benchmarkStore`, clipboard/debug report 저장은 port/interface로 묶는다.
   - 이 단계가 완료되면 서버 엔진/원격 대국 orchestration을 같은 controller에서 다룰 수 있다.

## 다음 추천 항목

1. `GameSessionAnalysisState`를 단일 source of truth로 승격
   - 현재는 reducer 계산 후 기존 Compose 개별 변수에 다시 풀어 넣는다.
   - 다음 단계에서는 `candidateMoves`, `candidateText`, `reviewAnalysis`, `reviewCandidateMoves`, `lastAnalysisKey`를 `remember { mutableStateOf(GameSessionAnalysisState...) }` 하나로 합친다.

2. `GameSessionScoreState`를 단일 source of truth로 승격
   - 현재는 display plan 적용부만 reducer를 탄다.
   - human move 직후 local score snapshot 기록, engine sync failure snapshot 반영, 새 게임 시작 score snapshot 반영까지 reducer 함수로 모은다.

3. `GameSessionRuntimeState` 범위 확장
   - runtime triple은 분리되었지만 `searchTimeSettings`와 `playerSetup`은 아직 UI state다.
   - AI 레벨/시간 설정 메뉴 개편을 고려하면 runtime 설정 묶음을 더 명확히 잡는 것이 좋다.

4. `GameSessionController` thin skeleton 도입
   - 처음부터 모든 coroutine을 옮기지 말고, 순수 reducer와 event result 타입만 먼저 둔다.
   - controller가 반환한 effect를 UI가 실행하는 구조로 가면 플랫폼 의존성과 대국 도메인을 분리하기 쉽다.

5. 플랫폼 port 분리
   - `GameSessionStore`, `EngineBenchmarkStore`, debug report mirror 저장, clipboard/toast는 UI에 묶여 있다.
   - 서버 엔진/원격 대국을 고려하면 persistence/diagnostic port를 먼저 정의하는 것이 유리하다.

## 검증 기록

- 2026-06-13: `GameSessionAnalysisState`, `GameSessionScoreState`, `GameSessionRuntimeState` 추가 후 `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test` 통과.

## 보류 판단

이번 배치에서 reducer를 바로 코드로 크게 이동하지 않는다. 이유는 다음과 같다.

- `GoCoachApp.kt`의 상태 변수가 아직 개별 `mutableStateOf`로 흩어져 있다.
- reducer만 외부로 빼면 외부 함수가 UI 상태 setter를 대량 인자로 받는 형태가 되어 구조가 더 나빠진다.
- 먼저 immutable state holder를 도입해야 테스트 가능한 순수 reducer로 이동할 수 있다.

따라서 이번 배치의 안전한 완료 기준은 후보 정리, 현재 분리 완료 지점 기록, 다음 배치 순서 확정이다.
