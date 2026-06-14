# KMP 이동 1차 스파이크 - 2026-06-15

## 목적

application 계층의 순수 정책/결과 포장 파일을 향후 `shared` 또는 별도 KMP middleware 모듈로 이동할 수 있는지 점검한다. 이번 스파이크는 실제 파일 이동보다, 이동 후보가 Android/UI/persistence/runtime 구현에 묶이지 않도록 자동화 계약을 먼저 확장하는 데 초점을 둔다.

## 이번에 자동화 계약에 추가한 후보

- `EngineDeviceBenchmarkApplication.kt`
- `EngineSessionLifecycleApplication.kt`
- `EngineStartupApplication.kt`
- `GameSessionUiStateHolderApplication.kt`
- `HumanMoveApplication.kt`
- `PositionAnalysisCacheOptimization.kt`
- `ScoreDisplayApplication.kt`
- `ScoreDisplayFormatterApplication.kt`
- `UndoApplication.kt`
- `ScoreSyncRunnerApplication.kt`

기존 후보였던 `AutoAiCompletionApplication.kt`, `AutoAiPolicyApplication.kt`, `ScoreSyncCompletionApplication.kt`, `TopMovesApplication.kt` 등과 함께 `LayeringContractTest.engineOperationApplicationPoliciesStayPortable` 대상에 포함했다.

## 확인한 제약

- 위 파일들은 현재 Android/Compose/persistence/engine runtime 구현 import 없이 컴파일된다.
- 다만 일부 파일은 `EngineSessionClient` 또는 `EngineCoreApi`를 직접 확장 함수로 참조한다. 이는 KMP 이동 자체를 막지는 않지만, 실제 모듈 이동 시 해당 client port도 함께 KMP 모듈로 이동하거나 interface-only port로 재정의해야 한다.
- `EngineDeviceBenchmarkApplication.kt`는 `System.nanoTime()`을 사용한다. Kotlin/JVM에서는 문제가 없지만 commonMain 이동 시 clock port가 필요하다.
- `ScoreDisplayApplication.kt`와 `UndoApplication.kt`는 Android/UI import 없이 통과한다. 다만 `ScoreDisplayApplication.kt`는 scoring/result display 문구를 함께 들고 있어, 실제 shared 이동 전에는 "도메인 결과"와 "문구 포맷"을 더 쪼갤지 결정해야 한다.
- `GameSessionUiStateHolderApplication.kt`는 Compose state를 직접 소유하지 않고 current/apply callback만 받는다. 따라서 KMP 후보라기보다 UI state mutation을 application reducer 경계로 모으는 중간 어댑터로 보는 것이 정확하다. 2026-06-15 10차 기준으로 score/final/endgame/undo뿐 아니라 Top Moves failure, Auto AI display/failure, Human sync failure 적용도 이 경계를 통과한다.
- `ScoreDisplayApplication.kt`는 `ScoreEstimateStateResult`, `FinalScoreStateResult`를 갖게 되어 domain state result와 display plan 분리를 시작했다. 아직 모든 display 문구가 분리된 것은 아니므로 실제 KMP 이동 전에는 score text/message formatter 분리가 추가로 필요하다.
- `ScoreDisplayFormatterApplication.kt`는 final/endgame display text와 endgame failure display text 조립을 담당한다. 현재 Android/UI/persistence/runtime 구현 의존이 없어 KMP 이동 후보로 가볍다. 다만 문구 자체를 shared/common에 둘지, locale/resource 기반 Android formatter로 둘지는 별도 제품 결정이 필요하다.
- `HumanMoveApplication.kt`는 `HumanEngineSyncEffectLaunchRequest`와 `HumanEngineSyncCompletionRequest`를 갖게 되었다. engine call 입력과 완료 시점 result guard 입력이 분리되어 remote/server engine 전환 시에도 late result discard 세맨틱스를 유지하기 쉬워졌다.
- `HumanMoveApplication.kt`는 `HumanEngineSyncCompletionApplyPlan`도 갖게 되었다. runtime log plan과 success/failure/discard 적용 disposition을 함께 묶어 UI/controller가 적용 순서를 일관되게 유지할 수 있다.
- `ScoreDisplayApplication.kt`와 `TopMovesApplication.kt`는 각각 score estimate/Top Moves completion apply plan을 갖게 되었다. 이는 UI가 raw completion 타입보다 application-defined disposition을 적용하도록 만드는 중간 단계다. 다만 실제 state/cache mutation은 아직 `GoCoachApp.kt` local helper에 남아 있으므로, 물리 KMP 이동 전에는 display/cache/write port 경계를 먼저 정리해야 한다.
- `ScoreSyncRunnerApplication.kt`는 scoring rule sync, post-undo sync, restored game sync runner를 한 곳에 모았다. Android/UI 의존은 없지만 `EngineSessionClient` port와 coroutine suspend runner에 의존하므로 실제 KMP 이동 시 engine session port의 위치가 함께 정리되어야 한다.
- UI state mutation은 여전히 `GoCoachApp.kt`에 남아 있으므로, 이번 스파이크는 policy/result wrapper 이동 가능성만 확인한 것이다.

## 결론

- 즉시 이동 1순위: `EngineStartupApplication.kt`, `ScoreSyncCompletionApplication.kt`, `AutoAiCompletionApplication.kt`
- 조건부 이동: `UndoApplication.kt`, `HumanMoveApplication.kt`, `PositionAnalysisCacheOptimization.kt`, `ScoreSyncRunnerApplication.kt`
- display 문구 분리 후 이동: `ScoreDisplayApplication.kt`
- 얇은 formatter 이동 후보: `ScoreDisplayFormatterApplication.kt`
- UI reducer 경계 안정화 후 재평가: `GameSessionUiStateHolderApplication.kt`
- clock/client port 정리 후 이동: `EngineDeviceBenchmarkApplication.kt`, `EngineSessionLifecycleApplication.kt`

이번 배치에서는 실제 파일 이동 대신 자동화 계약을 확장했다. 다음 배치에서 `EngineStartupApplication.kt` 또는 `ScoreSyncCompletionApplication.kt`를 작은 단위로 `shared` 후보 패키지에 이동하는 것이 가장 안전하다.
