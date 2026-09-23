# go-ai-coach 아키텍처 현황과 고도화 로드맵

작성일: 2026-07-30
갱신: 2026-09-23 — 260816 `:shared` 대이동을 매핑에 반영해 **아래 경로를 전부 실재 경로로 되돌렸고**, 매핑 밖에 떠 있던 패키지(`persistence/`·`vision/`·`shared/shared/` 하위)를 계층에 편입했으며, 2026-09-23 실측(import 방향 위반·`application/` 내부 SCC)을 「알려진 갭」에 편입했다.
레이어 순서 갱신: 2026-07-30 — External Integration이 4계층(3계층과 대등한 서비스 계층)으로 재배치되며 Application(5)/Session & Continuity(6)/Presentation(7) 번호가 한 칸씩 밀렸다. `docs/ARCHITECTURE.md`의 "레이어 순서 확정" 항목 참고.
기능 엔타이틀먼트 정책 배치: 2026-08-14 — "무료/광고/구매/클레임" 같은 기능별 정책이 앞으로도 계속 바뀔 것을 전제로, 그 정책 판정을 6계층에 `FeatureAccessPolicy`로 명문화하고(설계 초안엔 5계층으로 잘못 적었다가 착수 시점에 정정 — 6계층 `PremiumState`를 파라미터로 받으므로 5계층일 수 없다) 6계층 `PremiumState`를 단일 플래그(`isUndoClaimed`)에서 기능별 원장(`claimedFeatures: Set<FeatureId>`)으로 일반화, 프레젠테이션 3곳(`ui/GamePlaySection.kt` 2곳, `ui/KaTrainUxPanels.kt` 1곳)에 하드코딩돼 있던 판정을 이걸로 교체했다 — 구현 완료. 상세는 "알려진 갭"·"고도화 로드맵" 절, 그리고 `docs/spec/FEATURE_ACCESS_PRINCIPLES.md` 2장(같은 결론을 정책 문서 쪽에서 먼저 제안해 뒀던 것).

---

## 어떻게 읽는가 — 아키텍처 문서는 넷이고, 역할이 다르다

1. `docs/ARCHITECTURE.md` — **원칙**. 7계층의 정의와 그 이유. 앱 비종속이라 코드 이동과 무관하게 유효하다.
2. `docs/spec/GO_AI_COACH_ARCHITECTURE_ROADMAP.md`(**이 문서**) — **정본 매핑**. *"지금 무엇이 어디 있는가"* 와 *"물리 분리까지 무엇이 남았는가"*. 코드가 움직이면 여기가 따라 움직인다.
3. `work/plans/REMOTE_ENGINE_AND_LAYERING.md` — **실행 Stage 로그**. Stage A~E의 착수·완료 기록이 시간 순으로 쌓인다(Stage F는 `work/roadmap/REMOTE_ENGINE_MQ_TRANSPORT_KICKOFF_PLAN_260818_0825.md`로 분리). ⚠️ **2026-09-23에 개명·이동했다** — 옛 이름 `work/roadmap/LAYERED_ARCHITECTURE_REFACTORING_PLAN_260803_1500.md`로 찾으면 없다. 계층 정렬(Stage A~C)의 정본은 아래 4번으로 넘어갔고, 그 문서에 살아 있는 축은 원격 엔진(Stage D~F)이다.
4. `work/roadmap/260923-_ARCHITECTURE_DIAGNOSIS_AND_REFACTORING.md` — **2026-09-23 실측 기록·처방·함정 A~J**. *"그날 재어 보니 이랬다"* 를 남기는 문서라 **본문은 그 시점 그대로 두고 갱신하지 않는다.** 실측 결론만 이 문서로 흡수한다. 함정 A~J는 `docs/spec/PITFALLS.md`에 **67~76번으로 편입**됐고 번호 쪽이 정본이다.

⚠️ **넷이 서로 다른 폴더에 있다**(`docs/`·`docs/spec/`·`work/plans/`·`work/roadmap/`). 파일명만으로는 못 찾는 경우가 있으니 위 경로 그대로 연다.

**성격**: 이 문서는 `docs/ARCHITECTURE.md`(원칙 문서, 앱 비종속)의 7계층 모델을 go-ai-coach 코드베이스에 적용한 **파생 문서**다. 레이어 정의 자체나 그 이유는 여기서 반복하지 않고 원칙 문서를 따른다.

기존 `docs/ARCHITECTURE.md`가 갖고 있던 계층별 파일/패키지 표는 이 문서로 이전됐다 — 이전 버전은 git 히스토리로 확인 가능하다(`git log -p -- docs/ARCHITECTURE.md`).

---

## 계층별 현재 매핑

> "재편 여부" 항목은 2026-06-27판 7계층(Engine Runtime/Transport → Engine Core API Domain → Core Rules Domain → Middleware/Cache Domain → Game Domain → App Service/Session Orchestration → Presentation/Game UX) 대비 2026-07-30 재정의에서 경계가 바뀐 지점을 표시한다.
>
> ✅ **260816 이전 완료 — 아래 경로는 전부 실재 경로다**(2026-09-23 `find`로 재확인). 2026-07-30판에 있던 *"코드는 아직 옮기지 않았다, 아래는 개념적 재배치"* 경고는 더 이상 사실이 아니라 걷어냈다. 260816 웨이브 1~6으로 `application/` 트리 124개 프로덕션 파일과 `match/`가 `app-android`에서 `:shared`로 물리 이전됐고, 지금 `app-android/.../application/`에 남은 것은 영구 예외 `diagnostic/LocalFileDiagnosticEventExternalSink.kt` **하나뿐**이며 `app-android/.../match/`는 **디렉터리 자체가 없다.**

### 1계층 — Physical Compute

**위치**: `engine-android/src/main/java/com/worksoc/goaicoach/engine/android/KataGoProcessRuntime.kt`(실행 파일/모델 검증, CLI 인수 빌드, 프로세스 시작/종료)

**재편 여부**: 기존 1계층은 `engine-android` 패키지 전체(어댑터 포함)였다. 이번 재정의에서는 **"실제 KataGo 바이너리 프로세스를 실행/관리하는 부분"만** 1계층으로 좁히고, 그 바이너리에 GTP/JSON으로 말을 거는 어댑터는 2계층으로 옮겼다 — "어디서 도는가"(1계층)와 "어떻게 그것과 통신하는가"(2계층)를 분리하기 위함.

### 2계층 — Middleware / Bridge

