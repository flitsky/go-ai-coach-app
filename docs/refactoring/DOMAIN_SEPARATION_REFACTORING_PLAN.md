# 도메인 분리 리팩토링 계획

작성일: 2026-06-12

## 한줄 결론

엔진 기능은 `Engine Core API`로 빠짐없이 1:1 계층화하고, 앱/서버 공통 미들웨어는 그 코어 API를 조합해 대국/학습 기능을 제공한다. Game UX는 로컬 엔진인지 원격 서버인지 몰라야 하며, 항상 미들웨어 API만 호출한다.

## 사용자의 핵심 의도

현재 요구는 단순 파일 정리나 UI 코드 축소가 아니다. 앞으로 다음 확장을 전제로 도메인을 분리하자는 것이다.

- KataGo가 Android 로컬 process, JNI native, 원격 서버 중 어디서 실행되어도 UX 처리는 같아야 한다.
- 엔진의 원시 기능은 하나도 누락하지 않고 코어 API로 보존해야 한다.
- 미들웨어는 코어 API를 다양한 방식으로 조합해 대국, 분석, 학습, 계가, 캐릭터 성향, 원격 대국을 지원해야 한다.
- 나중에 엔진부만 고도화하거나, 미들웨어부만 고도화하거나, UX만 개편하는 작업이 서로 강하게 얽히면 안 된다.

## 목표 계층

```text
Presentation / Game UX
  -> App Service / Session Orchestration
  -> Game Domain
  -> Middleware Domain
  -> Core Rules Domain
  -> Engine Core API
  -> Engine Runtime / Transport
```

2026-06-14 업데이트:

앞으로 목표 구조는 최소 5계층이 아니라 7계층으로 명시한다. 이유는 엔진 로컬/원격 전환, 캐시 공급 체계, 흑/백 진영 확장, 앱 서비스 orchestration을 한 계층에 몰아넣으면 다시 `GoCoachApp.kt` 같은 거대한 조율자가 생기기 때문이다.

## 목표 7계층 구조

| 계층 | 이름 | 핵심 책임 | 현재 대표 위치 | 향후 목표 |
| ---: | --- | --- | --- | --- |
| 1 | Engine Runtime / Transport | KataGo binary/process/JNI/remote server 실행, asset/config/model 위치, OS별 transport | `engine-android`, `KataGoProcessEngineAdapter`, `scripts/seed-katago...` | Android local process, JNI, remote server transport를 구현체로 교체 가능하게 유지 |
| 2 | Engine Core API Domain | 엔진 원시 기능 1:1 노출. `initialize`, `configure`, `newGame`, `playMove`, `genMove`, `undo`, `clearSearchCache`, `analyze`, `estimateScore`, `deadStones`, `scoreFinal`, `stop` | `shared/EngineModels.kt`, `EngineCoreApi`, `EngineAdapter` | 엔진 기능은 빠짐없이 이 계층에 먼저 추가. UX 정책/캐시/AI 성향은 절대 넣지 않음 |
| 3 | Core Rules Domain | 바둑판, 착수, 합법수, 포획, 사석, 계가, fingerprint, engine DTO처럼 플랫폼 독립 순수 모델 | `shared/BoardModels.kt`, `BoardRules.kt`, `LegalMoveGenerator.kt`, `BoardScorer.kt`, `DeadStoneCleaner.kt`, `GameStateFingerprint.kt` | Android/Compose/엔진 process를 모르는 순수 KMP core로 유지 |
| 4 | Middleware / Cache Domain | Engine Core API를 조합해 position analysis, score sync, benchmark, cache read/write, 공식 cache origin, local/remote routing 제공 | `EngineSessionClient`, `EngineSession.kt`, `AnalysisSession.kt`, `PositionAnalysisCache.kt`, `PositionAnalysisCacheOptimization.kt`, `JsonPositionAnalysisCacheStore` | 캐싱 시스템의 중심. 로컬/원격 엔진과 공식 cache bundle을 같은 API로 감쌈 |
| 5 | Game Domain | 대국 규칙의 응용 계층. 심판, 턴 진행, 흑/백 진영, Player/AI/Remote seat, AI 캐릭터, 레벨링, 자동대국 정책 | `match/MatchReferee.kt`, `MatchPolicy.kt`, `PlayerSetup`, `SeatId`, `AiCharacterProfile`, `PlayLevel.kt` | 흑/백 진영 도메인을 명확히 유지하고, 원격 유저 seat까지 확장 가능하게 함 |
| 6 | App Service / Session Orchestration | 앱 use case 조율. 새 게임, 복원, 무르기, 사람 착수, AI 턴, Top Moves, 점수 요청, runtime log, debug report, 저장 port | `application/*`, `GameSessionControllerState`, `GameSessionCoreState`, `GameSessionSettingsState`, `ApplicationPorts.kt` | `GoCoachApp.kt`의 coroutine orchestration과 상태 전이를 점진 흡수 |
| 7 | Presentation / Game UX | 화면 상태, 이벤트, Compose 렌더링, 보드 drawing, 메뉴, 팝업. 도메인 판단 없이 상태 표시와 이벤트 전달 | `presentation/*`, `ui/*`, `GoCoachApp.kt`, `GoBoard.kt` | Engine Core API 직접 접근 금지. App Service API와 ViewState만 사용 |

