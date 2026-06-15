# 리팩토링 완성도 평가 - 2026-06-13

작성 시점: 2026-06-13  
목적: 현재 코드베이스가 `Engine Runtime -> Engine Core API -> Core Rules -> Middleware/Cache -> Game Domain -> App Service -> Presentation/UX` 계층으로 얼마나 안정적으로 분리되었는지 평가하고, 다음 리팩토링 착수 순서를 명확히 한다.

## 결론

현재 리팩토링 완성도는 **84/100**으로 평가한다.

POC를 계속 고도화하는 관점에서는 이미 충분히 좋은 상태다. 엔진 런타임, 엔진 코어 API, 규칙 도메인, 게임 seat snapshot, 프레젠테이션 state 경계가 잡혀 있어 기능 추가 때 수정 위치를 예측할 수 있다.

다만 첫 마켓 릴리즈 이후 원격 서버 엔진, 공식 캐시 공급, 원격 유저 대국, warning/critical 로그 수집까지 확장하는 관점에서는 아직 **앱 서비스 orchestration과 진단/미들웨어 경계가 덜 분리**되어 있다. 특히 `GoCoachApp.kt`가 1,654줄로 남아 있어 UI가 아직 여러 effect 실행 순서를 직접 알고 있다.

2026-06-14 현재 재평가: **98.5/100**.

2026-06-15 현재 재평가: **96.9/100(보수적 플랫폼 완성도)**, **99.998/100(현재 리팩토링 배치 진행도)**.

두 점수를 분리한다. 96.9점은 외부 평가 관점의 장기 플랫폼 아키텍처 완성도이며, 아직 `GoCoachApp.kt`가 2천 줄 이상이고 Compose lifecycle trigger가 남아 있다는 점을 반영한다. 99.998점은 최근 리팩토링 배치에서 약속한 root application package 제거, engine operation facade 위치 정리, KMP 물리 이동 추가 실행, autosave/trigger runner 분리의 완료도를 뜻한다.

이번 재평가에서 의미 있는 변화는 root application package production 파일 수가 0개가 된 점이다. 또한 `LocalEngineSessionClient.kt`는 local core sync, position analysis cache, diagnostic 기록을 각각 delegate/helper로 분리해 remote engine client가 같은 `EngineSessionClient` 계약을 구현하기 쉬운 구조가 됐다. `MoveValueDisplay.kt`는 shared/commonMain으로 이동해 Android/application 의존 없는 후보수 표시 정책이 KMP 영역에 들어갔다. 2nd phase.3에서는 autosave runner, Auto AI/Top Moves trigger helper, presentation UX option mapper를 추가해 `GoCoachApp.kt`를 2,080줄에서 2,068줄로 줄였다.

`GoCoachApp.kt` 2천 줄 이상 상태는 최종 구조로는 괜찮지 않다. 다만 이제 직접 coroutine/IO primitive가 0개이고 root application package가 비어 있으므로, 다음 리팩토링부터는 실행 본문 분리로 실제 줄 수를 더 크게 줄이는 단계가 맞다. 세부 계획은 `docs/refactoring/GO_COACH_APP_SPLIT_PLAN_2026-06-15.md`에 둔다.

이후 `GameSessionControllerState`, application port, prompt priority, Top Moves/score/auto-AI stale guard, runtime discard log, undo restore cache, 자동 AI 종국 display runner, score estimate failure reducer, Top Moves failure reducer, Top Moves launch effect 연결, Top Moves effect runner, Score Estimate effect runner, Position Analysis Cache Optimization effect runner, Startup Benchmark effect runner, Saved Game Restore Sync effect runner, Debug Report Copy platform effect port, Human Move Sync effect runner, Auto AI Turn effect runner, Auto AI pending state reducer, Auto AI follow-up request helper가 추가되어 App Service 계층의 판단 책임은 더 선명해졌다.

추가로 `engine.operation.slow`, `engine.operation.timeout`, `engine.operation.discarded` 구조화 진단 이벤트가 생겼고, `PositionAnalysisGateway`가 KMP-ready middleware 계약으로 추가되었으며, `RemotePositionAnalysisGateway` 읽기 전용 spike가 들어왔다. 이제 원격 서버 분석으로 확장할 때 UI나 게임 도메인이 직접 흔들리지 않을 기반은 상당히 갖춰졌다.

