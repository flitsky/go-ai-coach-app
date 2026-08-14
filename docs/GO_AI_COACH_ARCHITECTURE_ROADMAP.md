# go-ai-coach 아키텍처 현황과 고도화 로드맵

작성일: 2026-07-30
레이어 순서 갱신: 2026-07-30 — External Integration이 4계층(3계층과 대등한 서비스 계층)으로 재배치되며 Application(5)/Session & Continuity(6)/Presentation(7) 번호가 한 칸씩 밀렸다. [ARCHITECTURE.md](./ARCHITECTURE.md)의 "레이어 순서 확정" 항목 참고.
기능 엔타이틀먼트 정책 배치 결정: 2026-08-14 — "무료/광고/구매/클레임" 같은 기능별 정책이 앞으로도 계속 바뀔 것을 전제로, 그 정책 판정을 5계층에 `FeatureAccessPolicy`(신규, 미구현)로 명문화하고 6계층 `PremiumState`를 단일 플래그(`isUndoClaimed`)에서 기능별 원장(`claimedFeatures: Set<FeatureId>`)으로 일반화하는 로드맵을 추가했다. 지금은 이 판정이 7계층(`ui/GamePlaySection.kt`)에 하드코딩돼 있다 — 상세는 "알려진 갭"·"고도화 로드맵" 절, 그리고 `feature-access-principles/README.md` 2장(같은 결론을 정책 문서 쪽에서 먼저 제안해 뒀던 것).

**성격**: [ARCHITECTURE.md](./ARCHITECTURE.md)(원칙 문서, 앱 비종속)의 7계층 모델을 go-ai-coach 코드베이스에 적용한 **파생 문서**다. "지금 무엇이 어디 있는가"와 "물리적으로 완전히 분리 가능한 상태까지 무엇이 남았는가"를 담는다. 레이어 정의 자체나 그 이유는 여기서 반복하지 않고 원칙 문서를 따른다.

기존 `ARCHITECTURE.md`가 갖고 있던 계층별 파일/패키지 표는 이 문서로 이전됐다 — 이전 버전은 git 히스토리로 확인 가능하다(`git log -p -- docs/ARCHITECTURE.md`).

---

## 계층별 현재 매핑

> 표의 "재편 여부" 열은 2026-06-27판 7계층(Engine Runtime/Transport → Engine Core API Domain → Core Rules Domain → Middleware/Cache Domain → Game Domain → App Service/Session Orchestration → Presentation/Game UX) 대비 이번(2026-07-30) 재정의에서 경계가 바뀐 지점을 표시한다. **코드는 아직 옮기지 않았다 — 아래는 개념적 재배치이며, 실제 파일 이동/모듈 분리와 `LayeringContractTest.kt` 갱신은 "로드맵" 절의 실행 항목이다.**

### 1계층 — Physical Compute

**위치**: `engine-android/src/main/java/com/worksoc/goaicoach/engine/android/KataGoProcessRuntime.kt`(실행 파일/모델 검증, CLI 인수 빌드, 프로세스 시작/종료)

**재편 여부**: 기존 1계층은 `engine-android` 패키지 전체(어댑터 포함)였다. 이번 재정의에서는 **"실제 KataGo 바이너리 프로세스를 실행/관리하는 부분"만** 1계층으로 좁히고, 그 바이너리에 GTP/JSON으로 말을 거는 어댑터는 2계층으로 옮겼다 — "어디서 도는가"(1계층)와 "어떻게 그것과 통신하는가"(2계층)를 분리하기 위함.

### 2계층 — Middleware / Bridge

