# 아키텍처/구현 상태 평가와 리팩토링 계획 - 2026-06-27

작성일: 2026-06-27  
목적: 현재 `go-ai-coach` 코드와 문서를 기준으로 아키텍처 현황을 재평가하고, 다음 리팩토링을 Codex (low) 엔진도 안전하게 수행할 수 있는 작은 단위로 쪼갠다.

## 검토 기준

- 검토 문서: `README.md`, `docs/ARCHITECTURE.md`, `docs/DOCS_INDEX.md`, `docs/ENGINE.md`, `docs/ENGINE_API_CALL_POLICY.md`, `docs/refactoring/*`
- 검토 코드: `shared`, `engine-android`, `app-android` 전체 production/test Kotlin 파일
- 검증 명령: `make test`
- 검증 결과: 성공

## 현재 기준선

| 항목 | 2026-06-27 기준 |
| --- | ---: |
| production Kotlin 파일 | 178개 |
| test Kotlin 파일 | 78개 |
| `@Test` 테스트 수 | 546개 |
| `app-android/application` production 파일 | 107개 |
| `app-android/application` 하위 패키지 | 17개 |
| `shared/commonMain` production 파일 | 21개 |
| `engine-android` production 파일 | 10개 |
| `GoCoachApp.kt` | 791줄 |
| 가장 큰 UI 파일 | `GoBoard.kt` 685줄, `UiStrings.kt` 603줄, `PlayerSetupPanel.kt` 366줄 |
| 가장 큰 app-service 파일 | `ScoreDisplayApplication.kt` 534줄, `RuntimeEventApplication.kt` 487줄, `HumanMoveApplication.kt` 457줄, `DebugReportBuilder.kt` 406줄 |

## 현재 아키텍처 평가

현재 구조는 Android-first 로컬 KataGo 앱을 계속 기능 확장하기에는 충분히 안정적이다. 7계층 구조는 실제 코드와 대체로 맞고, `LayeringContractTest`가 UI/presentation/application에서 raw engine runtime으로 새는 의존을 막고 있다.

핵심 강점은 다음이다.

- `shared`가 보드 규칙, 계가, 엔진 core API, engine operation policy, 진단 이벤트 모델을 보유한다.
- `engine-android`는 KataGo process runtime, GTP stateful fast path, JSON position analysis path를 adapter 뒤에 둔다.
- `app-android/application`은 17개 기능 패키지로 분리되어 있고, controller + application runner + state holder 패턴이 자리 잡았다.
- `GoCoachApp.kt`는 2026-06-15 문서의 2천 줄대 상태에서 791줄까지 축소되어, 현재는 Compose state wiring, controller 생성, event dispatch, content rendering에 집중한다.
- `EngineOperationRequest`, session generation, stale result guard, operation lifecycle, runtime/diagnostic log가 주요 엔진 호출에 적용되어 있다.
- 원격 분석은 단순 인터페이스만 있는 상태가 아니다. `RemotePositionAnalysisGateway`와 기본 off HTTP transport spike가 존재한다. 다만 전체 `RemoteEngineSessionClient`는 아직 없다.
- JVM 단위 테스트와 architecture contract test가 풍부하다. 오늘 기준 `make test`가 통과한다.

남은 리스크는 다음이다.

- `GoCoachApp.kt`는 많이 줄었지만 import fan-in과 controller constructor wiring이 여전히 크다. Compose root가 "무엇이 필요한지"를 너무 많이 안다.
- app-service 대형 파일들이 다시 커지고 있다. 특히 score display, runtime log, human move, debug report는 내부 책임 분리 단위가 보인다.
- production 코드 일부가 wildcard import를 쓴다. 컴파일에는 문제가 없지만 경계 추적과 코드 리뷰 비용을 올린다.
- `GameSessionStateHolder`는 KMP-ready 성격이 강하지만 아직 `app-android`에 있다.
- `PositionAnalysisGateway`와 `RemotePositionAnalysisGateway`는 KMP-ready 계약이지만 물리적으로는 `app-android/middleware`에 있다.
- androidTest/Robolectric 수준의 UI/lifecycle 회귀 테스트가 아직 없다.
- remote path는 read-only position analysis까지이고, `genmove`, `play`, `undo`, endgame, cache/fallback 전체를 대표하는 remote session client는 없다.

## 평가 점수

