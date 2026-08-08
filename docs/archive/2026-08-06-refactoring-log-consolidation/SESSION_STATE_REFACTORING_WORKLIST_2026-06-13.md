# Session State 리팩토링 작업 리스트

작성일: 2026-06-13

## 목표

이번 배치의 목표는 이전 배치에서 도입한 reducer state holder를 실제 단일 source of truth로 승격하는 것이다. 최종 목표는 `GameSessionController` 도입이지만, 바로 coroutine orchestration까지 옮기면 회귀 위험이 크므로 state ownership부터 줄인다.

## 2시간 이상 상세 작업 목록

예상 총량: 2시간 30분 ~ 4시간

| 순서 | 작업 | 예상 | 목적 | 완료 기준 |
| ---: | --- | ---: | --- | --- |
| 1 | 작업 리스트 문서화 | 15분 | 이번 배치 범위와 커밋 단위를 고정 | 이 문서와 `DOCS_INDEX.md`, `THREAD_HISTORY.md` 갱신, 커밋/푸시 |
| 2 | `GameSessionAnalysisState` 단일 source of truth 승격 | 45~70분 | Top Moves/리뷰 분석 상태 개별 Compose 변수를 하나의 state holder로 통합 | `candidateMoves`, `candidateText`, `reviewAnalysis`, `reviewCandidateMoves`, `lastAnalysisKey` 직접 state 제거, 테스트 통과 |
| 3 | `GameSessionScoreState` 단일 source of truth 승격 | 45~70분 | score/endgame 표시 상태 개별 Compose 변수를 하나의 state holder로 통합 | `scoreText`, `scoreEstimate`, `scoreSnapshots`, `endgameLog` 직접 state 제거 또는 최소화, 테스트 통과 |
| 4 | `GameSessionRuntimeState` 단일 source of truth 승격 | 30~50분 | `playLevel`, `engineProfile`, `analysisPreset` 개별 Compose 변수를 하나의 runtime state로 통합 | runtime triple 직접 state 제거, 테스트 통과 |
| 5 | 남은 직접 대입 경로 정리 | 30~45분 | human move/local score/engine failure/startup 등 reducer 우회 경로 축소 | reducer helper 추가 또는 명시적 보류 기록 |
| 6 | 문서 갱신과 통합 검증 | 15~30분 | 다음 controller skeleton 착수 기준 확정 | `make test` 통과, 다음 추천 항목 문서화, 커밋/푸시 |

## 이번 배치의 중요한 원칙

- reducer state holder와 개별 Compose state를 동시에 source of truth로 두지 않는다.
- 기능 동작 변경 없이 상태 소유권만 줄인다.
- engine 호출, 자동 AI timing, 저장/복원 로직은 이번 배치에서 구조만 안전하게 유지하고 대규모 이동하지 않는다.
- 각 단계별로 테스트 후 커밋/푸시한다.

## 예상 리스크

- `candidateText`와 `engineMessage`는 여러 경로에서 동시에 갱신된다. analysis state 승격 중 누락되면 디버그/후보수 표시가 어긋날 수 있다.
- `scoreSnapshots`는 score graph와 저장/복원/종국 로그에 동시에 쓰인다. score state 승격 후 모든 참조가 같은 state를 보도록 해야 한다.
- runtime triple은 메뉴 설정, 복원, 자동 AI 턴에서 함께 움직인다. 하나만 개별 변수로 남으면 엔진 profile과 UI 표시가 어긋날 수 있다.

## 진행 로그

