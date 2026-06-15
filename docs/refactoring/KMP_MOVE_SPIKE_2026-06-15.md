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
- `ScoreSyncCompletionApplication.kt`도 `ScoreSyncCompletionApplyPlan`을 갖게 되었다. post-undo/scoring/restored sync의 success/failure/discard 적용 disposition이 completion plan과 분리되어, KMP 이동 후보로서의 순수 정책 성격이 더 명확해졌다.
- `ScoreEstimateRunnerApplication.kt`, `ScoreSyncRunnerApplication.kt`, `TopMovesApplication.kt`는 apply runner를 추가했다. runner 파일은 여전히 `EngineSessionClient` port에 의존하므로 실제 물리 이동 시 client port 위치 정리가 필요하지만, UI가 raw completion/result를 조립하지 않아도 되는 방향으로 경계가 안정화됐다.
- `ScoreSyncRunnerApplication.kt`는 scoring rule sync, post-undo sync, restored game sync runner를 한 곳에 모았다. Android/UI 의존은 없지만 `EngineSessionClient` port와 coroutine suspend runner에 의존하므로 실제 KMP 이동 시 engine session port의 위치가 함께 정리되어야 한다.
- 2026-06-15: `DiagnosticEventModel.kt`를 실제로 `shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/diagnostic/DiagnosticEventModel.kt`로 이동했다. 외부/내부 리뷰에서 지적한 "KMP 후보 문서화만 있고 실제 이동은 0건" 상태를 끝내기 위한 1차 물리 이동 스파이크다.
- 2026-06-15: `DiagnosticEventApplication.kt`와 `DiagnosticEventObserverApplication.kt`는 `app-android/.../application/diagnostic/` 하위 package로 이동했다. application 루트 package 밀집을 해소하기 위한 첫 package 분리 실행이다.
- 2026-06-15: `EngineEffectLauncherApplication.kt`를 `application/engine/` 하위 package에 추가해 engine IO dispatcher 선택을 UI local helper에서 분리했다. 아직 dispatcher injection까지는 하지 않았지만, UI가 engine execution policy를 직접 소유하지 않도록 하는 첫 단계다.
- UI state mutation은 여전히 `GoCoachApp.kt`에 남아 있으므로, 이번 스파이크는 policy/result wrapper 이동 가능성만 확인한 것이다.

## 결론

- 즉시 이동 1순위: `EngineStartupApplication.kt`, `ScoreSyncCompletionApplication.kt`, `AutoAiCompletionApplication.kt`
- 조건부 이동: `UndoApplication.kt`, `HumanMoveApplication.kt`, `PositionAnalysisCacheOptimization.kt`, `ScoreSyncRunnerApplication.kt`
- display 문구 분리 후 이동: `ScoreDisplayApplication.kt`
- 얇은 formatter 이동 후보: `ScoreDisplayFormatterApplication.kt`
- UI reducer 경계 안정화 후 재평가: `GameSessionUiStateHolderApplication.kt`
- clock/client port 정리 후 이동: `EngineDeviceBenchmarkApplication.kt`, `EngineSessionLifecycleApplication.kt`

이번 스파이크로 실제 KMP 물리 이동 1건과 application 하위 package 분리 2건을 수행했다. 다음 배치에서는 `EngineOperationPolicy.kt` 또는 `ScoreSyncCompletionApplication.kt` 중 하나를 대상으로 두 번째 물리 이동 가능성을 검토하는 것이 가장 안전하다.

## ext.1 후속 결과 - EngineOperationPolicy shared 이동

- 2026-06-15 ext.1: `EngineOperationPolicy.kt`의 실제 구현을 `shared/commonMain`의 `com.worksoc.goaicoach.shared.engine` package로 이동했다. `EngineOperationGate`, `EngineOperationKind`, `EngineOperationRequest`, timeout/fallback policy, result guard, apply plan, stale-result evaluation, benchmark/search/scoring-rule gate 정책이 shared에 위치한다.
- 앱 쪽 `application/EngineOperationPolicy.kt`는 기존 application 호출부를 깨지 않기 위한 facade로 남겼다. 특히 Kotlin `typealias`만으로는 sealed class 중첩 타입(`EngineOperationResultGuard.Discard`, `EngineOperationGate.Allow` 등)을 기존 코드처럼 안정적으로 참조할 수 없어, facade 타입을 명시 정의하고 shared 결과를 앱 타입으로 매핑한다.
- 이 이동은 "엔진 코어/미들웨어 정책은 Android UI와 무관해야 한다"는 장기 목표에 부합한다. 로컬 엔진과 원격 엔진 모두 late result discard, session generation guard, timeout/fallback label을 같은 shared 정책으로 해석할 수 있다.
- `DiagnosticEventLogPort`와 `DiagnosticEventExternalSinkPort`는 `application/diagnostic/DiagnosticEventPorts.kt`로 이동했다. diagnostic 모델은 shared, diagnostic observer/port는 application diagnostic package, 실제 파일 persistence는 persistence package에 남아 레이어 위치가 더 명확해졌다.
- `GoCoachApp.kt`의 engine-backed new game 및 post-game cache optimization IO 실행도 `EngineEffectLauncherApplication.runEngineIo()`를 사용한다. EffectLauncher는 아직 Android app-service helper 수준이지만, UI가 dispatcher/engine IO 선택을 직접 소유하지 않는 방향으로 넓어지고 있다.

## ext.1 이후 남은 KMP 이동 과제

