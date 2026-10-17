# 3계층 — Extended API (엔진 서비스)

go-ai-coach에서 이 계층이 **지금 어디에 있고, 무엇이 지키며, 무엇이 남았는가.** 정의와 경계 원칙은 `ARCHITECTURE.md` 「3계층 — Extended API」 한 곳에만 있어 여기서 반복하지 않는다. 전체 지도·모듈 지도·계층을 가로지르는 갭은 `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`.

> 2026-10-17에 `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`의 「3계층」 절과 「알려진 갭」·「고도화 로드맵」의 3계층 항목을 **문장 그대로** 옮겨 만들었다(계층 단위로 추적하려고). 옮기기 전 이력은 그 파일의 `git log -p`에, 이후 이력은 이 파일의 `git log`에 있다. 「갭과 남은 일」의 번호는 옛 「고도화 로드맵」 번호 그대로다.

## 계층 배정

아래 블록은 `ContractSymbols.kt`의 `LAYER_3_PACKAGES`를 그대로 옮긴 **사본**이다(접두 `com.worksoc.goaicoach.` 생략). 정본은 코드 쪽이고, `LayerDocContractTest`가 둘이 양방향으로 같은지 본다 — 패키지를 새로 만들거나 옮기면 코드 목록을 고치고, 빨개지면 이 블록을 따라 고친다.

<!-- layer-packages: LAYER_3_PACKAGES -->
```text
application.analysis
application.cacheoptimization
application.engine
application.engine.operation
application.safety
```

`shared.policy`의 `EngineOperationPolicy.kt`는 3계층 파일이지만 패키지는 5계층 목록에 있다(`LAYER_5_APPLICATION_DOMAIN.md` 배정 블록) — 배정은 패키지 단위다.

## 지금 위치

**위치**: 아래 항목은 별도 표시가 없는 한 전부 `shared/src/commonMain/kotlin/com/worksoc/goaicoach/application/` 아래에 있다(**260816에 `app-android`→`:shared` 물리 이전 완료**, 패키지명은 `com.worksoc.goaicoach.application.*` 그대로라 호출부 import는 바뀌지 않았다).