### 의존 방향

허용 방향은 아래 한 방향이다.

```text
Presentation / Game UX
  -> App Service / Session Orchestration
  -> Game Domain
  -> Middleware / Cache Domain
  -> Core Rules Domain
  -> Engine Core API
  -> Engine Runtime / Transport
```

예외:

- `App Service`는 use case 조율을 위해 `Game Domain`, `Middleware`, `Core Rules`, port interface를 함께 볼 수 있다.
- `Middleware / Cache`는 `Engine Core API`와 `Core Rules`의 `GameState`/`AnalysisLimit`/fingerprint를 볼 수 있다.
- `Engine Runtime / Transport` 구현체는 `Engine Core API`를 구현하지만 상위 앱 정책을 알아서는 안 된다.

금지:

- `ui`/`presentation`에서 `EngineCoreApi`, `EngineAdapter`, `KataGoProcessEngineAdapter` 직접 import 금지.
- `shared` core rules에서 Android, Compose, persistence, Firebase, 파일 시스템 의존 금지.
- `Engine Core API`에 AI 레벨링, 후보 색상, 캐시 origin, prompt 정책 추가 금지.
- `Game Domain`에 Android 저장소 구현이나 UI text rendering 정책 추가 금지.
- `Middleware / Cache`에 Compose state나 사용자 메뉴 표시 조건 추가 금지.

## 현재 코드 위치 재분류

현재 패키지와 목표 계층의 대응은 다음과 같이 본다.

| 현재 위치 | 목표 계층 | 판단 |
| --- | --- | --- |
| `shared/EngineModels.kt`, `EngineSearchMode.kt` | 2 Engine Core API | 엔진 DTO/API 계약. 유지 |
| `shared/Board*`, `LegalMoveGenerator`, `DeadStone*`, `EndgameScoreSelector` | 3 Core Rules | 순수 바둑 룰. 유지 |
| `shared/PlayLevel.kt`, `EngineAnalysisPolicy.kt`, `SearchTimeSettings.kt` | 5 Game Domain + 4 Middleware 경계 | 현재 shared에 있어 플랫폼 독립성은 좋다. 다만 AI 캐릭터/레벨링은 Game Domain 의미가 강하고, analysis budget 변환은 Middleware와 닿는다. 당장 이동보다 의미 문서화 우선 |
| `engine-android/*` | 1 Engine Runtime + 2 구현체 | local process 구현체. 서버 구현체가 생기면 sibling 구현체로 추가 |
| `application/EngineSessionClient.kt`, `EngineSession.kt`, `AnalysisSession.kt` | 4 Middleware | UI가 보는 엔진 조합 API. 적절한 위치 |
| `application/PositionAnalysisCache*`, `persistence/JsonPositionAnalysisCacheStore.kt` | 4 Middleware / Cache | cache domain. `persistence` 구현체는 infra adapter로 유지하되 port는 application/middleware에 둠 |
| `match/MatchReferee.kt`, `MatchPolicy.kt` | 5 Game Domain | 심판/seat/AI 자동대국 정책의 중심. 앞으로 더 강화 |
| `application/GameSession*`, `StartGameApplication`, `UndoApplication`, `HumanMoveApplication`, `GameAutomationApplication`, `TopMovesApplication` | 6 App Service | use case와 상태 전이. 현재 적절하나 controller로 더 묶을 필요 있음 |
| `application/ApplicationPorts.kt`, `persistence/*Store.kt` | 6 App Service port + infrastructure adapter | port/interface와 Android 구현 분리 방향 유지 |
| `presentation/GameScreenState.kt`, `GameUiEvent.kt` | 7 Presentation | UI 계약. 유지 |
| `ui/*` | 7 Game UX | Compose rendering. `GoCoachApp.kt`의 orchestration은 6계층으로 더 내려야 함 |