| 관점 | 점수 | 해석 |
| --- | ---: | --- |
| Android-first 로컬 엔진 앱 유지보수성 | 90/100 | 기능 추가와 버그 수정 위치를 예측할 수 있고 테스트가 많다. |
| 현재 리팩토링 배치 완료도 | 94/100 | 기존 대형 UI orchestration 분리 목표는 대부분 달성했다. |
| 장기 KMP/remote-engine 플랫폼 준비도 | 78/100 | 계약과 테스트 기반은 있으나 물리 모듈 이동, remote session client, 계측 테스트가 남아 있다. |

점수는 “완성도 과시”가 아니라 다음 투자 우선순위를 정하기 위한 보수적 기준이다.

## 시급도 높은 리팩토링 항목

아래 항목은 작은 단위로 수행한다. 한 PR/커밋에는 원칙적으로 항목 하나만 넣는다.

### H-01. Production wildcard import 제거

- 추천 엔진: Codex (low)
- 파일 범위: `app-android/src/main/java` production Kotlin 파일만
- 작업 지시: wildcard import를 명시 import로 바꾼다. 로직은 절대 변경하지 않는다.
- 우선 대상: `GoCoachApp.kt`, `GameScreenState.kt`, `GoCoachSessionFactory.kt`, `HumanMoveApplication.kt`, `AutoAiPolicyApplication.kt`, `AutoAiRunnerApplication.kt`, `DebugReportBuilder.kt`, `UserPreferencesApplication.kt`, `PositionAnalysisCacheOptimization.kt`
- 완료 기준: production 코드에서 `import ...*`가 사라진다. 단, Compose animation wildcard처럼 IDE가 자동 생성한 경우도 가능하면 명시 import로 고정한다.
- 검증: `rg "import .*\\.\\*" app-android/src/main/java shared/src/commonMain/kotlin engine-android/src/main/java -n`, `make test`
- 금지: 테스트 코드 wildcard import 정리는 별도 항목으로 둔다.

### H-02. `GoCoachApp.kt` controller wiring 가독성 정리

- 추천 엔진: Codex (low)
- 파일 범위: `GoCoachApp.kt`와 신규 `GoCoachControllerWiring.kt` 1개
- 작업 지시: controller 생성 중 서로 독립적인 1개 controller만 먼저 factory 함수로 옮긴다. 첫 후보는 `ScoreEstimateController` 또는 `PositionCacheOptimizationController`다.
- 완료 기준: `GoCoachApp.kt`의 해당 controller 생성 블록이 5줄 이하의 함수 호출로 줄고, callback 의미가 기존과 같다.
- 검증: 기존 controller test 유지, `LayeringContractTest`, `make test`
- 금지: 여러 controller를 한 번에 옮기지 않는다. state 소유권을 바꾸지 않는다.

### H-03. `RuntimeEventApplication.kt` 로그 함수 분리

- 추천 엔진: Codex (low)
- 파일 범위: `application/runtime/RuntimeEventApplication.kt`, 신규 파일 1개
- 작업 지시: 런타임 로그 함수 중 한 도메인만 새 파일로 이동한다. 첫 후보는 AI turn 로그 함수다.
- 완료 기준: public/internal 함수 시그니처와 문자열 포맷이 바뀌지 않는다. 테스트 변경은 import 보정만 허용한다.
- 검증: `RuntimeEventApplicationTest`, `make test`
- 금지: 로그 메시지 wording 개선, 필드명 변경, 이벤트 schema 변경을 같이 하지 않는다.

### H-04. `ScoreDisplayApplication.kt` 타입/포매터 분리

- 추천 엔진: Codex (low)
- 파일 범위: `application/score/ScoreDisplayApplication.kt`, 신규 `ScoreDisplayModels.kt` 또는 `FinalScoreDisplayApplication.kt`
- 작업 지시: data class/sealed class 모델만 먼저 분리하거나, final score display 함수만 분리한다. 둘을 동시에 하지 않는다.
- 완료 기준: `ScoreDisplayApplication.kt`가 100줄 이상 줄고, 기존 테스트가 그대로 통과한다.
- 검증: `ScoreDisplayApplicationTest`, `GameSessionScoreStateTest`, `make test`
- 금지: 계가 공식, 사석 cleanup, engine/local score 우선순위 변경 금지.

### H-05. `DebugReportBuilder.kt` section builder 분리

- 추천 엔진: Codex (low)
- 파일 범위: `application/debugreport/DebugReportBuilder.kt`, 신규 `DebugReportSections.kt`
- 작업 지시: debug report 본문 섹션 중 순수 문자열 조립 함수만 이동한다.
- 완료 기준: 생성되는 report 문자열이 기존 snapshot test와 동일하다.
- 검증: `DebugReportBuilderTest`, `make test`
- 금지: clipboard/toast/mirror port 동작 변경 금지.

