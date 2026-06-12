# 다음 리팩토링 작업 리스트

작성일: 2026-06-13

## 목표

이번 배치의 목표는 `GoCoachApp.kt`를 바로 ViewModel/Controller로 대규모 이전하기 전에, 안전하게 분리 가능한 application 로직을 먼저 밖으로 이동하는 것이다. 최종 방향은 `GameSessionController` 도입이지만, 자동 AI 턴, 벤치마크, 저장/복원, 종국 처리 timing regression 위험이 크므로 작은 단위로 검증한다.

## 현재 진단

- `GoCoachApp.kt`: 약 1,528줄. Compose 상태 보관, 이벤트 dispatch, coroutine launch, runtime log 문자열 조립, display plan 적용, engine busy 상태 전이를 모두 가진다.
- `application`: Top Moves, Score, Auto AI display plan, Human move, Undo, Start game, Preferences, Endgame은 상당히 분리되어 있다.
- `match`: `SeatId`, `AiCharacterProfile`, `MatchReferee`가 들어와 대국/좌석 경계는 좋아졌다.
- 남은 핵심 부채는 UI 파일 안의 orchestration이다. 특히 runtime event log 문자열, engine busy guard, benchmark progress, 자동 AI schedule/execute 경로가 길다.

## 2시간 이상 상세 작업 목록

예상 총량: 2시간 30분 ~ 4시간

| 순서 | 작업 | 예상 | 목적 | 완료 기준 |
| ---: | --- | ---: | --- | --- |
| 1 | 다음 리팩토링 계획 문서화 | 15분 | 작업 범위와 순서를 고정 | 이 문서와 `THREAD_HISTORY.md` 갱신, 커밋/푸시 |
| 2 | Runtime Event Log formatter 분리 | 45~60분 | `GoCoachApp.kt`의 긴 로그 문자열 조립을 application 계층으로 이동 | `RuntimeEventApplication.kt` 추가, 자동 AI/게임 시작/benchmark 주요 로그 함수화, 테스트 추가 |
| 3 | Engine operation guard 분리 | 30~45분 | benchmark/search time/scoring rule처럼 busy/ready 조건 메시지를 application 정책으로 이동 | guard 함수와 테스트 추가, UI는 결과만 적용 |
| 4 | Top Moves request launcher 전 단계 정리 | 30~45분 | `requestTopMoveAnalysisForState` 내부의 cache hit/miss와 request 조건을 더 작은 plan으로 분리 | 기존 TopMovesApplication 테스트 확장 |
| 5 | Auto AI runtime flow 분리 준비 | 45~60분 | `requestAiTurnForCurrentState`의 schedule/begin/success/failure 로그와 입력 snapshot을 data class로 분리 | 자동 AI event context 타입 추가, GameAutomationApplication 테스트 확장 |
| 6 | Display plan applier 후보 정리 | 30~45분 | `applyScoreEstimateDisplayPlan`, `applyFinalScoreDisplayPlan` 등 로컬 mutator를 controller 이전 가능한 목록으로 정리 | 문서와 코드 주석/테스트로 다음 이전 지점 명확화 |
| 7 | 통합 검증 | 15~30분 | 안전성 확인 | `make test` 통과, 문서 진행 로그 갱신, 커밋/푸시 |

## 이번 배치에서 바로 착수할 범위

우선순위는 안전성과 회귀 방지다.

1. Runtime Event Log formatter 분리
2. Engine operation guard 분리
3. 가능한 범위에서 Auto AI runtime flow의 log context 타입 분리
4. `GoCoachApp.kt` 라인 수와 import 수 감소 확인
5. `make test`로 통합 검증

## 보류할 작업

- `GameSessionController` 전체 도입은 이번 배치에서 바로 하지 않는다. 상태 소유권과 coroutine timing 변화가 크다.
- Compose 상태를 `StateFlow`로 이전하는 작업은 실제 기기 통합 테스트가 필요한 별도 배치로 분리한다.
- 원격 서버 엔진 구현은 아직 하지 않는다. 지금은 경계와 테스트만 강화한다.

## 진행 로그

- 2026-06-13: 작업 리스트 작성.
- 2026-06-13: Runtime Event Log formatter 분리 완료. `RuntimeEventApplication.kt`를 추가해 app start, game reset, engine game start, auto play delay, AI turn schedule/begin/success/endgame/failure/complete 로그 문자열을 application 계층에서 생성하도록 했다. `RuntimeEventApplicationTest` 추가, `:app-android:testDebugUnitTest` 통과.
- 2026-06-13: Engine operation guard 분리 완료. `EngineOperationPolicy.kt`를 추가해 benchmark 실행, Search Time 변경, scoring rule 변경의 ready/busy/no-op 판정을 application 계층으로 이동했다. `EngineOperationPolicyTest` 추가, `:app-android:testDebugUnitTest` 통과.
- 2026-06-13: Auto AI runtime flow 분리 준비 완료. `AutoAiTurnExecutionContext`와 `buildAutoAiTurnExecutionContext()`를 추가해 자동 AI 턴 직전의 state/player/level/analysis limit/cache isolation/review candidates snapshot 생성을 application 계층으로 이동했다. `GameAutomationApplicationTest` 확장, `:app-android:testDebugUnitTest` 통과.
- 2026-06-13: Top Moves request launcher 전 단계 정리 완료. `TopMoveAnalysisLaunchPlan`과 `buildTopMoveAnalysisLaunchPlan()`을 추가해 자동 중복 요청, 현재 snapshot 복원, 캐시 hit, 엔진 신규 요청 판단을 application 계층으로 이동했다. `TopMovesApplicationTest` 확장, `:app-android:testDebugUnitTest` 통과.
- 2026-06-13: Display plan applier 후보 정리 완료. `GAME_SESSION_CONTROLLER_CANDIDATES_2026-06-13.md`를 추가해 `applyScoreEstimateDisplayPlan`, `applyTopMoveAnalysisUpdate`, reset/undo/endgame/session restore reducer들의 이전 난이도와 `GameSessionController` 도입 순서를 정리했다. 지금은 Compose 상태 setter를 외부 함수에 넘기는 방식의 무리한 분리를 보류하고, immutable state holder 도입 후 reducer를 이전하는 것으로 결정했다.
- 2026-06-13: 통합 검증 완료. `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test`를 실행했고 통과했다.
