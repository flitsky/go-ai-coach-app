# go-ai-coach 아키텍처 현황과 고도화 로드맵

작성일: 2026-07-30
레이어 순서 갱신: 2026-07-30 — External Integration이 4계층(3계층과 대등한 서비스 계층)으로 재배치되며 Application(5)/Session & Continuity(6)/Presentation(7) 번호가 한 칸씩 밀렸다. [ARCHITECTURE.md](./ARCHITECTURE.md)의 "레이어 순서 확정" 항목 참고.
기능 엔타이틀먼트 정책 배치: 2026-08-14 — "무료/광고/구매/클레임" 같은 기능별 정책이 앞으로도 계속 바뀔 것을 전제로, 그 정책 판정을 6계층에 `FeatureAccessPolicy`로 명문화하고(설계 초안엔 5계층으로 잘못 적었다가 착수 시점에 정정 — 6계층 `PremiumState`를 파라미터로 받으므로 5계층일 수 없다) 6계층 `PremiumState`를 단일 플래그(`isUndoClaimed`)에서 기능별 원장(`claimedFeatures: Set<FeatureId>`)으로 일반화, 프레젠테이션 3곳(`ui/GamePlaySection.kt` 2곳, `ui/KaTrainUxPanels.kt` 1곳)에 하드코딩돼 있던 판정을 이걸로 교체했다 — 구현 완료. 상세는 "알려진 갭"·"고도화 로드맵" 절, 그리고 `feature-access-principles/README.md` 2장(같은 결론을 정책 문서 쪽에서 먼저 제안해 뒀던 것).

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
- `application/safety/EngineTurnWatchdog.kt` — AI 턴이 설정된 응답 시간(×1.2+3초, 무제한이면 60초)을 넘기면 감지하는 순수 판정 로직. 2026-07-30 신설. **260816**: `shared/src/commonMain/.../application/safety/`로 이전됨(패키지명은 유지) — 아래 "고도화 로드맵" 5번 항목의 스파이크 대상.
- `application/analysis/PositionAnalysisCache.kt`, `PositionAnalysisCacheOptimization*.kt` — JSON position analysis 결과를 품질/origin별로 저장하는 디스크 캐시
- `application/middleware/PositionAnalysisCacheResolver.kt` — 신뢰도 등급에 따라 캐시 hit을 평가/서빙
- `application/engine/RemoteEngineCandidate.kt` — DePIN 준비(260804 Stage E-1). 원격 후보 표현(`RemoteEngineCandidate`)과 선택/신뢰도 판단(`selectRemoteEngineCandidate`). `engine-android`를 import하지 않는 순수 3계층 판단 — 실제 `EngineCoreApi` 배선은 `engine/RemoteEngineSessionBootstrap.kt`(아래 참고)가 담당
- `engine/EngineBootstrap.kt`, `engine/RemoteEngineSessionBootstrap.kt` — `application/`이 아닌 별도 패키지(`com.worksoc.goaicoach.engine`)에 있는 composition-root 인접 배선 파일들. `application/`은 `engine.android`를 import할 수 없다는 기존 경계(`LayeringContractTest`) 때문에, "engine-android 구현을 실제로 생성"하는 코드는 전부 여기 산다 — 로컬은 `EngineBootstrap.createEngineBootstrap`, 원격은 `RemoteEngineSessionBootstrap.createRemoteEngineSessionClient`

**재편 여부**: 기존 4계층(Middleware/Cache Domain)에서 전송(2계층으로 이동)을 뺀 나머지 — 캐시, 신뢰도 라우팅, 동시성 lifecycle, 이번 세션에 추가된 엔진 턴 와치독까지 전부 여기.

**DePIN 관점에서의 역할(부분 착수, 260804 Stage E-1)**: 1계층이 여러 피어로 흩어지면, "지금 어느 피어를 쓸지 선택하고 그 결과를 얼마나 신뢰할지 판단"하는 책임이 이 계층으로 들어와야 한다. `selectRemoteEngineCandidate`가 그 자리를 잡았지만, 지금은 후보가 항상 최대 1개라 판단이 "활성화돼 있는가/엔드포인트가 유효한가"만큼만 있다 — 여러 후보의 응답 시간·성공률을 비교하는 진짜 신뢰도 판단은 실제로 후보가 2개 이상 생기는 시점(Stage F, DePIN 확장)에 채워야 한다.

