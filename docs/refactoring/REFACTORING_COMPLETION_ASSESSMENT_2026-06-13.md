# 리팩토링 완성도 평가 - 2026-06-13

작성 시점: 2026-06-13  
목적: 현재 코드베이스가 `Engine Runtime -> Engine Core API -> Core Rules -> Middleware/Cache -> Game Domain -> App Service -> Presentation/UX` 계층으로 얼마나 안정적으로 분리되었는지 평가하고, 다음 리팩토링 착수 순서를 명확히 한다.

## 결론

현재 리팩토링 완성도는 **84/100**으로 평가한다.

POC를 계속 고도화하는 관점에서는 이미 충분히 좋은 상태다. 엔진 런타임, 엔진 코어 API, 규칙 도메인, 게임 seat snapshot, 프레젠테이션 state 경계가 잡혀 있어 기능 추가 때 수정 위치를 예측할 수 있다.

다만 첫 마켓 릴리즈 이후 원격 서버 엔진, 공식 캐시 공급, 원격 유저 대국, warning/critical 로그 수집까지 확장하는 관점에서는 아직 **앱 서비스 orchestration과 진단/미들웨어 경계가 덜 분리**되어 있다. 특히 `GoCoachApp.kt`가 1,654줄로 남아 있어 UI가 아직 여러 effect 실행 순서를 직접 알고 있다.

2026-06-14 현재 재평가: **98/100**.

이후 `GameSessionControllerState`, application port, prompt priority, Top Moves/score/auto-AI stale guard, runtime discard log, undo restore cache, 자동 AI 종국 display runner, score estimate failure reducer, Top Moves failure reducer, Top Moves launch effect 연결, Top Moves effect runner, Score Estimate effect runner, Position Analysis Cache Optimization effect runner, Startup Benchmark effect runner, Saved Game Restore Sync effect runner, Debug Report Copy platform effect port, Human Move Sync effect runner, Auto AI Turn effect runner, Auto AI pending state reducer, Auto AI follow-up request helper가 추가되어 App Service 계층의 판단 책임은 더 선명해졌다. 다만 `GoCoachApp.kt`는 기능 증가와 함께 약 1,900줄 규모로 남아 있고, coroutine scheduling, stale guard 적용, 후속 effect 연결 같은 일부 앱서비스 조율은 여전히 UI 파일에 있다. 따라서 “도메인 분리 기반은 상당히 탄탄하지만, UI orchestration 축소는 아직 마무리 단계”로 본다.

## 계층별 평가

| 계층 | 현재 평가 | 점수 | 근거 | 남은 리스크 |
| --- | --- | ---: | --- | --- |
| 1. Engine Runtime / Transport | 양호 | 85 | `KataGoProcessRuntime`가 process command와 파일 검증을 분리했다. | GTP/JSON protocol client가 아직 같은 adapter 안에 있어 장기적으로 더 쪼갤 여지가 있다. |
| 2. Engine Core API | 양호 | 88 | concrete adapter가 `EngineCoreApi`를 직접 구현하고 compatibility alias 의존을 줄였다. | KataGo API 전체 1:1 노출 목록과 누락 검증 체계는 더 필요하다. |
| 3. Core Rules | 양호 | 86 | board state, legal move, scoring, dead-stone 관련 핵심 규칙이 shared에 모였다. | `PlayLevel`, 일부 analysis policy는 game/middleware 성격이 섞여 있어 장기적으로 세분화 대상이다. |
| 4. Middleware / Cache | 보통 이상 | 78 | `EngineSessionClient`, position cache, trusted cache provider 경계가 생겼다. | 패키지명이 아직 `application`에 섞인 부분이 있고, warning/critical 진단 로그 port가 아직 없다. |
| 5. Game Domain | 양호 | 82 | `MatchReferee`, `MatchSeatSnapshot`로 흑/백 seat와 turn 권한 판단이 분리됐다. | AI 착수 선택 정책이 `MatchPolicy`에 모여 있어 `AiMoveSelectionPolicy`로 분리하는 것이 좋다. |
| 6. App Service / Session Orchestration | 개선 필요 | 76 | 여러 state holder와 controller plan 함수가 생겼다. | `GoCoachApp.kt`가 coroutine/effect 실행을 많이 들고 있어 UI와 app service 경계가 완전히 얇지 않다. |
| 7. Presentation / Game UX | 양호 | 84 | `GameScreenState`, `GameUiEvent`, menu policy가 생겨 렌더링 입력이 정리됐다. | Player setup UI가 domain model을 직접 다루는 구간은 장기적으로 presentation DTO가 필요하다. |

