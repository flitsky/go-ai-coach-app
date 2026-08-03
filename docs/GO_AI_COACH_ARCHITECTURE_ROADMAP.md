# go-ai-coach 아키텍처 현황과 고도화 로드맵

작성일: 2026-07-30
레이어 순서 갱신: 2026-07-30 — External Integration이 4계층(3계층과 대등한 서비스 계층)으로 재배치되며 Application(5)/Session & Continuity(6)/Presentation(7) 번호가 한 칸씩 밀렸다. [ARCHITECTURE.md](./ARCHITECTURE.md)의 "레이어 순서 확정" 항목 참고.

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
- `shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/EngineModels.kt` — `EngineCoreApi` 인터페이스(1:1 원시 계약: `initialize`, `configure`, `playMove`, `analyze`, `estimateScore`, `deadStones`, `scoreFinal`, `clearSearchCache`, `stop`, `forceReset` 등), `AnalysisLimit`/`EngineProfile`/`CandidateMove` 등 순수 데이터 모델
- `engine-android/.../KataGoProcessEngineAdapter.kt` — `EngineCoreApi`의 **로컬** 구현체. GTP(`KataGoGtpAnalysisClient.kt`, `KataGoProtocolCommands.kt`)와 JSON(`KataGoJsonPositionAnalysisClient.kt`, `KataGoJsonAnalysisQueryFactory.kt`, `KataGoJsonAnalysisParser.kt`) 두 경로를 조율. 두 경로 공통 파싱은 `KataGoAnalysisParser.kt`/`KataGoAnalysisContext.kt`
- `engine-android/.../StubEngineAdapter.kt` — `EngineCoreApi`의 **스텁** 구현체(엔진 없이 UI/도메인 검증용)
- `application/middleware/HttpRemotePositionAnalysisTransport.kt`, `RemotePositionAnalysisGateway.kt` — `EngineCoreApi`와 동일한 계약을 **원격**으로 도달시키기 위한 read-only position-analysis 단위 트랜스포트 스파이크(2026-06-28 기준 기본값 off)

**재편 여부**: 기존 2계층(Engine Core API Domain, 계약 정의만)에 기존 4계층(Middleware/Cache Domain)의 **전송** 절반(원격 게이트웨이/트랜스포트)을 합쳤다. "계약을 정의하는 것"과 "그 계약을 실제로 어떻게 도달시키는가(로컬 stdio냐 원격 HTTP냐)"가 개념적으로 같은 책임이라고 보기 때문이다.

**핵심 갭**: `KataGoProcessEngineAdapter`(로컬)와 `HttpRemotePositionAnalysisTransport`(원격)는 아직 **같은 인터페이스의 대등한 두 구현체가 아니다** — 원격 트랜스포트는 position-analysis 한 종류의 호출만 다루고, `EngineCoreApi` 전체를 구현하지 않는다. "로컬이든 원격이든 완전히 동일한 계약"이라는 2계층의 설계 요구사항은 아직 충족되지 않은 상태다.

### 3계층 — Extended API (엔진 서비스)

**위치**:
- `application/engine/EngineSessionClient.kt` — UI/App Service가 바라보는 고수준 엔진 게이트웨이 인터페이스. `analyzePosition(state, limit, searchMode)`처럼 명시적 `GameState`를 받아 local/remote 차이를 숨김. `forceResetEngine()`처럼 비정상 상태 복구용 진입점도 여기 있다
- `application/engine/LocalEngineSessionClient.kt` + `LocalAiMoveEngineGateway.kt`, `LocalEndgameJudgeGateway.kt`, `LocalEngineCoreSessionDelegate.kt`, `LocalEngineBenchmarkDelegate.kt`, `LocalPositionAnalysisCacheCoordinator.kt` — local 구현체, 역할별 delegate로 분리
- `application/engine/operation/` — `EngineOperationLifecycleController` 등. 동시 엔진 호출 추적, 늦게 도착한 결과 폐기(stale guard), busy 상태 관리
- `application/safety/EngineTurnWatchdog.kt` — AI 턴이 설정된 응답 시간(×1.2+3초, 무제한이면 60초)을 넘기면 감지하는 순수 판정 로직. 2026-07-30 신설
- `application/analysis/PositionAnalysisCache.kt`, `PositionAnalysisCacheOptimization*.kt` — JSON position analysis 결과를 품질/origin별로 저장하는 디스크 캐시
- `application/middleware/PositionAnalysisCacheResolver.kt` — 신뢰도 등급에 따라 캐시 hit을 평가/서빙