## 캐싱 시스템 도메인의 위치

캐싱 시스템은 `Middleware / Cache Domain`에 둔다. 이유는 cache가 엔진 원시 기능도 아니고, 순수 바둑 룰도 아니며, 특정 UI 기능도 아니기 때문이다.

캐시 도메인이 가져야 할 책임:

- cache key 생성: `GameState.analysisFingerprint() + searchMode + AnalysisLimit`
- 품질 판정: `complete`, `partial`, `diagnostic`
- origin 우선순위: `bundled-trusted`, `operator-trusted`, `peer-shared`, `local-user`
- TTL/최대 개수/덮어쓰기 정책
- local engine result와 remote/operator cache bundle을 동일 API로 제공
- warning/diagnostic 로그로 `fill=SHORT` 같은 품질 이슈 노출

캐시 도메인이 갖지 말아야 할 책임:

- 팝업 표시 여부
- 보드 위 스팟 색상 rendering
- AI 캐릭터 성격
- KataGo process 실행 세부
- Firebase/Cloud Storage의 concrete SDK 호출

Firebase/원격 cache 업데이트는 별도 infrastructure adapter로 둔다. 미들웨어는 `TrustedPositionCacheProvider` 같은 port만 본다.

## Game Domain 내부 세부 도메인

Game Domain은 하나의 `match` 패키지로 끝내지 않고, 다음 개념을 명확히 분리한다.

| 세부 도메인 | 역할 | 현재 대응 | 목표 |
| --- | --- | --- | --- |
| Match Referee | 합법수, 착수 적용, pass/pass, board full, 종국 트리거 | `MatchReferee` | 사람/AI/원격 착수가 모두 같은 referee path 사용 |
| Black Seat | 흑 진영 controller, player/AI/remote 상태, 누적 시간 | `PlayerSetup.black`, `SeatId.Black` | seat state와 clock/state를 명시화 |
| White Seat | 백 진영 controller, player/AI/remote 상태, 누적 시간 | `PlayerSetup.white`, `SeatId.White` | Black과 대칭 API 유지 |
| AI Character | 엔진 종류, 레벨, 후보 선택 정책, randomness/personality | `AiCharacterProfile`, `PlayLevelSetting` | GTP fast / JSON leveling / server AI를 하나의 profile로 관리 |
| Match Settings | ruleset, board size, komi, time/search settings 연결 | `GameSettings`, `SearchTimeSettings` | 앱 설정과 대국 설정을 분리 |
| Remote Seat | 원격 유저 착수 송수신, 지연/재접속, 권한 | 아직 없음 | local/AI seat과 같은 referee path로 연결 |

## App Service Domain 내부 세부 도메인

App Service는 화면이 호출하는 use case 계층이다. 이 계층은 상위 UI보다 도메인과 미들웨어를 많이 알지만, Android concrete 구현에는 port를 통해 접근해야 한다.