### H-06. `GoBoard.kt` 좌표 계산 순수 함수 테스트 보강

- 추천 엔진: Codex (low)
- 파일 범위: `ui/GoBoard.kt`, 신규 또는 기존 UI 단위 테스트
- 작업 지시: 터치 좌표를 board coordinate로 바꾸는 계산을 순수 함수로 추출하고 테스트한다.
- 완료 기준: drawing 로직은 유지하고 좌표 계산만 테스트 가능해진다.
- 검증: 신규 테스트, `make test`
- 금지: 캔버스 스타일, 애니메이션, 색상 변경 금지.

### H-07. 문서 지표 최신화 체크 항목 추가

- 추천 엔진: Codex (low)
- 파일 범위: `docs/DOCS_INDEX.md`, `docs/ARCHITECTURE.md`, 필요 시 `README.md`
- 작업 지시: 줄 수, 패키지 수, remote transport 상태처럼 자주 낡는 지표를 “기준일 포함” 문장으로만 쓴다.
- 완료 기준: 문서가 특정 수치를 말할 때 기준일이 붙어 있다.
- 검증: 문서 diff 리뷰
- 금지: 과거 refactoring 로그의 historical 수치를 덮어쓰지 않는다.

## 중장기 고도화 항목

중장기 항목도 첫 작업 단위는 작게 시작한다. 아래 “첫 Codex (low) 단위”를 먼저 수행한 뒤 다음 단계로 넘어간다.

### M-01. `GameSessionStateHolder` shared 이전 준비

- 추천 엔진: Codex (medium)
- 첫 Codex (low) 단위: `GameSessionStateHolder`가 Android/Compose/java/org.json import를 갖지 않는지 architecture test를 추가한다.
- 전체 목표: `GameSessionStateHolder`와 필요한 순수 state 모델을 `shared`로 옮긴다.
- 완료 기준: 이전 전에는 contract test, 이전 후에는 shared commonTest가 상태 업데이트를 검증한다.
- 검증: `LayeringContractTest`, `shared:check`, `make test`
- 금지: shared 이동과 UI wiring 변경을 같은 작업에서 하지 않는다.

### M-02. middleware KMP 물리 모듈 분리

- 추천 엔진: Codex (medium)
- 첫 Codex (low) 단위: `PositionAnalysisGateway.kt`, `RemotePositionAnalysisGateway.kt`의 금지 import 테스트를 현재보다 더 명시적으로 문서화/테스트한다.
- 전체 목표: Android-free middleware 계약을 `shared` 또는 별도 KMP middleware source set으로 이동한다.
- 완료 기준: HTTP transport는 Android/JVM-bound 파일에 남고, 계약 파일만 KMP로 이동한다.
- 검증: `LayeringContractTest`, `RemotePositionAnalysisGatewayTest`, `make test`
- 금지: HTTP transport 이동, JSON codec 교체, remote feature flag 변경을 같이 하지 않는다.

### M-03. `RemoteEngineSessionClient` 최소 골격

- 추천 엔진: Codex (high)
- 첫 Codex (low) 단위: `RemoteEngineSessionClient`가 어떤 `EngineSessionClient` 메서드를 구현해야 하는지 체크리스트 문서를 만든다.
- 전체 목표: read-only position analysis뿐 아니라 score estimate/top moves 일부를 원격으로 라우팅하는 session client를 feature flag 뒤에 둔다.
- 완료 기준: 기본 off, local fallback, timeout/discard diagnostic, cache origin 표기가 테스트된다.
- 검증: fake transport 기반 JVM test, `make test`
- 금지: production 기본값을 remote on으로 바꾸지 않는다.

### M-04. androidTest/Robolectric smoke coverage

- 추천 엔진: Codex (medium)
- 첫 Codex (low) 단위: 현재 Gradle dependency와 test runner 상태를 조사하고 “첫 smoke test 후보” 문서/테스트 skeleton만 만든다.
- 전체 목표: 앱 시작, 저장 세션 prompt, 새 게임, UI 이벤트 dispatch, board tap 한 경로를 계측 또는 Robolectric으로 검증한다.
- 완료 기준: CI/로컬에서 안정적으로 재현되는 smoke test 1개가 생긴다.
- 검증: `:app-android:connectedDebugAndroidTest` 또는 Robolectric task
- 금지: 불안정한 실기기 의존 test를 기본 `make test`에 바로 넣지 않는다.

