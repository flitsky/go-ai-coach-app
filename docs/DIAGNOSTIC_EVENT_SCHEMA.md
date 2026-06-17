# Diagnostic Event Schema

이 문서는 `diagnostic_events.jsonl`에 기록되는 구조화 진단 이벤트의 최소 스키마를 정리한다.
목표는 앱을 모르는 개발자도 로그만 보고 어떤 엔진/대국 상태에서 병목, 타임아웃, 폐기, 계가 불일치가 발생했는지 추적할 수 있게 하는 것이다.

## 기본 Envelope

각 줄은 하나의 JSON event다. 현재 Android 저장소 구현은 append-only JSONL 파일로 관리한다.

공통 필드 (실제 코드 기준, `persistence/DiagnosticEventLog.kt`의 `DiagnosticEventLogCodec.encodeLine()`):

- `t`: 이벤트 생성 시각(epoch millis). **주의**: 사용자 동의 후 외부 전송용 payload를 만드는 `LocalFileDiagnosticEventExternalSink`(아래 "Local Export Adapter" 섹션)만 별도로 `createdAtMillis`라는 다른 키를 쓴다. 로컬 `diagnostic_events.jsonl` 본선 로그는 `t`이고, export adapter의 출력만 `createdAtMillis`다. 두 writer가 같은 의미의 필드에 다른 키를 쓰는 상태이므로, 로그를 파싱할 때는 어느 파일인지 먼저 확인해야 한다.
- `severity`: `info`, `warning`, `critical`.
- `code`: 이벤트 종류. 예: `engine.operation.slow`.
- `message`: 사람이 읽는 한 줄 설명.
- `context`: 이벤트별 key-value map. 값은 문자열이다.

운영 원칙:

- `code`는 안정적인 분석 키로 취급한다. 문구 변경은 가능하지만 `code` 의미는 쉽게 바꾸지 않는다.
- `context` key는 추가 가능하지만 기존 key의 의미를 바꾸면 안 된다.
- engine-facing operation은 가능하면 `EngineOperationRequest`에서 나온 `operation`, `operationId`, `sessionGeneration`, `positionFingerprint`, `moveCount`, `backendId`, `timeoutPolicy`, `fallbackPolicy`를 포함한다.

## 저장과 외부 수집 정책

현재 앱은 `DiagnosticEventLogPort`를 통해 로컬 JSONL 파일에 diagnostic event를 저장한다. 이 port는 “로컬 보존” 책임만 가진다.

향후 Firebase, MQ, 자체 서버, Sentry 같은 외부 수집 채널을 붙일 때는 같은 event schema를 재사용하되, 다음 원칙을 따른다.

- 외부 전송은 별도 sink/transport port로 분리한다. 로컬 JSONL 저장 구현에 네트워크 전송을 섞지 않는다.
- 사용자가 직접 오류 전송에 동의한 경우에는 최근 diagnostic event와 debug report를 함께 묶어 보낸다.
- warning/critical 자동 수집을 도입하더라도 개인정보와 기보 전송 범위를 명확히 고지한다.
- 정상 lifecycle event(`engine_operation_started`, `engine_operation_completed`)는 기본 외부 전송 대상이 아니다. slow/timeout/discarded/final disagreement처럼 분석 가치가 높은 이벤트부터 수집한다.
- 외부 전송 실패는 대국 흐름을 막지 않는다. 전송 실패 자체는 별도 local warning으로 남길 수 있지만, 재귀적으로 무한 전송하지 않는다.

현재 코드 기준 외부 전송 후보 판단은 `planDiagnosticEventExternalExport()`가 담당한다.

- `info`: `LocalOnly`. 로컬 JSONL과 사용자가 직접 복사한 debug report에만 포함한다.
- `warning`: `EligibleForUserConsentExport`. 성능/품질 분석 목적으로 사용자 동의 후 전송할 수 있다.
- `critical`: `EligibleForUserConsentExport`. 계가 불일치나 timeout처럼 정확성 문제가 의심되는 경우 사용자 동의 후 전송할 수 있다.

이 정책은 외부 네트워크 transport가 붙기 전에도 테스트 가능한 순수 application 정책으로 유지한다. `buildDiagnosticEventExternalSinkPlan()`은 `userConsented=true`일 때만 `DiagnosticEventExternalExportPayload`를 만들며, 실제 전송은 `DiagnosticEventExternalSinkPort` 구현체가 담당한다.

## Engine Operation Events

참고: `engine_operation_started`, `engine_operation_completed`는 현재 `diagnostic_events.jsonl`이 아니라 runtime event log에 남긴다. 이 두 이벤트는 정상 흐름에서도 매우 자주 발생하므로 운영 진단 JSONL에는 slow/timeout/discarded처럼 분석 가치가 높은 이벤트만 구조화해 저장한다.