| 세부 도메인 | 역할 | 현재 대응 | 다음 단계 |
| --- | --- | --- | --- |
| Session Controller | 전체 세션 상태와 effect 조율 | `GameSessionControllerState`, `GameSessionEffect` | Compose `remember` 상태를 점진 흡수 |
| Engine Operation Use Cases | start, restore sync, AI turn, score estimate, Top Moves | `EngineStartupApplication`, `GameAutomationApplication`, `TopMovesApplication`, `ScoreDisplayApplication` | coroutine runner와 reducer 분리 |
| Persistence Use Cases | 자동 저장, 이어하기, 설정 저장 | `SavedGamePersistence`, `SavedSessionPromptApplication`, `UserPreferencesApplication` | port 기반 테스트 강화 |
| Diagnostics Use Cases | runtime event, debug report, warning/critical log | `RuntimeEventApplication`, `DebugReportBuilder` | warning ring buffer port 추가 |
| Benchmark Use Cases | 기기 벤치마크, Search Time 추천 | `EngineDeviceBenchmarkApplication` | 개발자/운영자 진단 도구로 분리 가능 |

## 다음 리팩토링 우선순위

1. `warning_events.jsonl` port와 domain model 추가
   - 계층: App Service diagnostics + Middleware cache diagnostics
   - 목적: visits 미충족, 엔진 timeout, 계가 불일치 같은 warning/critical을 Copy Log와 별도 수집

2. `TrustedPositionCacheProvider` port 설계
   - 계층: Middleware / Cache Domain
   - 목적: bundled/operator/peer cache를 로컬 cache와 같은 조회 API로 통합

3. `GameSessionController`를 state holder에서 effect runner로 승격
   - 계층: App Service
   - 목적: `GoCoachApp.kt`의 coroutine orchestration 축소

4. `SeatState` 또는 `MatchSeatState` 도입
   - 계층: Game Domain
   - 목적: 흑/백 진영의 controller, 누적 시간, AI profile, remote 상태를 대칭 구조로 관리

5. `EngineSessionClient` 구현체 분리 강화
   - 계층: Middleware
   - 목적: `LocalEngineSessionClient`, future `RemoteEngineSessionClient`, future `ServerAnalysisClient`가 같은 contract를 구현

## 계층별 책임

### Engine Core API

엔진이 제공하는 모든 기능을 원시 API로 보존한다.

현재 코어 기능 목록:

| 기능 | 의미 |
| --- | --- |
| `initialize(profile)` | 엔진 초기화 |
| `configure(profile)` | 난이도/분석 제한 등 runtime 설정 |
| `newGame(boardSize, ruleset)` | 새 엔진 보드 시작 |
| `playMove(move)` | 엔진 상태에 착수 반영 |
| `genMove(player)` | 엔진 기본 응수 생성 |
| `undoMove()` | 엔진 상태 되돌림 |
| `clearSearchCache()` | 엔진 내부 search tree/cache 격리 |
| `analyze(limit)` | 후보수/형세 분석 |
| `estimateScore(limit)` | 스코어/승률/ownership 추정 |
| `deadStones()` | 사석 판정 |
| `scoreFinal()` | 최종 점수 |
| `stop()` | 엔진 종료 |

원칙:

- 코어 API는 대국 UX 정책을 모른다.
- 코어 API는 AI 레벨링, 후보수 색상, 캐릭터 성향, 저장/복원 정책을 모른다.
- 코어 API는 로컬/원격 transport 차이를 숨기지만, 원시 기능의 의미는 숨기지 않는다.
- 새 엔진 기능이 추가되면 먼저 코어 API에 1:1 반영하고, 그 다음 미들웨어 조합 기능을 만든다.

### Middleware Domain

미들웨어는 엔진 코어 기능과 core rules model을 조합해 앱/서버 공통 분석 API와 cache API를 만든다. AI 캐릭터, 심판, 흑/백 진영은 더 이상 Middleware 책임으로 보지 않고 Game Domain으로 분리한다.

도메인 분리 목표:

| 도메인 | 역할 | 현재 대응 |
| --- | --- | --- |
| Engine Core Wrapping | 명시적 국면 분석, sync 후 점수 추정, 복원 후 sync, benchmark | `EngineSessionClient`, `EngineSession.kt`, `EngineDeviceBenchmarkApplication.kt` |
| Position Analysis | 특정 `GameState`를 기준으로 JSON/GTP 분석을 요청하고 결과를 앱 모델로 변환 | `AnalysisSession.kt`, `TopMovesApplication.kt` 일부 |
| Score / Endgame Middleware | engine score estimate, final score, dead stone cleanup을 조합 | `ScoreDisplayApplication.kt`, `EndgameResolver.kt` |
| Position Cache | JSON position analysis 결과 저장/조회, 품질/origin/TTL 정책 | `PositionAnalysisCache.kt`, `JsonPositionAnalysisCacheStore` |
| Trusted Cache Provider | bundled/operator/peer cache bundle을 동일 조회 API로 제공 | 아직 없음 |
| Engine Benchmark | B16/B32/B64 latency/root visits 측정 | `EngineDeviceBenchmarkApplication.kt` |

### Game Domain

Game Domain은 바둑 대국의 의미를 다룬다. 여기에는 엔진 호출 방법이 아니라 “누가 어떤 진영으로 어떤 규칙 아래 착수하는가”가 들어간다.

| 도메인 | 역할 | 현재 대응 |
| --- | --- | --- |
| Match Referee | 합법수, 착수 적용, pass/pass 종료, 사석/계가 트리거 | `MatchReferee`, `GameState`, `HumanMoveApplication` 일부 |
| Black Seat | 흑 진영 controller, AI/사람/원격 유저 설정 | `PlayerSetup.black`, `SeatId.Black` |
| White Seat | 백 진영 controller, AI/사람/원격 유저 설정 | `PlayerSetup.white`, `SeatId.White` |
| AI Character | AI 성향, 엔진 종류, 플레이 레벨, 후보 선택 정책 | `AiCharacterProfile`, `PlayLevelSetting`, `MoveSelectionPolicy` |
| Game Settings | ruleset, Search Time, Top Moves, auto-play delay 같은 대국 설정 | `GameSettings`, `SearchTimeSettings`, `AutoPlayDelaySetting` |
| Remote User Match | 원격 유저 착수 수신/송신, 동기화, 지연/재접속 | 아직 없음 |

### App Service Domain

App Service는 UI가 호출하는 use case 계층이다. Game Domain과 Middleware를 조합하지만, Android 저장소와 엔진 구현체에는 port/interface로만 접근한다.

| 도메인 | 역할 | 현재 대응 |
| --- | --- | --- |
| Session Controller | 세션 상태, engine busy, 자동 AI pending, prompt priority, effect 조율 | `GameSessionControllerState`, `GameSessionEffect` |
| Move Use Cases | 사람 착수, AI 착수, 무르기, 새 게임, 복원 | `HumanMoveApplication`, `GameAutomationApplication`, `UndoApplication`, `StartGameApplication` |
| Analysis Use Cases | Top Moves 표시, 착수 리뷰, score graph, score estimate | `TopMovesApplication`, `ScoreDisplayApplication` |
| Persistence Use Cases | 저장/복원, 사용자 설정, benchmark profile 저장 | `SavedGamePersistence`, `UserPreferencesApplication`, `ApplicationPorts.kt` |
| Diagnostics Use Cases | runtime log, debug report, warning/critical log | `RuntimeEventApplication`, `DebugReportBuilder` |

### Game UX

Game UX는 다음을 하지 않는다.

- `Engine Core API` 직접 호출
- 엔진 내부 sync 순서 결정
- AI 후보 선택 정책 결정
- 종국 점수 선택 정책 결정
- 엔진 capability 문자열 판별
- cache 품질/origin/TTL 판단

Game UX는 다음만 담당한다.

- 화면 상태 렌더링
- 사용자 이벤트 수집
- App Service API 또는 controller event 호출
- App Service가 만든 `GameScreenState`/presentation state 표시

## 핵심 설계 결정

