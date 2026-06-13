# 다음 리팩토링 작업 리스트

작성일: 2026-06-14

## 현재 상황 요약

- `EngineCoreApi`와 `EngineSessionClient` 경계가 생겨 UI가 원시 엔진 구현체를 직접 알지 않는 구조가 마련되었다.
- `SeatId`, `AiCharacterProfile`, `MatchReferee`, `GameSettings`가 도입되어 대국/좌석/설정 도메인의 기본 분리는 진행되었다.
- `GameSessionAnalysisState`, `GameSessionScoreState`, `GameSessionRuntimeState`, `GameSessionMoveReviewState`, `GameSessionTurnTimeState`, `GameSessionCoreState`가 도입되어 화면 상태 일부는 application 계층의 reducer를 탄다.
- 다만 `GoCoachApp.kt`는 여전히 약 1,700줄 규모이며, Compose 상태 보관, coroutine 실행, benchmark, cache optimization, Top Moves launch, 설정 저장, 세션 저장, debug report 복사까지 한 파일에서 처리한다.
- 서버 엔진 또는 원격 대국으로 확장하려면 UI 파일 안의 orchestration을 더 얇게 만들고, application 계층에 “상태 전이/실행 계획/effect”를 더 많이 남겨야 한다.

## 이번 배치 목표

이번 배치의 목표는 큰 `GameSessionController`를 한 번에 도입하기보다, controller 이전의 전제 조건을 안전하게 쌓는 것이다.

1. 설정 상태를 application state holder로 묶어 Player Setup, Search Time, Top Moves, Auto delay 변경이 한 경계에서 관리되게 한다.
2. benchmark 화면/진행 상태를 작은 state holder로 분리한다.
3. post-game JSON cache optimization prompt/run 상태를 state holder로 분리한다.
4. Top Moves launch와 cache 상태 적용을 더 작은 reducer로 정리한다.
5. debug report 입력 조립을 UI에서 한 단계 더 얇게 만든다.
6. 문서와 테스트를 갱신하고 각 단계별로 커밋/푸시한다.

## 6시간 이상 상세 작업 계획

예상 총량: 6시간 30분 ~ 9시간

| 순서 | 작업 | 예상 | 목적 | 완료 기준 |
| ---: | --- | ---: | --- | --- |
| 1 | 현황/계획 문서화 | 30분 | 현재 분리 상태와 이번 배치 범위를 고정 | 이 문서와 `THREAD_HISTORY.md` 갱신, 커밋/푸시 |
| 2 | `GameSessionSettingsState` 도입 | 60~90분 | `playerSetup`, `autoPlayDelaySetting`, `searchTimeSettings`, `topMovesEnabled`를 묶어 설정 source of truth 축소 | 설정 변경 reducer/test 추가, `GoCoachApp.kt` 개별 state 일부 제거 |
| 3 | 설정 저장 snapshot 경계 정리 | 45~60분 | preference 저장 입력을 `GameSessionSettingsState` 기반으로 만들고 UI 인자 수 축소 | `buildUserPreferencesSnapshot` 호출부 단순화, 저장 codec 테스트 유지 |
| 4 | `EngineBenchmarkUiState` 도입 | 60~90분 | benchmark profile text/result/progress/averages 상태를 application state로 묶음 | benchmark 시작/성공/실패 reducer/test 추가, UI 변수 축소 |
| 5 | `PositionCacheOptimizationUiState` 도입 | 60~90분 | prompt dismissed/running 상태와 prompt 표시 판정을 한 경계로 묶음 | prompt dismiss/accept/run complete reducer/test 추가, cache optimization UI 변수 축소 |
| 6 | Top Moves launch state reducer 보강 | 45~75분 | launch plan 적용 시 candidate restore/cache hit/run state 갱신을 application reducer로 이동 | `TopMovesApplicationTest` 또는 신규 테스트 보강 |
| 7 | Debug report snapshot builder 정리 | 45~60분 | copy debug report의 다수 인자를 immutable snapshot으로 묶어 추후 diagnostic port 분리 준비 | `DebugReportBuilderTest` 유지/보강, UI 함수 인자 감소 |
| 8 | 문서/히스토리/다음 추천 항목 갱신 | 30~45분 | 다음 controller/effect 이전 작업 진입 조건 명확화 | refactoring 문서 진행 로그와 `THREAD_HISTORY.md` 갱신 |
| 9 | 통합 검증 | 30~45분 | 회귀 방지 | 단계별 `:app-android:testDebugUnitTest`, 최종 `make test` 통과 |

## 이번 배치에서 하지 않을 일

- coroutine 실행 전체를 controller로 옮기지 않는다. 엔진 busy timing, 자동 AI delay, restore, pass/pass 종국 흐름의 회귀 위험이 크다.
- 원격 서버 엔진 구현은 하지 않는다. 단, 서버 엔진으로 옮길 때 UI가 바뀌지 않도록 application 경계를 넓힌다.
- UI 디자인 변경은 하지 않는다. 이번 배치는 구조 정리와 테스트 보강에 집중한다.

## 진행 로그

- 2026-06-14: 작업 리스트 작성. 현재 상태는 엔진/좌석/심판/설정/분석/점수/runtime 일부가 application 계층으로 분리되었지만, `GoCoachApp.kt`가 아직 세션 orchestration의 중심이다.
- 2026-06-14: `GameSessionSettingsState`를 추가해 `playerSetup`, `autoPlayDelaySetting`, `searchTimeSettings`, `topMovesEnabled`를 application state holder로 묶었다. `GoCoachApp.kt`는 개별 Compose state 대신 `settingsState`를 source of truth로 사용하며, 이어하기 복원/Player Setup 변경/Search Time 변경/Top Moves 토글/Auto delay 변경이 state holder 메서드를 통과한다. `GameSessionSettingsStateTest`를 추가했고 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: 설정 저장 snapshot 경계를 정리했다. `buildUserPreferencesSnapshot(settingsState, ...)` overload를 추가해 `GoCoachApp.kt`가 Player Setup, Top Moves, Auto delay, Search Time을 개별 인자로 풀어 넘기지 않도록 했다. `UserPreferencesApplicationTest`에 state holder 기반 저장 테스트를 추가했고 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: `EngineBenchmarkUiState`를 추가해 benchmark text, Search Time 추천 평균, 진행 상태, 결과 확인 팝업 상태를 하나로 묶었다. `GoCoachApp.kt`의 `engineBenchmarkText`, `searchTimeBenchmarkAverages`, `benchmarkProgress`, `benchmarkResultToConfirm` 개별 state를 제거했고, `EngineBenchmarkProfile.averageMillisByVisits()`를 application 계층 함수로 이동했다. `EngineDeviceBenchmarkApplicationTest`를 보강했고 `:app-android:testDebugUnitTest`가 통과했다.