1. `application/EngineOperationPolicy.kt` facade 제거 또는 축소
   - 일부 호출부를 `shared.engine` 직접 import로 전환할 수 있다.
   - 다만 앱 전역에서 nested sealed 타입을 기존 package 이름으로 많이 참조하므로, 무리한 일괄 전환보다 package 단위 이동과 함께 줄이는 것이 안전하다.

2. `ScoreSyncCompletionApplication.kt` 또는 `AutoAiCompletionApplication.kt` 이동 검토
   - 순수 policy 성격은 강하지만 `EngineOperationResultGuard` 타입을 application facade로 참조한다.
   - 다음 이동 전에는 shared engine policy 타입을 직접 참조하도록 해당 파일을 먼저 정리하는 편이 낫다.

3. `EngineEffectLauncherApplication.kt`의 KMP 여부는 보류
   - 현재 `Dispatchers.IO`에 의존하므로 commonMain 이동 대상은 아니다.
   - 장기적으로는 dispatcher/clock/cancellation port를 주입하는 app-service adapter 또는 platform adapter로 보는 것이 정확하다.

## ext.2 후속 결과 - Top Moves package 분리와 EffectLauncher 확대

- 2026-06-15 ext.2: `TopMovesApplication.kt`를 `application/topmoves` 하위 package로 이동했다. Top Moves 관련 plan/result/runner가 application 루트 package에서 분리되어, 향후 `score`, `autoai`, `session` package 분리의 선례가 생겼다.
- Top Moves 파일은 여전히 `EngineSessionClient`, `GameSessionEffect`, `GameSessionControllerState`, cache model에 의존한다. 따라서 지금은 shared/common 이동 대상이 아니라 middleware/application 도메인 package 분리 단계로 보는 것이 맞다.
- 2026-06-15 ext.2: `GoCoachApp.kt`의 모든 직접 `withContext(Dispatchers.IO)`를 `runEngineIo()`로 대체했다. 이로써 UI 파일은 engine IO dispatcher를 직접 선택하지 않는다.
- 2026-06-15 ext.2: `DiagnosticEventExternalSinkApplication.kt`를 추가했다. diagnostic event 외부 전송은 shared export policy, application sink runner, future transport adapter의 세 구간으로 분리된다.
- `EngineOperationPolicy` facade는 아직 유지한다. 관련 타입 참조가 넓고 nested sealed 타입 호환 문제가 있어, facade 제거는 `topmoves`/`score` 같은 하위 package들이 shared engine 타입을 직접 쓰도록 단계적으로 바꾼 뒤 진행하는 것이 안전하다.

## ext.3 후속 결과 - Score package 분리와 shared engine 직접 참조

- 2026-06-15 ext.3: `ScoreDisplayApplication.kt`, `ScoreDisplayFormatterApplication.kt`, `ScoreEstimateRunnerApplication.kt`, `ScoreSyncCompletionApplication.kt`, `ScoreSyncRunnerApplication.kt`를 `application/score` 하위 package로 이동했다. score 관련 result, formatter, estimate runner, sync runner/completion이 application 루트 package에서 분리됐다.
- 새 `score` package는 `EngineOperationRequest`, `EngineOperationKind`, `EngineTimeoutPolicy`, `EngineFallbackPolicy`, `engineOperationRequest`를 `shared.engine`에서 직접 참조한다. 이는 `EngineOperationPolicy` facade 축소를 위한 첫 도메인 package 단위 vertical slice다.
- 단, `EngineOperationResultGuard`, `EngineOperationApplyPlan`, `buildEngineOperationApplyPlan`, `evaluateEngineOperationResultGuard`는 application facade를 유지한다. 해당 타입들은 nested sealed class와 app-side apply plan 매핑을 포함하므로, 무리하게 shared 타입으로 직접 바꾸면 기존 UI/application 호출부의 타입 안정성이 흔들릴 수 있다.
- 2026-06-15 ext.3: `EngineEffectLauncherApplication.kt`에 `launchUiEffect()`를 추가했다. 이 helper는 commonMain 이동 대상이 아니라 Android app-service launcher 경계다. 목적은 UI entry point가 coroutine launch primitive를 직접 흩뿌리지 않게 하고, 이후 startup/undo/autoAI/topmoves 목적별 effect runner로 분해할 안전한 접점을 만드는 것이다.
- `NoopDiagnosticEventExternalSink`도 추가했다. 실제 외부 transport가 준비되기 전에는 no-op adapter를 기본 구현으로 사용하고, 나중에 Firebase/HTTP/file export adapter를 같은 port 뒤에 붙일 수 있다.

## ext.3 이후 남은 KMP 이동 과제

1. `score` package 내부 shared engine 직접 참조 범위 확대
   - 현재 request/policy 값은 shared를 직접 쓰지만 result guard/apply plan은 application facade를 유지한다.
   - 다음 단계에서는 facade 유지 타입과 shared 직접 사용 타입의 adapter 경계를 별도 파일로 분리할 수 있다.

2. `autoai` package 분리
   - 자동 AI는 engine policy, level strategy, result guard, turn scheduling을 함께 사용한다.
   - shared engine 직접 참조 실험을 두 번째로 적용하기 좋은 도메인이다.

3. UI launcher의 KMP 여부는 계속 보류
   - `launchUiEffect()`는 coroutine scope/Job을 직접 다루므로 shared common policy가 아니라 Android app-service boundary로 둔다.
   - common으로 옮길 것은 launch primitive가 아니라 launch request/completion plan이다.