### 4계층 — External Integration (외부 연동)

**위치**:
- `shared/.../application/auth/AuthClientPort.kt`(α: 순수 포트, **260816** `app-android`→`shared` 이전 완료) ↔ `ui/AndroidAuthClient.kt`(Firebase Auth 어댑터 — Extended API 본체가 실제 SDK에 닿는 지점)
- `shared/.../application/premium/PremiumStatePorts.kt`(α: 순수 포트, **260816** `app-android`→`shared` 이전 완료) ↔ `persistence/PremiumStateStore.kt`(SharedPreferences 어댑터)
- `ui/AndroidPlatformPorts.kt` — 가벼운 플랫폼 포트(클립보드, 토스트) 공용 파일

**재편 여부**: 기존 모델에는 이 계층이 없었고 "포트/어댑터 분리 원칙"이라는 (구)4계층 문서의 부칙으로만 존재했다. 이번에 3계층과 대등한 정식 서비스 계층으로 승격했다 — 3계층이 엔진 raw 계약을 서비스로 조합하듯, 이 계층은 외부 SDK의 raw 계약을 서비스로 조합한다(자세한 논거는 ARCHITECTURE.md 4계층 절).

**핵심 갭 (260817 정정 — 아래 원문 "전부 스텁 상태"는 더 이상 사실이 아니다)**: AdMob은 실제 계정·광고 단위(배너+리워드 전면)로 완전히 라이브다(빌드타입별 테스트/실제 광고 전환 포함, `[[premium-admob-status]]`). Play Billing은 코드가 end-to-end로 완성돼 실제 Play Billing 인프라에 연결돼 있으나, Play Console 상품 등록·라이선스 테스터 설정이라는 사용자 쪽 외부 작업에 막혀 있어 실사용 완결 여부는 이 문서 갱신 시점 기준 별도 확인이 필요하다(`[[premium-billing-status]]`). Google/이메일 로그인도 실제 Firebase로 기기 검증까지 끝났지만, `ui/FeatureFlags.kt`의 `isLoginEnabled = false`로 앱의 로그인 진입 경로 자체가 지금은 꺼져 있다(Email Link만 별도로 의도적 보류, `[[auth-google-signin-status]]`). 셋 다 스텁이 아니라 "동작하는 실제 코드는 있지만 각자 다른 이유로 최종 사용자 경로에는 아직 안 열려 있거나(로그인 플래그 OFF, 결제 상품 미등록) 처음부터 열려 있다(AdMob)"가 정확한 현재 상태다.

포트(α)는 이미 이 계층 원칙대로 배치돼 있으니, 새 SDK 연동 시 파일을 어디 둘지는 이미 정해져 있다. **진짜 남은 갭은 원래 문구가 짚은 그대로다** — "Extended API 본체"(실패/재시도/캐시까지 감안한 안정화 서비스)는 아직 얇다: `AndroidAuthClient`/`PremiumStateStore`는 SDK 호출을 그대로 감싸는 수준이라, 3계층의 `PositionAnalysisCacheResolver` 같은 신뢰도/재시도 판단이 없다. 착수 시 유의: 로그인 쪽 하드닝은 `isLoginEnabled`가 켜지기 전까지 실기로 검증할 방법이 없다.

### 5계층 — Application / Domain

**위치**:
- `shared/src/commonMain/.../BoardModels.kt`, `BoardRules.kt`, `LegalMoveGenerator.kt`, `BoardScorer.kt`(+`BoardAreaScorer.kt`/`BoardTerritoryScorer.kt`), `EndgameScoreSelector.kt`, `GameStateReplayer.kt`, `ScoreTimeline.kt` — 순수 바둑 규칙(KMP `commonMain`)
- `shared/src/commonMain/kotlin/com/worksoc/goaicoach/match/MatchReferee.kt`, `AiMoveSelectionPolicy.kt`, `MatchPolicy.kt` — 대국 정책(참여 주체, 턴 권한, AI 레벨링). **260804 경로 정정**: 이전엔 `app-android/.../match/`였으나 "도메인별 파일 분리" 작업(커밋 `5278c12`)으로 `shared`로 이동했다 — 순수 로직이라 KMP 이식 대상이었다.
- `shared/src/commonMain/kotlin/com/worksoc/goaicoach/application/{session,autoai,undo,humanmove,startgame,savedgame,topmoves,debugreport,score,endgame,diagnostic,runtime,preferences,prompt,movereview,analysis}` — App Service 유스케이스 오케스트레이션. **260816**: 착수 계획서 기준 웨이브 1~6으로 `app-android`에서 `shared`로 물리 이전 완료(예외 1개, 아래 "핵심 갭" 참고; 착수 계획서 원문은 2026-08-17 문서 정리로 저장소에서 제거 — `docs/DOCS_INDEX.md` "문서 보존 정책" 참고). `session/GameSessionStateHolder.kt`가 세션 상태의 단일 source of truth
- `ui/GoCoachApp.kt` — 위 모든 컨트롤러를 생성/연결하는 composition root (2026-08-14 기준 819줄, `LayeringContractTest`가 라인수 819/상태훅 47 예산을 강제 — 예산 여유 0)