**위치**:
- `shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/EngineModels.kt` — `EngineCoreApi` 인터페이스(1:1 원시 계약: `initialize`, `configure`, `playMove`, `analyze`, `estimateScore`, `deadStones`, `scoreFinal`, `clearSearchCache`, `stop`, `forceReset` 등), `AnalysisLimit`/`EngineProfile`/`CandidateMove` 등 순수 데이터 모델. `RemotePositionAnalysisTransport.kt` — position-analysis 단위 원격 호출 계약(`RemotePositionAnalysisTransport`/`Request`/`Response`, 260804 이전엔 app-android에 있었음, §재편 여부 참고)
- `engine-android/.../KataGoProcessEngineAdapter.kt` — `EngineCoreApi`의 **로컬** 구현체. GTP(`KataGoGtpAnalysisClient.kt`, `KataGoProtocolCommands.kt`)와 JSON(`KataGoJsonPositionAnalysisClient.kt`, `KataGoJsonAnalysisQueryFactory.kt`, `KataGoJsonAnalysisParser.kt`) 두 경로를 조율. 두 경로 공통 파싱은 `KataGoAnalysisParser.kt`/`KataGoAnalysisContext.kt`
- `engine-android/.../StubEngineAdapter.kt` — `EngineCoreApi`의 **스텁** 구현체(엔진 없이 UI/도메인 검증용)
- `engine-android/.../RemoteEngineCoreApiAdapter.kt` — `EngineCoreApi`의 **원격** 구현체(13개 메서드 전체, 260803 Stage D). 상태변경 호출은 로컬에서 `GameState`를 추적하고, `genMove`/`analyze`/`estimateScore`/`deadStones`/`scoreFinal`만 원격 전송하는 상태 비저장 설계. `HttpRemoteEngineOperationTransport`가 HTTP 구현체
- `engine-android/.../HttpRemotePositionAnalysisTransport.kt` — `RemotePositionAnalysisTransport`의 read-only position-analysis 단위 트랜스포트 스파이크 구현체(2026-06-28 기준 기본값 off)
- `app-android/.../middleware/RemotePositionAnalysisGateway.kt` — 위 트랜스포트를 3계층 `PositionAnalysisGateway` 계약으로 감싸는 어댑터(app-android에 잔류, `:shared`의 `RemotePositionAnalysisTransport`만 알고 실제 구현체 이름은 모름)

**재편 여부**: 기존 2계층(Engine Core API Domain, 계약 정의만)에 기존 4계층(Middleware/Cache Domain)의 **전송** 절반(원격 게이트웨이/트랜스포트)을 합쳤다. "계약을 정의하는 것"과 "그 계약을 실제로 어떻게 도달시키는가(로컬 stdio냐 원격 HTTP냐)"가 개념적으로 같은 책임이라고 보기 때문이다. **260804 정리**: `EngineCoreApi`의 로컬/원격 구현체를 전부 `engine-android` 모듈로 물리적으로 모았다(그 전엔 원격 구현체가 app-android/middleware에 있었음) — app-android(3~7계층) 작업 시 엔진 내부 구현을 아예 안 봐도 되도록, 그리고 향후 원격/DePIN 확장의 물리적 근간이 되도록. 이 이동을 가능케 하려고 `RemotePositionAnalysisTransport`/`Request`/`Response`(전부 `:shared`-safe 타입만 사용)도 `:shared`로 옮겼다 — app-android(Gateway)와 engine-android(Http 구현체)가 순환 의존 없이 같은 계약을 공유하기 위함.

**핵심 갭(해소됨, 260803 Stage D)**: `KataGoProcessEngineAdapter`(로컬)와 `RemoteEngineCoreApiAdapter`(원격)가 이제 `EngineCoreApi` 전체에 대해 대등한 계약을 만족한다(계약 테스트로 검증). 3계층의 후보 선택/신뢰도 판단(`selectRemoteEngineCandidate`)도 260804 Stage E-1/E-2에서 마련됐다 — 아래 3계층 절 참고. 다만 이 원격 경로는 아직 앱 실제 컴포지션(MainActivity/GoCoachApp)에 배선되지 않았다(가리킬 실제 원격 서버가 없음) — Stage F(실제 물리 분산) 영역, 별도 승인 필요.

### 3계층 — Extended API (엔진 서비스)

