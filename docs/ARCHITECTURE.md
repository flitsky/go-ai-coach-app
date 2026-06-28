# Architecture

작성일: 2026-06-17  
갱신: 2026-06-27 — 코드 기준선을 다시 확인했다. `GoCoachApp.kt`는 791줄, `app-android/application`은 17개 하위 패키지/107개 production Kotlin 파일, `shared/commonMain`은 21개 production Kotlin 파일, `engine-android`는 10개 production Kotlin 파일이다. `make test`는 2026-06-27 기준 통과했다.

성격: `go-ai-coach`의 7계층 구조를 현재 코드베이스 기준으로 유지하는 canonical 문서. 과거 `archive/2026-06-17-architecture-docs-rewrite/ARCHITECTURE_LAYERS_ANALYSIS.md` 초안과 `refactoring/ARCHITECTURE_LAYERS_REVIEW_2026-06-14.md` 검토본은 이 문서로 대체한다 (7계층 방향 자체는 그대로 채택, 파일 경로와 구현 현황만 전면 갱신).

## 큰 그림

```
Presentation / Game UX
        ↓ (GameUiEvent)
App Service / Session Orchestration
        ↓ (lambda-injected controllers)
Game Domain  ←→  Middleware / Cache Domain
        ↓                  ↓
Core Rules Domain    Engine Core API Domain
                              ↓
                    Engine Runtime / Transport
```

화살표는 "누가 누구를 알아도 되는가"다. 상위 계층은 하위 계층의 인터페이스만 알고, 하위 계층은 상위 계층을 전혀 모른다. 이 경계를 지키는 이유는 엔진 구현체(local process → JNI → remote server)나 UI 프레임워크가 바뀌어도 게임 정책과 학습 UX를 다시 쓰지 않기 위함이다.

## 1계층: Engine Runtime / Transport

**책임**: OS 프로세스로 KataGo 바이너리를 실행하고 GTP/JSON stdin·stdout 스트림을 유지한다.

**위치**: `engine-android/src/main/java/com/worksoc/goaicoach/engine/android/`

| 파일 | 역할 |
| --- | --- |
| `KataGoProcessRuntime.kt` | 실행 파일/모델 검증, CLI 인수 빌드, 프로세스 시작/종료 |
| `KataGoProcessEngineAdapter.kt` | `EngineCoreApi` 구현체. GTP와 JSON 두 경로를 모두 이 어댑터 뒤에서 조율 |
| `KataGoGtpAnalysisClient.kt`, `KataGoProtocolCommands.kt` | GTP stateful fast path (`kata-search_analyze` 등) |
| `KataGoJsonPositionAnalysisClient.kt`, `KataGoJsonAnalysisQueryFactory.kt`, `KataGoJsonAnalysisParser.kt` | JSON position analysis path (요청별 `moves`/`maxVisits`/`overrideSettings` query) |
| `KataGoAnalysisParser.kt`, `KataGoAnalysisContext.kt` | 두 경로 공통 응답 파싱/컨텍스트 |
| `StubEngineAdapter.kt` | 엔진 없이 UI/도메인 로직만 검증하는 stub 구현체 |

이 계층은 수순 히스토리를 직접 추적하지 않는다. 상위 계층이 `GameStateReplayer`(3계층)로 만든 전체 수순을 매번 내려보내면, 이 계층은 그것을 그대로 재생(sync)만 한다.

## 2계층: Engine Core API Domain

**책임**: 엔진이 플랫폼에 상관없이 제공하는 원시 기능을 1:1 계약으로 정의한다. 제품 정책(레벨, 색상, UI)을 전혀 모른다.

**위치**: `shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/`

