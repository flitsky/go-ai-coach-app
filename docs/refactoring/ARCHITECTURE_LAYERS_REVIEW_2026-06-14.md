# 7계층 아키텍처 정제 검토

작성일: 2026-06-14  
성격: `ARCHITECTURE_LAYERS_ANALYSIS.md` 초안에 대한 비판적 정제 검토본  
관점: Android-first local AI Go coaching app에서 출발하되, 장기적으로 원격 엔진, 공식 캐시, 멀티플랫폼 UI, AI Agent 병렬 개발까지 수용 가능한 플랫폼 구조

## 결론

7계층 방향은 채택한다. 다만 현재 구현 상태를 “완성된 구조”처럼 표현하면 위험하다. 이 문서는 다음 결론을 공식 검토 기록으로 남긴다.

1. `Presentation -> App Service -> Game Domain -> Middleware / Cache -> Core Rules -> Engine Core API -> Runtime / Transport` 계층은 장기 목표로 적절하다.
2. `EngineCoreApi`는 원시 엔진 기능을 1:1로 여는 계약으로 유지하고, 제품 정책은 `EngineSessionClient`와 application middleware에서 감싼다.
3. 엔진과 미들웨어는 장기적으로 서버-투-서버 통신처럼 실패 가능하고 지연 가능한 경계로 취급한다.
4. 현재 `GameSessionController`는 완전한 SSOT/effect runner가 아니라 controller snapshot/effect type 도입 단계다.
5. 자동 AI 턴, 종국 resolve, prompt priority, 일부 persistence/diagnostic 실행 흐름은 아직 UI 파일에 남아 있으므로 다음 리팩토링 대상이다.
6. 대규모 플랫폼과 AI Agent 병렬 개발을 위해서는 “레이어 명명”보다 “실제 의존 방향, 실패 모델, 관측 가능성, 테스트 경계”가 더 중요하다.

## 원문 초안에서 수용할 부분

### 1. 7계층 모델

다음 계층 구분은 유지한다.

```text
Presentation / Game UX
  -> App Service / Session Orchestration
  -> Game Domain
  -> Middleware / Cache Domain
  -> Core Rules Domain
  -> Engine Core API
  -> Engine Runtime / Transport
```

이 구조는 관심사 분리에 유효하다. 특히 AI Agent들이 서로 다른 영역을 병렬로 작업할 때 충돌을 줄이는 데 도움이 된다.

예시:

- UI Agent: Compose layout, `GameScreenState`, board rendering
- Game Agent: seat/referee/AI level policy
- Engine Agent: KataGo process/JNI/remote transport
- Middleware Agent: engine orchestration, cache, endgame policy
- Diagnostic Agent: structured event, slow operation, warning/critical log

### 2. Engine Core API와 Middleware 분리

`EngineCoreApi`를 raw primitive로 두고, `EngineSessionClient`가 제품 유스케이스를 조합하는 방향은 맞다.

수용해야 할 원칙:

- `EngineCoreApi`는 `newGame`, `playMove`, `syncToGameState`, `analyze`, `estimateScore`, `deadStones`, `scoreFinal`, `clearSearchCache`, `stop` 같은 엔진 원시 기능을 제공한다.
- `EngineCoreApi`는 AI 캐릭터, UI 색상, prompt, cache origin, 사용자 설정 저장을 알지 않는다.
- `EngineSessionClient`는 local process, JNI, remote server 차이를 숨기는 middleware gateway다.
- UI는 원시 엔진 명령을 직접 호출하지 않는다.

이 방향은 원격 서버 도입 가능성을 열어 준다. 나중에 remote engine이 들어와도 상위 UX는 같은 middleware API를 호출해야 한다.

### 3. Cache Domain 독립

`PositionAnalysisCacheResolver`처럼 cache 품질을 별도 정책으로 보는 방향은 수용한다.

장기적으로 cache는 단순 성능 최적화가 아니라 제품 품질 계층이다.

- `bundled-trusted`: 앱에 포함된 공식 opening/cache
- `operator-trusted`: 운영자가 원격 배포하는 검증 cache
- `local-user`: 사용자 기기에서 플레이 중 얻은 cache
- `peer-shared`: 원격 사용자 간 공유 후보 cache

현재 정책은 `exact-hit` 중심으로 시작하되, 장기적으로 `compatible-hit`을 설계한다. 단, compatible-hit은 자동 착수에 바로 쓰기보다 신뢰 등급과 목적별 허용 범위를 분리해야 한다.

### 4. Seat / Referee / AI Policy 분리

흑/백 seat, player/AI/remote controller, AI level policy를 Game Domain으로 분리하는 방향은 맞다.

이 구조가 중요한 이유:

- 사람 vs AI, AI vs AI, 사람 vs 사람, 원격 유저 대국을 같은 match model로 확장할 수 있다.
- AI 난이도 고도화가 UI나 engine transport를 직접 건드리지 않게 된다.
- 학습용 캐릭터, 실험용 policy, 운영자 추천 policy를 독립적으로 테스트할 수 있다.

## 원문 초안의 위험한 부분

### 1. 현재 구현 상태를 과장한다

원문은 `GameSessionController`가 “코어 상태전이 및 Effect 실행 관리를 전담”한다고 표현한다. 현재 코드 기준으로 이는 아직 사실이 아니다.

현재 상태:

- `GameSessionControllerState`는 여러 state holder를 묶는 snapshot에 가깝다.
- `GameSessionEffect` sealed type은 도입되어 있다.
- 그러나 자동 AI 턴 coroutine, endgame resolve coroutine, 일부 prompt/persistence/diagnostic 실행은 여전히 `GoCoachApp.kt`에 남아 있다.

따라서 표현은 다음처럼 바꿔야 한다.

```text
현재 GameSessionController는 controller/effect runner 완성체가 아니라,
UI orchestration을 application 계층으로 옮기기 위한 중간 snapshot/effect contract다.
```

### 2. “원천 차단”, “보장” 같은 표현은 부정확하다

GTP process는 stateful이고, process-global `maxTime`, late response, timeout, stream 오염, search tree reuse 같은 문제가 있다.

따라서 다음 표현은 피한다.

- 상태 불일치 원천 차단
- 최적 속도 보장
- UI 정지 방지 보장
- 이펙트 러너로 오작동 차단

대신 다음처럼 쓴다.

- 상태 불일치 위험을 줄인다.
- session generation, board fingerprint, replay, structured log로 검출 가능하게 한다.
- timeout 발생 시 process restart 또는 재동기화 정책이 필요하다.
- 현재 구현은 완성형이 아니라 전환 중이다.

### 3. failover 모델이 부족하다

대규모 플랫폼으로 가려면 engine middleware는 로컬 함수 호출이 아니라 실패 가능한 외부 서비스 호출처럼 설계해야 한다.

필요한 개념:

- request id
- operation id
- match/session generation id
- backend id: local-process, jni, remote-server
- timeout policy
- retry policy
- fallback policy
- circuit breaker
- stale result discard
- idempotency key

예를 들어 `resolveEndgameForState()`가 늦게 끝났는데 사용자가 새 게임을 시작했다면, 결과를 UI에 적용하면 안 된다. 단순 `suspend fun` 성공/실패만으로는 부족하다.

### 4. Diagnostic이 아직 운영 플랫폼 수준은 아니다

현재 runtime event log와 debug report는 개발 단계에서는 유용하다. 그러나 운영/플랫폼 관점에서는 구조화 이벤트가 더 필요하다.

필요한 이벤트 예:

- `engine.operation.started`
- `engine.operation.slow`
- `engine.operation.timeout`
- `engine.operation.failed`
- `engine.fallback.used`
- `engine.cache.hit`
- `engine.cache.miss`
- `engine.cache.compatible_hit`
- `engine.result.disagreement`
- `endgame.assistant_judge.completed`
- `endgame.chief_judge.disagreement`

각 이벤트에는 최소 다음 필드가 있어야 한다.

- `operationId`
- `sessionGeneration`
- `boardFingerprint`
- `engineBackend`
- `searchMode`
- `requestedVisits`
- `rootVisits`
- `timeCapMs`
- `elapsedMs`
- `cacheHitType`
- `severity`

문자열 로그는 사람이 읽기에 좋지만, 운영 분석과 자동 리포팅에는 구조화 데이터가 필요하다.

## 계층별 정제 평가

| 계층 | 원문 방향 | 현재 평가 | 보강 필요 |
| --- | --- | --- | --- |
| Engine Runtime / Transport | 적절함 | local process 중심으로 잘 분리 중 | process lifecycle, timeout, late response, restart 정책 |
| Engine Core API | 적절함 | raw primitive 계약이 있음 | operation metadata, cancellation/failover semantics |
| Core Rules Domain | 적절함 | KMP 순수 모델이 강점 | ruleset 확장, dead-stone marking 협상 UI와 경계 |
| Middleware / Cache Domain | 매우 중요 | `EngineSessionClient`, cache resolver 존재 | remote backend, compatible cache, structured diagnostic |
| Game Domain | 적절함 | seat/referee/AI policy 분리 중 | remote user, clock, match authority, adjudication state |
| App Service / Orchestration | 방향은 맞음 | 아직 전환 중 | effect runner, generation discard, prompt priority |
| Presentation / Game UX | 방향은 맞음 | UI가 아직 orchestration 일부 보유 | rendering/event dispatch 중심으로 더 축소 |