**위치**:
- `application/engine/EngineSessionClient.kt` — UI/App Service가 바라보는 고수준 엔진 게이트웨이 인터페이스. `analyzePosition(state, limit, searchMode)`처럼 명시적 `GameState`를 받아 local/remote 차이를 숨김. `forceResetEngine()`처럼 비정상 상태 복구용 진입점도 여기 있다
- `application/engine/LocalEngineSessionClient.kt` + `LocalAiMoveEngineGateway.kt`, `LocalEndgameJudgeGateway.kt`, `LocalEngineCoreSessionDelegate.kt`, `LocalEngineBenchmarkDelegate.kt`, `LocalPositionAnalysisCacheCoordinator.kt` — local 구현체, 역할별 delegate로 분리
- `application/engine/operation/` — `EngineOperationLifecycleController` 등. 동시 엔진 호출 추적, 늦게 도착한 결과 폐기(stale guard), busy 상태 관리
- `application/safety/EngineTurnWatchdog.kt` — AI 턴이 설정된 응답 시간(×1.2+3초, 무제한이면 60초)을 넘기면 감지하는 순수 판정 로직. 2026-07-30 신설
- `application/analysis/PositionAnalysisCache.kt`, `PositionAnalysisCacheOptimization*.kt` — JSON position analysis 결과를 품질/origin별로 저장하는 디스크 캐시
- `application/middleware/PositionAnalysisCacheResolver.kt` — 신뢰도 등급에 따라 캐시 hit을 평가/서빙
- `application/engine/RemoteEngineCandidate.kt` — DePIN 준비(260804 Stage E-1). 원격 후보 표현(`RemoteEngineCandidate`)과 선택/신뢰도 판단(`selectRemoteEngineCandidate`). `engine-android`를 import하지 않는 순수 3계층 판단 — 실제 `EngineCoreApi` 배선은 `engine/RemoteEngineSessionBootstrap.kt`(아래 참고)가 담당
- `engine/EngineBootstrap.kt`, `engine/RemoteEngineSessionBootstrap.kt` — `application/`이 아닌 별도 패키지(`com.worksoc.goaicoach.engine`)에 있는 composition-root 인접 배선 파일들. `application/`은 `engine.android`를 import할 수 없다는 기존 경계(`LayeringContractTest`) 때문에, "engine-android 구현을 실제로 생성"하는 코드는 전부 여기 산다 — 로컬은 `EngineBootstrap.createEngineBootstrap`, 원격은 `RemoteEngineSessionBootstrap.createRemoteEngineSessionClient`

**재편 여부**: 기존 4계층(Middleware/Cache Domain)에서 전송(2계층으로 이동)을 뺀 나머지 — 캐시, 신뢰도 라우팅, 동시성 lifecycle, 이번 세션에 추가된 엔진 턴 와치독까지 전부 여기.

**DePIN 관점에서의 역할(부분 착수, 260804 Stage E-1)**: 1계층이 여러 피어로 흩어지면, "지금 어느 피어를 쓸지 선택하고 그 결과를 얼마나 신뢰할지 판단"하는 책임이 이 계층으로 들어와야 한다. `selectRemoteEngineCandidate`가 그 자리를 잡았지만, 지금은 후보가 항상 최대 1개라 판단이 "활성화돼 있는가/엔드포인트가 유효한가"만큼만 있다 — 여러 후보의 응답 시간·성공률을 비교하는 진짜 신뢰도 판단은 실제로 후보가 2개 이상 생기는 시점(Stage F, DePIN 확장)에 채워야 한다.

### 4계층 — External Integration (외부 연동)

**위치**:
- `application/auth/AuthClientPort.kt`(α: 순수 포트) ↔ `ui/AndroidAuthClient.kt`(Firebase Auth 어댑터 — Extended API 본체가 실제 SDK에 닿는 지점)
- `application/premium/PremiumStatePorts.kt`(α: 순수 포트) ↔ `persistence/PremiumStateStore.kt`(SharedPreferences 어댑터)
- `ui/AndroidPlatformPorts.kt` — 가벼운 플랫폼 포트(클립보드, 토스트) 공용 파일

**재편 여부**: 기존 모델에는 이 계층이 없었고 "포트/어댑터 분리 원칙"이라는 (구)4계층 문서의 부칙으로만 존재했다. 이번에 3계층과 대등한 정식 서비스 계층으로 승격했다 — 3계층이 엔진 raw 계약을 서비스로 조합하듯, 이 계층은 외부 SDK의 raw 계약을 서비스로 조합한다(자세한 논거는 ARCHITECTURE.md 4계층 절).

