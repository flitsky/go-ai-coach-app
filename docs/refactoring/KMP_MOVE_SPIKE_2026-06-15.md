# KMP 이동 1차 스파이크 - 2026-06-15

## 목적

application 계층의 순수 정책/결과 포장 파일을 향후 `shared` 또는 별도 KMP middleware 모듈로 이동할 수 있는지 점검한다. 이번 스파이크는 실제 파일 이동보다, 이동 후보가 Android/UI/persistence/runtime 구현에 묶이지 않도록 자동화 계약을 먼저 확장하는 데 초점을 둔다.

## 이번에 자동화 계약에 추가한 후보

- `EngineDeviceBenchmarkApplication.kt`
- `EngineSessionLifecycleApplication.kt`
- `EngineStartupApplication.kt`
- `HumanMoveApplication.kt`
- `PositionAnalysisCacheOptimization.kt`

기존 후보였던 `AutoAiCompletionApplication.kt`, `AutoAiPolicyApplication.kt`, `ScoreSyncCompletionApplication.kt`, `TopMovesApplication.kt` 등과 함께 `LayeringContractTest.engineOperationApplicationPoliciesStayPortable` 대상에 포함했다.

## 확인한 제약

- 위 파일들은 현재 Android/Compose/persistence/engine runtime 구현 import 없이 컴파일된다.
- 다만 일부 파일은 `EngineSessionClient` 또는 `EngineCoreApi`를 직접 확장 함수로 참조한다. 이는 KMP 이동 자체를 막지는 않지만, 실제 모듈 이동 시 해당 client port도 함께 KMP 모듈로 이동하거나 interface-only port로 재정의해야 한다.
- `EngineDeviceBenchmarkApplication.kt`는 `System.nanoTime()`을 사용한다. Kotlin/JVM에서는 문제가 없지만 commonMain 이동 시 clock port가 필요하다.
- UI state mutation은 여전히 `GoCoachApp.kt`에 남아 있으므로, 이번 스파이크는 policy/result wrapper 이동 가능성만 확인한 것이다.

## 결론

- 즉시 이동 1순위: `EngineStartupApplication.kt`, `ScoreSyncCompletionApplication.kt`, `AutoAiCompletionApplication.kt`
- 조건부 이동: `HumanMoveApplication.kt`, `PositionAnalysisCacheOptimization.kt`
- clock/client port 정리 후 이동: `EngineDeviceBenchmarkApplication.kt`, `EngineSessionLifecycleApplication.kt`

이번 배치에서는 실제 파일 이동 대신 자동화 계약을 확장했다. 다음 배치에서 `EngineStartupApplication.kt` 또는 `ScoreSyncCompletionApplication.kt`를 작은 단위로 `shared` 후보 패키지에 이동하는 것이 가장 안전하다.