1. `EngineAdapter`는 앞으로 호환 이름으로만 남기고, 의미상 `EngineCoreApi`를 정식 코어 API로 둔다.
2. `EngineSessionClient`는 미들웨어 API다. UI가 직접 보는 엔진 경계는 이 계층이다.
3. `analyzePosition(state, limit)`처럼 미들웨어 API는 명시적 `GameState`를 받는다. local 구현은 sync 후 analyze하고, remote 구현은 state를 payload로 보낸다.
4. 미들웨어 API는 조합 기능을 제공하지만, 원시 엔진 기능이 필요하면 `EngineCoreApi`를 통해 빠짐없이 접근 가능해야 한다.
5. 흑/백 진영은 단순 enum이 아니라 seat 도메인으로 취급한다. 지금은 `PlayerSetup.black/white`를 보존하되, `SeatId`와 seat helper를 도입해 점진 이전한다.
6. AI 캐릭터는 단순 play level이 아니라 engine choice, level, selection policy, 향후 personality/randomness를 묶는 도메인으로 확장한다.

## 리팩토링 절차

### Phase 1. Engine Core API 명시화

목표:

- 엔진 원시 기능을 `EngineCoreApi`에 1:1로 선언한다.
- 기존 `EngineAdapter`는 `EngineCoreApi`를 상속하는 호환 alias로 둔다.
- application/match 계층의 저수준 호출은 가능한 `EngineCoreApi`로 전환한다.

검증:

- 기존 fake `EngineAdapter` 테스트가 계속 통과해야 한다.
- `make test` 통과.

### Phase 2. Middleware API 정리

목표:

- `EngineSessionClient`를 미들웨어 API로 문서화한다.
- Top Moves, score sync, AI turn display helper처럼 조합 기능은 이 계층에서 제공한다.
- 원격 서버 구현이 들어와도 `Game UX`는 같은 API를 호출하도록 유지한다.

검증:

- fake `EngineSessionClient` 테스트 유지/보강.
- UI에서 `EngineCoreApi` 직접 import가 없어야 한다.

### Phase 3. Seat/AI Character/Game Settings 도메인 도입

목표:

- `SeatId.Black`, `SeatId.White`를 도입한다.
- `PlayerSetup`이 흑/백 seat helper를 통해 동작하도록 점진 변경한다.
- `AiCharacterProfile`을 도입해 AI engine, play level, selection policy를 묶는다.
- `GameSettings` 또는 equivalent snapshot으로 ruleset/search time/auto delay/top moves 설정을 묶는다.

검증:

- Player Setup 관련 테스트 유지.
- 저장/복원 codec 테스트 유지.

### Phase 4. Match Referee 도메인 분리

목표:

- 착수 가능 여부, 착수 적용, pass/pass 종료, board full, 사석/계가 트리거를 `MatchReferee` 후보로 모은다.
- 사람 착수/AI 착수/원격 유저 착수가 같은 referee path를 타도록 준비한다.

검증:

- 사석 제거/종국 회귀 테스트 유지.
- HumanMoveApplication/AutoAiTurn 테스트 유지.

### Phase 5. GameSessionController 도입

목표:

- `GoCoachApp.kt`의 `isEngineBusy`, pending flag, coroutine launch, runtime log, endgame flow를 controller/state holder로 이동한다.
- Compose는 controller state를 구독하고 이벤트만 전달한다.

검증:

- `make test`
- 실제 폰 설치 후 기본 대국, Top Moves, AI vs AI, undo, restore, pass/pass 계가 통합 테스트.

## 현재 착수 순서

2026-06-13 하향식이 아니라 하위 계층부터 다시 정리하는 배치에서는 아래 순서로 진행한다.

1. `[완료]` Engine Runtime / Transport 경계: KataGo process config, 파일 검증, GTP/JSON analysis command 생성 책임을 adapter 본문에서 분리
2. `[완료]` Engine Core API Domain: 원시 API 계약과 transport 구현체의 의존 방향을 테스트/문서로 보강
3. `[완료]` Core Rules Domain: 좌표/합법수/리플레이 helper를 순수 core 쪽으로 더 모아 adapter 중복을 줄임
4. `[완료]` Middleware / Cache Domain: position analysis cache provider/origin 포트를 더 명확히 분리
5. `[대기]` Game Domain: seat/referee/AI character가 engine 호출 세부를 모르도록 경계 강화
6. `[대기]` App Service / Presentation: controller snapshot과 UI 연결부를 더 얇게 만들고 계층 테스트 보강