**260814 정정**: 이 절에 한때 "기능별 접근 정책 판정"(`FeatureAccessPolicy`)을 5계층으로 적어뒀으나 오기였다 — `PremiumState`(6계층)를 파라미터로 받는 함수는 5계층이 아니라 6계층 소속이다(5계층은 6계층을 몰라야 하므로). 실제 구현은 아래 6계층 절 참고.

**재편 여부**: 기존 3계층(Core Rules)+5계층(Game Domain)+6계층(App Service/Session Orchestration)을 하나로 통합. 순수 규칙과 오케스트레이션은 성격이 다르지만 "이 앱만의 것"이라는 공통점으로 묶었다 — [ARCHITECTURE.md](./ARCHITECTURE.md)의 5계층 정의를 따른다. 아래 3계층(엔진 서비스)과 4계층(외부 연동 서비스)을 동등하게 소비한다.

**핵심 갭**: 예외 1개만 남았다 — `application/diagnostic/LocalFileDiagnosticEventExternalSink.kt`(`java.io.File` 직접 사용)는 포트/어댑터 분리 원칙에 따라 영구히 `app-android`에 잔류(포트 `DiagnosticEventExternalSinkPort`는 `shared`로 이전됨). `LayeringContractTest.kt`의 `engineOperationApplicationPoliciesStayPortable`이 이 예외 하나만 명시적으로 허용하고 나머지는 전부 `shared`에서 이식성을 상시 검증한다.

### 6계층 — Session & Continuity

**위치**:
- `shared/.../application/auth/AuthState.kt`, `AuthClientPort.kt` — 로그인 상태 순수 모델 + 포트
- `shared/.../application/premium/PremiumState.kt`, `PremiumStatePorts.kt` — 프리미엄 활성화 상태. `matchGeneration`(대국 세대 — 무르기로는 바뀌지 않음)으로 5계층의 `sessionGeneration`(엔진 오퍼레이션 무효화 세대 — 무르기마다 바뀜)과 **의도적으로 분리**돼 있다(2026-07-30 수정 — 이 분리가 없어서 무르기 시 프리미엄이 풀리는 버그가 있었다). 필드는 `source`(None/AdGrant/Purchase) + `adGrantStartedAtMillis` + `claimedFeatures: Set<FeatureId>`. **260814**: 예전엔 무르기 하나만을 위한 `isUndoClaimed: Boolean`이었으나, 앞으로 다른 기능도 같은 방식(초도 클레임+그랜드파더링)으로 무료 제공할 때 새 불리언을 또 추가하지 않도록 기능별 원장(`claimedFeatures`)으로 일반화했다. `source`/`adGrantStartedAtMillis`(구독형 축)와 `claimedFeatures`(1회 클레임형 축)는 여전히 서로 다른 축으로 분리돼 있다 — 위 문단이 이미 겪은 "축 혼동 버그"를 반복하지 않기 위함.
- **(260814 신설)** `shared/.../application/premium/FeatureAccessPolicy.kt` — 기능별 접근 정책 판정. `PremiumState`(바로 위 항목, 6계층 — "지금 무엇을 갖고 있는가")를 입력으로 받아, 기능 하나(`FeatureId`)에 대해 "지금 쓸 수 있는가, 없다면 무엇으로 풀 수 있는가"를 순수 함수로 판정한다 — `fun resolve(featureId: FeatureId, state: PremiumState, nowMillis: Long): FeatureAccess`(`FeatureAccess` = `Allowed(via: AllowedVia)` | `Locked(unlockOptions: Set<UnlockOption>)`). `PremiumState`를 입력으로 받으므로 6계층 소속이다(5계층은 6계층을 몰라야 함) — `PremiumState.isActive()`가 이미 같은 이유로 `PremiumState` 자신에 있는 선례를 따름. `ui/GamePlaySection.kt`의 `featureGated(access, action)`·`ui/KaTrainUxPanels.kt`의 `moveReviewAllowed`가 이 판정을 소비한다(둘 다 더 이상 `isActive`/클레임 여부를 직접 조합하지 않는다).