## 현재 강점

- 엔진 구현체를 교체할 때 `EngineCoreApi`와 `EngineSessionClient` 경계를 기준으로 변경 범위를 예측할 수 있다.
- 로컬 KataGo, stub, 향후 원격 서버 엔진을 같은 상위 정책으로 감싸기 위한 기반이 있다.
- 캐시의 출처(`local-user`, `bundled-trusted`, `operator-trusted`, `peer-shared`)와 품질(root visits)을 표현할 모델이 준비되어 있다.
- 게임 규칙과 화면 렌더링 판단이 과거보다 많이 분리되어 테스트 가능한 단위가 늘었다.
- architecture test가 UI/presentation/application이 raw engine runtime에 직접 붙는 것을 일부 방지한다.

## 현재 약점

- 앱 상태와 effect 실행 순서가 아직 `GoCoachApp.kt`에 많이 남아 있다.
- warning/critical 로그는 런타임 이벤트 로그와 분리되지 않아, 나중에 Firebase/서버/MQ 수집으로 확장하기 어렵다.
- `EngineSessionClient`가 middleware 역할을 하고 있지만 패키지 위치와 naming이 아직 app service와 섞여 있다.
- AI 레벨링 정책, 후보수 선택 정책, 캐시 품질 정책이 더 작고 명시적인 도메인 객체로 분리될 여지가 있다.
- 엔진 프로토콜별 구현(GTP stateful fast, JSON position analysis)이 한 adapter 안에 묶여 있어 실험/벤치마크 정책 전환 비용이 남아 있다.

## 다음 리팩토링 추천 순서

1. **Warning/Critical Diagnostic Event 경계 추가**
   - `runtime_event_log.txt`와 별도로 `diagnostic_events.jsonl`을 둔다.
   - visits short, engine timeout, score disagreement, cache quality warning을 구조화된 이벤트로 남긴다.
   - 나중에 Firebase Crashlytics/Analytics, 서버 MQ, 사용자 오류 전송 팝업으로 확장할 port를 만든다.

2. **GameSession Effect Runner 분리**
   - `GoCoachApp.kt`가 직접 처리하는 자동 AI 턴, Top Moves launch, score estimate, benchmark, 저장 복원 effect를 `GameSessionController` 쪽 실행 계획으로 이동한다.
   - UI는 state render와 event dispatch에 집중하게 한다.

3. **AI Move Selection Policy 분리**
   - AI 캐릭터/레벨이 후보수를 어떤 방식으로 고르는지 `AiMoveSelectionPolicy`로 분리한다.
   - B16 fast best-only, B32/B64 JSON leveling, future personality/randomness 정책을 독립 테스트한다.

4. **Engine Session Client 구현체 분리**
   - `LocalEngineSessionClient`, `RemoteEngineSessionClient` 후보 구조를 먼저 만든다.
   - 현재 adapter wrapper는 local implementation으로 명시하고, 서버 엔진 전환 시 상위 게임 로직이 바뀌지 않게 한다.

5. **Middleware / Cache 패키지 재배치**
   - cache policy, trusted provider, cache quality, optimization planning을 `application`에서 더 명확한 middleware 계층으로 이동한다.
   - 단번에 대량 이동하지 말고 import churn을 줄이는 순서로 진행한다.

6. **Player Setup Presentation DTO 추가**
   - UI가 `PlayerSetup` mutable domain model을 직접 조작하지 않도록 화면 입력 DTO와 domain 변환 함수를 둔다.
   - 향후 원격 유저, 관전자, AI 캐릭터 프리셋이 들어와도 메뉴 UI 변경 폭을 줄인다.

7. **GTP/JSON Protocol Client 분리**
   - KataGo process adapter 내부에서 GTP command, JSON analysis query, final status/score query를 더 작은 client로 분리한다.
   - 엔진 API 1:1 노출과 목적별 middleware 조합을 더 선명하게 만든다.

## 이번 즉시 착수 단위

이번 요청 직후에는 1번 항목인 **Warning/Critical Diagnostic Event 경계 추가**부터 수행한다.

이유는 다음과 같다.