- `EngineModels.kt` — `EngineCoreApi` 인터페이스(`initialize`, `configure`, `playMove`, `analyze`, `estimateScore`, `deadStones`, `scoreFinal`, `clearSearchCache` 등), `AnalysisLimit`, `EngineProfile`, `DifficultyProfile`, `CandidateMove`, `AnalysisResult` 등 순수 데이터 모델
- `EngineSearchMode.kt` — `GtpStatefulFast` / `JsonPositionAnalysis` 두 탐색 orchestration 모드 정의
- `EngineAnalysisPolicy.kt` — 목적(`TurnAnalysisPurpose`)별 `AnalysisLimit` 산출, 레벨 그룹→search mode 매핑(`aiMoveSearchMode()`)
- `PlayLevel.kt` — 난이도 그룹/단계 기본값

자세한 탐색 방식 비교와 레벨별 정책은 [ENGINE.md](./ENGINE.md)를 따른다.

## 3계층: Core Rules Domain

**책임**: 보드 크기, 합법수, 자충/패, 사석 정리, 계가 공식처럼 플랫폼·UI에 의존하지 않는 순수 바둑 규칙.

**위치**: `shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/`

| 파일 | 역할 |
| --- | --- |
| `BoardModels.kt` | `Board`, `StoneColor`, `Move`, `Ruleset` 등 핵심 데이터 클래스 |
| `BoardRules.kt`, `LegalMoveGenerator.kt` | 합법수 체크, 패(Ko) 검증 |
| `BoardScorer.kt`, `BoardAreaScorer.kt`, `BoardTerritoryScorer.kt` | Area(중국식)/Territory(한국·일본식) 계가 |
| `EndgameScoreSelector.kt` | 종국 시 최종 점수 판정 |
| `GameStateReplayer.kt` | 저장된 수순을 안전하게 재생해 보드 상태를 복원 — undo, 이어하기, 1계층 재동기화가 모두 이 경로를 공유 |
| `ScoreTimeline.kt` | 수순별 점수 추이 |

순수 Kotlin Multiplatform(`commonMain`)이라 Android/iOS/JVM 어디서도 동일하게 동작한다.

## 4계층: Middleware / Cache Domain

**책임**: 2계층의 원시 API를 유스케이스로 조합해 "분석 세션"을 제공하고, 로컬/원격 캐시 라우팅·신뢰도 정책을 조율한다.

**위치**: `app-android/src/main/java/com/worksoc/goaicoach/application/engine/` (2026-06-27 기준 20개 파일), `application/analysis/`, `middleware/`

| 파일/패키지 | 역할 |
| --- | --- |
| `application/engine/EngineSessionClient.kt` | UI/App Service가 바라보는 고수준 엔진 게이트웨이 인터페이스. `analyzePosition(state, limit, searchMode)`처럼 명시적 `GameState`를 받아 local/remote 차이를 숨김 |
| `application/engine/LocalEngineSessionClient.kt` + `LocalAiMoveEngineGateway.kt`, `LocalEndgameJudgeGateway.kt`, `LocalEngineCoreSessionDelegate.kt`, `LocalEngineBenchmarkDelegate.kt`, `LocalPositionAnalysisCacheCoordinator.kt` | local KataGo process 기반 구현체. 역할별로 작은 delegate로 분리되어 있음 |
| `application/engine/operation/` | `EngineOperationLifecycleController` 등 — 동시 엔진 호출 추적, 늦게 도착한 결과 폐기(stale guard), busy 상태 관리 |
| `application/analysis/PositionAnalysisCache.kt`, `PositionAnalysisCacheOptimization*.kt` | JSON position analysis 결과를 품질(`complete`/`partial`/`diagnostic`)과 origin(`local-user`/`bundled-trusted`/`operator-trusted`/`peer-shared`)별로 저장하는 디스크 캐시 |
| `middleware/PositionAnalysisCacheResolver.kt` | 신뢰도 등급에 따라 캐시 hit을 평가/서빙 |
| `middleware/PositionAnalysisGateway.kt`, `RemotePositionAnalysisGateway.kt`, `HttpRemotePositionAnalysisTransport.kt` | 원격 분석 서버로 전환할 때를 대비한 게이트웨이/transport 추상화. read-only position-analysis HTTP transport spike는 존재하며 기본값은 off다. **아직 `RemoteEngineSessionClient` 구현체는 없음** — 현재는 position-analysis 단위 원격 호출까지만 분리되어 있다 |