**재편 여부**: 신규 계층(번호만 5→6으로 이동, 정의는 그대로). 기존 7계층 모델에는 없었고, `application/auth`/`application/premium`이 사실상 이 자리를 채우고 있었지만 명문화된 계층은 아니었다.

**핵심 갭**: 아직 "세션/연속성"이라는 이름에 걸맞은 범용 개념(기기 식별자, 익명→실계정 승격, 다중 기기 정책)이 없다 — 지금은 auth/premium 각자가 필요한 만큼만 자기 상태를 갖고 있다. `auth-onboarding/README.md`의 "익명 인증 → 실계정 승격" 로드맵이 이 계층을 채우는 다음 작업이다(단, 익명 로그인 자체가 2026-08-05에 영구 폐기돼 그 로드맵 문구는 재검토가 먼저 필요 — 아래 로드맵 절 참고). `application/auth`/`application/premium` 자체는 이 6계층에 속하지만, 그 포트가 실제 Firebase/SharedPreferences에 닿는 부분(위 4계층 참고)과는 구분해서 봐야 한다. ~~`claimedFeatures` 일반화 전 수동 이어붙이기 부담~~ — 260814에 해소(바로 위 `PremiumState` 항목 참고).

### 7계층 — Presentation

**위치**: `ui/`(`GoCoachApp.kt`, `GoBoard.kt`, `GameMenuSection.kt`, `GamePlaySection.kt`, `KaTrainUxPanels.kt`, `EngineResponsePanel.kt`, `ScoreGraphPanel.kt` 등), `presentation/`(`GameUiEvent.kt`, `GameScreenState.kt`, `GoCoachScreenStateAssembler.kt` 등)

**재편 여부**: 기존 7계층과 동일한 정의, 번호도 그대로 최상위(7번) 유지.

---

## 알려진 갭 (2026-07-30 기준)