**위치**:
- `shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/EngineModels.kt` — `EngineCoreApi` 인터페이스(1:1 원시 계약: `initialize`, `configure`, `playMove`, `analyze`, `estimateScore`, `deadStones`, `scoreFinal`, `clearSearchCache`, `stop`, `forceReset` 등), `AnalysisLimit`/`EngineProfile`/`CandidateMove` 등 순수 데이터 모델. 같은 폴더의 `RemotePositionAnalysisTransport.kt` — position-analysis 단위 원격 호출 계약(`RemotePositionAnalysisTransport`/`Request`/`Response`, 260804 이전엔 app-android에 있었음, §재편 여부 참고)
- `engine-android/.../KataGoProcessEngineAdapter.kt` — `EngineCoreApi`의 **로컬** 구현체. GTP(`KataGoGtpAnalysisClient.kt`, `KataGoProtocolCommands.kt`)와 JSON(`KataGoJsonPositionAnalysisClient.kt`, `KataGoJsonAnalysisQueryFactory.kt`, `KataGoJsonAnalysisParser.kt`) 두 경로를 조율. 두 경로 공통 파싱은 `KataGoAnalysisParser.kt`/`KataGoAnalysisContext.kt`
- `engine-android/.../StubEngineAdapter.kt` — `EngineCoreApi`의 **스텁** 구현체(엔진 없이 UI/도메인 검증용)
- `engine-android/.../RemoteEngineCoreApiAdapter.kt` — `EngineCoreApi`의 **원격** 구현체(13개 메서드 전체, 260803 Stage D). 상태변경 호출은 로컬에서 `GameState`를 추적하고, `genMove`/`analyze`/`estimateScore`/`deadStones`/`scoreFinal`만 원격 전송하는 상태 비저장 설계. `HttpRemoteEngineOperationTransport`가 HTTP 구현체
- `engine-android/.../HttpRemotePositionAnalysisTransport.kt` — `RemotePositionAnalysisTransport`의 read-only position-analysis 단위 트랜스포트 스파이크 구현체(2026-06-28 기준 기본값 off)
- `app-android/src/main/java/com/worksoc/goaicoach/middleware/RemotePositionAnalysisGateway.kt` + 같은 폴더의 `PositionAnalysisGateway.kt`/`JsonNullableExtensions.kt` — 위 트랜스포트를 3계층 `PositionAnalysisGateway` 계약으로 감싸는 어댑터(app-android에 잔류, `:shared`의 `RemotePositionAnalysisTransport`만 알고 실제 구현체 이름은 모름)

**재편 여부**: 기존 2계층(Engine Core API Domain, 계약 정의만)에 기존 4계층(Middleware/Cache Domain)의 **전송** 절반(원격 게이트웨이/트랜스포트)을 합쳤다. "계약을 정의하는 것"과 "그 계약을 실제로 어떻게 도달시키는가(로컬 stdio냐 원격 HTTP냐)"가 개념적으로 같은 책임이라고 보기 때문이다. **260804 정리**: `EngineCoreApi`의 로컬/원격 구현체를 전부 `engine-android` 모듈로 물리적으로 모았다(그 전엔 원격 구현체가 app-android/middleware에 있었음) — app-android(3~7계층) 작업 시 엔진 내부 구현을 아예 안 봐도 되도록, 그리고 향후 원격/DePIN 확장의 물리적 근간이 되도록. 이 이동을 가능케 하려고 `RemotePositionAnalysisTransport`/`Request`/`Response`(전부 `:shared`-safe 타입만 사용)도 `:shared`로 옮겼다 — app-android(Gateway)와 engine-android(Http 구현체)가 순환 의존 없이 같은 계약을 공유하기 위함.

**핵심 갭(해소됨, 260803 Stage D)**: `KataGoProcessEngineAdapter`(로컬)와 `RemoteEngineCoreApiAdapter`(원격)가 이제 `EngineCoreApi` 전체에 대해 대등한 계약을 만족한다(계약 테스트로 검증). 3계층의 후보 선택/신뢰도 판단(`selectRemoteEngineCandidate`)도 260804 Stage E-1/E-2에서 마련됐다 — 아래 3계층 절 참고. **260818 갱신**: 이 원격 경로는 이제 `MainActivity`에 `BuildConfig.DEBUG` 한정으로 배선돼 있다(Stage E-3) — `local.properties`의 `debug.remoteEngineUrl`을 맥북 참조 서버(`scripts/run-katago-remote-analysis-server.py`)로 가리키면 실제 대국이 그 서버를 왕복한다(에뮬레이터 e2e 검증 완료). 기본값은 꺼짐이며, 앱 시작 시 한 번만 원격/로컬을 고르고 런타임 실패를 감지해 되돌리지 않는 한계가 남아 있다. 그 재설계와 MQ 전송 전환은 `work/roadmap/REMOTE_ENGINE_MQ_TRANSPORT_KICKOFF_PLAN_260818_0825.md`가 이어받았다 — Stage F(실제 물리 분산) 영역이라 앱 이식은 여전히 별도 승인 필요.

### 3계층 — Extended API (엔진 서비스)

**위치**: 아래 항목은 별도 표시가 없는 한 전부 `shared/src/commonMain/kotlin/com/worksoc/goaicoach/application/` 아래에 있다(**260816에 `app-android`→`:shared` 물리 이전 완료**, 패키지명은 `com.worksoc.goaicoach.application.*` 그대로라 호출부 import는 바뀌지 않았다).

- `application/engine/EngineSessionClient.kt` — UI/App Service가 바라보는 고수준 엔진 게이트웨이 인터페이스. `analyzePosition(state, limit, searchMode)`처럼 명시적 `GameState`를 받아 local/remote 차이를 숨김. `forceResetEngine()`처럼 비정상 상태 복구용 진입점도 여기 있다
- `application/engine/LocalEngineSessionClient.kt` + `LocalAiMoveEngineGateway.kt`, `LocalEndgameJudgeGateway.kt`, `LocalEngineCoreSessionDelegate.kt`, `LocalEngineBenchmarkDelegate.kt`, `LocalPositionAnalysisCacheCoordinator.kt` — local 구현체, 역할별 delegate로 분리
- `application/engine/operation/` — `EngineOperationLifecycleController` 등. 동시 엔진 호출 추적, 늦게 도착한 결과 폐기(stale guard), busy 상태 관리
- `application/safety/EngineTurnWatchdog.kt` — AI 턴이 설정된 응답 시간(×1.2+3초, 무제한이면 60초)을 넘기면 감지하는 순수 판정 로직. 2026-07-30 신설, 260816 `:shared` 이전의 스파이크 대상이었다(아래 "고도화 로드맵" 5번 (1)항)
- `application/analysis/PositionAnalysisCache.kt`, `PositionAnalysisCacheOptimization.kt`, `PositionAnalysisCacheOptimizationRunnerApplication.kt`, `PositionCacheOptimizationController.kt` — JSON position analysis 결과를 품질/origin별로 저장하는 디스크 캐시와 그 최적화
- `application/engine/RemoteEngineCandidate.kt` — DePIN 준비(260804 Stage E-1). 원격 후보 표현(`RemoteEngineCandidate`)과 선택/신뢰도 판단(`selectRemoteEngineCandidate`). `engine-android`를 import하지 않는 순수 3계층 판단 — 실제 `EngineCoreApi` 배선은 `app-android/.../engine/RemoteEngineSessionBootstrap.kt`(아래 참고)가 담당
- `shared/src/commonMain/kotlin/com/worksoc/goaicoach/middleware/PositionAnalysisCacheResolver.kt` — 신뢰도 등급에 따라 캐시 hit을 평가/서빙.
  ⚠️ **`middleware`는 `application`의 하위가 아니라 형제다.** 2026-07-30판이 `application/middleware/...`로 적어 뒀으나 **그런 경로는 존재한 적이 없다** — 이 파일의 패키지 선언은 `com.worksoc.goaicoach.middleware`이고 위치도 `application/` 트리 **밖**이다. 같은 이름의 형제 패키지가 `app-android/.../middleware/`에도 따로 있고 그쪽은 2계층 게이트웨이다(위 2계층 절). **모듈을 나누는 날 경계가 갈리는 자리**이므로 `application/middleware`라고 적지 않는다.