### `engine.operation.slow`

엔진 operation이 기대 latency threshold를 초과했지만 실패하지는 않았을 때 기록한다.

필수 context:

- `operation`: `EngineOperationKind.code`.
- `operationId`: operation 고유 id.
- `sessionGeneration`: 요청 시점의 match/session generation.
- `positionFingerprint`: 요청 대상 board fingerprint.
- `moveCount`: 요청 대상 move count.
- `backendId`: `local-engine`, `remote-server` 등.
- `timeoutPolicy`: `cap:2000ms`, `uncapped` 등.
- `fallbackPolicy`: `none`, `local-rules`, `cached-analysis` 등.
- `elapsedMillis`: 실제 소요 시간.
- `thresholdMillis`: slow로 판단한 기준 시간.

해석:

- 단건 slow는 사용자 기기 부하일 수 있다.
- 같은 `operation`/`backendId`에서 반복되면 time cap, JSON analysis 범위, 엔진 process reuse 정책을 검토한다.

### `engine.operation.timeout`

엔진 operation이 coroutine timeout으로 중단됐을 때 기록한다.

필수 context:

- `operation`
- `operationId`
- `sessionGeneration`
- `positionFingerprint`
- `moveCount`
- `backendId`
- `timeoutPolicy`
- `fallbackPolicy`
- `timeoutMillis`

해석:

- `fallbackPolicy=local-rules`면 화면에는 local rules 기반 결과가 대체 표시될 수 있다.
- `fallbackPolicy=none`에서 반복되면 사용자 흐름이 막힐 가능성이 크므로 critical로 본다.

### `engine.operation.discarded`

늦게 도착한 엔진 결과가 현재 match/session 상태와 맞지 않아 화면에 반영되지 않았을 때 기록한다.

필수 context:

- `reason`: 폐기 이유.
- `currentMoveCount`: 결과 도착 시점의 현재 move count.
- `positionFingerprint`: 결과 도착 시점의 현재 board fingerprint.

권장 context:

- `operation`
- `operationId`
- `sessionGeneration`

해석:

- 새 게임, 저장 복원, 무르기 직후에는 정상적으로 발생할 수 있다.
- 특정 flow에서 반복되면 UI가 오래된 coroutine을 취소하지 못하거나 operation generation 연결이 빠진 것이다.

## Analysis Quality Events

### `engine.visit_fill_short`

엔진이 요청 visits보다 낮은 root visits로 분석을 끝냈을 때 기록한다.

필수 context:

- `requestedVisits`
- `rootVisits`
- `searchMode`
- `positionFingerprint`

해석:

- 모바일에서 자주 발생하면 time cap이 너무 짧거나 JSON candidate 범위가 과하다.
- AI 레벨링 품질 검증에는 `requestedVisits` 대비 `rootVisits` fill ratio를 함께 봐야 한다.

### `engine.visit_fill_unknown`

엔진 응답에 root visits 정보가 없을 때 기록한다.

필수 context:

- `requestedVisits`
- `searchMode`
- `positionFingerprint`

해석:

- parser 또는 engine backend가 root visits를 제공하지 않는 경우다.
- 레벨링 공정성 검증에서는 신뢰도가 낮은 샘플로 분류한다.

## Score Events

### `score.final_disagreement`

엔진 최종 계가와 local score가 불일치할 때 기록하도록 설계된 이벤트다.

필수 context (설계상):

- `engineFinalScore`
- `localScore`
- `source`

**현재 상태(2026-06-17): 죽은 코드다.** `scoreDisagreementDiagnosticEvent()`(`application/diagnostic/DiagnosticEventApplication.kt`)가 정의되어 있지만 호출하는 곳이 코드베이스 어디에도 없다. 종국 판정(`deadStones`/`scoreFinal` 부심·주심 판정)에서 점수 불일치를 실제로 비교하는 로직이 아직 이 이벤트를 발행하는 지점까지 연결되지 않았다.

해석(연결되면):

- dead-stone cleanup, ruleset, komi, local scorer 전제 차이를 우선 확인한다.
- 사용자가 향후 "이의 제기" 또는 오류 전송을 선택하는 경우 최우선 수집 대상이다.

## Runtime Event Log — 별도 시스템

`diagnostic_events.jsonl`과 별개로, 앱은 `runtime_events.log`에 대국 흐름 전체를 narrative 형태로 계속 기록한다. 이 둘은 목적과 형식이 다르다.