**재편 여부**: 기존 4계층(Middleware/Cache Domain)에서 전송(2계층으로 이동)을 뺀 나머지 — 캐시, 신뢰도 라우팅, 동시성 lifecycle, 이번 세션에 추가된 엔진 턴 와치독까지 전부 여기.

**DePIN 관점에서의 역할**: 1계층이 여러 피어로 흩어지면, "지금 어느 피어를 쓸지 선택하고 그 결과를 얼마나 신뢰할지 판단"하는 책임이 이 계층으로 들어와야 한다. 현재는 이 판단 로직 자체가 없다 — `RemoteEngineSessionClient` 구현체 부재가 정확히 이 갭이다.

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
- `app-android/.../match/MatchReferee.kt`, `AiMoveSelectionPolicy.kt`, `MatchPolicy.kt` — 대국 정책(참여 주체, 턴 권한, AI 레벨링)
- `app-android/.../application/{session,autoai,undo,humanmove,startgame,savedgame,topmoves,debugreport,score,endgame,diagnostic,runtime,preferences,prompt,movereview,analysis}` — App Service 유스케이스 오케스트레이션. `session/GameSessionStateHolder.kt`가 세션 상태의 단일 source of truth
- `ui/GoCoachApp.kt` — 위 모든 컨트롤러를 생성/연결하는 composition root (2026-07-30 기준 850줄, `LayeringContractTest`가 라인수 880/상태훅 47 예산을 강제)

**재편 여부**: 기존 3계층(Core Rules)+5계층(Game Domain)+6계층(App Service/Session Orchestration)을 하나로 통합. 순수 규칙과 오케스트레이션은 성격이 다르지만 "이 앱만의 것"이라는 공통점으로 묶었다 — [ARCHITECTURE.md](./ARCHITECTURE.md)의 5계층 정의를 따른다. 아래 3계층(엔진 서비스)과 4계층(외부 연동 서비스)을 동등하게 소비한다.

### 6계층 — Session & Continuity

**위치**:
- `application/auth/AuthState.kt`, `AuthClientPort.kt` — 로그인 상태 순수 모델 + 포트
- `application/premium/PremiumState.kt`, `PremiumStatePorts.kt` — 프리미엄 활성화 상태. `matchGeneration`(대국 세대 — 무르기로는 바뀌지 않음)으로 5계층의 `sessionGeneration`(엔진 오퍼레이션 무효화 세대 — 무르기마다 바뀜)과 **의도적으로 분리**돼 있다(2026-07-30 수정 — 이 분리가 없어서 무르기 시 프리미엄이 풀리는 버그가 있었다)

**재편 여부**: 신규 계층(번호만 5→6으로 이동, 정의는 그대로). 기존 7계층 모델에는 없었고, `application/auth`/`application/premium`이 사실상 이 자리를 채우고 있었지만 명문화된 계층은 아니었다.

**핵심 갭**: 아직 "세션/연속성"이라는 이름에 걸맞은 범용 개념(기기 식별자, 익명→실계정 승격, 다중 기기 정책)이 없다 — 지금은 auth/premium 각자가 필요한 만큼만 자기 상태를 갖고 있다. `auth-onboarding/README.md`의 "익명 인증 → 실계정 승격" 로드맵이 이 계층을 채우는 다음 작업이다. `application/auth`/`application/premium` 자체는 이 6계층에 속하지만, 그 포트가 실제 Firebase/SharedPreferences에 닿는 부분(위 4계층 참고)과는 구분해서 봐야 한다.

### 7계층 — Presentation

**위치**: `ui/`(`GoCoachApp.kt`, `GoBoard.kt`, `GameMenuSection.kt`, `GamePlaySection.kt`, `KaTrainUxPanels.kt`, `EngineResponsePanel.kt`, `ScoreGraphPanel.kt` 등), `presentation/`(`GameUiEvent.kt`, `GameScreenState.kt`, `GoCoachScreenStateAssembler.kt` 등)

**재편 여부**: 기존 7계층과 동일한 정의, 번호도 그대로 최상위(7번) 유지.

---

## 알려진 갭 (2026-07-30 기준)