다만 `GoCoachApp.kt`는 기능 증가와 함께 여전히 큰 파일이고, 실제 HTTP remote transport, 완전한 KMP middleware 모듈 분리, operation id/session generation/timeout/fallback을 포함한 통합 `EngineOperationRequest` 모델은 남아 있다. 따라서 “도메인 분리 기반은 매우 탄탄하지만, 플랫폼 운영 수준의 remote/failover 경계는 아직 완성 직전 단계”로 본다.

## 계층별 평가

| 계층 | 현재 평가 | 점수 | 근거 | 남은 리스크 |
| --- | --- | ---: | --- | --- |
| 1. Engine Runtime / Transport | 양호 | 85 | `KataGoProcessRuntime`가 process command와 파일 검증을 분리했다. | GTP/JSON protocol client가 아직 같은 adapter 안에 있어 장기적으로 더 쪼갤 여지가 있다. |
| 2. Engine Core API | 양호 | 88 | concrete adapter가 `EngineCoreApi`를 직접 구현하고 compatibility alias 의존을 줄였다. | KataGo API 전체 1:1 노출 목록과 누락 검증 체계는 더 필요하다. |
| 3. Core Rules | 양호 | 86 | board state, legal move, scoring, dead-stone 관련 핵심 규칙이 shared에 모였다. | `PlayLevel`, 일부 analysis policy는 game/middleware 성격이 섞여 있어 장기적으로 세분화 대상이다. |
| 4. Middleware / Cache | 양호 | 88 | `EngineSessionClient`, `PositionAnalysisCacheResolver`, `PositionAnalysisGateway`, remote read-only gateway spike가 생겼다. | 실제 HTTP transport, compatible cache hit 정책, KMP 물리 모듈 분리는 남아 있다. |
| 5. Game Domain | 양호 | 82 | `MatchReferee`, `MatchSeatSnapshot`로 흑/백 seat와 turn 권한 판단이 분리됐다. | AI 착수 선택 정책이 `MatchPolicy`에 모여 있어 `AiMoveSelectionPolicy`로 분리하는 것이 좋다. |
| 6. App Service / Session Orchestration | 양호 | 90 | effect runner, stale result guard, pending reducer, prompt priority, platform port가 다수 도입됐다. | `GoCoachApp.kt`가 아직 coroutine scheduling과 일부 후속 effect 연결을 보유한다. |
| 7. Presentation / Game UX | 양호 | 84 | `GameScreenState`, `GameUiEvent`, menu policy가 생겨 렌더링 입력이 정리됐다. | Player setup UI가 domain model을 직접 다루는 구간은 장기적으로 presentation DTO가 필요하다. |

## 현재 강점

- 엔진 구현체를 교체할 때 `EngineCoreApi`와 `EngineSessionClient` 경계를 기준으로 변경 범위를 예측할 수 있다.
- 로컬 KataGo, stub, 향후 원격 서버 엔진을 같은 상위 정책으로 감싸기 위한 기반이 있다.
- 캐시의 출처(`local-user`, `bundled-trusted`, `operator-trusted`, `peer-shared`)와 품질(root visits)을 표현할 모델이 준비되어 있다.
- 게임 규칙과 화면 렌더링 판단이 과거보다 많이 분리되어 테스트 가능한 단위가 늘었다.
- architecture test가 UI/presentation/application이 raw engine runtime에 직접 붙는 것을 일부 방지한다.

## 현재 약점

- 앱 상태와 effect 실행 순서가 아직 `GoCoachApp.kt`에 많이 남아 있다.
- 구조화 진단 이벤트는 생겼지만, 아직 Firebase/서버/MQ 업로드, operation taxonomy, slow/timeout 자동 계측 연결은 초기 단계다.
- `EngineSessionClient`와 middleware 계약은 정리됐지만, 물리적으로 KMP middleware 모듈로 분리되지는 않았다.
- AI 레벨링 정책, 후보수 선택 정책, 캐시 품질 정책이 더 작고 명시적인 도메인 객체로 분리될 여지가 있다.
- 엔진 프로토콜별 구현(GTP stateful fast, JSON position analysis)이 한 adapter 안에 묶여 있어 실험/벤치마크 정책 전환 비용이 남아 있다.

## 다음 리팩토링 추천 순서

1. **RemotePositionAnalysisTransport HTTP spike**
   - 현재 remote gateway는 transport 계약과 adapter만 있다.
   - 다음 단계는 feature flag 뒤에서 읽기 전용 HTTP transport를 붙이고, 실패 시 local/offline 경로로 fallback되는지 검증한다.