이번 리팩토링 배치에서 바로 진행할 안전한 순서:

1. `[완료]` `EngineCoreApi` 도입
2. `[완료]` application/match의 코어 의존 타입을 `EngineCoreApi`로 점진 전환
3. `[완료]` `SeatId`, `SeatAssignment`, `AiCharacterProfile` 도입
4. `[완료]` PlayerSetup helper를 seat 도메인 기반으로 정리
5. `[완료]` MatchReferee 후보를 추가하고 사람 착수 로컬 처리부터 적용
6. `[완료]` 테스트/문서/히스토리 갱신 후 커밋/푸시

### 진행 로그

2026-06-13:

- 1계층 `Engine Runtime / Transport` 정리를 시작했다. `KataGoProcessRuntime.kt`를 추가해 `KataGoProcessConfig`, GTP command 생성, JSON analysis command 생성, 실행 파일/model/config 검증 책임을 `KataGoProcessEngineAdapter` 본문에서 분리했다.
- `KataGoProcessEngineAdapter`는 이제 엔진 프로토콜 orchestration과 응답 parsing 흐름에 집중하고, process command 세부는 runtime helper를 호출한다. 이 구조는 향후 JNI/remote server transport를 추가할 때 실행 경계만 대체하기 쉽게 만든다.
- `KataGoProcessRuntimeTest`를 추가해 GTP command가 profile visits와 startup override를 반영하는지, JSON analysis command가 runtime-safe override만 통과시키는지 검증했다.
- 검증: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :engine-android:testDebugUnitTest` 통과.
- 2계층 `Engine Core API Domain` 정리를 이어서 진행했다. `KataGoProcessEngineAdapter`와 `StubEngineAdapter`가 호환 alias인 `EngineAdapter` 대신 원시 계약인 `EngineCoreApi`를 직접 구현하도록 바꿨다.
- `EngineAdapter`는 외부/과거 코드 호환 이름으로 남기되, 새 concrete local process/JNI/remote 구현체는 `EngineCoreApi`를 직접 구현해야 한다는 KDoc을 명시했다.
- `LayeringContractTest`를 보강해 production `application`/`match` 계층이 `EngineAdapter` compatibility alias나 `engine.android` runtime 구현체를 직접 import하지 못하도록 했다.
- 검증: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest` 통과.
- 3계층 `Core Rules Domain` 정리를 진행했다. `BoardSize.allCoordinates()`를 shared core API로 추가해 좌표 순회 순서를 한 곳에서 관리하게 했다.
- `LegalMoveGenerator`는 새 좌표 helper를 사용하도록 바뀌었고, `KataGoProcessEngineAdapter`의 JSON/GTP fallback 합법수 계산도 `LegalMoveGenerator.legalPlayCoordinates()`를 호출하게 했다. 이로써 transport 구현체 안의 직접 합법수 계산 중복을 줄였다.
- `StubEngineAdapter`의 fallback 좌표 순회도 `BoardSize.allCoordinates()`로 연결했다.
- 검증: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :shared:testDebugUnitTest :engine-android:testDebugUnitTest` 통과.
- 4계층 `Middleware / Cache Domain` 정리를 진행했다. `TrustedPositionAnalysisCacheProvider` port를 추가해 bundled/operator/peer cache 공급자를 local user cache store와 분리했다.
- `AdapterEngineSessionClient`는 JSON position analysis 요청에서 local store를 먼저 확인하고, 없으면 trusted provider 목록에서 best entry를 선택한다. provider가 없을 때는 기존 local cache 동작과 stats text가 그대로 유지된다.
- `bestPositionAnalysisCacheEntry()` helper를 추가해 root visits, origin trust rank, 생성 시각 순서의 replacement 정책을 provider 선택에도 재사용했다.
- `EngineSessionTest`에 operator-trusted provider hit 시 엔진 sync/analyze를 호출하지 않고 cached result를 반환하는 테스트를 추가했다.
- 검증: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest` 통과.