**핵심 갭**: 실제 결제(Play Billing)/실제 로그인(Google/이메일)/광고(AdMob)는 전부 스텁 상태다(`premium-mode/README.md`, `auth-onboarding/README.md` 참고). 포트(α)는 이미 이 계층 원칙대로 배치돼 있으니, 실제 SDK 연동 시 새 파일을 어디 둘지는 이미 정해져 있다. 다만 지금은 "α(포트)"만 있고 "Extended API 본체"(실패/재시도/캐시까지 감안한 안정화 서비스)는 아직 얇다 — `AndroidAuthClient`/`PremiumStateStore`는 SDK 호출을 그대로 감싸는 수준이라, 3계층의 `PositionAnalysisCacheResolver` 같은 신뢰도/재시도 판단이 아직 없다.

### 5계층 — Application / Domain

**위치**:
- `shared/src/commonMain/.../BoardModels.kt`, `BoardRules.kt`, `LegalMoveGenerator.kt`, `BoardScorer.kt`(+`BoardAreaScorer.kt`/`BoardTerritoryScorer.kt`), `EndgameScoreSelector.kt`, `GameStateReplayer.kt`, `ScoreTimeline.kt` — 순수 바둑 규칙(KMP `commonMain`)
- `shared/src/commonMain/kotlin/com/worksoc/goaicoach/match/MatchReferee.kt`, `AiMoveSelectionPolicy.kt`, `MatchPolicy.kt` — 대국 정책(참여 주체, 턴 권한, AI 레벨링). **260804 경로 정정**: 이전엔 `app-android/.../match/`였으나 "도메인별 파일 분리" 작업(커밋 `5278c12`)으로 `shared`로 이동했다 — 순수 로직이라 KMP 이식 대상이었다.
- `app-android/.../application/{session,autoai,undo,humanmove,startgame,savedgame,topmoves,debugreport,score,endgame,diagnostic,runtime,preferences,prompt,movereview,analysis}` — App Service 유스케이스 오케스트레이션. `session/GameSessionStateHolder.kt`가 세션 상태의 단일 source of truth
- **(신규 제안, 미구현)** `application/featureaccess/FeatureAccessPolicy.kt` — 기능별 접근 정책 판정. 6계층 `PremiumState`(그 유저/기기가 "지금 무엇을 갖고 있는가")를 입력으로 받아, 기능 하나(`FeatureId`)에 대해 "지금 쓸 수 있는가, 없다면 무엇으로 풀 수 있는가"를 순수 함수로 판정한다 — `fun resolve(featureId: FeatureId, state: PremiumState, nowMillis: Long): FeatureAccess`(`FeatureAccess` = `Allowed(via: AllowedVia)` | `Locked(unlockOptions: Set<UnlockOption>)`). 3계층의 `PositionAnalysisCacheResolver`(원시 결과를 신뢰도별로 서빙하는 판정)와 같은 자리를 4계층 대신 여기(5계층)에 두는 이유, 그리고 지금 이 판정이 7계층에 새어 나가 있는 실태는 아래 "알려진 갭"·"고도화 로드맵" 참고.
- `ui/GoCoachApp.kt` — 위 모든 컨트롤러를 생성/연결하는 composition root (2026-07-30 기준 850줄, `LayeringContractTest`가 라인수 880/상태훅 47 예산을 강제)

**재편 여부**: 기존 3계층(Core Rules)+5계층(Game Domain)+6계층(App Service/Session Orchestration)을 하나로 통합. 순수 규칙과 오케스트레이션은 성격이 다르지만 "이 앱만의 것"이라는 공통점으로 묶었다 — [ARCHITECTURE.md](./ARCHITECTURE.md)의 5계층 정의를 따른다. 아래 3계층(엔진 서비스)과 4계층(외부 연동 서비스)을 동등하게 소비한다.

### 6계층 — Session & Continuity