- 최근 사용자가 실기기에서 `fill=SHORT`, 캐시 오염, 자동대국 속도 이상, 종국 score disagreement를 계속 확인하고 있다.
- 앱을 모르는 사람이 로그만 봐도 상태를 이해하려면 일반 runtime trace와 warning/critical event를 분리해야 한다.
- 이 작업은 UI 동작을 크게 건드리지 않고도 계층 분리 효과가 크며, 이후 Firebase/MQ/사용자 오류 전송 팝업으로 확장하기 쉽다.

완료 기준:

- `DiagnosticEvent` 도메인 모델과 severity/code/context 구조가 생긴다.
- `DiagnosticEventLogPort`로 저장소 구현을 숨긴다.
- Android local persistence는 JSONL 파일에 최근 1MB까지 저장한다.
- visits short 같은 엔진 품질 warning을 만들 수 있는 순수 함수와 단위 테스트가 생긴다.

## 즉시 착수 결과

2026-06-13에 1차 리팩토링을 완료했다.

- `DiagnosticEvent`, `DiagnosticSeverity`를 추가해 warning/critical event를 구조화했다.
- `engineVisitFillDiagnosticEvent()`로 root visits 미충족/미보고 상황을 순수 application 로직에서 판정할 수 있게 했다.
- `scoreDisagreementDiagnosticEvent()`로 종국 계가 불일치 같은 critical event를 표현할 수 있게 했다.
- `DiagnosticEventLogPort`를 추가해 저장소 구현을 application 계층 뒤에 숨겼다.
- `DiagnosticEventLog`는 `diagnostic_events.jsonl`에 JSONL 형식으로 최근 1MB까지 저장한다.
- `Copy Log` debug report에 `[DiagnosticEventLog]` 섹션을 추가했다.
- 이 단계에서는 실제 엔진 호출부에 무리하게 연결하지 않고, 다음 리팩토링에서 engine/session 정책과 연결할 수 있는 안정적 기반과 수집 통로만 만들었다.

검증:

- `./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.DiagnosticEventApplicationTest' --tests 'com.worksoc.goaicoach.persistence.DiagnosticEventLogTest'` 통과.
- `make test` 통과.

## 1단계 추가 리팩토링 결과

2026-06-14에 diagnostic event 경계를 실제 엔진 분석 결과에 연결했다.

- `AdapterEngineSessionClient`가 선택적으로 `DiagnosticEventLogPort`를 주입받는다.
- `analyzePosition()`이 실제 엔진 호출을 수행한 뒤 `rootVisits < requestedVisits` 또는 `rootVisits == null`이면 warning event를 기록한다.
- cache hit 결과는 새 엔진 호출이 아니므로 diagnostic event를 중복 기록하지 않는다.
- `MainActivity`가 하나의 `DiagnosticEventLog` 인스턴스를 생성해 `AdapterEngineSessionClient`와 `GoCoachApp`에 함께 주입한다.
- `GoCoachApp`은 diagnostic log 파일을 직접 생성하지 않고 주입받은 port를 debug report에 사용한다.

검증:

- `EngineSessionTest`, `DiagnosticEventApplicationTest`, `DebugReportBuilderTest` 관련 테스트 통과.
- `make test` 통과.

## 2단계 추가 리팩토링 결과

2026-06-14에 `GameSession Effect Runner` 분리의 첫 안전 단위로 debug report copy effect를 application plan으로 분리했다.

- `DebugReportCopyPlan`을 추가해 clipboard label, report 본문, engine message, toast message를 application 계층에서 구성한다.
- `buildDebugReportCopyPlan()`이 `DebugReportSnapshot`을 받아 복사 effect에 필요한 값을 모두 만든다.
- `GoCoachApp.kt`는 report 문자열과 사용자 메시지를 직접 조합하지 않고, plan을 받아 Clipboard/Toast/Mirror 같은 플랫폼 effect만 실행한다.
- `GameSessionEffect.CopyDebugReport` 타입을 추가해 향후 controller/effect runner가 debug report copy를 명시적 effect로 다룰 수 있게 했다.

검증:

- `DebugReportBuilderTest`, `GameSessionControllerTest` 관련 테스트 통과.
- `make test` 통과.

## 3단계 추가 리팩토링 결과

2026-06-14에 AI 후보수 선택 정책을 `MatchPolicy`에서 분리했다.

