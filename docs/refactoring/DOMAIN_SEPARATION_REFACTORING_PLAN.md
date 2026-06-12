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
Game UX
  - Compose 화면
  - 사용자 입력/표시
  - 미들웨어 API 호출만 허용

Middleware Domain
  - Engine Core 기능 래핑 도메인
  - AI 캐릭터/성향 도메인
  - 게임 설정 도메인
  - 대국 진행/심판 도메인
  - 흑 진영 도메인
  - 백 진영 도메인
  - 원격 유저 대국 확장 도메인

Engine Core API
  - 엔진이 제공하는 원시 기능 1:1
  - initialize/configure/newGame/playMove/genMove/undo
  - clearSearchCache/analyze/estimateScore/deadStones/scoreFinal/stop
  - 구현체: Stub, LocalProcess, JNI, RemoteServer

Engine Runtime
  - KataGo GTP process
  - JNI native engine
  - remote server transport
```

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

미들웨어는 엔진 코어 기능을 조합해 앱이 쓰기 좋은 API를 만든다.

초기 도메인 분리 목표:

| 도메인 | 역할 | 현재 대응 |
| --- | --- | --- |
| Engine Core Wrapping | 명시적 국면 분석, sync 후 점수 추정, 복원 후 sync, benchmark | `EngineSessionClient`, `EngineSession.kt`, `EngineDeviceBenchmarkApplication.kt` |
| AI Character | AI 성향, 엔진 종류, 플레이 레벨, 후보 선택 정책 | `PlayLevelSetting`, `MoveSelectionPolicy`, 향후 `AiCharacterProfile` |
| Game Settings | ruleset, Search Time, Top Moves, UX 옵션, auto-play delay | `UserPreferencesSnapshot`, `SearchTimeSettings`, `AutoPlayDelaySetting` |
| Match Referee | 합법수, 착수 적용, pass/pass 종료, 사석/계가 트리거 | `GameState`, `HumanMoveApplication`, `EndgameResolver`, 향후 `MatchReferee` |
| Black Seat | 흑 진영 controller, AI/사람/원격 유저 설정 | `PlayerSetup.black`, 향후 `SeatId.Black` |
| White Seat | 백 진영 controller, AI/사람/원격 유저 설정 | `PlayerSetup.white`, 향후 `SeatId.White` |
| Remote User Match | 원격 유저 착수 수신/송신, 동기화, 지연/재접속 | 아직 없음 |

### Game UX

Game UX는 다음을 하지 않는다.

- `Engine Core API` 직접 호출
- 엔진 내부 sync 순서 결정
- AI 후보 선택 정책 결정
- 종국 점수 선택 정책 결정
- 엔진 capability 문자열 판별

Game UX는 다음만 담당한다.

- 화면 상태 렌더링
- 사용자 이벤트 수집
- 미들웨어 API 호출
- 미들웨어 결과를 화면 상태에 적용

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

이번 리팩토링 배치에서 바로 진행할 안전한 순서:

1. `[완료]` `EngineCoreApi` 도입
2. `[완료]` application/match의 코어 의존 타입을 `EngineCoreApi`로 점진 전환
3. `[완료]` `SeatId`, `SeatAssignment`, `AiCharacterProfile` 도입
4. `[완료]` PlayerSetup helper를 seat 도메인 기반으로 정리
5. `[완료]` MatchReferee 후보를 추가하고 사람 착수 로컬 처리부터 적용
6. 테스트/문서/히스토리 갱신 후 커밋/푸시

### 진행 로그

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

## 주의할 점

- 이름만 바꾸는 대규모 rename은 뒤로 미룬다. 현재는 호환 타입을 두고 의미를 명확히 하는 방식이 안전하다.
- 원격 서버 엔진을 아직 구현하지 않는다. 단, 구현 가능한 경계를 코드와 테스트로 만든다.
- 도메인을 너무 잘게 쪼개어 실제 흐름 추적이 어려워지면 안 된다. 먼저 seat, AI character, settings, referee처럼 변경 이유가 명확한 단위만 분리한다.
- `GoCoachApp.kt`의 coroutine orchestration은 한 번에 옮기지 않는다. 자동 AI, 종국, restore, benchmark는 timing regression 위험이 높다.