**위치**:
- `application/auth/AuthState.kt`, `AuthClientPort.kt` — 로그인 상태 순수 모델 + 포트
- `application/premium/PremiumState.kt`, `PremiumStatePorts.kt` — 프리미엄 활성화 상태. `matchGeneration`(대국 세대 — 무르기로는 바뀌지 않음)으로 5계층의 `sessionGeneration`(엔진 오퍼레이션 무효화 세대 — 무르기마다 바뀜)과 **의도적으로 분리**돼 있다(2026-07-30 수정 — 이 분리가 없어서 무르기 시 프리미엄이 풀리는 버그가 있었다). 현재 필드는 `source`(None/AdGrant/Purchase) + `adGrantStartedAtMillis` + `isUndoClaimed: Boolean` 하나뿐이다. **(신규 제안, 미구현)** `isUndoClaimed`를 `claimedFeatures: Set<FeatureId>`로 일반화한다 — 무르기 하나만을 위한 불리언이 아니라, 앞으로 다른 기능도 같은 방식(초도 클레임+그랜드파더링)으로 무료 제공할 때 새 불리언을 또 추가하지 않고 같은 필드에 `FeatureId`만 늘어나게 한다. `source`/`adGrantStartedAtMillis`(구독형 축)와 `claimedFeatures`(1회 클레임형 축)는 지금처럼 계속 서로 다른 축으로 분리해서 둔다 — 위 문단이 이미 겪은 "축 혼동 버그"를 반복하지 않기 위함.

**재편 여부**: 신규 계층(번호만 5→6으로 이동, 정의는 그대로). 기존 7계층 모델에는 없었고, `application/auth`/`application/premium`이 사실상 이 자리를 채우고 있었지만 명문화된 계층은 아니었다.

**핵심 갭**: 아직 "세션/연속성"이라는 이름에 걸맞은 범용 개념(기기 식별자, 익명→실계정 승격, 다중 기기 정책)이 없다 — 지금은 auth/premium 각자가 필요한 만큼만 자기 상태를 갖고 있다. `auth-onboarding/README.md`의 "익명 인증 → 실계정 승격" 로드맵이 이 계층을 채우는 다음 작업이다. `application/auth`/`application/premium` 자체는 이 6계층에 속하지만, 그 포트가 실제 Firebase/SharedPreferences에 닿는 부분(위 4계층 참고)과는 구분해서 봐야 한다. 추가로, `claimedFeatures` 일반화 전인 지금도 이미 그 대가를 치르고 있다 — `ui/GoCoachApp.kt:704-720`이 `setPurchased`/`purchasePremium`/`activateAdGrant` 세 전이 지점 모두에서 `.copy(isUndoClaimed = premiumState.isUndoClaimed)`를 수동으로 이어붙이는데, 그 이유를 설명하는 주석이 "안 그러면 클레임이 조용히 사라진다"다. 클레임 가능 기능이 하나(무르기)뿐이라 아직 참을 만하지만, 원장으로 일반화하지 않은 채 두 번째 기능이 추가되면 이 수동 이어붙이기가 기능 수만큼 반복된다.

### 7계층 — Presentation

**위치**: `ui/`(`GoCoachApp.kt`, `GoBoard.kt`, `GameMenuSection.kt`, `GamePlaySection.kt`, `KaTrainUxPanels.kt`, `EngineResponsePanel.kt`, `ScoreGraphPanel.kt` 등), `presentation/`(`GameUiEvent.kt`, `GameScreenState.kt`, `GoCoachScreenStateAssembler.kt` 등)

**재편 여부**: 기존 7계층과 동일한 정의, 번호도 그대로 최상위(7번) 유지.

---

## 알려진 갭 (2026-07-30 기준)