- `shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/engine/EngineOperationPolicy.kt` — 엔진 작업이 막힌 이유를 타입으로 표현(`EngineOperationBlockReason`/`EngineOperationGate`). `application/` 트리 밖의 `shared.engine` 패키지에 있다(2026-09-23 매핑 편입)
- `app-android/.../engine/EngineBootstrap.kt`, `RemoteEngineSessionBootstrap.kt`, `DeferredEngineCoreApi.kt`, `EngineIdentity.kt` — `application/`이 아닌 별도 패키지(`com.worksoc.goaicoach.engine`)에 있는 composition-root 인접 배선 파일들. `application/`은 `engine.android`를 import할 수 없다는 경계(`LayeringContractTest`) 때문에, "engine-android 구현을 실제로 생성"하는 코드는 전부 여기 산다 — 로컬은 `EngineBootstrap.createEngineBootstrap`, 원격은 `RemoteEngineSessionBootstrap.createRemoteEngineSessionClient`

**재편 여부**: 기존 4계층(Middleware/Cache Domain)에서 전송(2계층으로 이동)을 뺀 나머지 — 캐시, 신뢰도 라우팅, 동시성 lifecycle, 엔진 턴 와치독까지 전부 여기.

**DePIN 관점에서의 역할(부분 착수, 260804 Stage E-1)**: 1계층이 여러 피어로 흩어지면, "지금 어느 피어를 쓸지 선택하고 그 결과를 얼마나 신뢰할지 판단"하는 책임이 이 계층으로 들어와야 한다. `selectRemoteEngineCandidate`가 그 자리를 잡았지만, 지금은 후보가 항상 최대 1개라 판단이 "활성화돼 있는가/엔드포인트가 유효한가"만큼만 있다 — 여러 후보의 응답 시간·성공률을 비교하는 진짜 신뢰도 판단은 실제로 후보가 2개 이상 생기는 시점(Stage F, DePIN 확장)에 채워야 한다.

### 4계층 — External Integration (외부 연동 · 기기 영속 저장)

**위치**:
- `shared/.../application/auth/AuthClientPort.kt`(α: 순수 포트, **260816** `app-android`→`shared` 이전 완료) ↔ `app-android/.../ui/AndroidAuthClient.kt`(Firebase Auth 어댑터 — Extended API 본체가 실제 SDK에 닿는 지점)
- `shared/.../application/premium/PremiumStatePorts.kt`·`PurchasePort.kt`·`AdRewardPort.kt`(α: 순수 포트) ↔ `app-android/.../persistence/PremiumStateStore.kt`(SharedPreferences 어댑터), `app-android/.../ui/AndroidBillingClient.kt` 등 SDK 어댑터
- `app-android/.../ui/AndroidPlatformPorts.kt` — 가벼운 플랫폼 포트(클립보드, 토스트) 공용 파일
- **`app-android/src/main/java/com/worksoc/goaicoach/persistence/`(2026-09-23 기준 23개 파일)** — **기기 영속 저장**. `GameSessionStore.kt`·`GameHistoryStore.kt`·`UserPreferencesStore.kt`·`AttendanceStore.kt`·`BotCollectionStore.kt` 등. 전부 `org.json`/`Context`/`java.io.File`에 결합된 어댑터라 그대로 `:shared`로 옮길 수 있는 파일이 0개다.
  ⚠️ **2026-09-23에 4계층 정의에 편입했다.** 그 전까지 이 23개 파일은 **어느 계층에도 배정돼 있지 않았고**, 매핑에 없는 패키지에는 계층 규칙이 적용되지 않는다 — 즉 사각지대였다. 편입 근거는 `work/roadmap/260923-_ARCHITECTURE_DIAGNOSIS_AND_REFACTORING.md` §3("4계층에 기기 영속 저장을 포함한다").
- **`shared/.../shared/vision/BoardVisionModels.kt`(α: 순수 포트/모델 — `BoardVisionScannerPort`, `BoardCornerPoints`, `DetectedBoard`) ↔ `app-android/src/main/java/com/worksoc/goaicoach/vision/`(3개 파일: `AndroidBoardVisionScanner.kt`·`AndroidBitmapPerspectiveTransformer.kt`·`GridStoneDetector.kt`, `android.graphics.Bitmap` 어댑터)** — 다른 4계층 항목과 **정확히 같은 포트/어댑터 짝**이다. 2026-09-23 매핑 편입(위 `persistence/`와 같은 이유).

**재편 여부**: 기존 모델에는 이 계층이 없었고 "포트/어댑터 분리 원칙"이라는 (구)4계층 문서의 부칙으로만 존재했다. 2026-07-30에 3계층과 대등한 정식 서비스 계층으로 승격했고, **2026-09-23에 정의를 "외부 SDK 연동 + 기기 영속 저장"으로 확장**했다 — 둘 다 "앱 밖의 무언가(SDK든 파일시스템이든)에 닿는 어댑터"라는 같은 성격이고, 분리해 두면 persistence가 모델 밖에 떠 있게 되기 때문이다(자세한 논거는 `docs/ARCHITECTURE.md` 4계층 절과 진단서 §3).

**핵심 갭 (260817 정정 → 2026-09-23 재정정)**:
- **AdMob**: 실제 계정·광고 단위로 라이브(빌드타입별 테스트/실제 광고 전환 포함, `[[premium-admob-status]]`).
- **Play Billing**: *"Play Console 상품 등록에 막혀 있다"* 는 **2026-08 서술이고 더 이상 사실이 아니다.** `app-android/.../ui/FeatureFlags.kt`의 `isPurchaseEnabled = true`이며(2026-09-18 전환, 2026-09-23 코드 확인) 파는 것은 **월 구독 3,900원 하나**다. 봇 캐릭터 개별 구매만 `isBotCharacterPurchaseEnabled = false`로 별도 대기 중이다(`[[premium-billing-status]]`). ⚠️ 이 플래그는 **스토어 등재문과 묶여 있다**(`StoreListingPaymentContractTest`가 양방향을 막는다).
- **Google/이메일 로그인**: 실제 Firebase로 기기 검증까지 끝났으나 `FeatureFlags.isLoginEnabled = false`로 앱의 로그인 진입 경로 자체가 닫혀 있다(2026-09-23 코드 확인). Email Link는 별도로 의도적 보류(`[[auth-google-signin-status]]`). **익명 로그인은 2026-08-05에 영구 폐기**돼 선택지에 없다.

