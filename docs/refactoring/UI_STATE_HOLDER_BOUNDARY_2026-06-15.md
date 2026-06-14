# UI State Holder 경계 설계 - 2026-06-15

## 배경

`GoCoachApp.kt`는 engine workflow의 성공/실패/폐기 판단을 상당 부분 application completion plan으로 넘겼지만, Compose state mutation은 아직 한 파일에 집중되어 있다. 이는 현재까지는 장점도 있다. `gameState`, `analysisState`, `scoreState`, `runtimeState`, `settingsState`가 한 scope 안에 있어 상태 전이를 추적하기 쉽고, 늦게 도착한 엔진 결과를 폐기하는 guard 적용도 한 지점에서 확인된다.

다만 파일 크기와 import 수가 커지고 있어 다음 단계에서는 UI state holder 경계를 잡아야 한다. 이 작업은 단순 파일 분리가 아니라 "상태 소유권"을 정리하는 작업이어야 한다.

## 권장 경계

1. `GameSessionUiStateHolder`
   - 소유 상태: `GameSessionCoreState`, `GameSessionAnalysisState`, `GameSessionScoreState`, `GameSessionMoveReviewState`, `GameSessionRuntimeState`
   - 책임: application display/completion plan을 받아 Compose 상태에 반영한다.
   - 금지: 엔진 호출, persistence 직접 접근, coroutine launch.

2. `GameSessionEffectLauncher`
   - 소유 상태: 없음.
   - 책임: `GameSessionEffect`와 `EngineOperationRequest`를 받아 `EngineSessionClient`를 호출한다.
   - 반환: workflow result 또는 completion plan.
   - 금지: Compose state mutation.

3. `GameSessionUiController`
   - 책임: UI event를 받아 state holder와 effect launcher를 조합한다.
   - 입력: `GameUiEvent`, 현재 state snapshot, callback ports.
   - 금지: 엔진 core/runtime 직접 호출.

## 다음 실제 분리 순서

1. `GoCoachApp.kt`의 local apply 함수들을 state holder 후보로 묶는다.
   - `applyScoreEstimateDisplayPlan`
   - `applyFinalScoreDisplayPlan`
   - `applyHumanEngineSyncDisplayPlan`
   - `applyUndoLocalStatePlan`
   - `applyAutoAiTurnDisplayPlan`

2. state holder는 data class 또는 작은 class로 시작한다.
   - 초기에는 별도 Compose state를 직접 들지 않고, 현재 state snapshot을 받아 next snapshot을 반환하는 순수 함수에 가깝게 둔다.
   - 이후 필요할 때만 Compose `MutableState` 소유로 이동한다.

3. effect launcher는 Top Moves 또는 Score Estimate 중 하나로만 먼저 적용한다.
   - 가장 안전한 후보는 `requestEngineScoreEstimate()`다.
   - 이유: 입력/출력 범위가 작고, follow-up 자동 착수나 endgame 분기가 없다.

4. controller는 마지막에 도입한다.
   - event dispatch 전체를 한 번에 옮기면 blast radius가 크다.
   - 먼저 state holder/effect launcher가 안정화된 뒤, 메뉴/보드/엔진 버튼 event부터 순차 이관한다.

## 주의사항

- UI 파일 줄 수를 줄이는 것 자체가 목표가 아니다. 상태 전이 순서와 늦은 엔진 결과 폐기 의미가 더 중요하다.
- state holder가 persistence port나 engine client를 직접 알게 되면 계층이 다시 섞인다.
- application completion plan은 "무엇을 적용할지"만 말하고, state holder는 "현재 UI 상태에 어떻게 적용할지"만 담당해야 한다.

## 현재 결론

이번 8차에서는 state holder를 실제 파일로 만들지 않는다. 대신 score estimate, engine undo, human sync runtime log의 completion/log plan을 application 계층에 추가해 다음 state holder 분리의 입력 형태를 더 균일하게 만들었다.