- ~~`GameSessionStateHolder`(5계층)는 여전히 `app-android`에 있다~~ — **260816 해소**. `application/safety/` 스파이크로 이전 절차(물리 이동·`internal`→public·JUnit→kotlin.test·양쪽 모듈 컴파일·iOS 타깃 컴파일까지)를 먼저 검증한 뒤, 같은 날 웨이브 1~6으로 `application/` 트리 124개 프로덕션 파일 전부(영구 예외 1개 제외)를 `shared`로 물리 이전 완료. `GameSessionStateHolder.kt` 본체(웨이브 5)와 그에 의존하는 마지막 컨트롤러들(웨이브 6) 모두 포함. `:shared`/`:app-android` 컴파일 + `make test` 전체 그린, `NewGameBoardTapSmokeTest.kt`/`AppLaunchSmokeTest.kt` 에뮬레이터 실기 재확인도 통과 — 상세는 아래 "고도화 로드맵" 5번 참고(당시 착수 계획서 원문은 2026-08-17 문서 정리로 제거, `docs/DOCS_INDEX.md` "문서 보존 정책" 참고).
- ~~`RemoteEngineSessionClient`(3계층, 여러 원격 후보 중 선택·신뢰도 판단)가 없다~~ — 260804 Stage E-1/E-2에서 최소 형태로 해소(`selectRemoteEngineCandidate`+`RemoteEngineSessionBootstrap.createRemoteEngineSessionClient`). `RemoteEngineCoreApiAdapter`(원격 `EngineCoreApi` 구현체 자체, 260803 Stage D)와 함께 이제 준비돼 있지만, 아직 앱 실제 컴포지션(MainActivity/GoCoachApp)에 배선되지 않았고 "고정된 원격 서버 1대"조차 가리킬 실제 서버가 없어 실제로 쓰이고 있지 않다 — Stage F 영역.
- ~~2계층의 로컬/원격 구현체가 대등하지 않다~~ — 260803 Stage D에서 해소(위 2계층 절 참고). 260804에 물리적으로도 `engine-android` 한 모듈로 모았다.
- 4계층(외부 연동)이 포트(α)만 있고 안정화 서비스 본체가 얇다.
- 6계층(세션/연속성)이 auth/premium 각자의 필요만 채우고 있고, 범용 개념이 없다.
- ~~기능 엔타이틀먼트 판정이 6계층이 아니라 7계층에 있다~~ — 260814에 해소. "이 유저가 무르기를 쓸 수 있는가" 같은 OR 조합이 `ui/GamePlaySection.kt`의 `undoClaimGated`/`premiumGated`(형세보기·추천수용) 두 로컬 함수와 `ui/KaTrainUxPanels.kt`의 독립 인라인 체크(착수평가)까지 총 3곳에 각각 하드코딩돼 있던 것을, `application/premium/FeatureAccessPolicy.kt`(위 6계층 절) 하나로 통합했다 — 세 곳 모두 이제 `featureGated(access, action)`/`moveReviewAllowed` 형태로 판정 결과만 소비한다.
- `LayeringContractTest.kt`는 아직 2026-06-27판 경계(옛 1~7계층 이름) 기준으로 작성돼 있다. 이번 재정의(2/3계층 재편, 4/6계층 신설, 5/7 번호 이동)를 반영하지 않았다.
- androidTest(Robolectric/계측) 커버리지가 기본 검증 경로에 없다(`make test`에 안 묶여 있음 — 의도적, M-04 제약). 컴파일+JVM 단위 테스트가 기본 검증이다. **260816**: `AppLaunchSmokeTest.kt`(실제 `MainActivity`→`createEngineBootstrap`→`GoCoachApp` 경로)가 `@Ignore` 스켈레톤에서 활성 테스트로 전환됐다 — M-04 target list 중 "app-launch" 경로 완료. **260817**: `SavedSessionPromptSmokeTest.kt` 신설으로 "saved-session-prompt" 경로도 완료(에뮬레이터 3회 연속 통과) — 당시 작업 우선순위 백로그 원문은 2026-08-17 문서 정리로 제거(`docs/DOCS_INDEX.md` "문서 보존 정책" 참고). 더 넓은 이벤트 디스패치 커버리지만 여전히 열려 있다.

## 고도화 로드맵

우선순위 순서가 아니라 계층별로 정리한 것이며, 착수 순서는 별도 착수 계획서(`refactoring/`에 `YYMMDD HHhMMm` 타임스탬프 관례로 추가)에서 정한다.