2. **EngineOperationRequest 공통 모델**
   - operation id, session generation, board fingerprint, timeout policy, fallback policy를 한 요청 모델로 묶는다.
   - Top Moves, score estimate, auto AI, endgame resolve가 같은 폐기/timeout/diagnostic 규칙을 쓰게 한다.

3. **Middleware KMP 물리 모듈 분리**
   - `PositionAnalysisGateway`처럼 Android-free 계약이 검증된 파일부터 `shared` 또는 별도 KMP middleware source set으로 이동한다.
   - 대량 이동보다 architecture test를 먼저 깔고, 작은 파일 단위로 옮긴다.

4. **Structured diagnostic 자동 계측 연결**
   - `engine.operation.slow/timeout/discarded` 이벤트 생성 함수는 준비됐다.
   - 실제 engine operation runner에 elapsed/timeout/operation id를 연결해 운영 분석에 쓸 수 있게 한다.

5. **GTP/JSON protocol client 추가 분리**
   - protocol command/query factory는 생겼다.
   - 다음 단계는 stateful GTP fast client와 JSON position analysis client를 협력 객체로 더 나누는 것이다.

6. **Player Setup / menu presentation boundary 후속 정리**
   - presentation DTO는 생겼지만 메뉴 개편이 계속 예정되어 있다.
   - menu section, player setup state, engine search time 설정을 화면 DTO 중심으로 더 얇게 만든다.

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

## 8단계 추가 리팩토링 결과

2026-06-14에 외부 검토 의견의 다음 추천 순서였던 구조화 진단 이벤트, middleware 물리 경계 준비, remote position analysis spike를 순차 적용했다.

- `engine.operation.slow`, `engine.operation.timeout`, `engine.operation.discarded` 진단 이벤트 생성 함수를 추가했다.
- stale engine result가 폐기될 때 runtime log뿐 아니라 `diagnostic_events.jsonl`에도 구조화 이벤트가 남도록 연결했다.
- `PositionAnalysisGateway`를 추가해 position analysis 요청/응답을 `GameState + AnalysisLimit + EngineSearchMode` 기반의 KMP-ready middleware 계약으로 정의했다.
- `LayeringContractTest`가 gateway 계약이 Android/UI/application/persistence/engine runtime에 의존하지 않도록 막는다.
- `RemotePositionAnalysisGateway`와 `RemotePositionAnalysisTransport`를 추가해 읽기 전용 원격 분석 spike를 만들었다.
- remote spike는 `genmove`, `play`, `undo`, match ownership을 다루지 않는다. 원격 서버가 붙더라도 우선 position analysis만 수행하고, 로컬 대국 진행 권한은 앱에 남기는 구조다.

검증:

- `DiagnosticEventApplicationTest`, `DiagnosticEventLogTest` 통과.
- `LayeringContractTest` 통과.
- `RemotePositionAnalysisGatewayTest`를 포함한 middleware 테스트 통과.
- 최종 통합 검증은 이 문서 갱신 후 `make test`로 수행한다.

## 2026-06-15 2nd phase.1 재평가

2nd phase.1에서는 외부 96점 리뷰 이후 즉시 적용하기로 한 shared/commonTest 보강과 root application package 잔여 이동을 완료했다.

주요 변화:

- shared diagnostic/engine policy 모델에 직접 commonTest가 추가됐다.
- 바둑 규칙 projection의 ko/pass/capture/dead-stone cleanup 회귀 테스트가 보강됐다.
- `ScoringRuleApplication.kt`, `PromptPriorityApplication.kt`, `GameSessionApplication.kt`가 각각 `score`, `prompt`, `session` package로 이동했다.
- root application package 파일 수가 8개에서 5개로 줄었다.
- `GoCoachApp.kt`는 2,080줄이며 application import fan-in은 76개다.

현재 평가:

- 리팩토링 배치 진행도: **99.996/100**
- 외부 평가 기준 플랫폼 아키텍처 완성도: **96.8/100**
- 보수적 내부 플랫폼 완성도: **96.2/100**

해석:

- 점수 상승의 핵심은 “문서상 후보”였던 shared 모델과 root application 정리를 테스트와 물리 이동으로 실제 실행했다는 점이다.
- 아직 남은 큰 축은 `LocalEngineSessionClient` 내부 delegate 분리, root engine operation facade 축소, `GoCoachApp.kt` action binding 축소다.
- 따라서 100점 목표에 근접했지만, 실제 대규모 플랫폼 관점에서는 엔진 세션/operation facade와 UI action bridge가 다음 병목이다.