- `GameSessionStateHolder`(5계층)는 여전히 `app-android`에 있다. `shared`로 옮기는 KMP 이식은 아직 안 함.
- `RemoteEngineSessionClient`(3계층) 구현체가 없다. `middleware/Remote*`와 `HttpRemotePositionAnalysisTransport`는 read-only position-analysis 단위까지만 원격 호출을 다룬다.
- 2계층의 로컬/원격 구현체가 대등하지 않다 (위 2계층 절 참고).
- 4계층(외부 연동)이 포트(α)만 있고 안정화 서비스 본체가 얇다.
- 6계층(세션/연속성)이 auth/premium 각자의 필요만 채우고 있고, 범용 개념이 없다.
- `LayeringContractTest.kt`는 아직 2026-06-27판 경계(옛 1~7계층 이름) 기준으로 작성돼 있다. 이번 재정의(2/3계층 재편, 4/6계층 신설, 5/7 번호 이동)를 반영하지 않았다.
- androidTest(Robolectric/계측) 커버리지가 기본 검증 경로에 없다. 컴파일+JVM 단위 테스트가 기본 검증이다.

## 고도화 로드맵

우선순위 순서가 아니라 계층별로 정리한 것이며, 착수 순서는 별도 착수 계획서(`refactoring/`에 `YYMMDD HHhMMm` 타임스탬프 관례로 추가)에서 정한다.

1. **2계층 — 로컬/원격 계약 대등화**: `HttpRemotePositionAnalysisTransport`가 `EngineCoreApi` 전체(또는 그에 준하는 상위 계약)를 구현하도록 확장. 실패/타임아웃/재시도가 로컬 구현체와 동일한 방식으로 상위에 보이는지 검증.
2. **3계층 — `RemoteEngineSessionClient` 도입**: 여러 원격/피어 후보 중 선택·신뢰도 판단을 흡수. DePIN 방향이라면 여기에 "피어 평판/정산 기록"의 자리가 생긴다.
3. **1계층 — 물리 실행 환경 추상화**: 지금은 `KataGoProcessRuntime`이 "이 기기에서 프로세스 실행"만 가정한다. 원격 서버/피어 기기라는 "다른 물리 위치"를 1계층 개념에 맞게 명시적으로 표현할 방법을 정의(예: 실행 위치를 나타내는 값 타입).
4. **4계층 — 외부 연동 서비스 본체 두껍게 하기**: `premium-mode/README.md` Step 3(실제 광고)/Step 4(실제 결제), `auth-onboarding/README.md` Step 2~3(Google/이메일 로그인)을 구현하며, 포트(α)뿐 아니라 3계층 수준의 재시도/캐시/신뢰도 판단을 갖춘 서비스 본체로 채운다.
5. **6계층 — 세션/연속성 공식화**: `auth-onboarding/README.md` Step 4(익명→실계정 승격, Firestore 동기화)를 이 계층의 정식 구현으로 진행. 기기 식별자 기반 다중 기기 정책도 이 단계에서 결정.
6. **`LayeringContractTest.kt` 갱신**: 위 항목들이 실제 코드로 옮겨질 때마다, 이번 재정의(2/3계층 경계, 4/6계층 신설, 5/7 번호 이동)를 반영해 계층 위반을 기계적으로 검증하도록 갱신. **코드가 실제로 옮겨지기 전까지는 테스트를 먼저 갱신하지 않는다** — 아직 물리적으로 분리되지 않은 것을 분리된 것처럼 강제하면 오탐만 늘어난다.
7. **문서 정리 후속 작업**: `docs/refactoring/`(23개 파일, 그중 8개는 2026-06-15 외부/내부 리뷰 클러스터로 중복이 크다)과 `docs/archive/` 전체를 다시 훑어 통폐합할지는 이번 범위 밖의 별도 작업이다. 필요해지면 이 로드맵에 항목을 추가한다.

## 관련 문서

- 레이어 원칙 자체(앱 비종속): [ARCHITECTURE.md](./ARCHITECTURE.md)
- 엔진 탐색 방식·레벨 정책·캐시 운영 상세: [ENGINE.md](./ENGINE.md)
- 프리미엄/결제 로드맵: `premium-mode/README.md`
- 인증/온보딩 로드맵: `auth-onboarding/README.md`
- 이 7계층 모델이 정착하기까지의 리팩토링 과정: `refactoring/` (날짜별 작업 로그)
