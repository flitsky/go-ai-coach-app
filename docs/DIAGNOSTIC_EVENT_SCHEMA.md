# Diagnostic Event Schema

이 문서는 `diagnostic_events.jsonl`에 기록되는 구조화 진단 이벤트의 최소 스키마를 정리한다.
목표는 앱을 모르는 개발자도 로그만 보고 어떤 엔진/대국 상태에서 병목, 타임아웃, 폐기, 계가 불일치가 발생했는지 추적할 수 있게 하는 것이다.

## 기본 Envelope

각 줄은 하나의 JSON event다. 현재 Android 저장소 구현은 append-only JSONL 파일로 관리한다.

공통 필드:

- `createdAtMillis`: 이벤트 생성 시각.
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

엔진 최종 계가와 local score가 불일치할 때 기록한다.

필수 context:

- `engineFinalScore`
- `localScore`
- `source`

해석:

- dead-stone cleanup, ruleset, komi, local scorer 전제 차이를 우선 확인한다.
- 사용자가 향후 "이의 제기" 또는 오류 전송을 선택하는 경우 최우선 수집 대상이다.

## 확장 예정

- 원격 엔진 전환 시 `backendId=remote-server`, transport status, HTTP status, retry count를 추가한다.
- MQ/Firebase/Sentry 전송 adapter는 이 문서의 `code`와 필수 context를 그대로 유지한다.
- operation-id busy stack은 UI runtime log에 연결됐다. JSONL diagnostic으로도 started/completed를 남길지는 로그량과 운영 분석 필요성을 보고 별도 결정한다.