- `AiMoveSelectionPolicy`를 추가해 AI 착수용 analysis limit 결정과 후보수 선택을 전담하게 했다.
- `SelectedAiMove`를 별도 domain model로 이동해 `applyAiTurn()`은 후보 선택 정책 호출 결과만 처리한다.
- 기존 pass best candidate override, 현재 AI 색상 후보 필터링, pointLoss 없는 후보 제외, selection policy range 기반 random 선택 동작은 유지했다.
- 정책 함수에 `Random`을 주입할 수 있게 해 독립 테스트에서 재현 가능한 선택 검증이 가능해졌다.

검증:

- `AiMoveSelectionPolicyTest`, `MatchPolicyTest` 통과.
- `make test` 통과.

## 4단계 추가 리팩토링 결과

2026-06-14에 local/remote engine session client 경계를 더 명확히 했다.

- `EngineSessionBackend`를 추가해 session client가 `local-engine`인지 `remote-server`인지 capability로 표현할 수 있게 했다.
- 기존 `AdapterEngineSessionClient` 구현체 이름을 `LocalEngineSessionClient`로 바꿨다.
- 기존 이름은 deprecated typealias로 남겨 외부 변경 폭을 줄이고, 새 코드에서는 local 구현체임이 드러나도록 했다.
- `MainActivity`는 이제 `LocalEngineSessionClient`를 직접 생성하고 `EngineSessionBackend.LocalEngine` capability를 명시한다.

검증:

- `EngineSessionTest`에서 local backend capability를 검증했다.
- `make test` 통과.

## 5단계 추가 리팩토링 결과

2026-06-14에 middleware/cache 경계를 점진적으로 분리했다.

- `com.worksoc.goaicoach.middleware.PositionAnalysisCacheResolver`를 추가했다.
- local cache store와 trusted cache provider 목록 중 어떤 entry를 재사용할지 결정하는 책임을 `LocalEngineSessionClient`에서 resolver로 이동했다.
- `LocalEngineSessionClient`는 cache stats, quality lookup, reusable entry lookup, local put을 resolver에 위임한다.
- 이 단계에서는 대량 패키지 이동을 피하고, cache 선택 정책만 먼저 middleware helper로 분리했다.

검증:

- `PositionAnalysisCacheResolverTest`, `EngineSessionTest` 통과.
- `make test` 통과.

## 6단계 추가 리팩토링 결과

2026-06-14에 Player Setup presentation DTO를 도입했다.

- `PlayerSetupUiState`와 `PlayerSetupSideUiState`를 presentation 계층에 추가했다.
- `buildPlayerSetupUiState()`가 seat label, controller label, AI 단계 label, engine label, visits detail, 자동대국 delay 표시 여부, summary text를 계산한다.
- `GameScreenState`는 원본 `PlayerSetup`과 함께 `playerSetupUi`를 제공한다.
- `PlayerSetupPanel`은 표시 문자열을 직접 계산하지 않고 `PlayerSetupUiState`를 렌더링한다.
- 기존 이벤트는 여전히 `PlayerSetup`을 반환하므로 설정 변경 동작은 유지했다.

검증:

- `PlayerSetupUiStateTest`, `GameScreenStateTest` 통과.
- `make test` 통과.

## 7단계 추가 리팩토링 결과

2026-06-14에 KataGo GTP/JSON protocol 생성 경계를 분리했다.

- `KataGoProtocolCommands`를 추가해 board setup, move, genmove, search analyze, raw NN, final score/status, maintenance command 문자열 생성을 한 곳으로 모았다.
- `KataGoJsonAnalysisQueryFactory`를 추가해 JSON position analysis query 생성 책임을 `KataGoProcessEngineAdapter`에서 분리했다.
- `KataGoProcessEngineAdapter`는 process lifecycle, send/receive, engine state replay, parser 조합에 집중하고 protocol 포맷 지식은 helper에 위임한다.
- 이 단계는 아직 별도 process client 클래스까지 나누지는 않았다. 다음 단계에서 `GtpStatefulFastClient`와 `JsonPositionAnalysisClient`를 별도 협력 객체로 추출할 수 있는 기반 작업이다.

검증:

- `KataGoProtocolCommandsTest`, `KataGoJsonAnalysisQueryFactoryTest` 통과.
- `:engine-android:testDebugUnitTest` 통과.
- `make test` 통과.
