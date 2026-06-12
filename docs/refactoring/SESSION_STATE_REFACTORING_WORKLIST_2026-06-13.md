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