캐시 정책, 품질 등급, origin 계층의 상세 운영 규칙은 [ENGINE.md](./ENGINE.md) → `ENGINE_API_CALL_POLICY.md`를 따른다.

## 5계층: Game Domain

**책임**: 대국 자체의 흐름, 참여 주체(흑/백 seat), 턴 권한, AI 캐릭터 레벨링 같은 경기 정책.

**위치**: `app-android/src/main/java/com/worksoc/goaicoach/match/`

| 파일 | 역할 |
| --- | --- |
| `MatchReferee.kt` | 사람/AI/2인용 어떤 주체든 단일 통로로 착수를 수락하는 심판 |
| `AiMoveSelectionPolicy.kt` | 레벨 단계별로 엔진 후보(`order`) 중 어디를 선택할지 결정 |
| `MatchPolicy.kt` | 자동대국 딜레이 등 경기 운영 정책 |

KataGo 프로세스 명령을 직접 호출하지 않고, 4계층 `EngineSessionClient` 계약만 통해 분석을 받는다.

## 6계층: App Service / Session Orchestration

**책임**: Presentation이 호출하는 최상위 유스케이스. 새 게임, 무르기, 복원, 자동대국, 벤치마크, 저장/로깅 흐름을 조율하고 outbound 포트(저장소 등)를 연결한다.

**위치**: `app-android/src/main/java/com/worksoc/goaicoach/application/` 아래 17개 도메인 서브패키지 (`session`, `autoai`, `undo`, `humanmove`, `startgame`, `savedgame`, `topmoves`, `debugreport`, `score`, `endgame`, `diagnostic`, `runtime`, `preferences`, `prompt`, `movereview`, `analysis`, `engine`)

이 계층은 2026-06 리팩토링(R1~R12)을 거치며 "거대한 단일 컨트롤러"에서 "기능별 작은 컨트롤러 + 단일 상태 보관소" 구조로 전환되었다.

- **`application/session/GameSessionStateHolder.kt`**: 대국 세션 상태의 단일 source of truth(SSOT). `MutableStateFlow` 기반이며 Compose/Android 의존성이 없는 순수 Kotlin이라 향후 `shared` 모듈로 옮길 수 있다(현재는 `app-android`에 위치 — 아직 cross-platform 이동 전).
- **`application/session/GameSessionController.kt`**: `GameSessionControllerState`(core/settings/benchmark/savedSession/autoAiTurn/... 합성 상태)와 각 기능의 plan 타입 조합을 정의. 더 이상 단일 effect-runner가 아니라 상태 타입 정의 + 서브 패키지 plan 재노출 역할.
- **기능별 패키지 패턴**: `autoai`, `undo`, `humanmove`, `startgame`, `savedgame`, `topmoves`, `debugreport` 등은 각각 동일한 3-파일 관용구를 따른다.
  - `XxxApplication.kt` — 순수 함수: plan 빌드, 상태 적용. 부작용 없음, 단위 테스트 용이.
  - `XxxController.kt` — `internal class`. 람다로 의존성을 주입받고(`currentXxx: () -> T`, `applyXxx: (T) -> Unit`), Compose 상태를 직접 들지 않음.
  - 필요 시 `XxxModels.kt` — plan/result 데이터 클래스.
- **`ui/GoCoachApp.kt`**가 이 모든 컨트롤러를 생성하고 람다로 서로 연결하는 합성 루트(composition root) 역할을 한다. 2026-06-27 기준 791줄(과거 1838줄 이상)까지 줄었고, `app-android/src/test/.../LayeringContractTest.kt`가 workflow ownership과 계층 의존 금지를 회귀 방지한다.