| | `diagnostic_events.jsonl` | `runtime_events.log` |
| --- | --- | --- |
| 목적 | slow/timeout/discarded/fill-short처럼 분석 가치가 높은 이상 신호만 | 정상 흐름을 포함한 모든 주요 전환을 순서대로 기록 |
| 형식 | JSON, 한 줄에 하나의 event 객체 | 평문 `key=value` 공백 구분, 한 줄에 하나의 event |
| 코드 | `application/diagnostic/DiagnosticEventApplication.kt`, writer는 `persistence/DiagnosticEventLog.kt` | `application/runtime/RuntimeEventApplication.kt`, writer는 `persistence/RuntimeEventLog.kt` |
| 빈도 | 이상 상황에서만 | 거의 모든 턴/엔진 호출마다 |

`RuntimeLogContext.event()`가 만드는 한 줄의 형태는 다음과 같다(필드는 고정 순서):

```text
event=ai_turn_begin phase=ai_turn app="Go AI Coach" purpose="..." mode=AiVsHuman setup="..." board="..." engine=KataGo engineReady=true engineBusy=true runtime="..." analysis="topMoves=true cache=... coverage=..." score="..." turnTime="Time B 12.3s / W 8.1s" flags="..." transition=await_human_move detail="..."
```

`event` 필드가 이벤트 종류(아래 20개), `phase`가 상위 단계 분류, `transition`이 다음 예상 상태, `detail`이 자유 설명이다. 나머지는 매 이벤트마다 공통으로 찍히는 현재 스냅샷(보드, 엔진 상태, 분석 캐시 통계, 플래그)이다.

### 현재 정의된 20개 runtime event

| event | phase | 의미 |
| --- | --- | --- |
| `app_start` | startup | 앱 프로세스 시작 |
| `game_reset` | game_setup | 새 대국 시작 |
| `engine_game_start_request` | game_setup | 엔진에 새 게임 시작 요청 |
| `engine_game_start_success` | game_setup | 엔진 새 게임 시작 성공 |
| `engine_game_start_failure` | game_setup | 엔진 새 게임 시작 실패 |
| `auto_play_delay_change` | settings | 자동대국 딜레이 변경 |
| `ai_turn_schedule` | ai_turn | AI 턴 예약 |
| `ai_turn_schedule_cancelled` | ai_turn | 예약된 AI 턴 취소 |
| `ai_turn_begin` | ai_turn | AI 턴 분석/착수 시작 |
| `ai_turn_success` | ai_turn | AI 턴 착수 성공 |
| `ai_turn_endgame_detected` | ai_turn | AI 턴 중 종국 조건 감지 |
| `ai_turn_endgame_success` | ai_turn | 종국 처리 성공 |
| `ai_turn_endgame_failure` | ai_turn | 종국 처리 실패 |
| `ai_turn_failure` | ai_turn | AI 턴 실패 |
| `ai_turn_complete` | ai_turn | AI 턴 종료(성공/실패 무관 마무리) |
| `engine_operation_started` | engine_operation | 엔진 operation 시작 (참고: 위 "Engine Operation Events" 섹션의 slow/timeout/discarded와 짝이지만, started/completed 자체는 diagnostic JSONL이 아니라 여기 남는다) |
| `engine_operation_completed` | engine_operation | 엔진 operation 완료 |
| `engine_operation_discarded` | engine_operation | 늦게 도착한 결과 폐기 |
| `human_move_accepted` | human_move | 사람 착수 로컬 반영 |
| `human_engine_sync_success` | human_move | 사람 착수의 엔진 동기화 성공 |
| `human_engine_sync_failure` | human_move | 사람 착수의 엔진 동기화 실패 |

이 표는 `RuntimeEventApplication.kt`의 함수 목록과 1:1로 대응한다. 새 runtime event를 추가하면 이 표도 같이 갱신한다.

## 확장 예정

- 원격 엔진 전환 시 `backendId=remote-server`, transport status, HTTP status, retry count를 추가한다.
- MQ/Firebase/Sentry 전송 adapter는 이 문서의 `code`와 필수 context를 그대로 유지한다.
- operation-id busy stack은 UI runtime log에 연결됐다. JSONL diagnostic으로도 started/completed를 남길지는 로그량과 운영 분석 필요성을 보고 별도 결정한다.

## Local Export Adapter

2026-06-15 기준 `LocalFileDiagnosticEventExternalSink`가 추가됐다.

- 역할: 사용자 동의 후 warning/critical diagnostic export payload를 로컬 JSONL 파일로 저장하는 Android/JVM-bound adapter.
- 위치: `application/diagnostic`의 `DiagnosticEventExternalSinkPort` 뒤에 붙는 platform adapter다. shared/common 이동 후보가 아니다.
- 용도: Firebase/Sentry/MQ 같은 원격 수집 전 단계에서 payload shape를 검증하고, 개발자가 재현 로그를 파일로 회수할 수 있게 하는 것이다.
- 정책: 기본 UX에 즉시 노출하지 않는다. debug report copy와 중복되는 영역이 있으므로, ext.7 이후 "로그 내보내기" 또는 개발자-only 메뉴로 연결할지 별도 결정한다.
