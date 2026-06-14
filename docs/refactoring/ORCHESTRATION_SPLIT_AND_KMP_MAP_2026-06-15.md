# Orchestration Split/KMP 이동 맵 - 2026-06-15

작성일: 2026-06-15  
목적: `GoCoachApp.kt` orchestration 분리 후보와 middleware/KMP 이동 후보를 한 파일에 고정한다. 다음 리팩토링에서 줄 수 절감보다 workflow ownership 분리를 우선하기 위한 기준 문서다.

## 현재 지표

- `GoCoachApp.kt`: 2,211줄
- `GameAutomationApplication.kt`: 503줄
- `AutoAiCompletionApplication.kt`: 161줄
- `ScoreDisplayApplication.kt`: 469줄
- `DiagnosticEventApplication.kt`: 216줄
- `DiagnosticEventModel.kt`: 109줄

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
| `DiagnosticEventApplication.kt` | `kotlinx.coroutines.TimeoutCancellationException` 의존 | observer와 diagnostic event builder만 남았다. 다음 단계에서 observer만 별도 runner 파일로 분리 가능하다. |
| `ScoreDisplayApplication.kt` | `match.MatchMode`, `BoardScorer`, `ScoreTimeline` 의존 | score display model과 engine runner를 분리한다. runner는 app-service, display math는 shared/middleware 후보. |
| `GameAutomationApplication.kt` | `match`, `shared`, score display, engine runner 의존이 섞임 | completion plan은 `AutoAiCompletionApplication.kt`로 분리 완료. 남은 request/schedule/display runner를 추가 분리한다. |

### 이동 보류 후보

| 파일 | 이유 |
| --- | --- |
| `HttpRemotePositionAnalysisTransport.kt` | `java.net.HttpURLConnection`, `org.json` 의존. JVM/Android-bound transport detail로 유지한다. |
| Android persistence 구현체 | 파일 시스템과 Android context에 붙어 있으므로 port 구현체로 유지한다. |

## 다음 작업 제안

1. `GameAutomationApplication.kt`를 `AutoAiTurnPolicy`와 `AutoAiTurnRunner` 성격으로 추가 분리한다.
2. `DiagnosticEventApplication.kt`에서 coroutine observer(`runObservedEngineOperation`, noop log)를 별도 파일로 분리한다.
3. `ScoreDisplayApplication.kt`에서 score sync completion plan과 runner effect를 분리한다.
4. `GoCoachApp.kt`에 남은 workflow별 local helper를 `ui/workflow` 또는 application app-service helper로 이동할 후보를 확정한다.
5. 위 분리 후 KMP 이동 후보 파일에 architecture test를 추가한다.

## 2026-06-15 리팩토링 반영 사항

- `DiagnosticEventModel.kt`를 추가해 `DiagnosticSeverity`, `DiagnosticEvent`, 외부 export 판단 정책, `DiagnosticEventExternalSinkPlan`을 coroutine observer에서 분리했다.
- `AutoAiCompletionApplication.kt`를 추가해 auto AI turn/endgame operation token, stale guard, completion plan을 `GameAutomationApplication.kt`에서 분리했다.
- `ScoreSyncCompletionRequest`를 추가해 score sync success/failure completion 입력을 하나의 request object로 묶었다. 다음 score sync runner 추출 시 이 객체가 application 경계 입력이 된다.
- test fake `DiagnosticEventExternalSinkPort`를 추가해 사용자 동의 기반 외부 전송 경로를 실제 transport 없이 검증할 수 있게 했다.