- 2026-06-13: 작업 리스트 작성.
- 2026-06-13: `GameSessionAnalysisState` 단일 source of truth 승격 완료. `GoCoachApp.kt`의 `candidateMoves`, `candidateText`, `reviewAnalysis`, `reviewCandidateMoves`, `lastAnalysisKey` 개별 Compose state를 제거하고 `analysisState` 하나로 통합했다. Top Moves 요청/표시, benchmark candidate text, debug report, screen state 입력이 모두 `analysisState`를 참조하도록 정리했다. `:app-android:testDebugUnitTest` 통과.
- 2026-06-13: `GameSessionScoreState` 단일 source of truth 승격 완료. `GoCoachApp.kt`의 `scoreText`, `scoreEstimate`, `scoreSnapshots`, `endgameLog` 개별 Compose state를 제거하고 `scoreState` 하나로 통합했다. score graph, score estimate request, human move local snapshot, undo, debug report, screen state 입력이 모두 `scoreState`를 참조하도록 정리했다. `:app-android:testDebugUnitTest` 통과.
- 2026-06-13: `GameSessionRuntimeState` 단일 source of truth 승격 완료. `GoCoachApp.kt`의 `playLevel`, `engineProfile`, `analysisPreset` 개별 Compose state를 제거하고 `runtimeState` 하나로 통합했다. 엔진 시작, Top Moves, 점수 추정, 새 대국 시작, 자동 AI 턴, undo sync, debug report, screen state 입력이 모두 `runtimeState`를 참조하도록 정리했다. `:app-android:testDebugUnitTest` 통과.
- 2026-06-13: 통합 검증 완료. `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test`를 실행했고 통과했다. 현재 `GoCoachApp.kt`는 1,626줄이며, analysis/score/runtime 핵심 표시 상태는 단일 source of truth로 정리되었다.
- 2026-06-13: `GameSessionMoveReviewState` 단일 source of truth 승격 완료. `GoCoachApp.kt`의 `moveReviewText`, `moveReviews`, `lastMoveText` 개별 Compose state를 제거하고 `moveReviewState` 하나로 통합했다. human move, undo, reset, restore, 자동 AI 턴 표시, debug report, screen state 입력이 모두 `moveReviewState`를 참조하도록 정리했다. `:app-android:testDebugUnitTest` 통과.
- 2026-06-13: `GameSessionCoreState` 초안 도입 완료. `gameState`, `isGameEnded`, `analysisState`, `scoreState`, `runtimeState`, `moveReviewState`, `engineMessage`를 application 계층의 순수 상태 모델로 묶고 reset, restore, undo, scoring rule change, final score, score estimate, endgame failure, 자동 AI 턴, human move local result 적용 함수를 추가했다. 아직 UI wiring은 변경하지 않았고, 다음 반복에서 `GoCoachApp.kt`의 `apply*Plan` helper를 core reducer 호출로 이관할 예정이다. `:app-android:testDebugUnitTest` 통과.
- 2026-06-13: `GoCoachApp.kt`의 주요 `apply*Plan` helper를 `GameSessionCoreState` reducer 브리지로 이관했다. score estimate, final score, endgame failure, 자동 AI 턴, 새 대국, 저장 대국 복원, undo, scoring rule change, human move local result가 transient core state를 통해 계산된 뒤 기존 Compose state에 반영된다. core state를 Compose에 중복 저장하지 않아 이행 과정의 이중 source of truth 위험을 피했다. `:app-android:testDebugUnitTest` 통과.
- 2026-06-13: `HumanEngineSyncFailurePlan`과 `PlayerSetupChangePlan.Apply` 적용도 `GameSessionCoreState` reducer로 흡수했다. 사람 착수 후 엔진 sync 실패 표시 상태와 Player Setup 변경 시 runtime/analysis reset이 core state 테스트로 고정되었다. `:app-android:testDebugUnitTest` 통과.
- 2026-06-13: runtime log enrichment를 도입했다. `RuntimeLogContext`가 앱 목적, 모드, 플레이어 설정, 보드 요약, 엔진 준비/Busy 상태, runtime profile, Top Moves/cache/coverage, score text, flags, 다음 예상 transition을 모든 runtime event에 공통으로 붙인다. 또한 사람 착수 흐름을 `human_move_accepted`, `human_engine_sync_success`, `human_engine_sync_failure` 이벤트로 기록해 로그만 보고 사람 턴에서 엔진 sync/Top Moves/종국 판정으로 이어지는 흐름을 추적할 수 있게 했다. `make test` 통과.
- 2026-06-13: `GameSessionTurnTimeState`를 추가해 흑/백 누적 착수 시간을 application state holder로 분리했다. 사람 착수와 AI 착수 성공 시점에서 동일한 타이머 경계를 사용하고, reset/restore는 새 타이머로 시작하며 undo는 기존 누적값을 유지한 채 현재 턴 시작점만 다시 잡는다. 화면, debug report, runtime event log가 모두 같은 시간 상태를 참조한다. `make test` 통과.

## 다음 리팩토링 추천 항목

1. `GameSessionCoreState` 초안 도입
   - `gameState`, `isGameEnded`, `analysisState`, `scoreState`, `runtimeState`, `moveReviewState`, `engineMessage`를 하나의 application state로 묶는다.
   - 완료: Compose state를 완전히 대체하지 않고 reducer 테스트부터 만들었다.

2. `GameSessionReducer` 순수 함수 도입
   - 완료: core state 내부 함수로 주요 plan 적용 경로를 만들고 `GoCoachApp.kt`의 주요 `apply*Plan` 함수들이 core state 전이 결과를 참조하도록 이관했다.
   - 추가 완료: `HumanEngineSyncFailurePlan`, `PlayerSetupChangePlan.Apply`도 core reducer로 흡수했다.
   - 남은 작업은 `EngineStartupDisplayPlan`, Search Time 변경, Top Moves launch snapshot 복원처럼 UI 외부 플래그와 얽힌 부분 상태 갱신을 controller/effect로 분리하는 것이다.

3. Thin `GameSessionController` skeleton
   - coroutine 실행까지 한 번에 옮기지 말고, event 입력과 reducer 결과/effect 타입만 먼저 정의한다.
   - UI는 effect를 실행하고 controller는 다음 state와 필요한 engine action을 설명하는 구조가 안전하다.
   - 로그 인리치먼트 이후에는 controller effect가 runtime event를 직접 생성하도록 옮기는 것이 자연스럽다.

4. 플랫폼 port 분리
   - `GameSessionStore`, `EngineBenchmarkStore`, debug report mirror, clipboard/toast를 port로 감싼다.
   - 서버 엔진/원격 대국을 염두에 두면 persistence/diagnostic boundary를 먼저 분리하는 것이 좋다.

5. Turn time persistence/effect 분리
   - 현재 `GameSessionTurnTimeState`는 앱 프로세스 내 세션 시간이다. 다음 단계에서 저장 대국 snapshot에 포함할지, 앱 재시작 후는 새 측정으로 볼지 정책을 확정한다.
   - 자동 AI 턴 delay, engine busy, human input disabled 시간을 착수 시간에 포함하는 현재 정책이 UX 기대와 맞는지 로그 기반으로 검증한다.
