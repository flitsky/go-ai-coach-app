# GameSessionController 이전 후보 정리

작성일: 2026-06-13

## 목적

`GoCoachApp.kt`의 남은 큰 책임은 Compose 상태 보관, 화면 상태 적용, coroutine orchestration이다. 다음 큰 리팩토링은 `GameSessionController` 또는 이에 준하는 state holder를 도입해 Game UX가 로컬/서버 엔진 차이를 모르게 만드는 것이다.

이번 문서는 바로 옮길 수 있는 reducer 후보와 아직 UI에 남겨야 할 항목을 구분한다. 무리하게 함수만 밖으로 빼면 Compose `mutableStateOf`를 외부에서 간접 변경하는 구조가 되어 오히려 상태 소유권이 흐려지므로, 이전 순서를 고정한다.

## 현재 관찰

- `GoCoachApp.kt`: 1,571줄.
- application 계층으로 이동 완료된 주요 영역:
  - 엔진 세션 경계: `EngineSessionClient`
  - Top Moves 분석 실행/완료 update/launch plan
  - 자동 AI 턴 실행 context 및 display plan
  - 점수 추정, 종국, undo, 저장/복원, 시작 plan
  - 런타임 이벤트 로그 formatter
  - 엔진 busy/ready operation guard
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

2. `AnalysisUiReducer` 도입
   - `TopMoveAnalysisUpdate`, top move clear, review clear를 immutable state reducer로 이전한다.
   - Top Moves는 최근 가장 많이 변경된 영역이므로 작은 reducer부터 테스트한다.

3. `ScoreUiReducer` 도입
   - `ScoreEstimateDisplayPlan`, `FinalScoreDisplayPlan`, `EndgameFailureDisplayPlan` 적용을 이전한다.
   - 점수/종국은 오류 탐지 로그와 직결되므로 테스트를 먼저 추가한다.

4. `GameSessionController` 도입
   - coroutine launch, `isEngineBusy`, `isAutoAiTurnPending`, `lastAnalysisKey`를 controller 소유로 이동한다.
   - Compose는 event dispatch와 render에 집중한다.

5. 플랫폼 경계 분리
   - `sessionStore`, `benchmarkStore`, clipboard/debug report 저장은 port/interface로 묶는다.
   - 이 단계가 완료되면 서버 엔진/원격 대국 orchestration을 같은 controller에서 다룰 수 있다.

## 보류 판단

이번 배치에서 reducer를 바로 코드로 크게 이동하지 않는다. 이유는 다음과 같다.

- `GoCoachApp.kt`의 상태 변수가 아직 개별 `mutableStateOf`로 흩어져 있다.
- reducer만 외부로 빼면 외부 함수가 UI 상태 setter를 대량 인자로 받는 형태가 되어 구조가 더 나빠진다.
- 먼저 immutable state holder를 도입해야 테스트 가능한 순수 reducer로 이동할 수 있다.

따라서 이번 배치의 안전한 완료 기준은 후보 정리, 현재 분리 완료 지점 기록, 다음 배치 순서 확정이다.
