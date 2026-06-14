# Orchestration Split/KMP 이동 맵 - 2026-06-15

작성일: 2026-06-15  
목적: `GoCoachApp.kt` orchestration 분리 후보와 middleware/KMP 이동 후보를 한 파일에 고정한다. 다음 리팩토링에서 줄 수 절감보다 workflow ownership 분리를 우선하기 위한 기준 문서다.

## 현재 지표

- `GoCoachApp.kt`: 2,211줄
- `AutoAiPolicyApplication.kt`: 243줄
- `AutoAiRunnerApplication.kt`: 268줄
- `AutoAiCompletionApplication.kt`: 161줄
- `ScoreDisplayApplication.kt`: 257줄
- `ScoreEstimateRunnerApplication.kt`: 119줄
- `ScoreSyncCompletionApplication.kt`: 101줄
- `TopMovesApplication.kt`: 437줄
- `DiagnosticEventApplication.kt`: 172줄
- `DiagnosticEventModel.kt`: 109줄
- `DiagnosticEventObserverApplication.kt`: 46줄

`GoCoachApp.kt` 줄 수는 아직 크다. 하지만 현재 가장 중요한 평가지표는 단순 줄 수가 아니라, UI 파일이 engine operation의 성공/실패/stale 정책을 직접 판단하는 비율이다.

## `GoCoachApp.kt` Orchestration Split 후보

### 1순위: Auto AI workflow runner

범위:

- `requestAiTurnForCurrentState()`
- AI turn schedule/delay validation
- AI turn success/failure completion
- auto AI pass/pass endgame resolve completion
- follow-up Top Moves request

이유:

- 현재 가장 긴 중첩 coroutine 흐름이다.
- engine operation lifecycle, runtime log, turn time, endgame, follow-up analysis가 한 곳에 모여 있다.
- 이번 배치에서 `AutoAiTurnCompletionPlan`과 `AutoAiEndgameCompletionPlan`이 들어갔으므로, 다음에는 runner helper로 옮길 준비가 됐다.

주의:

- Compose state mutation과 `runtimeEventLog.append()`가 많다.
- 한 번에 controller로 이동하지 말고, `AutoAiWorkflowRunnerInput`/`AutoAiWorkflowCallbacks` 같은 얇은 app-service helper부터 도입한다.

### 2순위: Score sync workflow runner

범위:

- `schedulePostUndoLocalEngineSync()`
- `changeScoringRule()`의 engine sync 구간
- `restoreSavedSession()`의 restored sync 구간

이유:

- 세 경로가 `runCatching + withContext + ScoreSyncCompletionPlan + follow-up Top Moves` 패턴을 공유한다.
- 이번 배치에서 `ScoreSyncCompletionPlan`이 들어갔으므로 중복 제거가 가능하다.

주의:

- post-undo는 quiet delay와 pending cancellation이 있어 scoring/restored sync와 완전히 같지 않다.
- 공통화 대상은 “엔진 sync 실행 후 completion 적용” 부분으로 제한한다.

### 3순위: Top Moves workflow runner

범위:

- `requestTopMoveAnalysisForState()`
- cache hit/engine run/failure/stale discard
- undo restore cache와 general analysis cache update

이유:

- 사용자 학습 경험에서 가장 많이 호출되는 경로다.
- 향후 coach engine 또는 remote analysis와 연결될 가능성이 크다.

주의:

- 분석 cache, undo restore cache, review snapshot이 같이 움직이므로 cache policy와 분리해서 다루면 안 된다.

### 4순위: Saved session/startup workflow

범위:

- engine startup
- saved session prompt
- restored game sync
- benchmark prompt

이유:

- 앱 생명주기와 사용자 prompt 우선순위가 얽힌다.
- 원격 엔진/서버 로그인/remote cache update가 붙으면 더 커질 수 있다.

주의:

- 사용자 체감 회귀가 큰 영역이므로 작은 state holder와 prompt policy부터 확장한다.

## Middleware/KMP 이동 후보

### 즉시 이동 후보

| 파일 | 현재 의존 | 판단 |
| --- | --- | --- |
| `EngineOperationPolicy.kt` | `shared.GameState`, `shared.Ruleset`, `analysisFingerprint` | KMP 이동 난도 낮음. operation metadata/guard 정책은 Android와 무관하다. |
| `EngineOperationResultApplication.kt` | `shared.GameState`, application runtime/diagnostic types | runtime log type이 application에 남아 있어 바로 shared 이동은 어렵지만 middleware application package로 분리 가능하다. |
| `PositionAnalysisGateway.kt` | shared DTO only | 이미 KMP-ready 계약으로 관리 중이다. |
| `RemotePositionAnalysisGateway.kt` | shared DTO only | 이미 KMP-ready 계약으로 관리 중이다. |