포트(α)는 이미 이 계층 원칙대로 배치돼 있으니, 새 SDK 연동 시 파일을 어디 둘지는 이미 정해져 있다. **진짜 남은 갭**은 "Extended API 본체"(실패/재시도/캐시까지 감안한 안정화 서비스)가 아직 얇다는 것이다: `AndroidAuthClient`/`PremiumStateStore`는 SDK 호출을 그대로 감싸는 수준이라, 3계층의 `PositionAnalysisCacheResolver` 같은 신뢰도/재시도 판단이 없다. 착수 시 유의: 로그인 쪽 하드닝은 `isLoginEnabled`가 켜지기 전까지 실기로 검증할 방법이 없다.

### 5계층 — Application / Domain

**위치**:
- `shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/` — 순수 바둑 규칙(KMP `commonMain`). `BoardModels.kt`, `BoardRules.kt`, `LegalMoveGenerator.kt`, `BoardScorer.kt`(+`BoardAreaScorer.kt`/`BoardTerritoryScorer.kt`), `DeadStoneDetector.kt`/`DeadStoneCleaner.kt`, `EndgameScoreSelector.kt`, `GameStateReplayer.kt`, `ScoreTimeline.kt` 등
- `shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/diagnostic/DiagnosticEventModel.kt` — 진단 이벤트 순수 모델(`DiagnosticEvent`/`DiagnosticSeverity`). 실제 기록은 4계층 어댑터가 한다(2026-09-23 매핑 편입)
- `shared/src/commonMain/kotlin/com/worksoc/goaicoach/match/MatchReferee.kt`, `AiMoveSelectionPolicy.kt`, `MatchPolicy.kt`, `MatchTurnOrchestration.kt` — 대국 정책(참여 주체, 턴 권한, AI 레벨링). **260804 경로 정정**: 이전엔 `app-android/.../match/`였으나 "도메인별 파일 분리" 작업(커밋 `5278c12`)으로 `shared`로 이동했다 — 순수 로직이라 KMP 이식 대상이었다. 지금 `app-android`에는 `match/` 디렉터리가 없다.
- `shared/src/commonMain/kotlin/com/worksoc/goaicoach/application/` — App Service 유스케이스 오케스트레이션. **2026-09-23 기준 29개 서브패키지**: `analysis`·`attendance`·`auth`·`autoai`·`botcharacter`·`concurrency`·`consumable`·`debugreport`·`device`·`diagnostic`·`endgame`·`engine`·`gamehistory`·`guide`·`humanmove`·`lifecycle`·`movereview`·`preferences`·`premium`·`prompt`·`runtime`·`safety`·`savedgame`·`score`·`session`·`startgame`·`time`·`topmoves`·`undo`(일부는 위 3·4·6계층 절 소속). **260816**: 웨이브 1~6으로 `app-android`에서 `shared`로 물리 이전 완료(영구 예외 1개, 아래 "핵심 갭" 참고). `session/GameSessionStateHolder.kt`가 세션 상태의 단일 source of truth
- `app-android/.../ui/GoCoachApp.kt` — 위 모든 컨트롤러를 생성/연결하는 composition root. `LayeringContractTest`의 `lineBudget`/`stateHookBudget`이 예산을 강제한다.
  ⚠️ **단위가 raw 줄 수가 아니다.** 2026-09-05(백로그 #102)부터 `codeLinesOf`가 **import·`package`·주석·빈 줄을 빼고** 센다 — *"왜 이 순서여야 하는가"* 를 주석으로 적으면 예산이 깎이던 왜곡을 없애기 위해서다. raw 줄 수와 예산을 나란히 비교하면 틀린 계산이 된다.
  **2026-09-23 기준 실측**: 코드 줄 **733 / 예산 777**(여유 44), 상태 훅 **42 / 예산 42**(**여유 0**). raw는 954줄이고, 같은 날 `b19bdc57`(중복 `EngineBenchmarkController` 제거)로 raw 970→954가 됐다 — 다만 **그 삭제된 지역 변수에는 `remember`/`mutableStateOf`/`LaunchedEffect`가 하나도 없어 훅 예산은 42 그대로, 회수 0**이다(진단서 함정 H).
  ⚠️ **조이는 힘은 훅 쪽에 있다.** 훅이 1차 지표이고 여유가 0이며, 줄 수는 뒷받침이라 **일부러 여유를 뒀다**. 예산을 올릴 때는 그 사유를 `LayeringContractTest` 안의 주석 이력에 남긴다 — 사유 없이 올리는 순간 이 그물은 뜻을 잃는다.
- `app-android/src/main/java/com/worksoc/goaicoach/` 최상위(`MainActivity.kt`, `GoAiCoachApplication.kt`, `AppForegroundEvents.kt`, `AttendanceCheckInCoordinator.kt`, `DeveloperModeResetCoordinator.kt`, `ReleaseResetCoordinator.kt`) — **조립 전용 루트 패키지**. 전 계층을 참조해도 되는 자리다(진단서 §3의 처방을 따라 2026-09-23에 명문화 — 그전엔 이것도 모델 밖에 떠 있었다).

**260814 정정**: 이 절에 한때 "기능별 접근 정책 판정"(`FeatureAccessPolicy`)을 5계층으로 적어뒀으나 오기였다 — `PremiumState`(6계층)를 파라미터로 받는 함수는 5계층이 아니라 6계층 소속이다(5계층은 6계층을 몰라야 하므로). 실제 구현은 아래 6계층 절 참고.

**재편 여부**: 기존 3계층(Core Rules)+5계층(Game Domain)+6계층(App Service/Session Orchestration)을 하나로 통합. 순수 규칙과 오케스트레이션은 성격이 다르지만 "이 앱만의 것"이라는 공통점으로 묶었다 — `docs/ARCHITECTURE.md`의 5계층 정의를 따른다. 위 3계층(엔진 서비스)과 4계층(외부 연동 서비스)을 동등하게 소비한다.

**핵심 갭**: 예외 1개만 남았다 — `app-android/.../application/diagnostic/LocalFileDiagnosticEventExternalSink.kt`(`java.io.File` 직접 사용)는 포트/어댑터 분리 원칙에 따라 영구히 `app-android`에 잔류(포트 `DiagnosticEventExternalSinkPort`는 `shared`로 이전됨). `LayeringContractTest.kt`의 `engineOperationApplicationPoliciesStayPortable`이 이 예외 하나만 명시적으로 허용하고 나머지는 전부 `shared`에서 이식성을 상시 검증한다.

### 6계층 — Session & Continuity

**위치**:
- `shared/.../application/auth/AuthState.kt`, `AuthClientPort.kt` — 로그인 상태 순수 모델 + 포트
- `shared/.../application/premium/PremiumState.kt`, `PremiumStatePorts.kt` — 프리미엄 활성화 상태. `matchGeneration`(대국 세대 — 무르기로는 바뀌지 않음)으로 5계층의 `sessionGeneration`(엔진 오퍼레이션 무효화 세대 — 무르기마다 바뀜)과 **의도적으로 분리**돼 있다(2026-07-30 수정 — 이 분리가 없어서 무르기 시 프리미엄이 풀리는 버그가 있었다). 필드는 `source`(None/AdGrant/Purchase) + `adGrantStartedAtMillis` + `claimedFeatures: Set<FeatureId>`. **260814**: 예전엔 무르기 하나만을 위한 `isUndoClaimed: Boolean`이었으나, 앞으로 다른 기능도 같은 방식(초도 클레임+그랜드파더링)으로 무료 제공할 때 새 불리언을 또 추가하지 않도록 기능별 원장(`claimedFeatures`)으로 일반화했다. `source`/`adGrantStartedAtMillis`(구독형 축)와 `claimedFeatures`(1회 클레임형 축)는 여전히 서로 다른 축으로 분리돼 있다 — 위 문단이 이미 겪은 "축 혼동 버그"를 반복하지 않기 위함.
- **(260814 신설)** `shared/.../application/premium/FeatureAccessPolicy.kt` — 기능별 접근 정책 판정. `PremiumState`(바로 위 항목, 6계층 — "지금 무엇을 갖고 있는가")를 입력으로 받아, 기능 하나(`FeatureId`)에 대해 "지금 쓸 수 있는가, 없다면 무엇으로 풀 수 있는가"를 순수 함수로 판정한다 — `fun resolve(featureId: FeatureId, state: PremiumState, nowMillis: Long): FeatureAccess`(`FeatureAccess` = `Allowed(via: AllowedVia)` | `Locked(unlockOptions: Set<UnlockOption>)`). `PremiumState`를 입력으로 받으므로 6계층 소속이다(5계층은 6계층을 몰라야 함) — `PremiumState.isActive()`가 이미 같은 이유로 `PremiumState` 자신에 있는 선례를 따름. `ui/GamePlaySection.kt`의 `featureGated(access, action)`·`ui/KaTrainUxPanels.kt`의 `moveReviewAllowed`가 이 판정을 소비한다(둘 다 더 이상 `isActive`/클레임 여부를 직접 조합하지 않는다).

**재편 여부**: 신규 계층(번호만 5→6으로 이동, 정의는 그대로). 기존 7계층 모델에는 없었고, `application/auth`/`application/premium`이 사실상 이 자리를 채우고 있었지만 명문화된 계층은 아니었다.

**핵심 갭**: 아직 "세션/연속성"이라는 이름에 걸맞은 범용 개념(기기 식별자, 게스트→실계정 승격, 다중 기기 정책)이 없다 — 지금은 auth/premium 각자가 필요한 만큼만 자기 상태를 갖고 있다. ⚠️ **`work/plans/LOGIN_AND_ACCOUNT_SYSTEM.md`의 "익명 인증 → 실계정 승격" 로드맵을 그대로 집어오면 안 된다 — 익명 로그인은 2026-08-05에 영구 폐기됐다**("재설치마다 허수 계정이 쌓이는 문제를 이전 앱에서 실제로 겪음"). 목표를 "게스트(로컬 ID)→실계정 승격"처럼 익명 인증을 전제하지 않는 형태로 다시 정의하는 것이 이 계층을 채우는 다음 작업이다(아래 로드맵 7번). `application/auth`/`application/premium` 자체는 이 6계층에 속하지만, 그 포트가 실제 Firebase/SharedPreferences에 닿는 부분(위 4계층 참고)과는 구분해서 봐야 한다. ~~`claimedFeatures` 일반화 전 수동 이어붙이기 부담~~ — 260814에 해소(바로 위 `PremiumState` 항목 참고).

### 7계층 — Presentation

**위치**: `app-android/src/main/java/com/worksoc/goaicoach/ui/`(`GoCoachApp.kt`, `GoBoard.kt`, `GameMenuSection.kt`, `GamePlaySection.kt`, `KaTrainUxPanels.kt`, `ScoreGraphPanel.kt`, `UiStrings*.kt` 등), `app-android/src/main/java/com/worksoc/goaicoach/presentation/`(`GameUiEvent.kt`, `GameScreenState.kt`, `GoCoachScreenStateAssembler.kt`, `GameMenuEventPolicy.kt`, `KaTrainUxOptions*.kt`, `PlayerSetupUiState.kt`)

⚠️ `ui/` 패키지에는 **4계층 SDK 어댑터 6개**(`AndroidAuthClient.kt`, `AndroidBillingClient.kt` 등)가 섞여 있다 — 위 4계층 절 참고. 이름이 계층을 전달하지 못하는 구간이고, 진단서 §1.4가 그 범위를 실측해 뒀다.

**재편 여부**: 기존 7계층과 동일한 정의, 번호도 그대로 최상위(7번) 유지.

---

## 알려진 갭

### 이미 닫힌 것

- ~~`GameSessionStateHolder`(5계층)는 여전히 `app-android`에 있다~~ — **260816 해소**. `application/safety/` 스파이크로 이전 절차(물리 이동·`internal`→public·JUnit→kotlin.test·양쪽 모듈 컴파일·iOS 타깃 컴파일까지)를 먼저 검증한 뒤, 같은 날 웨이브 1~6으로 `application/` 트리 124개 프로덕션 파일 전부(영구 예외 1개 제외)를 `shared`로 물리 이전 완료. `:shared`/`:app-android` 컴파일 + `make test` 전체 그린, `NewGameBoardTapSmokeTest.kt`/`AppLaunchSmokeTest.kt` 에뮬레이터 실기 재확인도 통과 — 상세는 아래 "고도화 로드맵" 5번 참고.
- ~~`RemoteEngineSessionClient`(3계층, 여러 원격 후보 중 선택·신뢰도 판단)가 없다~~ — 260804 Stage E-1/E-2에서 최소 형태로 해소(`selectRemoteEngineCandidate`+`RemoteEngineSessionBootstrap.createRemoteEngineSessionClient`). **260818 Stage E-3에서 `MainActivity`에 `BuildConfig.DEBUG` 한정으로 실제 배선됐다**. 남은 갭은 "배선"이 아니라 런타임 실패 감지/폴백과 여러 후보 비교 신뢰도 판단이며, Stage F 영역이다.
- ~~2계층의 로컬/원격 구현체가 대등하지 않다~~ — 260803 Stage D에서 해소. 260804에 물리적으로도 `engine-android` 한 모듈로 모았다.
- ~~기능 엔타이틀먼트 판정이 6계층이 아니라 7계층에 있다~~ — 260814에 해소. 세 곳(`ui/GamePlaySection.kt` 2곳, `ui/KaTrainUxPanels.kt` 1곳)에 하드코딩돼 있던 OR 조합을 `application/premium/FeatureAccessPolicy.kt` 하나로 통합했다.
- ~~`LayeringContractTest.kt`가 아직 2026-06-27판 옛 계층 이름 기준으로 작성돼 있다~~ — **사실이 아니었다(2026-09-23 확인).** 260803 Stage A-1이 옛 계층 이름 7종(`Engine Runtime/Transport` … `Presentation/Game UX`)을 전수 grep해 *"이 파일 어디에도 없다"* 를 확인하고 이 항목을 닫았다(`work/plans/REMOTE_ENGINE_AND_LAYERING.md` 진행 로그 — 2026-09-23 개명 전 이름은 `LAYERED_ARCHITECTURE_REFACTORING_PLAN_260803_1500.md`였다). 이 테스트들은 처음부터 계층 번호가 아니라 패키지/클래스명(`application/auth`, `EngineCoreApi`, `middleware`)으로 경계를 표현한다. 오늘 `0c33d32c`로 스캔 경로까지 `:shared` 실재 경로로 갱신됐다.

### 열려 있는 것

- **계층 강제 수단이 문자열 스캔이라는 것 자체가 갭이다**(2026-09-23 실측, 진단서 §1.2). `LayeringContractTest.kt`의 import 규칙 10개 중 **4개가 0개 파일을 검사하며 무조건 통과**하고 있었다 — 260816에 코드가 `shared`로 이사했는데 스캔 경로가 따라오지 않았고, `ktFilesIn`이 없는 디렉터리를 조용히 빈 목록으로 돌려줬기 때문이다. 즉 **초록이 "위반 없음"이 아니라 "검사 안 함"이었다.** 오늘 `0c33d32c`(경로 갱신)와 `01a85479`(`ktFilesIn`에 `require(files.isNotEmpty())`)로 **빈 디렉터리**는 봉했지만, *"스캔은 되는데 금지 문자열이 낡아 아무것도 못 잡는다"* 는 **여전히 조용히 통과한다.** 금지 조각이 저장소에 실재하는지 보는 메타 검사가 필요하다.
- **import 방향 위반이 730/2,004개(36%)다**(2026-09-23 실측). 내역: L7→L5 **389**, L3→L5 **169**(엔진 서비스가 세션/도메인을 안다 — 방향이 거꾸로다), L2→L5 66, L7→L3·L4·L2 92, 기타 14. 즉 UI가 계층을 건너뛰어 유스케이스를 직접 부르고, 아래 계층이 위 계층을 아는 구간이 실재한다.
- **`application/` 안에 17개 패키지 강결합 사이클(SCC)이 있다**(2026-09-23 실측, Tarjan). `analysis, autoai, debugreport, diagnostic, endgame, engine, engine.operation, humanmove, preferences, runtime, savedgame, score, session, startgame, topmoves, undo` + `middleware`. **3계층으로 선언된 `application/engine`과 5계층으로 선언된 `application/session`이 같은 사이클 안에 있다** — 사이클 안에서는 어느 쪽이 상위인지 정의되지 않으므로, 이 구간에 대해 7계층 서사는 참도 거짓도 아니고 **성립하지 않는다.**
  ⚠️ **그래서 지금 "모듈 분리"를 제안하면 안 된다.** `application/engine`만 떼어내는 순간 session·score·match·runtime·endgame이 딸려오고, 그것들이 다시 engine을 참조해 **Gradle 순환 의존으로 빌드가 멈춘다. 사이클을 먼저 끊지 않은 모듈화는 컴파일에서 죽는다.** 모듈 경계는 이 문제를 **원리적으로 보지 못한다**(목표 그래프에서 17패키지가 전부 한 모듈 안에 들어가기 때문).
  ⚠️ 위 두 줄은 **2026-09-23 실측값**이고, 엣지 추출·SCC 계산 **방법과 원자료는 `work/roadmap/260923-_ARCHITECTURE_DIAGNOSIS_AND_REFACTORING.md` §1에 있다.** 수치가 의심스러우면 거기 적힌 방법으로 다시 잰다 — 재생산 절차를 이 문서에 복사하지 않는다(낡으면 두 곳이 어긋난다).
- 4계층(외부 연동)이 포트(α)만 있고 안정화 서비스 본체가 얇다.
- 6계층(세션/연속성)이 auth/premium 각자의 필요만 채우고 있고, 범용 개념이 없다.
- androidTest(Robolectric/계측) 커버리지가 기본 검증 경로에 없다(`make test`에 안 묶여 있음 — 의도적, M-04 제약). 컴파일+JVM 단위 테스트가 기본 검증이다. **260816**: `AppLaunchSmokeTest.kt`(실제 `MainActivity`→`createEngineBootstrap`→`GoCoachApp` 경로)가 `@Ignore` 스켈레톤에서 활성 테스트로 전환됐다. **260817**: `SavedSessionPromptSmokeTest.kt` 신설로 "saved-session-prompt" 경로도 완료. 더 넓은 이벤트 디스패치 커버리지만 여전히 열려 있다.

## 고도화 로드맵

우선순위 순서가 아니라 계층별로 정리한 것이며, 착수 순서는 별도 착수 계획서에서 정한다.

⚠️ **착수 계획서는 `work/roadmap/`에 `시작일-완결일_이름.md` 형태로 둔다**(완결 전에는 뒤를 비운다 — 예: `260923-_ACTIVE_BACKLOG.md`, 완결 후 예: `260919-260922_STUDY_CONTENT_AND_1_0_RELEASE.md`). 2026-07-30판이 지시하던 *"`refactoring/`에 `YYMMDD HHhMMm` 타임스탬프 관례로 추가"* 는 **따르면 안 된다** — `docs/refactoring/` 폴더는 2026-08-17에 삭제됐고 `docs/DOCS_INDEX.md`가 **"되살리지 말 것"** 으로 못박았다. 시분 타임스탬프 관례도 그때 함께 폐기됐다.

1. ~~**2계층 — 로컬/원격 계약 대등화**~~ — 완료(260803 Stage D, 260804 물리적 모듈 통합). `RemoteEngineCoreApiAdapter`가 `EngineCoreApi` 전체를 구현하고, 로컬(`KataGoProcessEngineAdapter`)과 실패/타임아웃/재시도 신뢰도가 동등함을 계약 테스트로 검증했으며, 둘 다 `engine-android` 모듈에 물리적으로 함께 있다.
2. ~~**3계층 — `RemoteEngineSessionClient` 도입**~~ — 최소 형태 완료(260804 Stage E-1/E-2). 여러 원격/피어 후보 중 선택·신뢰도 판단을 흡수하는 자리(`selectRemoteEngineCandidate`)는 마련됐지만, 지금은 후보가 1개뿐이라 판단이 얕다. 후보가 실제로 여러 개가 되는 시점(DePIN 방향)에 응답시간/성공률 비교, "피어 평판/정산 기록"의 자리를 채워야 한다.
3. **1계층 — 물리 실행 환경 추상화**: 지금은 `KataGoProcessRuntime`이 "이 기기에서 프로세스 실행"만 가정한다. 원격 서버/피어 기기라는 "다른 물리 위치"를 1계층 개념에 맞게 명시적으로 표현할 방법을 정의(예: 실행 위치를 나타내는 값 타입).
4. **4계층 — 외부 연동 서비스 본체 두껍게 하기**: 광고·결제·로그인 **연동 자체는 끝났다**(위 4계층 "핵심 갭" — AdMob 라이브, 월 구독 라이브, 로그인은 코드 완성 후 플래그 OFF). 남은 일은 포트(α) 위에 3계층 수준의 재시도/캐시/신뢰도 판단을 갖춘 서비스 본체를 얹는 것이다. 근거 문서: `work/plans/PREMIUM_MODE.md`, `work/plans/LOGIN_AND_ACCOUNT_SYSTEM.md`.
5. ~~**5계층 — `GameSessionStateHolder` → `:shared` KMP 이전.**~~ — **완료(260816)**. 이식 가능성(플랫폼 비종속)은 `engineOperationApplicationPoliciesStayPortable`이 사전에 검증했고, `GameSessionCoreState`/`GameSessionController`가 `autoai/engine/humanmove/savedgame/score/startgame/topmoves/undo/debugreport/analysis/movereview/preferences` 12개 서브패키지를 전이적으로 끌어들여 "일부만 이전"이 불가능하다는 예측대로 `application/`(124개 프로덕션 파일) 전체를 한 단위로 이전했다.
   - ~~**(1) 스파이크**~~ — **완료(260816)**. `application/safety/EngineTurnWatchdog.kt`(팬인/팬아웃 최소, `SearchTimeLimit` 하나만 의존)를 `shared/commonMain`으로, 테스트를 `shared/commonTest`로 옮겨 전체 절차를 실제로 검증했다. 확인된 것: (a) 패키지명(`com.worksoc.goaicoach.application.safety`)은 그대로 유지 가능 — `:shared` 안에 이미 `match/`처럼 비-`shared` 패키지가 있어 모듈 경계와 패키지명이 독립적이므로, app-android 호출부(`ui/GamePlaySection.kt`)의 import 문은 **한 줄도 안 바뀜**. (b) `internal fun` 2개를 public으로 바꿔야 했다 — 예상대로. (c) **새로 발견한 것**: 테스트도 함께 옮겨야 하고, `shared/commonTest`는 `org.junit.*`가 아니라 `kotlin.test.*`(멀티플랫폼 API)를 쓰는 게 기존 컨벤션이라 import 전환이 필요했다. (d) `:shared`가 `androidMain` 없이도 문제없이 받아들였고, `./gradlew :shared:compileKotlinIosSimulatorArm64 -PenableIosTargets=true`까지 깨끗이 통과.
   - ~~**(2) 본 이전**~~ — **완료(웨이브 1~6, 260816)**. **계획 수립 중 정정한 예상**: "`internal`→기본 가시성 확대는 실제 app-android 호출부가 있는 지점만"이라고 예상했었는데, 스파이크(파일 1개) 결과를 트리 전체로 낙관적으로 외삽한 것이었다 — 실측하니 509개 중 424개(83%)가 이미 `ui/`/`persistence/`/`middleware/`/테스트에서 참조되고 있어 이동 순서와 무관하게 public 전환이 필요했다. 도중에 발견된 함정들 — `LayeringContractTest.kt`의 하드코딩 경로 문제(`applicationFile()` 헬퍼로 해결), Kotlin 크로스모듈 스마트캐스트 제약(로컬 val 캡처로 해결), `middleware/`처럼 `application/` 트리 밖 패키지를 참조하는 파일(포터빌리티 확인 후 함께 이전), 테스트가 `persistence/`·`ui/` 같은 비이식 레이어를 직접 테스트하는 경우(영구 잔류로 원위치), `kotlin.test.assertNotNull`/`assertFailsWith`가 `Unit`이 아니라 값을 반환해 표현식-바디 테스트 함수의 추론 반환 타입을 깨는 경우(명시적 `: Unit` 반환 타입으로 해결) — 도 전부 처리했다. 웨이브별 실행 기록 원문은 2026-08-17 문서 정리로 제거됐다(`docs/DOCS_INDEX.md` "문서 보존 정책" 참고, 필요시 `git log`로 복원).
   - ~~**(3) `LayeringContractTest.kt` 갱신**~~ — **완료(260816)**. `engineOperationApplicationPoliciesStayPortable`의 스캔 대상을 `app-android/.../application`(이전 완료 후 사실상 공집합)에서 실제 파일들이 있는 `shared/src/commonMain/.../application`으로 변경. `ui.`/`persistence.`/`engine.`(composition-root) 임포트 금지 체크는 제거 — `shared`가 `app-android`에 대한 Gradle 의존성 자체가 없어 어기면 텍스트 검사 없이도 그냥 컴파일 에러가 난다. `android.`/`androidx.`/`java.`/`org.json.` 금지 체크는 유지 — `shared`의 `androidTarget`은 이 API들에 실제 접근 가능해서 컴파일은 통과하지만 iOS 등 다른 KMP 타깃을 조용히 깨뜨릴 수 있기 때문. ⚠️ **다만 이때 나머지 넷의 스캔 경로는 따라오지 않았다** — 그 결과가 위 「알려진 갭」의 "죽은 안전망" 항목이고, 2026-09-23 `0c33d32c`로 되살렸다.
   - ~~**(4) 회귀 확인**~~ — **완료(260816, 2회)**. `NewGameBoardTapSmokeTest.kt`/`AppLaunchSmokeTest.kt`를 웨이브 5(키스톤 이동 직후)와 웨이브 6(세션 배선에 손대는 마지막 웨이브) 완료 직후 각각 에뮬레이터(`emulator-5554`)에서 `connectedDebugAndroidTest`로 재확인 — 둘 다 2/2 green.
   - ~~**(5) 플랫폼 누수 회귀 복구**~~ — **완료(260824)**. (1)(d)에서 확인했던 "iOS 컴파일 깨끗이 통과"는 그 뒤 어느 시점에 깨져 있었다 — `./gradlew :shared:compileKotlinIosSimulatorArm64 -PenableIosTargets=true`가 에러 49개. iOS 타깃은 `-PenableIosTargets=true`로만 켜지므로 평소 안드로이드 빌드/테스트는 계속 그린이었고, 아무도 모르는 채 누적됐다. **원인은 (3)에서 유지하기로 한 텍스트 검사의 사각지대**: `import java.`를 막아도 `java.lang.*`은 JVM 자동 임포트라 `System.currentTimeMillis()`가 import 한 줄 없이 통과한다(`kotlin.synchronized`도 동일). 실제 내역은 시간 39건(20개 파일) + `synchronized` 4건 + `Dispatchers.IO` 1건이었다.
     - **시간(39건)**: `application/time/AppClock.kt`의 `currentEpochMillis()` 하나로 모았다. 호출부가 이미 갖고 있던 `nowMillis` 주입 시임(기본값 파라미터/람다)은 그대로 두고 그 **기본값**만 이 함수를 거치게 했으므로 테스트가 시간을 고정하는 방법은 전과 동일하다. 구현은 stdlib `kotlin.time.Clock`이라 새 의존성이 아니다. 경과 시간 측정(`System.nanoTime()`)은 `kotlin.time.TimeSource.Monotonic`으로 바꿨다.
     - **`synchronized`(4건) / `Dispatchers.IO`(1건)**: 이 둘은 진짜로 플랫폼마다 답이 다르다 — `expect`/`actual` 2개로 처리하고 `androidMain`/`iosMain` 소스셋을 신설했다. `application/concurrency/SharedLock.kt`(android=모니터 락, ios=`NSLock`), `application/engine/EngineIoDispatcher.kt`(android=`Dispatchers.IO`, ios=`newFixedThreadPoolContext(8, "engine-io")`). **안드로이드 런타임 동작은 전부 동일하게 유지**했다.
     - **coroutines 업그레이드는 답이 아니었다(260824 실측)**: 최신 안정판 1.11.0으로 올려서 확인해도 네이티브에는 여전히 `internal` 선언뿐이라 iosMain에서 `Dispatchers.IO`에 접근할 수 없다. `Dispatchers.Default.limitedParallelism(n)`도 답이 아니다(Default의 스레드를 나눠 쓰는 뷰일 뿐 스레드가 늘지 않아 블로킹 문제가 그대로다). **업그레이드 없이 1.8.0에서 해결됐다.** 확인 과정에서 두 군데(`shared`, `engine-android`)에 하드코딩돼 있던 `1.8.0`은 버전 카탈로그(`kotlinxCoroutines`)로 옮겨 놨다.
     - **재발 방지**: `LayeringContractTest.sharedCommonMainAvoidsImplicitlyImportedJvmApis` 신설 — import 문이 아니라 `System.`/`System::`/`Thread.`/`Runtime.`/`synchronized(` **이름 자체**를 `shared/commonMain` 전체에서 막는다. 기본 테스트 루프에 포함되므로 iOS 타깃을 켜지 않아도 걸린다.
6. ~~**6계층 — 기능 엔타이틀먼트 정책 도입**~~ — 완료(260814). `application/premium/PremiumState.kt`의 `isUndoClaimed: Boolean`을 `claimedFeatures: Set<FeatureId>`로 일반화하고(`persistence/PremiumStateStore.kt`에 구버전 불리언 하위호환 마이그레이션 포함), `application/premium/FeatureAccessPolicy.kt`를 신설해 `ui/GamePlaySection.kt`(형세보기/추천수/무르기)·`ui/KaTrainUxPanels.kt`(착수평가)에 각자 하드코딩돼 있던 3곳의 판정을 이 함수 하나로 통합했다. **의도적으로 남겨둔 것**: 클레임 전용 다이얼로그(`ui/GamePlaySection.kt`의 `showUndoClaimDialog`)를 `PremiumUpsellDialog`에 `Claim` 선택지로 통합하는 UI 단순화는 이번 범위에서 제외 — 클레임 가능 기능이 아직 무르기 하나뿐이라 지금 합치는 건 과설계로 판단, 두 번째 클레임형 기능이 생기면 재검토.
7. **6계층 — 세션/연속성 공식화**: `work/plans/LOGIN_AND_ACCOUNT_SYSTEM.md` Step 4(실계정 승격, Firestore 동기화)를 이 계층의 정식 구현으로 진행 — **단, 익명 로그인 자체가 2026-08-05에 영구 폐기 결정됐으므로("재설치마다 허수 계정이 쌓이는 문제를 이전 앱에서 실제로 겪음") 그 문서의 "익명→실계정 승격" 경로 자체가 성립하지 않는다. 이 항목은 착수 전에 목표를 다시 정의해야 한다** — 예를 들어 "게스트(로컬 ID)→실계정 승격"처럼 익명 인증을 전제하지 않는 형태로. 기기 식별자 기반 다중 기기 정책도 이 재정의와 함께 결정.
8. **계층 강제 수단을 문자열 스캔에서 컴파일러 쪽으로 옮기기**: 위 「알려진 갭」의 세 항목(죽은 안전망, import 방향 위반 730건, 17패키지 SCC)이 같은 뿌리를 가리킨다 — 규칙이 소스 텍스트를 훑는 형태라 코드보다 빨리 낡는다. 처방(패키지 FQN으로 계층을 드러내기 → PSI 기반 규칙 → 최후에 모듈)과 그 순서·함정은 `work/roadmap/260923-_ARCHITECTURE_DIAGNOSIS_AND_REFACTORING.md` §3·§5에 있다. **코드가 실제로 옮겨지기 전까지 테스트를 먼저 조이지 않는다** — 아직 분리되지 않은 것을 분리된 것처럼 강제하면 오탐만 늘어난다.
9. ~~**문서 정리 후속 작업**~~ — **완료(260817)**. `docs/refactoring/`(리팩토링 축이 이미 종료됨)과 `docs/archive/` 전체(55개 파일, 1.2MB)를 저장소에서 제거했다. "삭제 대신 보관" 원칙을 뒤집는 결정이라 `docs/DOCS_INDEX.md` "문서 보존 정책" 절에 사유와 복원 방법을 기록했다. 유일한 예외는 실측 데이터로 계속 인용되던 `ENGINE_STRENGTH_RESEARCH.md`로, `docs/engine/`로 이동 보존했다.

## 관련 문서

- 레이어 원칙 자체(앱 비종속): `docs/ARCHITECTURE.md`
- 2026-09-23 실측 기록·처방·함정 A~J: `work/roadmap/260923-_ARCHITECTURE_DIAGNOSIS_AND_REFACTORING.md` — **봉인 문서다**(그 시점 기록). 실측 결론은 이 문서가 흡수하되 그쪽 본문은 갱신하지 않는다.
- 엔진 탐색 방식·레벨 정책·캐시 운영 상세: `docs/ENGINE.md`
- 프리미엄/결제 로드맵: `work/plans/PREMIUM_MODE.md`
- 인증/온보딩 로드맵: `work/plans/LOGIN_AND_ACCOUNT_SYSTEM.md`
- 기능 유/무료 정책 원칙("무엇을 무료/광고/구매/클레임으로 줄지"의 근거): `docs/spec/FEATURE_ACCESS_PRINCIPLES.md`
- 그 원칙을 초도 발행에 구체 적용한 전략/체크리스트: `work/plans/GOOGLE_PLAY_LAUNCH_PLAN.md`
- 함정 번호 정본(영구불변, 재사용 없음): `docs/spec/PITFALLS.md` — 진단서 §4의 함정 A~J가 **67~76번**으로 들어가 있다
- 원격 엔진 Stage 로그(계층 정렬 Stage A~C는 위 진단서로 이관): `work/plans/REMOTE_ENGINE_AND_LAYERING.md`
- 지금 무엇을 할 것인가(활성 백로그): `work/roadmap/260923-_ACTIVE_BACKLOG.md`
- 이 7계층 모델이 정착하기까지의 리팩토링 과정: 날짜별 작업 로그는 2026-08-17 문서 보존 정책 전환으로 삭제됐다(git 히스토리로만 보존). **진행 중인 로드맵은 `work/roadmap/`에 있다** — `docs/refactoring/` 폴더는 없어졌고 되살리지 않는다(`docs/DOCS_INDEX.md` 「문서 보존 정책」).