## 우리가 채택할 설계 원칙

### 1. Local-first, Hybrid-ready

기본 대국은 로컬 엔진으로 빠르게 동작해야 한다. 그러나 정밀 분석, 공식 cache, remote match, 저사양 기기 보완은 같은 상위 API 뒤에서 선택적으로 붙인다.

### 2. Engine boundary는 서버 호출처럼 다룬다

로컬 process라도 다음을 전제로 둔다.

- 느릴 수 있다.
- 실패할 수 있다.
- 늦은 응답이 올 수 있다.
- 이전 요청 상태가 남아 있을 수 있다.
- restart가 필요할 수 있다.

이 전제를 받아들여야 원격 서버로 확장할 때 구조가 덜 흔들린다.

### 3. UI는 engine operation lifecycle을 직접 알지 않는다

UI는 가능하면 다음만 담당한다.

- 화면 렌더링
- 사용자 이벤트 전달
- effect 실행 결과 표시

엔진 operation plan, retry/fallback, timeout, diagnostic logging, stale result discard는 App Service / Middleware 쪽으로 이동해야 한다.

### 4. AI Agent 업무 분담은 도메인 경계와 테스트로 보호한다

AI Agent들이 동시에 작업할 수 있게 하려면 파일 위치보다 더 중요한 것이 있다.

- public/internal contract가 명확해야 한다.
- 각 도메인의 테스트가 있어야 한다.
- 다른 도메인의 구현 세부를 직접 만지지 않아야 한다.
- DTO와 effect plan은 작고 불변이어야 한다.

## 우선 적용할 리팩토링 방향

### 1순위: Engine Operation 모델

엔진 호출을 다음 공통 모델로 감싼다.

```kotlin
data class EngineOperationRequest(
    val operationId: String,
    val sessionGeneration: Long,
    val boardFingerprint: String,
    val kind: EngineOperationKind,
    val timeoutPolicy: EngineTimeoutPolicy,
    val fallbackPolicy: EngineFallbackPolicy,
)
```

첫 구현은 거창할 필요 없다. 최소한 자동 AI 턴과 종국 resolve에 operation id와 generation을 붙이는 것부터 시작한다.

### 2순위: ResolveAutoAiEndgame effect runner 분리

현재 `AutoAiTurnEndgamePlan`과 `GameSessionEffect.ResolveAutoAiEndgame`은 도입되었다. 다음 단계는 실제 coroutine 실행을 UI에서 빼는 것이다.

목표:

- UI는 effect를 실행하거나 dispatcher에 전달한다.
- endgame resolve 성공/실패는 application reducer로 반영한다.
- session generation이 달라졌으면 결과를 폐기한다.

### 3순위: Structured diagnostic event 강화

엔진 지연/실패를 개선 포인트로 삼으려면 로그가 데이터화되어야 한다.

우선 추가할 event:

- `engine.operation.slow`
- `engine.operation.timeout`
- `engine.fallback.used`
- `engine.cache.compatible_hit`
- `endgame.judge.disagreement`

### 4순위: Middleware module boundary 정리

현재 middleware 일부는 `app-android/application`에 있고 일부는 `app-android/middleware`에 있다. 장기적으로는 다음 중 하나를 선택한다.

- `app-android` 내부에서 package만 정리한다.
- `shared` 또는 별도 KMP middleware module로 이동한다.

당장은 대규모 이동보다 contract와 테스트를 먼저 고정한다.

## 문서 운영 정책

이 파일은 2026-06-14 시점의 정제 검토본이다.

운영 원칙:

- 원문 초안은 raw context로 보존한다.
- 날짜가 붙은 검토본은 특정 시점의 판단 기록으로 보존한다.
- 최신 의사결정은 `FUTURE_ARCHITECTURE_VISION.md`, `ENGINE_API_CALL_POLICY.md`, `SCORE_AND_ENDGAME_DECISION.md` 같은 canonical 문서에 반영한다.
- dated review 문서가 많아져 토큰 비용이 커지면 `docs/archive/`로 이동하거나 git history에만 남기고 최신 canonical 문서만 유지한다.

## 최종 판단

`ARCHITECTURE_LAYERS_ANALYSIS.md` 초안은 방향이 좋다. 그러나 현재 구현을 과장하는 문장을 줄이고, 플랫폼급 확장을 위해 실패 모델과 관측 가능성을 더 강하게 넣어야 한다.

우리가 수용해야 할 핵심은 7계층 구조 자체다.  
우리가 보완해야 할 핵심은 “엔진 호출은 항상 실패 가능하고 지연 가능하며, 그 흔적이 구조화 로그와 operation model에 남아야 한다”는 점이다.