## 7계층: Presentation / Game UX

**책임**: Compose UI 렌더링, 사용자 입력/메뉴 이벤트 처리, 바둑판 캔버스 드로잉. 도메인 로직이나 비동기 엔진 호출 순서를 모른다.

**위치**: `app-android/src/main/java/com/worksoc/goaicoach/ui/`, `.../presentation/`

| 파일 | 역할 |
| --- | --- |
| `ui/GoCoachApp.kt` | 메인 화면 합성 루트. 6계층 컨트롤러를 모아 `GameUiEvent` 디스패치로 연결 |
| `ui/GoBoard.kt` | 터치 좌표 ↔ 바둑 좌표 매핑, 캔버스 드로잉(후보수 원, 사석 표시 등) |
| `ui/GameMenuSection.kt`, `GameMenuActionsPanel.kt`, `GamePlaySection.kt`, `PlayerSetupPanel.kt`, `KaTrainUxPanels.kt`, `EngineResponsePanel.kt`, `ScoreGraphPanel.kt`, `GoCoachContent.kt` | 화면 섹션별 Compose 컴포저블 |
| `ui/HolderBackedState.kt` | `GameSessionStateHolder`(6계층)를 Compose `var`로 안전하게 연결하는 위임 프로퍼티 |
| `ui/GoCoachSessionFactory.kt` | 앱 시작 시 초기 세션 상태를 만드는 팩토리 |
| `presentation/GameUiEvent.kt` | UI가 발행하는 이벤트 sealed 타입 + 디스패치 |
| `presentation/GameScreenState.kt`, `GoCoachScreenStateAssembler.kt`, `PlayerSetupUiState.kt` | 도메인 상태 → 화면 표시 전용 DTO 변환 |
| `presentation/KaTrainUxOptionsMapper.kt` | 사용자 설정 ↔ UI 토글 옵션 매핑. `Eval` 버튼처럼 "토글 on/off"와 "켜질 때의 부가 효과"를 분리해 나중에 기능을 쪼갤 수 있게 하는 작은 정책 함수도 여기 둔다(예: `applyEvalActivation`) |

## 알려진 갭 / 다음 단계

- `GameSessionStateHolder`는 여전히 `app-android`에 있다. KMP `shared`로 옮기는 작업은 아직 안 함.
- `RemoteEngineSessionClient`는 없다. `middleware/Remote*`와 `HttpRemotePositionAnalysisTransport`는 read-only position-analysis 단위까지만 원격 호출을 다룬다. 4계층 전체를 원격으로 라우팅하는 구현체는 후속 작업이다.
- AI vs AI 자동대국의 search tree 격리는 `EngineSearchMode.GtpStatefulFast`일 때만 `clearSearchCache()`로 처리한다. `JsonPositionAnalysis` 레벨(초급 이상)은 position-scoped라 격리가 불필요하다.
- androidTest(Robolectric/계측) 커버리지가 아직 없다. 현재는 컴파일+JVM 단위 테스트로만 검증된다.
- 다음 리팩토링 기준선과 작은 작업 단위는 [refactoring/ARCHITECTURE_IMPLEMENTATION_REVIEW_2026-06-27.md](./refactoring/ARCHITECTURE_IMPLEMENTATION_REVIEW_2026-06-27.md)를 따른다.

## 더 깊은 문서

- 엔진 탐색 방식·레벨 정책·캐시 운영 상세: [ENGINE.md](./ENGINE.md)
- 이 7계층으로 정착하기까지의 리팩토링 과정: `refactoring/` (날짜별 작업 로그)
- 더 먼 미래의 구조(원격 엔진, 공식 캐시, 멀티플랫폼 UI) — 2026-06-14 시점 초기 비전 문서: `archive/2026-06-17-early-decisions/FUTURE_ARCHITECTURE_VISION.md`