- `GameSessionStateHolder`(5계층)는 여전히 `app-android`에 있다. `shared`로 옮기는 KMP 이식은 아직 안 함.
- ~~`RemoteEngineSessionClient`(3계층, 여러 원격 후보 중 선택·신뢰도 판단)가 없다~~ — 260804 Stage E-1/E-2에서 최소 형태로 해소(`selectRemoteEngineCandidate`+`RemoteEngineSessionBootstrap.createRemoteEngineSessionClient`). `RemoteEngineCoreApiAdapter`(원격 `EngineCoreApi` 구현체 자체, 260803 Stage D)와 함께 이제 준비돼 있지만, 아직 앱 실제 컴포지션(MainActivity/GoCoachApp)에 배선되지 않았고 "고정된 원격 서버 1대"조차 가리킬 실제 서버가 없어 실제로 쓰이고 있지 않다 — Stage F 영역.
- ~~2계층의 로컬/원격 구현체가 대등하지 않다~~ — 260803 Stage D에서 해소(위 2계층 절 참고). 260804에 물리적으로도 `engine-android` 한 모듈로 모았다.
- 4계층(외부 연동)이 포트(α)만 있고 안정화 서비스 본체가 얇다.
- 6계층(세션/연속성)이 auth/premium 각자의 필요만 채우고 있고, 범용 개념이 없다.
- **기능 엔타이틀먼트 판정이 5계층이 아니라 7계층에 있다.** "이 유저가 무르기를 쓸 수 있는가"(`premium.isUndoClaimed || premium.isActive`)라는 OR 조합 자체가 도메인 규칙(이 앱이라면 항상 참인 규칙 — [ARCHITECTURE.md](./ARCHITECTURE.md) 5계층 경계 원칙의 "이 규칙이 이 앱이라면 항상 참인가" 테스트에 해당)인데, 지금은 `ui/GamePlaySection.kt:283-285`의 `undoClaimGated` 로컬 함수 안에 하드코딩돼 있다. 형세보기/추천수용 `premiumGated`(같은 파일 276-278행)도 같은 패턴이다. 두 판정 모두 `application/featureaccess/FeatureAccessPolicy.kt`(위 5계층 절, 신규 제안)로 옮기는 것이 로드맵 항목이다. 6계층의 `PremiumUpsellDialogHost`(`ui/PremiumUiState.kt`)는 이미 "선택지 3개 중 하나를 고르면 무엇을 하는가"를 한 곳에 모아 3개 호출부(`GameSetupLobby.kt`/`GamePlaySection.kt`/`KaTrainUxPanels.kt`)의 중복을 막고 있으므로 참고할 선례는 있다 — 다만 그 대상이 "무엇을 잠글지 판정"이 아니라 "이미 잠긴 걸 어떻게 풀지 실행"이라는 차이가 있다.
- `LayeringContractTest.kt`는 아직 2026-06-27판 경계(옛 1~7계층 이름) 기준으로 작성돼 있다. 이번 재정의(2/3계층 재편, 4/6계층 신설, 5/7 번호 이동)를 반영하지 않았다.
- androidTest(Robolectric/계측) 커버리지가 기본 검증 경로에 없다. 컴파일+JVM 단위 테스트가 기본 검증이다.

## 고도화 로드맵

우선순위 순서가 아니라 계층별로 정리한 것이며, 착수 순서는 별도 착수 계획서(`refactoring/`에 `YYMMDD HHhMMm` 타임스탬프 관례로 추가)에서 정한다.

