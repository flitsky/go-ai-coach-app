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

## 다음 리팩토링 추천 항목

1. `GameSessionCoreState` 초안 도입
   - `gameState`, `isGameEnded`, `analysisState`, `scoreState`, `runtimeState`, `moveReviewText`, `moveReviews`, `lastMoveText`, `engineMessage`를 하나의 application state로 묶는다.
   - 초기에는 Compose state를 완전히 대체하지 말고 reducer 테스트부터 만든다.

2. `GameSessionReducer` 순수 함수 도입
   - `GameSessionResetPlan`, `SavedGameRestorePlan`, `UndoLocalStatePlan`, `ScoringRuleChangePlan`, `AutoAiTurnDisplayPlan`을 `GameSessionCoreState -> GameSessionCoreState`로 적용한다.
   - 지금 UI에 남아 있는 `apply*Plan` 함수들을 reducer 테스트로 옮기는 준비 단계다.

3. `MoveReviewState` 분리
   - `moveReviewText`, `moveReviews`, `lastMoveText`는 아직 개별 Compose state다.
   - 착수/무르기/복원에서 같이 움직이므로 별도 state holder로 빼면 `GameSessionCoreState` 결합이 쉬워진다.

4. Thin `GameSessionController` skeleton
   - coroutine 실행까지 한 번에 옮기지 말고, event 입력과 reducer 결과/effect 타입만 먼저 정의한다.
   - UI는 effect를 실행하고 controller는 다음 state와 필요한 engine action을 설명하는 구조가 안전하다.

5. 플랫폼 port 분리
   - `GameSessionStore`, `EngineBenchmarkStore`, debug report mirror, clipboard/toast를 port로 감싼다.
   - 서버 엔진/원격 대국을 염두에 두면 persistence/diagnostic boundary를 먼저 분리하는 것이 좋다.