- `application/engine/EngineSessionClient.kt` — UI/App Service가 바라보는 고수준 엔진 게이트웨이 인터페이스. `analyzePosition(state, limit, searchMode)`처럼 명시적 `GameState`를 받아 local/remote 차이를 숨김. `forceResetEngine()`처럼 비정상 상태 복구용 진입점도 여기 있다
- `application/engine/LocalEngineSessionClient.kt` + `LocalAiMoveEngineGateway.kt`, `LocalEndgameJudgeGateway.kt`, `LocalEngineCoreSessionDelegate.kt`, `LocalEngineBenchmarkDelegate.kt`, `LocalPositionAnalysisCacheCoordinator.kt` — local 구현체, 역할별 delegate로 분리. 공개 오퍼레이션은 **오퍼레이션 락** 안에서 한 번에 하나씩 돈다(refactor backlog #15) — 추천 수 분석·형세 추정은 기다리지 않고 `EngineOperationBusy`로 포기해 5계층의 "잠시 뒤"·"바쁘다" 흐름으로 가고, 나머지는 기다린다. `forceResetEngine`은 락을 잡지 않는다(함정 71). 누가 기다리고 누가 포기하는지, 오퍼레이션 마감을 두지 않는 이유는 그 락의 KDoc
- `application/engine/operation/` — `EngineOperationLifecycleController` 등. 동시 엔진 호출 추적, 늦게 도착한 결과 폐기(stale guard), busy 상태 관리. ⚠️ 여기의 busy는 세대로 거른 **장부**다 — 엔진이 실제로 비었는지는 모른다. 엔진 호출의 직렬화는 위 오퍼레이션 락이 한다
- `application/safety/EngineTurnWatchdog.kt` — AI 턴이 설정된 응답 시간(×1.2+3초, 무제한이면 60초)을 넘기면 감지하는 순수 판정 로직. 2026-07-30 신설, 260816 `:shared` 이전의 스파이크 대상이었다(`LAYER_5_APPLICATION_DOMAIN.md`의 옛 로드맵 5번 (1)항)
- `application/analysis/PositionAnalysisCache.kt`, `PositionAnalysisCacheOptimization.kt` — JSON position analysis 결과를 품질/origin별로 저장하는 디스크 캐시와 그 최적화 **모델**
- `application/cacheoptimization/PositionAnalysisCacheOptimizationRunnerApplication.kt`, `PositionCacheOptimizationController.kt`, `PositionAnalysisCacheOptimizationWorkflowResult.kt` — 그 최적화를 **엔진으로 실행하는 쪽**. 2026-09-24 refactor backlog #32⑤(`029aa35c`)가 `analysis`에서 떼어 냈다 — 실행부가 `EngineSessionClient`를 보는 탓에 `analysis`가 `engine`과 사이클을 이루고 있었다. 이제 `engine → analysis`가 정방향이다.
- `application/engine/RemoteEngineCandidate.kt` — DePIN 준비(260804 Stage E-1). 원격 후보 표현(`RemoteEngineCandidate`)과 선택/신뢰도 판단(`selectRemoteEngineCandidate`). `engine-android`를 import하지 않는 순수 3계층 판단 — 실제 `EngineCoreApi` 배선은 `app-android/.../engine/RemoteEngineSessionBootstrap.kt`(아래 참고)가 담당
- `application/analysis/PositionAnalysisCacheResolver.kt` — 신뢰도 등급에 따라 캐시 hit을 평가/서빙.
  ✅ **2026-09-23 정리(refactor backlog #28, `2254d324`)**: 예전엔 `application/` 트리 **밖**의 형제 패키지 `com.worksoc.goaicoach.middleware`에 있었고, `:shared` 안에서 `application`을 거꾸로 import하는 유일한 파일이었다. `application.analysis`로 흡수해 그 역방향을 없앴다(`:shared`에는 이제 `middleware` 패키지가 없다). ⚠️ 이름이 같은 `app-android/.../middleware/`는 **별개**다 — 그쪽은 2계층 게이트웨이이고 그대로 있다(`LAYER_2_MIDDLEWARE_BRIDGE.md`).
- `shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/policy/EngineOperationPolicy.kt` — 엔진 작업이 막힌 이유를 타입으로 표현(`EngineOperationBlockReason`/`EngineOperationGate`). `application/` 트리 밖의 `shared.policy` 패키지에 있다(2026-09-23 매핑 편입, 260924 `shared.engine`에서 `shared.policy`로 이름 정리 — refactor backlog #70)
- `app-android/.../engine/EngineBootstrap.kt`, `RemoteEngineSessionBootstrap.kt`, `DeferredEngineCoreApi.kt`, `EngineIdentity.kt` — `application/`이 아닌 별도 패키지(`com.worksoc.goaicoach.engine`)에 있는 composition-root 인접 배선 파일들. `application/`은 `engine.android`를 import할 수 없다는 경계(`LayeringContractTest`) 때문에, "engine-android 구현을 실제로 생성"하는 코드는 전부 여기 산다 — 로컬은 `EngineBootstrap.createEngineBootstrap`, 원격은 `RemoteEngineSessionBootstrap.createRemoteEngineSessionClient`

**재편 여부**: 기존 4계층(Middleware/Cache Domain)에서 전송(2계층으로 이동)을 뺀 나머지 — 캐시, 신뢰도 라우팅, 동시성 lifecycle, 엔진 턴 와치독까지 전부 여기.

**DePIN 관점에서의 역할(부분 착수, 260804 Stage E-1)**: 1계층이 여러 피어로 흩어지면, "지금 어느 피어를 쓸지 선택하고 그 결과를 얼마나 신뢰할지 판단"하는 책임이 이 계층으로 들어와야 한다. `selectRemoteEngineCandidate`가 그 자리를 잡았지만, 지금은 후보가 항상 최대 1개라 판단이 "활성화돼 있는가/엔드포인트가 유효한가"만큼만 있다 — 여러 후보의 응답 시간·성공률을 비교하는 진짜 신뢰도 판단은 실제로 후보가 2개 이상 생기는 시점(Stage F, DePIN 확장)에 채워야 한다.

**계약이 아는 것 — 2026-09-24(refactor backlog #73)**: 원칙 문서가 3계층 경계 원칙도 4계층과 같은 어휘(*"위 계층의 동작은 모르고, 값과 순수 규칙만 안다"*)로 고쳤다. 5계층이 보는 `EngineSessionClient`는 시그니처 타입 23개의 필드 폐포 52개 타입(주 생성자 필드 기준)이 전부 값이다. 벗어나는 것은 하나 — `runStartupBenchmark(onProgress: suspend (EngineBenchmarkProgress) -> Unit)`의 `onProgress`는 4계층 규칙 ⓐ가 금지하는 동작 콜백이다(값의 흐름으로 돌려주면 풀린다). 구현 쪽은 **4계층과 닿는 자리만** 쟀다(`LAYER_4_EXTERNAL_INTEGRATION.md` 「3·4계층을 건너는 참조」) — 4계층 포트를 쥐는 3계층 파일 11개는 전부 **네 조건을 채우는 관측 포트**(`DiagnosticEventLogPort`·`RuntimeEventLogPort` — 조건 판정은 `LAYER_4_EXTERNAL_INTEGRATION.md`)에 덧붙이기만 해서 원칙 문서의 관측 포트 예외에 들고, 그 밖의 3→4 참조는 0이다(`EngineBenchmarkStorePort`는 3계층 자신의 계약으로 센다). 위 계층 **동작**을 부르는지(ⓑ의 첫째 금지)와 나머지 3계층 계약(`AiMoveEngineGateway`·`EndgameJudgeGateway` 등)은 재지 않았다 — `GO_AI_COACH_ARCHITECTURE_ROADMAP.md` 「알려진 갭」의 L3→L5 import 169건이 그 후보다.

## 지키는 테스트

- `LayeringContractTest.localEngineSessionDelegateOwnsSessionOrchestration` · `localEngineBenchmarkDelegateOwnsRawBenchmarkExecution` — 엔진 세션 조율과 벤치마크 실행이 제자리에 있다.
- `LayeringContractTest.engineOperationApplicationPoliciesStayPortable` — `:shared` application 전부의 플랫폼 이식성.

## 갭과 남은 일

### 닫힌 것

2. ~~**3계층 — `RemoteEngineSessionClient` 도입**~~ — 최소 형태 완료(260804 Stage E-1/E-2). 여러 원격/피어 후보 중 선택·신뢰도 판단을 흡수하는 자리(`selectRemoteEngineCandidate`)는 마련됐지만, 지금은 후보가 1개뿐이라 판단이 얕다. 후보가 실제로 여러 개가 되는 시점(DePIN 방향)에 응답시간/성공률 비교, "피어 평판/정산 기록"의 자리를 채워야 한다.

- ~~`RemoteEngineSessionClient`(3계층, 여러 원격 후보 중 선택·신뢰도 판단)가 없다~~ — 260804 Stage E-1/E-2에서 최소 형태로 해소(`selectRemoteEngineCandidate`+`RemoteEngineSessionBootstrap.createRemoteEngineSessionClient`). **260818 Stage E-3에서 `MainActivity`에 `BuildConfig.DEBUG` 한정으로 실제 배선됐다**(refactor backlog #110부터 `GoCoachProcessRuntime`). 남은 갭은 "배선"이 아니라 런타임 실패 감지/폴백과 여러 후보 비교 신뢰도 판단이며, Stage F 영역이다.