1. ~~**2계층 — 로컬/원격 계약 대등화**~~ — 완료(260803 Stage D, 260804 물리적 모듈 통합). `RemoteEngineCoreApiAdapter`가 `EngineCoreApi` 전체를 구현하고, 로컬(`KataGoProcessEngineAdapter`)과 실패/타임아웃/재시도 신뢰도가 동등함을 계약 테스트로 검증했으며, 둘 다 `engine-android` 모듈에 물리적으로 함께 있다.
2. ~~**3계층 — `RemoteEngineSessionClient` 도입**~~ — 최소 형태 완료(260804 Stage E-1/E-2). 여러 원격/피어 후보 중 선택·신뢰도 판단을 흡수하는 자리(`selectRemoteEngineCandidate`)는 마련됐지만, 지금은 후보가 1개뿐이라 판단이 얕다. 후보가 실제로 여러 개가 되는 시점(DePIN 방향)에 응답시간/성공률 비교, "피어 평판/정산 기록"의 자리를 채워야 한다.
3. **1계층 — 물리 실행 환경 추상화**: 지금은 `KataGoProcessRuntime`이 "이 기기에서 프로세스 실행"만 가정한다. 원격 서버/피어 기기라는 "다른 물리 위치"를 1계층 개념에 맞게 명시적으로 표현할 방법을 정의(예: 실행 위치를 나타내는 값 타입).
4. **4계층 — 외부 연동 서비스 본체 두껍게 하기**: `premium-mode/README.md` Step 3(실제 광고)/Step 4(실제 결제), `auth-onboarding/README.md` Step 2~3(Google/이메일 로그인)을 구현하며, 포트(α)뿐 아니라 3계층 수준의 재시도/캐시/신뢰도 판단을 갖춘 서비스 본체로 채운다.
5. **(신규, 2026-08-14) 5/6계층 — 기능 엔타이틀먼트 정책 도입**: 정책적 결정(무료/광고/구매/클레임)이 앞으로도 계속 바뀔 것을 전제로 한 항목. 두 부분을 함께 진행한다 — 하나만 하면 반쪽짜리다.
   - **6계층**: `application/premium/PremiumState.kt`의 `isUndoClaimed: Boolean`을 `claimedFeatures: Set<FeatureId>`로 일반화. `persistence/PremiumStateStore.kt`(4계층 어댑터)의 JSON 스키마 마이그레이션(기존 `isUndoClaimed` 불리언 → `claimedFeatures` 배열, 구버전 로컬 저장값 하위호환) 포함.
   - **5계층**: `application/featureaccess/FeatureAccessPolicy.kt` 신설 — `resolve(featureId: FeatureId, state: PremiumState, nowMillis: Long): FeatureAccess`. 기능별 정책(무르기는 클레임+프리미엄 OR, 형세보기/추천수는 프리미엄만, 향후 다른 기능은 또 다를 수 있음)을 이 한 함수의 `when(featureId)` 분기 안에 모은다 — 정책이 바뀔 때 고칠 파일이 이 하나로 좁혀지는 것이 이 항목의 핵심 목표다.
   - **7계층 후속**: `ui/GamePlaySection.kt`의 `premiumGated`/`undoClaimGated`(276-285행)를 `FeatureAccessPolicy.resolve(...)` 호출로 교체. `ui/PremiumUiState.kt`의 `claimUndo: () -> Unit`은 `claim: (FeatureId) -> Unit`으로, `ui/GamePlaySection.kt`의 전용 `showUndoClaimDialog`(292-314행)는 기존 `PremiumUpsellDialog`(같은 파일 상단)에 `Claim` 선택지를 추가해 `unlockOptions` 기반으로 통합하는 것을 검토 — 잠긴 기능마다 전용 다이얼로그를 새로 만들지 않기 위함.
   - 근거: `feature-access-principles/README.md` 2장이 이미 "단일 플래그 → 기능별 원장" 방향을 제안해 뒀고(정책 문서 쪽 결정), 이 항목은 그 결정을 7계층 모델의 정확한 위치(5계층 판정 vs 6계층 상태)에 배치하는 아키텍처 쪽 결정이다. 자세한 갭 설명은 위 "알려진 갭" 참고.
6. **6계층 — 세션/연속성 공식화**: `auth-onboarding/README.md` Step 4(익명→실계정 승격, Firestore 동기화)를 이 계층의 정식 구현으로 진행. 기기 식별자 기반 다중 기기 정책도 이 단계에서 결정.
7. **`LayeringContractTest.kt` 갱신**: 위 항목들이 실제 코드로 옮겨질 때마다, 이번 재정의(2/3계층 경계, 4/6계층 신설, 5/7 번호 이동)를 반영해 계층 위반을 기계적으로 검증하도록 갱신. **코드가 실제로 옮겨지기 전까지는 테스트를 먼저 갱신하지 않는다** — 아직 물리적으로 분리되지 않은 것을 분리된 것처럼 강제하면 오탐만 늘어난다.
8. **문서 정리 후속 작업**: `docs/refactoring/`(23개 파일, 그중 8개는 2026-06-15 외부/내부 리뷰 클러스터로 중복이 크다)과 `docs/archive/` 전체를 다시 훑어 통폐합할지는 이번 범위 밖의 별도 작업이다. 필요해지면 이 로드맵에 항목을 추가한다.

## 관련 문서

- 레이어 원칙 자체(앱 비종속): [ARCHITECTURE.md](./ARCHITECTURE.md)
- 엔진 탐색 방식·레벨 정책·캐시 운영 상세: [ENGINE.md](./ENGINE.md)
- 프리미엄/결제 로드맵: `premium-mode/README.md`
- 인증/온보딩 로드맵: `auth-onboarding/README.md`
- 기능 유/무료 정책 원칙("무엇을 무료/광고/구매/클레임으로 줄지"의 근거) — 이 문서의 "5/6계층 기능 엔타이틀먼트 정책" 항목이 배치를 결정하는 반대편 문서: `feature-access-principles/README.md`
- 그 원칙을 초도 발행에 구체 적용한 전략/체크리스트: `launch-plan/README.md`
- 이 7계층 모델이 정착하기까지의 리팩토링 과정: `refactoring/` (날짜별 작업 로그)