2026-06-12:

- `shared`에 `EngineCoreApi`를 추가하고, 기존 `EngineAdapter`는 이를 상속하는 호환 이름으로 전환했다.
- `EngineSessionClient`, `EngineSession` application helper, `EndgameResolver`, `EngineDeviceBenchmarkApplication`, `MatchPolicy`의 원시 엔진 의존 타입을 `EngineCoreApi`로 낮췄다.
- 실제 process/stub 구현체와 bootstrap wiring은 기존 `EngineAdapter` 이름을 유지해 대규모 rename 없이 안전하게 이전할 수 있게 했다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest` 통과.
- `SeatId.Black/White`, `SeatAssignment`, `AiCharacterProfile`을 추가했다. 저장 포맷의 `PlayerSetup.black/white`는 유지하되, 내부 판단은 seat helper를 통해 접근할 수 있게 했다.
- `PlayerSetup.matchMode`, `humanSeatCount`, `isAutoPlay`, `summary`, `boardInputEnabled`, `turnStatus`, 자동 AI/Top Moves trigger, runtime play level 선택을 seat 도메인 기반으로 정리했다.
- 검증: `MatchPolicyTest.playerSetupExposesSeatAssignmentsAndAiCharacters` 추가, `:app-android:testDebugUnitTest` 통과.
- `MatchReferee`를 추가해 착수 적용, pass/pass 또는 board full 종국 판정, pass/pass 로컬 최종 점수 생성을 심판 도메인 경계로 모았다.
- 사람 착수 로컬 처리, AI 착수 적용, 자동 AI 종국 표시 계획, 사람 착수 후 엔진 sync 종국 판단이 `MatchReferee` 경계를 사용하게 했다.
- 검증: `MatchRefereeTest` 추가, `:app-android:testDebugUnitTest` 통과.
- `GameSettings`를 추가해 ruleset, Top Moves 기본 상태, AI 자동대국 딜레이, Search Time 설정을 application 계층의 설정 도메인 묶음으로 다루게 했다.
- 기존 UI 호환을 위해 `InitialUserPreferencesPlan.topMovesEnabled`와 `autoPlayDelaySetting`은 computed property로 유지하고, 내부에는 `settings`를 추가했다.
- 검증: `UserPreferencesApplicationTest`에 `GameSettings` 복원 검증 추가, `:app-android:testDebugUnitTest` 통과.
- `LayeringContractTest`를 추가해 `ui`와 `presentation` 계층이 `EngineAdapter`, `EngineCoreApi`, `engine.android` 구현체를 직접 import하지 못하도록 회귀 방지 테스트를 걸었다.
- 검증: `:app-android:testDebugUnitTest` 통과.
- `EngineBootstrap.adapter`를 `EngineBootstrap.coreApi`로 바꿔 app bootstrap wiring에서도 원시 엔진 계약 이름이 드러나게 했다. concrete 구현체는 기존 `EngineAdapter` 호환 타입을 그대로 사용한다.
- 검증: `:app-android:testDebugUnitTest` 통과.
- 통합 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test` 통과.

## 주의할 점

- 이름만 바꾸는 대규모 rename은 뒤로 미룬다. 현재는 호환 타입을 두고 의미를 명확히 하는 방식이 안전하다.
- 원격 서버 엔진을 아직 구현하지 않는다. 단, 구현 가능한 경계를 코드와 테스트로 만든다.
- 도메인을 너무 잘게 쪼개어 실제 흐름 추적이 어려워지면 안 된다. 먼저 seat, AI character, settings, referee처럼 변경 이유가 명확한 단위만 분리한다.
- `GoCoachApp.kt`의 coroutine orchestration은 한 번에 옮기지 않는다. 자동 AI, 종국, restore, benchmark는 timing regression 위험이 높다.