### 조건부 이동 후보

| 파일 | 제약 | 선행 작업 |
| --- | --- | --- |
| `DiagnosticEventModel.kt` | application model only | 2026-06-15 분리 완료. severity/event/export policy/sink plan은 coroutine 의존 없이 관리된다. |
| `DiagnosticEventApplication.kt` | diagnostic builder only | 2026-06-15 observer 분리 완료. event builder 파일로 축소됐다. |
| `DiagnosticEventObserverApplication.kt` | `kotlinx.coroutines.TimeoutCancellationException` 의존 | observer 책임만 보유한다. coroutine 정책이 허용되는 middleware/app-service 후보로 분류한다. |
| `ScoreDisplayApplication.kt` | `match.MatchMode`, `BoardScorer`, `ScoreTimeline` 의존 | score request/display/local/final score display 중심으로 축소됐다. |
| `ScoreEstimateRunnerApplication.kt` | engine session client 의존 | engine call orchestration만 보유한다. app-service runner 후보로 유지한다. |
| `ScoreSyncCompletionApplication.kt` | engine operation guard 의존 | sync result completion guard만 보유한다. KMP 이동 후보에 가깝다. |
| `AutoAiPolicyApplication.kt` | `match`, `shared` 의존 | auto AI request/schedule/execution context policy로 분리 완료. |
| `AutoAiRunnerApplication.kt` | engine session client, score display 의존 | auto AI engine call/display runner로 분리 완료. app-service runner 후보로 유지한다. |
| `AutoAiCompletionApplication.kt` | engine operation guard 의존 | auto AI result completion guard만 보유한다. KMP 이동 후보에 가깝다. |

### 이동 보류 후보

| 파일 | 이유 |
| --- | --- |
| `HttpRemotePositionAnalysisTransport.kt` | `java.net.HttpURLConnection`, `org.json` 의존. JVM/Android-bound transport detail로 유지한다. |
| Android persistence 구현체 | 파일 시스템과 Android context에 붙어 있으므로 port 구현체로 유지한다. |

## 다음 작업 제안

1. `GoCoachApp.kt` auto AI coroutine block에서 completion apply/endgame apply/follow-up request local helper를 추출한다.
2. Top Moves workflow의 engine run/failure/stale apply도 completion plan 패턴으로 정리한다.
3. Score sync workflow는 `ScoreSyncCompletionRequest`를 입력으로 받는 execution helper를 추가한다.
4. 새 application 파일들을 shared/KMP 또는 middleware module로 물리 이동할 후보를 다시 평가한다.
5. `GoCoachApp.kt` 줄 수보다 workflow ownership을 기준으로 UI-local helper를 계속 줄인다.

## 2026-06-15 리팩토링 반영 사항

- `DiagnosticEventModel.kt`를 추가해 `DiagnosticSeverity`, `DiagnosticEvent`, 외부 export 판단 정책, `DiagnosticEventExternalSinkPlan`을 coroutine observer에서 분리했다.
- `AutoAiCompletionApplication.kt`를 추가해 auto AI turn/endgame operation token, stale guard, completion plan을 `GameAutomationApplication.kt`에서 분리했다.
- `ScoreSyncCompletionRequest`를 추가해 score sync success/failure completion 입력을 하나의 request object로 묶었다. 다음 score sync runner 추출 시 이 객체가 application 경계 입력이 된다.
- test fake `DiagnosticEventExternalSinkPort`를 추가해 사용자 동의 기반 외부 전송 경로를 실제 transport 없이 검증할 수 있게 했다.

## 2026-06-15 4차 리팩토링 반영 사항

- `DiagnosticEventObserverApplication.kt`를 추가해 `runObservedEngineOperation()`과 `NoopDiagnosticEventLog`를 diagnostic event builder에서 분리했다.
- `GameAutomationApplication.kt`를 제거하고 `AutoAiPolicyApplication.kt`, `AutoAiRunnerApplication.kt`, `AutoAiCompletionApplication.kt` 3개 책임 파일로 재구성했다.
- `ScoreSyncCompletionApplication.kt`와 `ScoreEstimateRunnerApplication.kt`를 추가해 score sync completion guard와 score estimate/scoring/restored runner를 `ScoreDisplayApplication.kt`에서 분리했다.
- `TopMoveAnalysisLaunchRequest`를 추가해 Top Moves launch 판단 입력을 하나의 request object로 묶었다. 아직 engine run workflow는 UI에 남아 있지만 다음 runner 추출의 입력 경계가 생겼다.
- `LayeringContractTest`에 신규 application 파일들을 추가해 Android/UI/persistence/engine runtime 의존이 들어오지 못하도록 고정했다.