1. ~~**2계층 — 로컬/원격 계약 대등화**~~ — 완료(260803 Stage D, 260804 물리적 모듈 통합). `RemoteEngineCoreApiAdapter`가 `EngineCoreApi` 전체를 구현하고, 로컬(`KataGoProcessEngineAdapter`)과 실패/타임아웃/재시도 신뢰도가 동등함을 계약 테스트로 검증했으며, 둘 다 `engine-android` 모듈에 물리적으로 함께 있다.
2. ~~**3계층 — `RemoteEngineSessionClient` 도입**~~ — 최소 형태 완료(260804 Stage E-1/E-2). 여러 원격/피어 후보 중 선택·신뢰도 판단을 흡수하는 자리(`selectRemoteEngineCandidate`)는 마련됐지만, 지금은 후보가 1개뿐이라 판단이 얕다. 후보가 실제로 여러 개가 되는 시점(DePIN 방향)에 응답시간/성공률 비교, "피어 평판/정산 기록"의 자리를 채워야 한다.
3. **1계층 — 물리 실행 환경 추상화**: 지금은 `KataGoProcessRuntime`이 "이 기기에서 프로세스 실행"만 가정한다. 원격 서버/피어 기기라는 "다른 물리 위치"를 1계층 개념에 맞게 명시적으로 표현할 방법을 정의(예: 실행 위치를 나타내는 값 타입).
4. **4계층 — 외부 연동 서비스 본체 두껍게 하기**: `premium-mode/README.md` Step 3(실제 광고)/Step 4(실제 결제), `auth-onboarding/README.md` Step 2~3(Google/이메일 로그인)을 구현하며, 포트(α)뿐 아니라 3계층 수준의 재시도/캐시/신뢰도 판단을 갖춘 서비스 본체로 채운다.
5. ~~**5계층 — `GameSessionStateHolder` → `:shared` KMP 이전.**~~ — **완료(260816)**. 이식 가능성(플랫폼 비종속)은 `engineOperationApplicationPoliciesStayPortable`(125개 파일, 21개 서브패키지 전체 스캔)이 사전에 검증했고, `GameSessionCoreState`/`GameSessionController`가 `autoai/engine/humanmove/savedgame/score/startgame/topmoves/undo/debugreport/analysis/movereview/preferences` 12개 서브패키지를 전이적으로 끌어들여 "일부만 이전"이 불가능하다는 예측대로 `application/`(124개 프로덕션 파일) 전체를 한 단위로 이전했다.
   - ~~**(1) 스파이크**~~ — **완료(260816)**. `application/safety/EngineTurnWatchdog.kt`(팬인/팬아웃 최소, `SearchTimeLimit` 하나만 의존)를 `shared/commonMain`으로, 테스트를 `shared/commonTest`로 옮겨 전체 절차를 실제로 검증했다. 확인된 것: (a) 패키지명(`com.worksoc.goaicoach.application.safety`)은 그대로 유지 가능 — `:shared` 안에 이미 `match/`처럼 비-`shared` 패키지가 있어 모듈 경계와 패키지명이 독립적이므로, app-android 호출부(`ui/GamePlaySection.kt`)의 import 문은 **한 줄도 안 바뀜**. (b) `internal fun` 2개를 public으로 바꿔야 했다 — 예상대로. (c) **새로 발견한 것**: 테스트도 함께 옮겨야 하고, `shared/commonTest`는 `org.junit.*`가 아니라 `kotlin.test.*`(멀티플랫폼 API)를 쓰는 게 기존 컨벤션이라 import 전환이 필요했다 — API가 동일해서 기계적 치환이지만, 본 이전 때 옮기는 모든 테스트 파일마다 반복해야 할 작업. (d) `:shared`가 `androidMain` 없이도 문제없이 받아들였고, `./gradlew :shared:compileKotlinIosSimulatorArm64 -PenableIosTargets=true`까지 깨끗이 통과 — 진짜 멀티플랫폼 이식성까지 확인됨. `:shared:testDebugUnitTest`/`:app-android:testDebugUnitTest`/`make test` 전부 그린.
   - ~~**(2) 본 이전**~~ — **완료(웨이브 1~6, 260816)**. 당시 착수 계획서가 나머지 124개 프로덕션 파일(+기존 단위테스트 50개)의 실제 파일 단위 의존 그래프를 스캔해 이동 순서를 확정했다. **계획 수립 중 정정한 예상**: "`internal`→기본 가시성 확대는 실제 app-android 호출부가 있는 지점만"이라고 예상했었는데, 스파이크(파일 1개) 결과를 트리 전체로 낙관적으로 외삽한 것이었다 — 실측하니 509개 중 424개(83%)가 이미 `ui/`/`persistence/`/`middleware/`/테스트에서 참조되고 있어 이동 순서와 무관하게 public 전환이 필요했다. 같은 날 이어서 웨이브 1(56개 파일, 원래 계획한 19개가 아니라 파일 단위 의존 그래프 조사 자체의 결함 — 같은 패키지 내부의 무-import 참조 누락 — 을 실행 중 발견/교정한 뒤 확정된 진짜 원자적 단위)부터 시작해 웨이브 2~6까지 총 6개 웨이브로 나머지 68개 파일을 이전했다 — 웨이브 5에서 키스톤 `GameSessionStateHolder.kt` 본체, 웨이브 6에서 그에 의존하는 마지막 컨트롤러들(topmoves/session/startgame)이 이동했다. `internal`→public 전환은 사전 감사가 아니라 컴파일러가 지목하는 대로 반복 수렴하는 방식이 전 웨이브에서 그대로 통했고, 도중에 발견된 함정들 — `LayeringContractTest.kt`의 하드코딩 경로 문제(`applicationFile()` 헬퍼로 해결), Kotlin 크로스모듈 스마트캐스트 제약(로컬 val 캡처로 해결), `middleware/`처럼 `application/` 트리 밖 패키지를 참조하는 파일(포터빌리티 확인 후 함께 이전), 테스트가 `persistence/`·`ui/` 같은 비이식 레이어를 직접 테스트하는 경우(영구 잔류로 원위치), `kotlin.test.assertNotNull`/`assertFailsWith`가 `Unit`이 아니라 값을 반환해 표현식-바디 테스트 함수의 추론 반환 타입을 깨는 경우(명시적 `: Unit` 반환 타입으로 해결) — 도 전부 처리했다. 웨이브별 실행 기록 원문은 2026-08-17 문서 정리로 제거됐다(`docs/DOCS_INDEX.md` "문서 보존 정책" 참고, 필요시 `git log`로 복원 가능).
   - ~~**(3) `LayeringContractTest.kt` 갱신**~~ — **완료(260816)**. `engineOperationApplicationPoliciesStayPortable`의 스캔 대상을 `app-android/.../application`(이전 완료 후 사실상 공집합)에서 실제 파일들이 있는 `shared/src/commonMain/.../application`으로 변경. `ui.`/`persistence.`/`engine.`(composition-root) 임포트 금지 체크는 제거 — `shared`가 `app-android`에 대한 Gradle 의존성 자체가 없어(commonMain은 `kotlinx-coroutines-core`만 의존) 어기면 텍스트 검사 없이도 그냥 컴파일 에러가 난다. `android.`/`androidx.`/`java.`/`org.json.` 금지 체크는 유지 — `shared`의 `androidTarget`은 이 API들에 실제 접근 가능해서 컴파일은 통과하지만 iOS 등 다른 KMP 타깃을 조용히 깨뜨릴 수 있기 때문.
   - ~~**(4) 회귀 확인**~~ — **완료(260816, 2회)**. `NewGameBoardTapSmokeTest.kt`/`AppLaunchSmokeTest.kt`를 웨이브 5(키스톤 이동 직후)와 웨이브 6(세션 배선에 손대는 마지막 웨이브) 완료 직후 각각 에뮬레이터(`emulator-5554`)에서 `connectedDebugAndroidTest`로 재확인 — 둘 다 2/2 green.
   - ~~**(5) 플랫폼 누수 회귀 복구**~~ — **완료(260824)**. (1)(d)에서 확인했던 "iOS 컴파일 깨끗이 통과"는 그 뒤 어느 시점에 깨져 있었다 — `./gradlew :shared:compileKotlinIosSimulatorArm64 -PenableIosTargets=true`가 에러 49개. iOS 타깃은 `-PenableIosTargets=true`로만 켜지므로 평소 안드로이드 빌드/테스트는 계속 그린이었고, 아무도 모르는 채 누적됐다. **원인은 (3)에서 유지하기로 한 텍스트 검사의 사각지대**: `import java.`를 막아도 `java.lang.*`은 JVM 자동 임포트라 `System.currentTimeMillis()`가 import 한 줄 없이 통과한다(`kotlin.synchronized`도 동일). 실제 내역은 시간 39건(20개 파일) + `synchronized` 4건 + `Dispatchers.IO` 1건이었다 — 즉 "시간"만의 문제가 아니었다.
     - **시간(39건)**: `application/time/AppClock.kt`의 `currentEpochMillis()` 하나로 모았다. 호출부가 이미 갖고 있던 `nowMillis` 주입 시임(기본값 파라미터/람다)은 그대로 두고 그 **기본값**만 이 함수를 거치게 했으므로 테스트가 시간을 고정하는 방법은 전과 동일하다. `SystemEngineClock`도 여기로 위임한다. 구현은 stdlib `kotlin.time.Clock`이라 새 의존성이 아니다(kotlinx-datetime 도입 여부는 여전히 별개 결정). 경과 시간 측정(`System.nanoTime()`)은 `kotlin.time.TimeSource.Monotonic`으로 바꿨다.
     - **`synchronized`(4건) / `Dispatchers.IO`(1건)**: 이 둘은 진짜로 플랫폼마다 답이 다르다 — `expect`/`actual` 2개로 처리하고 `androidMain`/`iosMain` 소스셋을 신설했다(이전엔 `commonMain`만 있었다). `application/concurrency/SharedLock.kt`(android=모니터 락, ios=`NSLock`), `application/engine/EngineIoDispatcher.kt`(android=`Dispatchers.IO`, ios=`Dispatchers.Default` — coroutines 1.8.0은 네이티브에서 `Dispatchers.IO`를 공개하지 않는다. iOS 실출시 전에 1.9+로 올려 바꿔야 함). **안드로이드 런타임 동작은 전부 동일하게 유지**했다.
     - **재발 방지**: `LayeringContractTest.sharedCommonMainAvoidsImplicitlyImportedJvmApis` 신설 — import 문이 아니라 `System.`/`System::`/`Thread.`/`Runtime.`/`synchronized(` **이름 자체**를 `shared/commonMain` 전체에서 막는다. 기본 테스트 루프에 포함되므로 iOS 타깃을 켜지 않아도 걸린다.