### M-05. `UiStrings.kt` 문자열 catalog 분리

- 추천 엔진: Codex (low)
- 첫 Codex (low) 단위: `UiStrings.kt`에서 언어 선택 enum/provider와 실제 문자열 catalog를 파일 2개로 나눈다.
- 전체 목표: 한국어/영어 UI 문자열을 기능별 catalog로 나눠 option/menu/player setup 문구 변경 비용을 낮춘다.
- 완료 기준: UI 문자열 동작은 같고 파일 책임만 나뉜다.
- 검증: existing UI/presentation tests, `make test`
- 금지: 문구 번역/개선과 파일 분리를 섞지 않는다.

### M-06. engine protocol adapter 추가 분해

- 추천 엔진: Codex (medium)
- 첫 Codex (low) 단위: `KataGoProcessEngineAdapter.kt`에서 이미 위임 중인 GTP/JSON client 호출 흐름을 문서화하고 테스트 목록을 정리한다.
- 전체 목표: process lifecycle, GTP stateful client, JSON position client, parser, benchmark path를 더 독립적으로 테스트한다.
- 완료 기준: adapter가 process lifecycle과 고수준 조율만 갖고, protocol별 세부는 client가 소유한다.
- 검증: `:engine-android:testDebugUnitTest`, `make test`
- 금지: GTP command sequence, timeout, search cache isolation 정책 변경 금지.

### M-07. cache/remote 일관성 정책 고도화

- 추천 엔진: Codex (medium)
- 첫 Codex (low) 단위: cache origin/quality와 remote/local fallback 케이스를 표로 문서화한다.
- 전체 목표: local-user, bundled-trusted, operator-trusted, peer-shared, remote-server 결과의 신뢰도와 expiry 정책을 통합한다.
- 완료 기준: cache hit 선택과 diagnostic message가 origin/quality를 일관되게 드러낸다.
- 검증: `PositionAnalysisCacheResolverTest`, `RemotePositionAnalysisGatewayTest`, 신규 cache policy test
- 금지: 기존 local cache 파일 포맷을 migration 없이 변경하지 않는다.

### M-08. release/native packaging 운영 결정

- 추천 엔진: Codex (high)
- 첫 Codex (low) 단위: `make dev`, `make friend-apk`, `make release`가 요구하는 native/model artifact와 실패 조건을 표로 정리한다.
- 전체 목표: debug/friend/release 빌드의 KataGo native artifact 공급 정책을 명확히 하고, 배포 전 검증 체크리스트를 자동화한다.
- 완료 기준: release artifact 누락, model/config 누락, ABI mismatch가 early fail한다.
- 검증: `make doctor`, `make dev-stub`, release dry-run 성격의 Gradle task
- 금지: 실제 release artifact를 repo에 커밋하지 않는다.

## 권장 작업 순서

1. H-01 wildcard import 제거
2. H-03 `RuntimeEventApplication.kt` 일부 분리
3. H-04 `ScoreDisplayApplication.kt` 일부 분리
4. H-05 `DebugReportBuilder.kt` 일부 분리
5. H-02 `GoCoachApp.kt` controller wiring 1개 이동
6. M-01 shared 이전 contract test
7. M-02 middleware KMP 이동 contract 강화
8. M-04 smoke test 조사/skeleton

이 순서는 로직 변경 없이 가독성과 경계 테스트를 먼저 올리고, 그 다음 KMP/remote 같은 구조 변경으로 넘어가는 흐름이다.

## Codex (low) 작업 공통 규칙

- 한 번에 production 파일 1~3개만 수정한다.
- 동작 변경이 목표가 아닌 항목은 테스트 기대값을 바꾸지 않는다.
- 변경 전후 `make test`를 실행한다.
- 줄 수 감소를 목표로 삼지 말고, “어떤 책임이 어느 파일로 이동했는지”를 완료 기준으로 삼는다.
- 문서나 test fixture가 아닌 production 코드에서 wildcard import를 새로 만들지 않는다.
- remote, cache, engine timing, scoring rule, endgame 판정은 한 작업에서 하나의 축만 건드린다.

## 오늘 반영한 문서 현행화

- `docs/ARCHITECTURE.md`: 작성/갱신일, 현재 `GoCoachApp.kt` 줄 수, application/engine 파일 수, 원격 HTTP transport 상태, known gap을 2026-06-27 기준으로 갱신한다.
- `docs/DOCS_INDEX.md`: 이 문서를 리팩토링 전략/진행 로그 목록에 추가한다.