6. ~~**6계층 — 기능 엔타이틀먼트 정책 도입**~~ — 완료(260814). `application/premium/PremiumState.kt`의 `isUndoClaimed: Boolean`을 `claimedFeatures: Set<FeatureId>`로 일반화하고(`persistence/PremiumStateStore.kt`에 구버전 불리언 하위호환 마이그레이션 포함), `application/premium/FeatureAccessPolicy.kt`(같은 6계층 — 애초 설계 초안엔 5계층으로 잘못 적혀 있었으나 착수 시점에 정정)를 신설해 `ui/GamePlaySection.kt`(형세보기/추천수/무르기)·`ui/KaTrainUxPanels.kt`(착수평가)에 각자 하드코딩돼 있던 3곳의 판정을 이 함수 하나로 통합했다. **의도적으로 남겨둔 것**: 클레임 전용 다이얼로그(`ui/GamePlaySection.kt`의 `showUndoClaimDialog`)를 `PremiumUpsellDialog`에 `Claim` 선택지로 통합하는 UI 단순화는 이번 범위에서 제외 — 클레임 가능 기능이 아직 무르기 하나뿐이라 지금 합치는 건 과설계로 판단, 두 번째 클레임형 기능이 생기면 재검토.
7. **6계층 — 세션/연속성 공식화**: `auth-onboarding/README.md` Step 4(익명→실계정 승격, Firestore 동기화)를 이 계층의 정식 구현으로 진행 — **단, 익명 로그인 자체가 2026-08-05에 영구 폐기 결정됐으므로("재설치마다 허수 계정이 쌓이는 문제를 이전 앱에서 실제로 겪음") "익명→실계정 승격" 경로 자체가 성립하지 않는다. 이 항목은 착수 전에 목표를 다시 정의해야 한다** — 예를 들어 "게스트(로컬 ID)→실계정 승격"처럼 익명 인증을 전제하지 않는 형태로. 기기 식별자 기반 다중 기기 정책도 이 재정의와 함께 결정.
8. **`LayeringContractTest.kt` 갱신**: 위 항목들이 실제 코드로 옮겨질 때마다, 이번 재정의(2/3계층 경계, 4/6계층 신설, 5/7 번호 이동)를 반영해 계층 위반을 기계적으로 검증하도록 갱신. **코드가 실제로 옮겨지기 전까지는 테스트를 먼저 갱신하지 않는다** — 아직 물리적으로 분리되지 않은 것을 분리된 것처럼 강제하면 오탐만 늘어난다.
9. ~~**문서 정리 후속 작업**~~ — **완료(260817)**. `docs/refactoring/`(리팩토링 축이 이미 종료됨)과 `docs/archive/` 전체(55개 파일, 1.2MB)를 저장소에서 제거했다. "삭제 대신 보관" 원칙을 뒤집는 결정이라 `docs/DOCS_INDEX.md` "문서 보존 정책" 절에 사유와 복원 방법을 기록했다. 유일한 예외는 실측 데이터로 계속 인용되던 `ENGINE_BEGINNER_VISITS_BENCHMARK.md`로, `docs/engine-research/`로 이동 보존했다.

## 관련 문서

- 레이어 원칙 자체(앱 비종속): [ARCHITECTURE.md](./ARCHITECTURE.md)
- 엔진 탐색 방식·레벨 정책·캐시 운영 상세: [ENGINE.md](./ENGINE.md)
- 프리미엄/결제 로드맵: `premium-mode/README.md`
- 인증/온보딩 로드맵: `auth-onboarding/README.md`
- 기능 유/무료 정책 원칙("무엇을 무료/광고/구매/클레임으로 줄지"의 근거) — 이 문서의 "6계층 — 기능 엔타이틀먼트 정책 도입" 항목이 배치를 결정하는 반대편 문서: `feature-access-principles/README.md`
- 그 원칙을 초도 발행에 구체 적용한 전략/체크리스트: `launch-plan/README.md`
- 이 7계층 모델이 정착하기까지의 리팩토링 과정: `refactoring/` (날짜별 작업 로그)
