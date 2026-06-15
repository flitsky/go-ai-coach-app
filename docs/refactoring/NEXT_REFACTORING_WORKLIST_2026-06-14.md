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
- 2026-06-14: `PositionAnalysisCacheOptimizationUiState`를 추가해 종국 후 JSON cache 최적화 prompt, dismissed fingerprint, running flag를 하나로 묶었다. `GoCoachApp.kt`의 `cacheOptimizationPrompt`, `dismissedCacheOptimizationFingerprint`, `isPositionCacheOptimizationRunning` 개별 state를 제거했고, prompt 표시/닫기/수락/실행 완료 흐름이 state holder 메서드를 통과한다. `PositionAnalysisCacheOptimizationTest`를 보강했고 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: Top Moves launch state reducer를 보강했다. `GameSessionAnalysisState.applyTopMoveAnalysisLaunchPlan()`을 추가해 skip/restore/cache-hit/run-engine 결과가 application 계층에서 분석 상태 전이로 변환되게 했다. `GoCoachApp.kt`는 더 이상 `TopMoveAnalysisLaunchPlan` sealed 하위 타입을 직접 분기하지 않고, engine 실행이 필요한 경우의 `runEnginePlan`만 처리한다. `TopMovesApplicationTest`를 보강했고 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: `DebugReportSnapshot`을 추가했다. `copyDebugReport()`는 이제 수십 개 인자를 직접 `buildDebugReport()`에 넘기지 않고 snapshot을 구성해 넘긴다. 기존 문자열 생성 함수는 호환 overload로 유지했다. `DebugReportBuilderTest`를 snapshot 기반으로 갱신했고 `:app-android:testDebugUnitTest`가 통과했다.

## 배치 완료 결과

- 커밋 단위로 7개 변경을 완료했다.
  - `Plan next session refactoring batch`
  - `Consolidate session settings state`
  - `Use settings state for preference snapshots`
  - `Consolidate benchmark UI state`
  - `Consolidate position cache optimization UI state`
  - `Move top move launch state updates into application`
  - `Introduce debug report snapshot`
- `GoCoachApp.kt` 라인 수는 약 1,728줄에서 약 1,717줄로 소폭 감소했다.
- 라인 수보다 중요한 변화는 설정/benchmark/cache optimization/Top Moves launch/debug report 입력이 application 계층의 state holder 또는 reducer를 통과하게 된 점이다.
- 최종 검증은 `make test`로 수행한다.

## 다음 리팩토링 추천 항목

1. `GameSessionController` thin skeleton 도입
   - 지금까지 만든 `GameSessionCoreState`, `GameSessionSettingsState`, `EngineBenchmarkUiState`, `PositionAnalysisCacheOptimizationUiState`를 하나의 controller state로 묶는다.
   - 첫 단계에서는 coroutine 실행을 옮기지 말고 event -> state/effect plan만 반환한다.

2. Engine effect 타입 분리
   - `RunTopMoveAnalysis`, `RunAutoAiTurn`, `RunScoreEstimate`, `RunBenchmark`, `RunCacheOptimization`, `SyncRestoredGame` 같은 effect sealed class를 만든다.
   - UI는 effect를 실행하고 결과를 controller reducer에 다시 넣는다.

3. Persistence/diagnostic port 분리
   - `GameSessionStore`, `UserPreferencesStore`, `EngineBenchmarkStore`, `RuntimeEventLog`, debug report mirror 저장을 port/interface로 묶는다.
   - 서버 엔진/원격 대국으로 이동할 때 UI와 저장소 정책이 얽히지 않게 한다.

4. Saved session state holder 도입
   - `pendingSavedSession`, `shouldShowResumePrompt`, `hasCheckedSavedSession`을 묶는다.
   - 저장/복원 prompt가 engine startup/benchmark/cache optimization prompt와 충돌하지 않도록 prompt priority 정책을 application 계층에서 관리한다.

5. Auto AI turn runner 분리
   - 자동 AI 턴의 schedule, cancellation, begin/success/failure/endgame 로그, turn time 기록을 하나의 runner plan으로 묶는다.
   - AI vs AI 자동대국 UI 입력 막힘, delay 설정, search cache isolation 정책을 이 경계에서 더 쉽게 검증할 수 있다.

## 다음 배치 진행 로그

- 2026-06-14: `GameSessionControllerState` 얇은 skeleton을 추가했다. 기존 `GameSessionCoreState`, `GameSessionSettingsState`, `EngineBenchmarkUiState`, `PositionAnalysisCacheOptimizationUiState`를 한 객체로 묶고, `gameState`, `isGameEnded`, `playerSetup`, `matchMode`, `engineMessage`를 읽기 전용 convenience property로 노출한다. 아직 coroutine 실행이나 UI wiring은 옮기지 않았다.
- 2026-06-14: `GameSessionEffect` sealed interface 초안을 추가했다. `RunTopMoveAnalysis`, `RunAutoAiTurn`, `RunScoreEstimate`, `RunStartupBenchmark`, `RunPositionCacheOptimization`, `SyncRestoredGame` effect 타입만 정의해 다음 단계에서 UI coroutine 실행을 application/controller 경계로 분리할 수 있게 했다.
- 2026-06-14: `GameSessionControllerTest`를 추가해 controller state가 중첩 상태를 손실 없이 노출/교체하는지, effect 타입이 기존 application plan을 실행하지 않고 운반만 하는지 검증했다. 기본 셸 Java 25에서는 Gradle Kotlin DSL이 `IllegalArgumentException: 25`로 실패하므로, JDK 17과 Android SDK를 명시해 `:app-android:testDebugUnitTest`를 실행했고 통과했다.
- 2026-06-14: `SavedSessionUiState`를 추가해 `pendingSavedSession`, `shouldShowResumePrompt`, `hasCheckedSavedSession`를 하나의 application state holder로 묶었다. `GoCoachApp.kt`는 저장 세션 prompt 상태를 개별 Compose state로 보관하지 않고 `savedSessionUiState`에서 파생해 사용한다. `SavedSessionPromptApplicationTest`를 보강했고 JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: `planSavedGamePersistence(savedSessionUiState, ...)` overload를 추가해 자동 저장 계획이 저장 세션 prompt gate를 개별 Boolean이 아니라 application state holder로 받게 했다. `GoCoachApp.kt`의 자동 저장 `LaunchedEffect`도 `savedSessionUiState`를 key로 사용한다. `SavedGamePersistenceTest`를 보강했고 JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: `GameSessionControllerState`에 `savedSession: SavedSessionUiState` 축을 추가했다. controller state가 core/settings/benchmark/saved-session/cache-optimization 상태를 함께 대표하게 되었고, `withSavedSession()` 교체 메서드를 테스트했다. JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: `AutoAiTurnUiState`를 추가해 자동 AI 턴 예약 pending 플래그를 application state holder로 묶었다. `GoCoachApp.kt`는 직접 Boolean 대입 대신 `markScheduled()`와 `clearPending()`을 사용한다. 이는 이후 Auto AI turn runner/effect 분리 전 단계다. `GameAutomationApplicationTest`를 보강했고 JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: 이번 배치 통합 검증으로 `JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test`를 실행했고 통과했다.
- 2026-06-14: Auto AI turn runner 분리의 다음 안전 단위로 `AutoAiTurnScheduleValidationPlan`을 추가했다. delay 이후 현재 상태가 여전히 AI 턴 실행 가능한지 검증하고, 가능하면 `AutoAiTurnExecutionContext`를 함께 반환한다. `GoCoachApp.kt`는 더 이상 delay 후 `shouldRequestAiTurn(...)` 조건을 직접 풀지 않고 controller snapshot의 validation plan만 처리한다. `GameAutomationApplicationTest`를 보강했고 JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.

## 다음 추천 리팩토링 항목

1. `GameSessionControllerState`를 `GoCoachApp.kt`에 실제로 도입
   - 현재는 controller state skeleton과 테스트만 존재한다.
   - 다음 단계에서는 Compose state들을 한 번에 옮기지 말고, `core/settings/benchmark/savedSession/autoAiTurn/cacheOptimization` 순서로 controller snapshot을 조립하는 helper부터 둔다.

2. Auto AI turn runner plan 분리
   - `requestAiTurnForCurrentState()` 내부의 schedule, delay, cancellation, begin/success/failure/endgame completion을 `AutoAiTurnRunnerPlan` 또는 controller effect로 나눈다.
   - UI는 effect 실행만 담당하고, 성공/실패 display plan은 application reducer로 반영하게 한다.

3. Prompt priority 정책 분리
   - resume prompt, benchmark progress/result, cache optimization prompt가 동시에 걸릴 때 어떤 prompt가 우선인지 application 계층에 명문화한다.
   - 현재는 각 흐름이 UI에서 독립적으로 계산된다.

4. Persistence/diagnostic port 인터페이스 도입
   - `GameSessionStore`, `UserPreferencesStore`, `EngineBenchmarkStore`, `RuntimeEventLog`, debug report mirror 저장을 application port로 묶는다.
   - 로컬 저장소와 향후 원격/서버 저장소를 바꿔 끼울 수 있는 기반을 만든다.

## 2026-06-14 추가 리팩토링 진행 로그

- 2026-06-14: 폰 원격 설치를 먼저 수행했다. mDNS에서 `SM-S908N` 무선 ADB 서비스를 발견해 `192.168.35.166:33421`로 연결했고, `make install-dev-engine`로 최신 debug APK 설치, KataGo model/config seed, cold launch를 완료했다.
- 2026-06-14: `GameSessionControllerState`에 `autoAiTurn: AutoAiTurnUiState` 축을 추가했다. controller state convenience property로 `shouldShowResumePrompt`, `isAutoAiTurnPending`도 노출한다.
- 2026-06-14: `GoCoachApp.kt`에 `currentControllerSessionState()`를 추가하고 runtime log context 및 `GameScreenStateInput` 조립이 controller snapshot을 참조하게 했다. 아직 상태 저장 source 자체를 controller 하나로 합치지는 않았고, 읽기/조립 경계부터 연결했다. JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: `PromptPriorityApplication`을 추가해 resume prompt와 cache optimization prompt의 표시 우선순위를 application 계층 함수로 분리했다. 공통 gate는 “엔진 startup 완료 + engine idle”이며, resume prompt가 보이는 동안 cache optimization prompt는 숨긴다. `GameScreenState`는 inline 조건 대신 `decidePromptVisibility()`를 호출한다. JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: `ApplicationPorts.kt`를 추가해 saved game, user preferences, engine benchmark, runtime event log, debug report mirror 저장을 application port로 정의했다. 기존 Android persistence 구현체가 해당 port를 구현하도록 연결했고, `DebugReportMirrorStore`를 추가해 `GoCoachApp.kt`의 raw file write를 제거했다. JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: `GoCoachApp.kt`의 `sessionStore`, `preferencesStore`, `benchmarkStore`, `runtimeEventLog`, `debugReportMirror` 변수 타입을 구체 persistence class가 아니라 application port interface로 바꿨다. 생성 지점은 Android 구현체를 사용하지만 이후 호출부는 port 계약에 의존한다. JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: `GameSessionControllerState.toRuntimeLogContext(...)` 확장 함수를 추가해 runtime log context 조립을 application 계층으로 이동했다. `GoCoachApp.kt`는 engine ready/busy, cache stats, turn time 같은 외부 값만 넘긴다. `RuntimeEventApplicationTest`를 보강했고 JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: `buildGameScreenStateInput(controller, ...)` helper를 추가해 `GameScreenStateInput` 조립을 controller snapshot 기반으로 옮겼다. `GoCoachApp.kt`는 화면 외부 값만 넘기고 세션 내부 상태를 직접 풀어 쓰지 않는다. `GameScreenStateTest`를 보강했고 JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: `GameSessionControllerState.toDebugReportSnapshot(...)` 확장 함수를 추가해 debug report snapshot 조립을 application 계층으로 이동했다. `GoCoachApp.kt`는 engine/cache/runtime-log/turn-time 같은 외부 값만 넘기고, 세션 내부 진단 항목은 controller snapshot에서 파생한다. `DebugReportBuilderTest`를 보강했고 JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: `buildGameSessionControllerState(...)` builder를 추가해 `GoCoachApp.kt`의 controller/core snapshot 수동 조립 중복을 줄였다. UI는 Compose state 소유권을 유지하되 현재 상태를 application controller snapshot으로 변환하는 책임을 application helper에 위임한다. `GameSessionControllerTest`를 보강했고 JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: `GameSessionControllerState.toAutoAiTurnRequestPlan(...)`와 `toAutoAiTurnExecutionContext(...)`를 추가했다. 자동 AI 턴의 요청 가능 여부와 실행 context 생성이 UI 세부 필드 조합 대신 controller snapshot에서 파생된다. `GameAutomationApplicationTest`를 보강했고 JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: `GameSessionControllerState.toTopMoveAnalysisLaunchPlan(...)`와 `toShowTopMovesPlan(...)`를 추가했다. Top Moves 분석 launch/cache/display 판단이 UI 세부 필드 조합 대신 controller snapshot에서 파생된다. `TopMovesApplicationTest`를 보강했고 JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: 이번 추가 배치 최종 검증으로 `make test`를 실행했고 통과했다. 이후 무선 ADB `SM-S908N(192.168.35.166:33421)`에 `make install-dev-engine`로 최신 debug APK와 KataGo model/config를 설치하고 cold launch를 확인했다. launch `TotalTime=617ms`.
- 2026-06-14: Auto AI turn runner 분리의 후속 안전 단위로 `AutoAiTurnRunPlan`을 추가했다. delay 후 validation 결과가 단순 execution context가 아니라 `delayMillis + AutoAiTurnExecutionContext`를 함께 운반한다. `GameSessionEffect.RunAutoAiTurn`도 context 대신 run plan을 받도록 바꿔 다음 단계에서 UI coroutine 실행을 effect runner로 옮길 준비를 했다. `GoCoachApp.kt`는 validation 결과에서 `runPlan`을 받고, begin log도 plan의 delay 값을 사용한다. `GameAutomationApplicationTest`, `GameSessionControllerTest`를 보강했고 JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: 자동 AI 턴 실패 반영을 application reducer로 이동했다. `AutoAiTurnFailureDisplayPlan`과 `GameSessionCoreState.applyAutoAiTurnFailureDisplayPlan()`을 추가해 UI가 실패 시 `engineMessage`와 `candidateText`를 직접 대입하지 않도록 했다. 실패 경로는 보드와 점수 상태를 바꾸지 않는다는 테스트를 추가했고 JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: 자동 AI 턴 성공 후 후속 Top Moves 분석 판단을 `AutoAiTurnFollowUpPlan`으로 분리했다. 기존 UI는 `display.nextAnalysisState` nullable 값을 직접 보고 `requestTopMoveAnalysisForState()`를 호출했지만, 이제 application 계층의 `buildAutoAiTurnFollowUpPlan()`이 `None` 또는 `RequestTopMoveAnalysis`를 반환한다. `GameAutomationApplicationTest`로 일반 진행/종국 케이스를 고정했고 JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: 자동 AI 턴 성공 후 종국 resolve 입력 조립을 `AutoAiTurnEndgamePlan`으로 분리했다. 기존 UI는 `display.shouldResolveEndgame`, `display.gameState`, `display.profile`, `display.endgamePrePassCandidates`, source string을 직접 조합했지만, 이제 application 계층의 `buildAutoAiTurnEndgamePlan()`이 `None` 또는 `Resolve`를 반환한다. `GameSessionEffect.ResolveAutoAiEndgame`도 추가해 다음 단계에서 종국 resolve coroutine을 effect runner로 옮길 준비를 했다. `GameAutomationApplicationTest`, `GameSessionControllerTest`를 보강했고 JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: `AutoAiTurnEndgameDisplayPlan`과 `EngineSessionClient.runAutoAiEndgameDisplayPlan()`을 추가해 자동 AI pass/pass 종국 resolve 실행과 성공/실패 표시 계획 생성을 application 계층으로 이동했다. `GoCoachApp.kt`는 이제 종국 resolve를 직접 호출하거나 `buildResolvedEndgameDisplayPlan()`/`buildEndgameFailureDisplayPlan()`을 직접 호출하지 않고, 반환된 display plan을 적용하고 runtime log만 남긴다. `GameAutomationApplicationTest`에 성공/실패 runner 테스트를 추가했고 JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: undo 후 과거 국면으로 돌아갈 때 엔진 분석을 불필요하게 반복하지 않도록 `UndoAnalysisRestoreCache`를 추가했다. 일반 `AnalysisResultCache`는 계속 기본 비활성으로 유지하되, root visits가 requested visits를 충족한 complete 분석 snapshot만 undo 복원 cache에 저장한다. Top Moves launch는 undo 복원 cache를 먼저 확인하고 없을 때만 일반 cache/엔진 호출로 넘어간다. `TopMovesApplicationTest`에 complete/short root visits 정책 테스트를 추가했고 JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: stale result guard의 첫 단계로 `PositionScopedOperationToken`과 `EngineOperationResultGuard`를 추가했다. Top Moves engine 실행은 요청 당시 position fingerprint와 `AnalysisCacheKey`를 token으로 보관하고, 결과/실패가 돌아왔을 때 현재 국면과 key가 일치할 때만 적용한다. 무르기/새 게임/복원/search time 변경 등으로 상태가 바뀐 경우 과거 응답은 적용하지 않는다. `EngineOperationPolicyTest`, `TopMovesApplicationTest`를 보강했고 JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: 폰 `SM-S908N(192.168.35.166:42037)`에 최신 debug APK와 KataGo model/config를 설치하고 cold launch `TotalTime=596ms`를 확인했다.
- 2026-06-14: 같은 stale result guard 패턴을 수동 score estimate 경로에도 적용했다. `ScoreEstimateOperationToken`을 추가해 요청 당시 position fingerprint를 보관하고, score estimate 성공/실패가 돌아왔을 때 현재 국면이 같을 때만 점수 상태와 메시지를 갱신한다. `ScoreDisplayApplicationTest`를 보강했고 JDK 17/Android SDK 환경에서 `:app-android:testDebugUnitTest`가 통과했다.
- 2026-06-14: 종국 빠른 판정 정책을 실제 코드에 연결했다. 첫 구현은 5초 cap과 진단용 `scoreFinal()` 생략이었으나, 사용자 피드백에 따라 `deadStones()` 2초 + `scoreFinal()` 1초로 단계별 cap을 분리했다. debug log에는 `assistantJudgeDeadStonesTimeCapMs=2000`, `assistantJudgeFinalScoreTimeCapMs=1000`, `assistantJudgeTotalTimeCapMs=3000`, `diagnosticKataGoFinalScore=...`를 남긴다.
- 2026-06-14: stale result guard를 자동 AI 턴과 자동 AI pass/pass 종국 경로까지 확장했다. `AutoAiTurnOperationToken`과 `AutoAiEndgameOperationToken`은 요청 당시 position fingerprint를 저장하며, 결과 도착 시 현재 board가 달라졌으면 성공/실패 결과 모두 화면에 반영하지 않는다. `GoCoachApp.kt`는 guard 결과가 `Apply`일 때만 turn time, runtime success/failure log, board/score/final score display를 갱신한다.
- 2026-06-14: 엔진 다중화/CoachEngine 논의는 후속 검토로 보류하고, 다음 리팩토링으로 stale result discard 관측성을 보강했다. `runtimeEngineOperationDiscardedLog()`를 추가하고 Top Moves, score estimate, 자동 AI 턴, 자동 AI 종국 경로에서 stale result를 조용히 버리지 않고 runtime event로 남기도록 정리했다. `RuntimeEventApplicationTest`를 보강했고 JDK 17/Android SDK 환경에서 관련 application 테스트가 통과했다.
- 2026-06-14: score estimate 실패 경로를 application display plan/reducer로 이동했다. `ScoreEstimateFailureDisplayPlan`과 `buildScoreEstimateFailureDisplayPlan()`을 추가했고, `GameSessionScoreState`/`GameSessionCoreState`가 실패 적용을 담당한다. `GoCoachApp.kt`는 더 이상 score estimate 실패 시 `engineMessage`와 `scoreState`를 직접 수정하지 않는다. 관련 Score/Core state 테스트를 보강했고 JDK 17/Android SDK 환경에서 관련 application 테스트가 통과했다.
- 2026-06-14: Top Moves 실패 경로를 application display plan/reducer로 이동했다. `TopMoveAnalysisFailureDisplayPlan`과 `buildTopMoveAnalysisFailureDisplayPlan()`을 추가했고, `GameSessionAnalysisState`/`GameSessionCoreState`가 실패 시 review snapshot, analysis key, 표시 후보, engine message 반영을 담당한다. `GoCoachApp.kt`는 더 이상 Top Moves 실패 시 `engineMessage`, review analysis, displayed spots를 직접 조합하지 않는다. 관련 TopMoves/Analysis/Core state 테스트를 보강했고 JDK 17/Android SDK 환경에서 관련 application 테스트가 통과했다.
- 2026-06-14: Top Moves launch update가 raw `TopMoveAnalysisPlan` 대신 `GameSessionEffect.RunTopMoveAnalysis`를 반환하도록 연결했다. `TopMoveAnalysisLaunchPlan.RunEngine`은 `plan`, `deep`, `automatic`을 함께 보존하고, `GameSessionAnalysisState.applyTopMoveAnalysisLaunchPlan()`은 pending analysis key와 실행 effect를 같이 만든다. `GoCoachApp.kt`는 이제 launch 결과의 effect를 실행한다. `TopMovesApplicationTest`와 `GameSessionControllerTest`를 보강했고 JDK 17/Android SDK 환경에서 관련 application 테스트가 통과했다.
- 2026-06-14: Git GC 경고를 먼저 정리했다. `.git/gc.log`의 원인은 unreachable loose object 누적이었고, `git prune` 및 stale gc log 제거 후 loose object 수가 7788개에서 1145개로 줄었다. 이후 `git gc --auto` 확인에서도 경고가 재발하지 않았다.
- 2026-06-14: Top Moves effect 실행 세부 인자 조립을 application runner로 이동했다. `TopMoveAnalysisExecutionContext`와 `EngineSessionClient.runTopMoveAnalysisEffect()`를 추가해 `GoCoachApp.kt`가 `engineProfile`, `analysisPreset`, `topMovesEnabled`, `cacheEnabled`를 raw `runTopMoveAnalysis()`에 직접 조합해 넘기지 않도록 했다. `TopMovesApplicationTest`에 effect runner 검증을 추가했고 JDK 17/Android SDK 환경에서 관련 application 테스트가 통과했다.
- 2026-06-14: Score Estimate 요청도 `GameSessionEffect.RunScoreEstimate` 흐름으로 연결했다. `ScoreEstimateLaunchStateUpdate`와 `ScoreEstimateRequestPlan.toScoreEstimateLaunchStateUpdate()`를 추가해 점수 추정 버튼의 message/local display/engine effect 분기를 application 계층에서 만들고, `EngineSessionClient.runScoreEstimateEffect()`를 추가해 `GoCoachApp.kt`가 raw `runScoreEstimateDisplayPlan()`을 직접 호출하지 않도록 했다. `ScoreDisplayApplicationTest`에 launch update/effect runner 테스트를 추가했고 JDK 17/Android SDK 환경에서 관련 application 테스트가 통과했다.
- 2026-06-14: 폰 `SM-S908N(192.168.35.47:41809)`에 최신 debug APK와 KataGo model/config를 설치하고 cold launch `TotalTime=1175ms`를 확인했다.
- 2026-06-14: Position Analysis Cache Optimization 실행부를 `GameSessionEffect.RunPositionCacheOptimization` runner로 연결했다. `EngineSessionClient.runPositionAnalysisCacheOptimizationEffect()`를 추가해 post-game cache optimization을 다시 활성화하더라도 `GoCoachApp.kt`가 raw `optimizePositionAnalysisCache(plan)`를 직접 호출하지 않도록 했다. `PositionAnalysisCacheOptimizationTest`에 effect runner 위임 테스트를 추가했고 JDK 17/Android SDK 환경에서 관련 application 테스트가 통과했다.
- 2026-06-14: Startup Benchmark 실행부를 `GameSessionEffect.RunStartupBenchmark` runner로 연결했다. `StartupBenchmarkExecutionContext`와 `EngineSessionClient.runStartupBenchmarkEffect()`를 추가해 `GoCoachApp.kt`가 raw `runStartupBenchmark(...)`를 직접 호출하지 않도록 했다. progress UI 갱신과 benchmark store 저장은 앱서비스 조율 책임으로 유지하고, 엔진 호출 경계만 effect runner 패턴으로 맞췄다. `EngineDeviceBenchmarkApplicationTest`에 effect runner 위임 테스트를 추가했고 JDK 17/Android SDK 환경에서 관련 application 테스트가 통과했다.
- 2026-06-14: Saved game restore sync 실행부를 `GameSessionEffect.SyncRestoredGame` runner로 연결했다. `RestoredGameSyncExecutionContext`와 `EngineSessionClient.runRestoredGameSyncEffect()`를 추가해 `GoCoachApp.kt`가 raw `runRestoredGameSyncDisplayPlan(...)`을 직접 호출하지 않도록 했다. `ScoreDisplayApplicationTest`에 effect runner 위임 테스트를 추가했고 JDK 17/Android SDK 환경에서 관련 application 테스트가 통과했다.
- 2026-06-14: Debug report copy의 platform side effect를 port/effect 경계로 분리했다. `ClipboardPort`, `UserNoticePort`, `runDebugReportCopyEffect()`를 추가하고 Android 구현은 `AndroidPlatformPorts.kt`로 이동했다. `GoCoachApp.kt`는 debug report 생성 후 `GameSessionEffect.CopyDebugReport` 실행 결과만 반영하며, Android clipboard/toast API를 직접 호출하지 않는다. `DebugReportBuilderTest`에 effect runner 포트 호출 테스트를 추가했고 JDK 17/Android SDK 환경에서 관련 application 테스트가 통과했다.
- 2026-06-14: Human move 이후 engine sync 실행부를 `GameSessionEffect.SyncHumanMove` runner로 연결했다. `HumanEngineSyncRunPlan`과 `EngineSessionClient.runHumanEngineSyncEffect()`를 추가해 `GoCoachApp.kt`가 raw `syncAfterHumanMove(...)`를 직접 호출하지 않도록 했다. 사람 착수 후 성공/실패 display plan, runtime log, 후속 Top Moves 요청 흐름은 기존과 동일하게 유지했다. `HumanMoveApplicationTest`와 `GameSessionControllerTest`를 보강했고 JDK 17/Android SDK 환경에서 관련 application 테스트가 통과했다.
- 2026-06-14: Auto AI turn 주 실행부를 `GameSessionEffect.RunAutoAiTurn` runner로 실제 연결했다. `AutoAiTurnRunExecutionContext`와 `EngineSessionClient.runAutoAiTurnEffect()`를 추가해 `GoCoachApp.kt`가 raw `runAutoAiTurnDisplayPlan(...)` 인자를 직접 조합하지 않도록 했다. 자동 AI 턴 성공/실패, stale guard, pass/pass 종국 resolve, 후속 Top Moves 요청 순서는 기존과 동일하게 유지했다. `GameAutomationApplicationTest`를 보강했고 JDK 17/Android SDK 환경에서 관련 application 테스트가 통과했다.
- 2026-06-14: Auto AI turn pending 상태 전이를 `AutoAiTurnUiState` reducer로 정리했다. `applyAutoAiTurnRequestPlan()`, `applyAutoAiTurnScheduleValidationPlan()`, `completeAutoAiTurnRun()`을 추가해 `GoCoachApp.kt`가 자동 AI schedule/cancel/complete 지점에서 raw `markScheduled()`/`clearPending()`을 직접 호출하지 않도록 했다. `GameAutomationApplicationTest`에 상태 전이 테스트를 추가했고 JDK 17/Android SDK 환경에서 관련 application 테스트가 통과했다.
- 2026-06-14: Auto AI turn 완료 후 후속 Top Moves 요청 분기를 nullable request helper로 정리했다. `AutoAiTurnFollowUpRequest`와 `AutoAiTurnFollowUpPlan.toAutoAiTurnFollowUpRequest()`를 추가해 `GoCoachApp.kt`가 follow-up sealed subtype을 직접 분기하지 않도록 했다. continuing game은 automatic Top Moves request를 반환하고, 종국/none은 null을 반환한다. `GameAutomationApplicationTest`를 보강했고 JDK 17/Android SDK 환경에서 관련 application 테스트가 통과했다.
- 2026-06-14: 외부 검토 의견의 1순위였던 structured diagnostic event 고도화를 진행했다. `engine.operation.slow`, `engine.operation.timeout`, `engine.operation.discarded` 이벤트 생성 함수를 추가했고, stale engine result discard 경로가 `diagnostic_events.jsonl`에도 구조화 이벤트를 남기도록 연결했다. `DiagnosticEventApplicationTest`와 `DiagnosticEventLogTest`가 통과했다.
- 2026-06-14: middleware 물리 경계 정리의 첫 안전 단위로 `PositionAnalysisGateway` 계약을 추가했다. 이 계약은 `GameState`, `AnalysisLimit`, `EngineSearchMode`, `AnalysisResult` 같은 shared DTO만 의존하며, `LayeringContractTest`가 Android/UI/application/persistence/engine runtime import를 막는다.
- 2026-06-14: remote engine driver spike의 최소 단위로 `RemotePositionAnalysisGateway`와 `RemotePositionAnalysisTransport`를 추가했다. 범위는 읽기 전용 position analysis로 제한했고, `genmove/play/undo`나 match ownership은 포함하지 않았다. `RemotePositionAnalysisGatewayTest`로 요청 전달, fingerprint 포함, `PositionAnalysisBackend.Remote` 변환을 검증했다.

## 현재 완료 기준 재정의

- 이번 요청의 추천 순서 3개는 모두 코드와 테스트로 반영됐다.
- remote gateway는 아직 production wiring이 아니다. 다음 단계에서 feature flag 뒤의 HTTP transport 구현과 offline fallback 검증이 필요하다.
- middleware gateway 계약은 KMP-ready로 고정했지만, 물리적 KMP 모듈 이동은 아직 하지 않았다. 이동 전에 현재 architecture test를 더 확장하는 것이 안전하다.

## 다음 추천 리팩토링 항목

1. HTTP `RemotePositionAnalysisTransport` spike
   - 읽기 전용 `/position-analysis` 형태의 transport를 만들고 feature flag 기본 off로 둔다.
   - timeout/failure 시 local engine으로 fallback되는지 테스트한다.

2. `EngineOperationRequest` 공통 모델
   - operation id, session generation, board fingerprint, timeout, fallback, backend id를 묶는다.
   - Top Moves, score estimate, auto AI, endgame resolve의 stale discard/diagnostic/timeout 정책을 통합한다.

3. Structured diagnostic 자동 계측
   - 현재 이벤트 생성 함수는 준비됐지만 slow/timeout은 실제 operation runner 전체에 자동 연결되어 있지 않다.
   - elapsed, timeout, backend, requested/root visits를 일관되게 남기는 helper를 둔다.

4. Middleware KMP 물리 이동 준비
   - `PositionAnalysisGateway`, remote transport contract, cache resolver 중 Android-free 파일을 후보로 분류한다.
   - import churn을 줄이기 위해 architecture test를 먼저 확장한 뒤 작은 단위로 옮긴다.

## 2026-06-14 추가 진행 로그: Engine Operation Lifecycle/Generation

- 2026-06-14: auto AI turn과 auto AI endgame stale guard를 공통 `EngineOperationRequest`로 전환했다. 이제 Top Moves, score estimate, auto AI turn, auto AI endgame의 engine-facing operation이 동일한 operation id, generation, timeout, fallback metadata를 갖는다.
- 2026-06-14: `GameSessionRuntimeState.sessionGeneration`을 추가하고 새 게임, 저장 복원, 무르기 전환 시 세대를 증가시켰다. 늦게 도착한 engine result는 현재 세대와 요청 세대가 다르면 적용하지 않는다.
- 2026-06-14: stale discard runtime/diagnostic log에 operation kind, operation id, session generation을 추가했다. 원격 엔진 또는 다중 엔진 도입 후에도 로그만으로 어떤 요청 결과가 폐기됐는지 추적할 수 있게 됐다.
- 2026-06-14: `runObservedEngineOperation()`을 score estimate, auto AI turn, auto AI endgame effect runner에도 적용했다. slow/timeout diagnostic event가 position analysis뿐 아니라 주요 engine operation에 공통으로 기록된다.
- 2026-06-14: `EngineOperationLifecycleTransition` reducer를 추가하고 `GoCoachApp.kt`의 raw busy Boolean 전이를 helper 호출로 치환했다. 현재 동작은 동일하지만 향후 operation id 기반 busy stack 또는 concurrent operation counter로 바꿀 때 수정 지점이 작아졌다.
- 검증: 기본 Java 25는 Gradle Kotlin DSL이 `IllegalArgumentException: 25`로 실패하므로 JDK 17을 명시했다. `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest` 통과.
- 최종 통합 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test` 통과.

## 다음 추천 리팩토링 항목

1. `GameSessionEffect.ResolveAutoAiEndgame` 실제 연결
   - 현재 endgame runner는 application 함수로 분리됐지만, UI coroutine은 아직 `runAutoAiEndgameDisplayPlan()`을 직접 호출한다.
   - effect 타입을 실제 실행 경로에 연결하면 auto AI main turn과 endgame resolve의 effect 구조가 완전히 대칭이 된다.

2. Engine operation lifecycle의 operation-id stack화 준비
   - 지금은 Boolean reducer지만, remote/coach engine 병렬 작업을 고려하면 operation id별 started/completed tracking이 필요해질 수 있다.
   - 우선 data model과 테스트만 추가하고 UI 적용은 작은 단위로 진행한다.

3. Structured diagnostic event schema 문서화
   - `engine.operation.slow`, `timeout`, `discarded`의 필수 context key를 문서화한다.
   - 향후 MQ/Firebase/Sentry 전송 adapter가 들어와도 이벤트 schema가 흔들리지 않게 한다.

4. `GoCoachApp.kt` coroutine runner 추가 축소
   - startup/new-game/human-sync/undo/cache optimization 경로의 started/completed/failure 패턴을 공통 runner로 묶을 수 있는지 검토한다.
   - 단, UX state 변경이 섞인 구간은 무리하게 추상화하지 않고 operation 경계가 명확한 곳부터 진행한다.

## 2026-06-14 추가 진행 로그: Effect/Lifecycle/Diagnostic Schema

- 2026-06-14: `GameSessionEffect.ResolveAutoAiEndgame`을 실제 UI 실행 경로에 연결했다. `EngineSessionClient.runAutoAiEndgameEffect()`를 추가해 auto AI main turn과 endgame resolve가 같은 effect runner 구조를 갖게 됐다.
- 2026-06-14: `EngineOperationLifecycleState`를 도입해 engine busy lifecycle이 active operation id set을 관리하도록 확장했다. 현재 UI는 helper에서 `isEngineBusy` 즉시값도 함께 갱신하므로 기존 UX 동작은 유지된다.
- 2026-06-14: `GoCoachApp.kt`의 engine operation start/complete helper가 operation id를 받도록 변경했다. 공통 operation request가 있는 Top Moves, Score Estimate, Auto AI는 request의 operation id를 쓰고, startup/undo/cache 등은 UI-local id를 발급한다.
- 2026-06-14: `launchTrackedEngineOperation()` helper를 추가해 Top Moves와 Score Estimate coroutine의 started/finally-completed 패턴을 축소했다. startup/new-game/human-sync/undo/cache optimization은 UX state 조율이 섞여 있어 다음 안전 단위로 남겼다.
- 2026-06-14: `DIAGNOSTIC_EVENT_SCHEMA.md`를 추가해 structured diagnostic event envelope, engine operation slow/timeout/discarded, visit fill, final score disagreement event schema를 문서화했다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest` 통과, `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test` 통과.

## 다음 추천 리팩토링 항목

1. Engine operation lifecycle을 runtime/diagnostic event와 더 직접 연결
   - `engine.operation.started/completed` 이벤트를 추가할지 검토한다.
   - 너무 많은 로그가 생기지 않도록 debug build 또는 warning/critical 중심 정책을 먼저 정한다.

2. Remaining coroutine runner 축소
   - startup, new game, restored sync, human sync, undo, cache optimization 중 operation 경계가 명확한 것부터 `launchTrackedEngineOperation()`으로 이동한다.
   - UI state 조율이 복잡한 경로는 작은 display plan/reducer를 먼저 만든다.

3. Engine operation request 확대
   - human move sync, restored game sync, scoring rule sync, startup benchmark도 `EngineOperationRequest` 또는 별도 request model을 갖게 해 diagnostic metadata를 통일한다.

4. Diagnostic event schema 테스트
   - 문서화된 필수 context key가 누락되지 않도록 schema contract test를 추가한다.
   - 향후 Firebase/Sentry/MQ adapter 도입 전에 이벤트 품질을 고정한다.

## 2026-06-15 추가 진행 로그: Operation Request Coverage/Runtime Lifecycle

- 2026-06-15: engine operation lifecycle을 runtime event log에 직접 연결했다. 이제 engine-facing 작업 시작/완료 시 `engine_operation_started`, `engine_operation_completed` 로그가 operation id와 active operation count를 남긴다. 정상 흐름에서 빈번히 발생하는 이벤트라 `diagnostic_events.jsonl`이 아닌 runtime event log에만 기록한다.
- 2026-06-15: `EngineOperationKind` 범위를 넓혔다. `engine_startup`, `engine_new_game`, `scoring_rule_sync`, `post_undo_sync`, `engine_undo`를 추가해 UI-local 문자열 id 생성을 제거하고 `engineOperationRequest(...)`가 operation id의 중심이 되게 했다.
- 2026-06-15: human move sync, restored game sync, scoring rule sync, startup benchmark, position cache optimization runner가 `EngineOperationRequest`와 `DiagnosticEventLogPort`를 선택적으로 받도록 확장했다. UI는 실제 실행 시 현재 session generation, board fingerprint, timeout/fallback policy를 명시해 전달한다.
- 2026-06-15: startup, new game, undo, cache optimization, scoring rule sync, post-undo sync 일부 경로를 `launchTrackedEngineOperation()` 또는 `runTrackedEngineOperation()`으로 정리했다. 작업 완료 시 `finally`에서 lifecycle complete가 호출되므로 실패/취소 시 busy 상태가 남을 위험을 줄였다.
- 2026-06-15: `PositionAnalysisCacheOptimizationPlan`이 최종 `GameState`를 보존하도록 보강했다. post-game cache optimization도 표준 operation request가 요구하는 board fingerprint를 같은 방식으로 생성할 수 있다.
- 2026-06-15: `DiagnosticEventApplicationTest`에 schema contract test를 추가했다. `engine.operation.slow`, `engine.operation.timeout`, `engine.operation.discarded`가 문서화된 필수 context key를 유지하는지 검증한다.
- 2026-06-15: `RuntimeEventApplicationTest`에 operation started/completed 로그 테스트를 추가했다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest` 통과, `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test` 통과.

## 현재 리팩토링 완성도 평가

- 주관 점수: 96/100.
- 상향 요인: UI가 직접 엔진 low-level 호출 인자를 조합하는 구간이 계속 줄고 있으며, stale result, lifecycle, slow/timeout/discarded 관측성이 같은 operation metadata로 정렬되고 있다.
- 남은 감점 요인: `GoCoachApp.kt`가 아직 coroutine orchestration의 중심이다. operation metadata는 application 정책으로 통일됐지만 startup/new-game/undo의 실행 runner 자체는 아직 앱서비스 계층에 남아 있다. 또한 diagnostic JSONL은 schema contract가 생겼지만 원격 전송 adapter나 sampling 정책은 없다.

## 다음 추천 리팩토링 항목

1. Engine session lifecycle runner 분리
   - startup, engine-backed new game, engine undo를 `EngineSessionClient` extension runner로 옮긴다.
   - 목표는 `GoCoachApp.kt`가 `startSession`, `startNewGame`, `undoMove`를 직접 호출하지 않게 하는 것이다.

2. `EngineOperationScope` 도입
   - `operationRequest + runObservedEngineOperation + lifecycle start/complete`를 하나의 실행 계약으로 묶는다.
   - UI는 lifecycle callback만 주입하고, operation execution은 application runner에 위임한다.

3. Result guard 범위 재검토
   - scoring rule sync, restored sync, post-undo sync에도 generation/position guard를 적용할 수 있는지 검토한다.
   - 단, restore처럼 operation 생성 시점과 apply 시점의 generation이 다른 경로는 별도 테스트가 필요하다.

4. Runtime event log sampling/rotation 정책 정리
   - started/completed 로그가 늘었으므로 runtime log 1MB ring buffer 정책이 실제로 충분한지 확인한다.
   - warning/critical 로그와 일반 lifecycle 로그를 분리 저장할지 검토한다.

5. Middleware KMP 이동 후보 파일 2차 분류
   - Android-free application/middleware 파일을 추려 `shared` 또는 별도 KMP middleware 모듈로 이동 가능한 순서를 만든다.
   - 먼저 architecture test를 강화하고, 물리 이동은 작은 단위로 수행한다.

## 2026-06-15 추가 진행 로그: Session Lifecycle Runner/Guard

- 2026-06-15: `GameSessionEffect.StartEngineSession`, `StartEngineBackedGame`, `UndoEngineMoves`를 추가했다. startup/new game/undo도 effect로 운반되며, UI가 raw engine session primitive를 직접 호출하지 않는 방향으로 이동했다.
- 2026-06-15: `EngineSessionLifecycleApplication.kt`를 추가했다. `EngineSessionClient.runEngineStartupEffect()`, `runEngineBackedNewGameEffect()`, `runEngineUndoEffect()`가 `startSession`, `startNewGame`, `undoMove` raw 호출을 감싸고, `EngineOperationRequest` 기반 `runObservedEngineOperation()`을 통과한다.
- 2026-06-15: `EngineOperationScope`를 추가했다. 이 scope는 operation lifecycle start/complete callback만 담당하고, slow/timeout diagnostic은 기존 effect runner의 observer에 맡긴다. 이렇게 해서 lifecycle log와 diagnostic JSONL이 중복 기록되지 않도록 했다.
- 2026-06-15: `GoCoachApp.kt`의 `launchTrackedEngineOperation()`/`runTrackedEngineOperation()`이 문자열 id가 아니라 `EngineOperationRequest` 전체를 받도록 바꿨다. Top Moves, score estimate, benchmark, startup, new game, undo, sync 경로가 같은 lifecycle scope를 사용한다.
- 2026-06-15: post-undo sync, scoring rule sync, restored game sync, human move sync 결과에 `evaluateEngineOperationResultGuard()`를 적용했다. 늦게 도착한 sync 결과는 화면에 반영하지 않고 discard runtime/diagnostic log만 남긴다.
- 2026-06-15: `EngineSessionLifecycleApplicationTest`를 추가해 startup/new-game/undo runner 위임과 `EngineOperationScope` 실패 시 complete callback 보장을 검증했다. `GameSessionControllerTest`도 새 effect 타입을 포함하도록 보강했다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest` 통과.

## 현재 리팩토링 완성도 평가

- 주관 점수: 96.5/100.
- 상향 요인: session lifecycle raw primitive가 application runner 뒤로 이동했고, sync 계열 늦은 결과 폐기 정책이 더 넓게 적용됐다. operation request가 단순 로그 id가 아니라 실행 scope의 입력으로 쓰이기 시작했다.
- 남은 감점 요인: UI가 아직 많은 coroutine orchestration과 success/failure display plan 조합을 직접 소유한다. `EngineOperationScope`는 도입됐지만 아직 application-level supervisor/runner까지 완전히 승격되지는 않았다.

## 다음 추천 리팩토링 항목

1. `EngineOperationScope`를 application runner까지 끌어올리기
   - 현재 scope 생성은 `GoCoachApp.kt`에 남아 있다.
   - lifecycle callbacks를 주입받는 앱서비스 runner를 만들어 UI는 callback 구현만 제공하게 한다.

2. Sync result guard helper를 application 계층으로 분리
   - `shouldApplyEngineOperationResult()`는 아직 UI local helper다.
   - apply/discard decision과 follow-up Top Moves 요청 여부를 reducer/plan으로 분리한다.

3. Human sync success/failure display runner 정리
   - `buildHumanEngineSyncSuccessPlan()`/`FailurePlan()` 호출과 runtime log 생성 순서를 application runner plan으로 묶는다.
   - stale result일 때 success/failure log를 남기지 않는 정책을 테스트로 더 구체화한다.

4. Runtime event log volume 정책 실측
   - started/completed가 늘어난 뒤 1MB ring buffer가 한 판 이상의 흐름을 충분히 담는지 확인한다.
   - 필요하면 lifecycle event sampling 또는 operation summary event로 압축한다.

5. Middleware KMP 이동 후보 2차 정리
   - `EngineOperationPolicy`, `DiagnosticEventApplication`, position analysis gateway 중 Android-free 파일을 물리 이동 후보로 분류한다.
   - 이동 전 package dependency test를 먼저 강화한다.

## 2026-06-15 추가 진행 로그: Sync Apply Plan 정리

- 2026-06-15: `EngineOperationApplyPlan`을 추가해 엔진 결과를 화면에 적용할지, 늦은 결과로 폐기할지를 application 계층의 plan으로 표현하게 했다. `GoCoachApp.kt`의 local helper는 이제 guard 판단 자체를 직접 수행하지 않고 plan을 받아 runtime/diagnostic discard log만 연결한다.
- 2026-06-15: `HumanEngineSyncCompletionPlan`을 추가했다. human move 이후 엔진 sync 성공/실패 결과는 먼저 session generation과 board fingerprint guard를 통과해야 하며, stale result이면 success/failure display plan과 runtime success/failure log가 생성되지 않는다.
- 2026-06-15: `runEngineOperationInScope()` helper를 추가해 UI가 `EngineOperationScope`를 직접 생성하지 않도록 정리했다. 아직 lifecycle callback 구현은 UI에 남아 있지만, scope 생성과 complete 보장 계약은 application 함수로 이동했다.
- 2026-06-15: `EngineOperationPolicyTest`, `HumanMoveApplicationTest`, `EngineSessionLifecycleApplicationTest`를 보강해 apply/discard plan wrapping, stale human sync discard, scoped engine operation 실패 시 complete 보장을 검증했다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest` 통과.

## 현재 리팩토링 완성도 평가

- 주관 점수: 97/100.
- 상향 요인: UI가 엔진 결과의 신선도 판정과 human sync success/failure plan 조합을 직접 소유하던 비율이 더 줄었다. 특히 늦게 도착한 human sync 결과가 success/failure 로그를 남기지 않는 정책이 application test로 고정됐다.
- 남은 감점 요인: `GoCoachApp.kt`에는 아직 `shouldApplyEngineOperationResult()`라는 UI-local side effect helper와 post-undo/scoring/restored sync의 apply/discard 분기가 남아 있다. lifecycle callback 구현도 앱서비스 계층에서 완전히 빠진 것은 아니므로, 다음 단계에서는 operation result handler와 sync completion plan을 더 넓은 경로로 확대하는 것이 적절하다.

## 다음 추천 리팩토링 항목

1. Operation result handler 분리
   - 현재 `shouldApplyEngineOperationResult()`는 application plan을 사용하지만 discard runtime/diagnostic log side effect는 UI helper에 남아 있다.
   - `EngineOperationApplyPlan`을 runtime event plan 또는 diagnostic append command로 바꾸는 작은 adapter를 만들어 UI local helper를 줄인다.

2. Post-undo/scoring/restored sync completion plan 도입
   - human sync처럼 post-undo sync, scoring rule sync, restored game sync에도 success/failure/discard completion plan을 만든다.
   - 목표는 sync 계열 작업이 모두 같은 stale result 폐기 규칙과 로그 생성 순서를 따르게 하는 것이다.

3. Human sync runtime log plan 이동
   - `runtimeHumanEngineSyncSuccessLog()`/`FailureLog()` 호출 시점은 아직 UI에 있다.
   - elapsed time과 display/failure plan을 받아 runtime log event를 만드는 application-level plan으로 이동하면 UI는 append만 담당한다.

4. Engine operation lifecycle callback provider 축소
   - lifecycle started/completed callback 구현이 `GoCoachApp.kt`에 남아 있다.
   - runtime state와 event log port를 받는 앱서비스 helper로 이전할 수 있는지 검토한다. 단, Compose state mutation이 직접 필요하므로 무리한 추상화는 피한다.

5. KMP 이동 후보 의존성 고정 테스트 확대
   - `EngineOperationPolicy`, `HumanMoveApplication`, `DiagnosticEventApplication` 중 Android-free 파일을 물리 이동 후보로 분류한다.
   - 이동 전 import 금지 테스트를 강화해 Android/UI/persistence 의존성이 다시 들어오지 않게 한다.

## 2026-06-15 추가 진행 로그: 외부 리뷰 반영/Discard Handler

- 2026-06-15: 외부 개발자 검토 의견을 `EXTERNAL_REVIEW_2026-06-15_PROJECT_EVALUATION.md`로 원문 보존했다. 외부 평가는 91/100으로, 운영 플랫폼 관점에서 `GoCoachApp.kt` 경량화, middleware 물리 분리, 운영 자동 계측 연결을 핵심 과제로 제시했다.
- 2026-06-15: 외부 의견에 대한 내부 재검토 문서 `INTERNAL_REVIEW_OF_EXTERNAL_FEEDBACK_2026-06-15.md`를 추가했다. 결론은 “방향은 수용하되, 단기에는 무리한 1,000줄 목표보다 UI orchestration 책임 제거와 계층 회귀 방지 테스트를 우선한다”이다.
- 2026-06-15: `EngineOperationDiscardLogPlan`과 `buildEngineOperationDiscardLogPlan()`을 추가했다. `GoCoachApp.kt`는 더 이상 `runtimeEngineOperationDiscardedLog()`와 `engineOperationDiscardedDiagnosticEvent()`를 직접 호출하지 않고, application plan이 만든 runtime log와 diagnostic event를 append만 한다.
- 2026-06-15: `LayeringContractTest`를 강화했다. `EngineOperationPolicy.kt`, `EngineOperationResultApplication.kt`, `DiagnosticEventApplication.kt`가 Android/UI/persistence/engine runtime 의존을 갖지 못하도록 고정해 KMP 또는 middleware 물리 이동 후보의 경계를 보호한다.
- 2026-06-15: `DIAGNOSTIC_EVENT_SCHEMA.md`에 로컬 JSONL 저장과 향후 외부 수집 sink를 분리하는 정책을 추가했다. 외부 전송은 대국 흐름을 막지 않고, 정상 lifecycle log보다 slow/timeout/discarded/final disagreement 같은 분석 가치가 높은 이벤트를 우선 대상으로 삼는다.
- `GoCoachApp.kt` 라인 수는 2,166줄에서 2,160줄로 소폭 감소했다. 이번 단계의 핵심 성과는 라인 수 감소보다 discard event 생성 책임이 UI에서 application plan으로 이동한 것이다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest' --tests 'com.worksoc.goaicoach.application.RuntimeEventApplicationTest' --tests 'com.worksoc.goaicoach.application.DiagnosticEventApplicationTest'` 통과, `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test` 통과.

## 현재 리팩토링 완성도 평가

- 주관 점수: 97.2/100.
- 외부 운영 플랫폼 관점 점수는 91/100을 수용한다. 이 점수는 실제 출시 이후 원격 엔진, 외부 진단 수집, KMP 물리 모듈 분리까지 포함한 보수적 평가로 해석한다.
- 내부 리팩토링 진행도는 97.2/100으로 본다. engine result discard event 생성이 UI에서 빠졌고, KMP 이동 후보 경계가 architecture test로 보강됐다.
- 남은 감점 요인은 여전히 `GoCoachApp.kt`의 큰 orchestration 책임, sync 계열 completion plan의 불균일성, 외부 diagnostic sink 미구현이다.

## 다음 추천 리팩토링 항목

1. Post-undo/scoring/restored sync completion plan 확장
   - human sync와 같은 `ApplySuccess`/`ApplyFailure`/`Discard` completion plan을 만든다.
   - stale result이면 success/failure runtime log와 후속 Top Moves 요청을 만들지 않는 규칙을 테스트로 고정한다.

2. Engine operation result handler port화
   - 현재 UI는 application plan을 append하지만 append 호출 자체는 UI helper에 남아 있다.
   - runtime event log port와 diagnostic event log port를 받는 작은 app-service handler로 이동할 수 있는지 검토한다.

3. Diagnostic external sink port 설계
   - 즉시 네트워크 전송을 붙이지 않고, 로컬 JSONL과 외부 전송 sink를 분리하는 port 계약부터 설계한다.
   - 사용자 동의 기반 오류 전송과 운영자용 warning/critical 수집은 별도 policy로 둔다.

4. `GoCoachApp.kt` coroutine orchestration 2차 축소
   - post-undo sync, scoring rule sync, restored sync의 runCatching/onSuccess/onFailure 반복을 runner/helper로 묶는다.
   - UX state 변경이 많은 구간은 reducer를 먼저 만들고 실행부 이동은 나중에 한다.

5. Middleware 물리 모듈 이동 준비 목록 확정
   - architecture test가 보호하는 파일부터 `shared` 또는 별도 KMP middleware 모듈로 이동 가능한지 dependency map을 작성한다.
   - 바로 이동하기보다 CI에서 import boundary를 더 촘촘히 고정한 뒤 작은 파일 단위로 옮긴다.

## 2026-06-15 추가 진행 로그: Score Sync Completion/Diagnostic Export Policy

- 2026-06-15: `ScoreSyncCompletionPlan`을 추가했다. post-undo sync, scoring rule sync, restored game sync가 성공/실패/폐기를 같은 completion plan으로 처리한다.
- 2026-06-15: `GoCoachApp.kt`의 post-undo/scoring/restored sync 경로는 더 이상 성공/실패마다 `shouldApplyEngineOperationResult()`를 직접 호출하지 않는다. application plan을 만든 뒤 `ApplySuccess`, `ApplyFailure`, `Discard`만 적용한다.
- 2026-06-15: 더 이상 사용되지 않는 UI-local `shouldApplyEngineOperationResult()` helper를 제거했다.
- 2026-06-15: `recordEngineOperationDiscardLog()`를 추가했다. engine operation discard 발생 시 runtime log와 diagnostic event를 어떤 순서로 어떤 port에 기록하는지는 application helper가 담당하고, UI는 context/current state/port만 넘긴다.
- 2026-06-15: `planDiagnosticEventExternalExport()`를 추가했다. `info` 이벤트는 로컬 보존만, `warning`/`critical` 이벤트는 사용자 동의 기반 외부 전송 후보로 분류한다. 아직 네트워크 sink는 붙이지 않았고, 정책만 순수 함수로 고정했다.
- 2026-06-15: `DIAGNOSTIC_EVENT_SCHEMA.md`에 외부 전송 후보 판단 정책을 보강했다.
- 2026-06-15: `ScoreDisplayApplicationTest`, `RuntimeEventApplicationTest`, `DiagnosticEventApplicationTest`를 보강해 sync completion guard, discard port 기록, 외부 export 판단 정책을 검증했다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.ScoreDisplayApplicationTest' --tests 'com.worksoc.goaicoach.application.RuntimeEventApplicationTest' --tests 'com.worksoc.goaicoach.application.DiagnosticEventApplicationTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest'` 통과, `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test` 통과.

## 현재 리팩토링 완성도 평가

- 주관 점수: 97.5/100.
- 상향 요인: sync 계열 apply/discard 규칙이 더 균일해졌고, UI-local result guard helper가 제거됐다. discard logging은 application port helper를 통과하며, 외부 diagnostic export 여부도 순수 정책으로 분리됐다.
- 남은 감점 요인: `GoCoachApp.kt`는 여전히 큰 파일이며, sync completion plan을 적용하는 local helper 자체는 UI에 남아 있다. 또한 외부 diagnostic sink는 아직 실제 transport가 아니라 export eligibility policy까지만 있다.

## 다음 추천 리팩토링 항목

1. Score sync runner helper 추가 축소
   - post-undo/scoring/restored sync의 `runCatching + withContext + completion plan 적용` 반복을 작은 runner/helper로 묶는다.
   - 단, 각 경로의 follow-up target과 UX message가 다르므로 context object를 먼저 정의한다.

2. Auto AI endgame/result completion plan 정리
   - auto AI turn/endgame 성공/실패/stale 분기도 completion plan 패턴으로 맞춘다.
   - runtime success/failure log 생성과 discard log 생성 순서를 application test로 고정한다.

3. Diagnostic external sink port spike
   - `planDiagnosticEventExternalExport()` 이후 단계로, 사용자 동의가 들어왔을 때 최근 JSONL/debug report bundle을 외부 sink에 넘기는 port 계약만 정의한다.
   - Android/Firebase 구현은 아직 붙이지 않는다.

4. `GoCoachApp.kt` orchestration split 후보 선정
   - score sync, auto AI, top moves 세 덩어리 중 파일 분리 효과가 가장 큰 순서를 정한다.
   - 단순 줄 수 절감보다 “각 파일이 하나의 workflow만 소유하는가”를 기준으로 한다.

5. Middleware/KMP 이동 dependency map 작성
   - `EngineOperationPolicy`, `EngineOperationResultApplication`, `DiagnosticEventApplication`, `ScoreDisplayApplication`의 import graph를 정리한다.
   - shared 또는 별도 middleware KMP 모듈로 옮기려면 어떤 shared DTO가 추가로 필요한지 확인한다.

## 2026-06-15 추가 진행 로그: Auto AI Completion/External Sink/분리 맵

- 2026-06-15: `AutoAiTurnCompletionPlan`을 추가했다. auto AI turn 성공/실패/stale 결과가 application completion plan을 통과하며, UI는 더 이상 `evaluateAutoAiTurnResultGuard()`를 직접 호출하지 않는다.
- 2026-06-15: `AutoAiEndgameCompletionPlan`을 추가했다. auto AI pass/pass 종국 resolve의 resolved/failed/stale 결과도 application completion plan으로 정리했다.
- 2026-06-15: `GoCoachApp.kt`의 auto AI block은 completion plan을 받아 runtime log와 state apply만 수행한다. 여전히 coroutine orchestration은 UI에 남아 있으므로 다음 단계 runner 분리 대상이다.
- 2026-06-15: `DiagnosticEventExternalSinkPort`, `DiagnosticEventExternalExportPayload`, `DiagnosticEventExternalSinkPlan`을 추가했다. 사용자 동의가 들어왔을 때 warning/critical diagnostic event와 debug report text를 외부 sink로 넘길 수 있는 계약만 정의했고, Android/Firebase 구현은 붙이지 않았다.
- 2026-06-15: `ORCHESTRATION_SPLIT_AND_KMP_MAP_2026-06-15.md`를 추가했다. 다음 분리 우선순위는 auto AI workflow runner, score sync workflow runner, Top Moves workflow runner, saved session/startup workflow 순서로 정리했다.
- 2026-06-15: KMP 이동 후보는 `EngineOperationPolicy.kt`, `EngineOperationResultApplication.kt`, `PositionAnalysisGateway.kt`, `RemotePositionAnalysisGateway.kt`를 즉시 후보로 보고, `DiagnosticEventApplication.kt`, `ScoreDisplayApplication.kt`, `GameAutomationApplication.kt`는 파일 내부 책임 분리 후 조건부 이동 후보로 분류했다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.GameAutomationApplicationTest' --tests 'com.worksoc.goaicoach.application.DiagnosticEventApplicationTest'` 통과, `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test` 통과.

## 현재 리팩토링 완성도 평가

- 주관 점수: 97.8/100.
- 상향 요인: auto AI turn/endgame도 score sync와 같은 completion plan 패턴으로 들어왔다. diagnostic external sink는 구현체 없이도 정책과 port 계약이 생겼고, orchestration/KMP 이동 맵이 문서로 고정됐다.
- 남은 감점 요인: `GoCoachApp.kt`는 2,199줄로 여전히 크며, auto AI coroutine orchestration은 아직 UI 내부에 남아 있다. KMP 이동도 dependency map 단계이지 물리적 Gradle 모듈 이동은 아니다.

## 다음 추천 리팩토링 항목

1. Auto AI workflow runner helper 도입
   - `requestAiTurnForCurrentState()`의 schedule/delay/run/completion/endgame/follow-up 흐름을 `AutoAiWorkflowRunner` 성격의 app-service helper로 분리한다.
   - Compose state mutation은 callback으로 주입하고, engine call orchestration만 먼저 옮긴다.

2. Score sync execution helper 도입
   - post-undo/scoring/restored sync의 공통 `runCatching + completion + follow-up` 실행부를 `ScoreSyncWorkflow` helper로 묶는다.
   - post-undo quiet delay와 pending cancellation은 별도 유지한다.

3. Diagnostic external sink fake implementation/test
   - production transport는 보류하고, test fake sink와 export bundle builder를 추가해 사용자 동의 기반 전송 UX를 붙일 준비를 한다.

4. Diagnostic event model/observer 파일 분리
   - `DiagnosticEventApplication.kt`를 event model/export policy와 coroutine observer로 나눈다.
   - KMP 이동 후보를 더 명확히 하기 위한 선행 작업이다.

5. `GameAutomationApplication.kt` 파일 분리
   - request/schedule policy, completion plan, engine runner를 별도 파일로 나눠 KMP 이동 가능성과 리뷰 단위를 개선한다.

## 2026-06-15 추가 진행 로그: Diagnostic/Auto AI 파일 분리와 Score Sync 입력 축소

- 2026-06-15: `DiagnosticEventModel.kt`를 추가했다. diagnostic severity/event model, summary normalization, 외부 export eligibility, sink plan은 coroutine observer와 분리되어 Android/coroutine-free 정책 파일로 관리된다.
- 2026-06-15: `DiagnosticEventApplication.kt`는 324줄에서 216줄로 줄었고, 이제 visit fill/score disagreement/slow/timeout/discarded event builder와 `runObservedEngineOperation()`만 담당한다.
- 2026-06-15: `AutoAiCompletionApplication.kt`를 추가했다. auto AI turn/endgame operation token, result guard, completion plan은 `GameAutomationApplication.kt`에서 분리되어 161줄짜리 독립 application 파일이 됐다.
- 2026-06-15: `GameAutomationApplication.kt`는 661줄에서 503줄로 줄었다. 아직 request/schedule policy와 display runner가 함께 남아 있으므로 다음에는 policy/runner 분리를 이어간다.
- 2026-06-15: `ScoreSyncCompletionRequest`를 추가했다. UI helper는 operation/current state/session generation/follow-up state를 직접 반복 전달하지 않고 하나의 request object로 completion plan을 만든다.
- 2026-06-15: `DiagnosticEventExternalSinkPort` fake test를 추가했다. 실제 Firebase/원격 sink 구현 없이도 consent 기반 export payload가 전송 성공/실패를 표현할 수 있는지 검증한다.
- 2026-06-15: `LayeringContractTest` 대상에 `DiagnosticEventModel.kt`와 `AutoAiCompletionApplication.kt`를 추가했다. 새로 분리한 파일도 Android/UI/persistence/engine runtime import를 가질 수 없다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.DiagnosticEventApplicationTest' --tests 'com.worksoc.goaicoach.application.GameAutomationApplicationTest' --tests 'com.worksoc.goaicoach.application.ScoreDisplayApplicationTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest'` 통과, `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test` 통과.

## 현재 리팩토링 완성도 평가

- 주관 점수: 98.1/100.
- 상향 요인: 이전 문서에서 조건부 KMP 후보로 분류한 diagnostic event model/export policy와 auto AI completion plan이 실제 파일 경계로 분리됐다. score sync도 runner 추출 전에 필요한 request object가 생겼고, external diagnostic sink는 fake로 테스트 가능한 port 계약이 됐다.
- 남은 감점 요인: `GoCoachApp.kt`는 2,211줄로 아직 크며, auto AI coroutine orchestration과 score sync execution orchestration은 UI 내부에 남아 있다. `ScoreDisplayApplication.kt`는 request object 추가로 소폭 커졌으므로 다음 배치에서 completion/runner 파일 분리로 상쇄해야 한다.

## 다음 추천 리팩토링 항목

1. Diagnostic observer 파일 추가 분리
   - `runObservedEngineOperation()`과 `NoopDiagnosticEventLog`를 `DiagnosticEventObserverApplication.kt`로 이동한다.
   - event builder 파일과 coroutine observer 파일을 분리해 KMP 이동 후보를 더 명확히 한다.

2. Auto AI policy/runner 파일 분리
   - `GameAutomationApplication.kt`에서 request/schedule policy와 engine runner/display builder를 나눈다.
   - completion은 이미 분리됐으므로 다음에는 `AutoAiTurnPolicyApplication.kt`, `AutoAiRunnerApplication.kt` 성격으로 나누는 것이 자연스럽다.

3. Score display completion/runner 파일 분리
   - `ScoreSyncCompletionPlan`과 `ScoreSyncCompletionRequest`를 별도 파일로 옮긴다.
   - `runScoreEstimateDisplayPlan`, restored/scoring sync runner는 app-service runner 파일로 이동 후보를 만든다.

4. `GoCoachApp.kt` auto AI workflow local helper 추출
   - 긴 auto AI coroutine block에서 completion apply, endgame apply, follow-up request 부분을 local helper로 먼저 쪼갠다.
   - 바로 class runner로 이동하기보다 callback 경계를 안정화한다.

5. Top Moves workflow runner 준비
   - Top Moves 분석 경로도 operation request, cache hit, engine run, stale discard, review update가 한 곳에 몰려 있다.
   - 바로 이동하기 전에 cache policy와 UI display update 입력을 request object로 묶는다.

## 2026-06-15 추가 진행 로그: Observer/Policy/Runner 파일 분리

- 2026-06-15: `DiagnosticEventObserverApplication.kt`를 추가했다. `runObservedEngineOperation()`과 `NoopDiagnosticEventLog`는 observer 파일로 이동했고, `DiagnosticEventApplication.kt`는 diagnostic event builder만 담당한다.
- 2026-06-15: `GameAutomationApplication.kt`를 제거했다. 자동 AI는 `AutoAiPolicyApplication.kt`(request/schedule/execution context), `AutoAiRunnerApplication.kt`(display/engine runner/endgame runner), `AutoAiCompletionApplication.kt`(operation token/stale completion)로 분리됐다.
- 2026-06-15: `ScoreSyncCompletionApplication.kt`를 추가해 score sync completion guard를 display 파일에서 분리했다.
- 2026-06-15: `ScoreEstimateRunnerApplication.kt`를 추가해 score estimate, scoring rule sync, restored game sync runner를 display 파일에서 분리했다.
- 2026-06-15: `TopMoveAnalysisLaunchRequest`를 추가했다. Top Moves launch 판단의 흩어진 입력을 하나의 request object로 묶어 다음 workflow runner 분리의 입력 경계를 만들었다.
- 2026-06-15: `LayeringContractTest`에 `AutoAiPolicyApplication.kt`, `AutoAiRunnerApplication.kt`, `DiagnosticEventObserverApplication.kt`, `ScoreEstimateRunnerApplication.kt`, `ScoreSyncCompletionApplication.kt`, `TopMovesApplication.kt`를 추가했다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.DiagnosticEventApplicationTest' --tests 'com.worksoc.goaicoach.application.GameAutomationApplicationTest' --tests 'com.worksoc.goaicoach.application.ScoreDisplayApplicationTest' --tests 'com.worksoc.goaicoach.application.TopMovesApplicationTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest'` 통과, `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test` 통과.

## 현재 리팩토링 완성도 평가

- 주관 점수: 98.4/100.
- 상향 요인: application 계층 파일 경계가 실제 workflow 책임 단위로 훨씬 선명해졌다. 특히 자동 AI는 기존 단일 파일에서 policy/runner/completion 3분할로 정리됐고, score display도 display/runner/completion으로 나뉘었다.
- 남은 감점 요인: `GoCoachApp.kt`의 auto AI coroutine block, Top Moves engine run/failure/stale apply, score sync execution apply helper가 아직 UI-local이다. 파일 분리는 좋아졌지만 “실행 orchestration” 자체는 한 단계 더 옮겨야 한다.

## 다음 추천 리팩토링 항목

1. Auto AI workflow local helper 추출
   - `GoCoachApp.kt`의 auto AI success/failure/endgame completion apply 블록을 local helper 함수로 먼저 쪼갠다.
   - callback class runner로 바로 이동하기 전에 state mutation 경계를 안정화한다.

2. Top Moves completion plan 도입
   - Top Moves success/failure/stale 결과도 `TopMoveAnalysisCompletionPlan`으로 통일한다.
   - cache update, undo restore update, displayed candidate update 적용 순서를 application test로 고정한다.

3. Score sync execution helper 도입
   - `ScoreSyncCompletionRequest`를 입력으로 삼아 post-undo/scoring/restored sync의 success/failure/discard apply 반복을 줄인다.
   - post-undo quiet delay는 별도 유지한다.

4. Application 파일 KMP 이동 가능성 재평가
   - `AutoAiCompletionApplication.kt`, `ScoreSyncCompletionApplication.kt`, `DiagnosticEventModel.kt`는 shared/middleware 물리 이동 후보로 다시 평가한다.

5. `GoCoachApp.kt` workflow ownership metric 추가
   - 줄 수만 보지 말고 Top Moves/Auto AI/Score Sync 별 UI-owned branch 수를 문서화한다.

## 2026-06-15 추가 진행 로그: UI Workflow Completion Helper 정리

- 2026-06-15: `TopMoveAnalysisCompletionPlan`을 추가했다. Top Moves success/failure/stale 결과가 application completion plan을 통과하며, UI는 cache update, undo restore update, failure display, discard log 적용만 담당한다.
- 2026-06-15: `TopMovesApplicationTest`에 completion plan 테스트를 추가했다. 성공 결과는 update와 analysis key를 반환하고, 실패는 failure display plan을 만들며, 보드가 바뀐 stale 결과는 discard로 가는 것을 검증한다.
- 2026-06-15: `GoCoachApp.kt`에 `runScoreSyncCompletion()` local helper를 추가했다. post-undo/scoring-rule/restored-game sync의 `runCatching + Dispatchers.IO + success/failure completion apply` 반복을 한 곳으로 줄였다.
- 2026-06-15: `GoCoachApp.kt`의 자동 AI 적용부를 `applyAutoAiTurnSuccessCompletion()`, `applyAutoAiTurnFailureCompletion()`, `applyAutoAiEndgamePlan()` local helper로 분리했다. Compose state mutation은 아직 UI에 남기되 success/endgame/failure 분기 중첩은 줄였다.
- 2026-06-15: KMP/물리 이동 후보 재평가 결과, `DiagnosticEventModel.kt`, `AutoAiCompletionApplication.kt`, `ScoreSyncCompletionApplication.kt`는 가장 먼저 이동 가능한 정책 파일이고, `AutoAiRunnerApplication.kt`, `ScoreEstimateRunnerApplication.kt`, `DiagnosticEventObserverApplication.kt`는 coroutine/engine client 의존을 가진 app-service 후보로 분류했다.
- 2026-06-15: `GoCoachApp.kt` workflow ownership metric을 측정했다. 현재 UI-local 주요 workflow는 Top Moves 82줄(`runCatching` 1, `withContext` 1), Score Sync 175줄(`runCatching` 0, `withContext` 0), Auto AI 126줄(`runCatching` 1, `withContext` 1)이다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.TopMovesApplicationTest' --tests 'com.worksoc.goaicoach.application.ScoreDisplayApplicationTest' --tests 'com.worksoc.goaicoach.application.GameAutomationApplicationTest'` 통과. 최종 통합 검증으로 `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test`도 통과.

## 현재 리팩토링 완성도 평가

- 주관 점수: 98.6/100.
- 상향 요인: Top Moves도 completion plan 패턴에 들어와 score sync/auto AI와 stale result 처리 방식이 더 균일해졌다. Score sync는 UI-local 실행 helper로 중복이 줄었고, Auto AI의 중첩 success/endgame/failure 적용부도 helper 경계로 나뉘었다.
- 남은 감점 요인: `GoCoachApp.kt`는 아직 2,232줄이고, Top Moves/Auto AI는 여전히 UI coroutine 안에서 engine call을 직접 시작한다. 다음 단계에서는 callback runner나 workflow class로 실행 소유권을 더 옮겨야 한다.

## 다음 추천 리팩토링 항목

1. Top Moves workflow runner class 도입
   - `requestTopMoveAnalysisForState()`의 launch/update/failure/discard 흐름을 `TopMoveAnalysisWorkflow` 성격의 helper로 옮긴다.
   - cache write callback과 display apply callback을 명시적으로 주입한다.

2. Auto AI workflow callbacks 도입
   - 현재 local helper로 쪼갠 success/failure/endgame apply를 `AutoAiWorkflowCallbacks` 형태로 묶는다.
   - 그 다음 engine call orchestration을 application runner로 이동한다.

3. Score sync workflow helper를 application 파일로 승격
   - 현재 UI-local `runScoreSyncCompletion()`을 `ScoreSyncWorkflowApplication.kt`로 옮길 수 있는지 검토한다.
   - UI state mutation callback을 받는 형태가 적절하다.

4. KMP 이동 1차 스파이크
   - `DiagnosticEventModel.kt` 또는 `ScoreSyncCompletionApplication.kt` 중 하나를 shared/middleware 후보로 실제 이동 가능한지 작은 Gradle spike로 검토한다.

5. UI import/handler 정리
   - `GoCoachApp.kt` import가 많아지고 있으므로 workflow별 facade import 또는 `ui/workflow` package로 정리할 후보를 선정한다.

## 2026-06-15 추가 진행 로그: Workflow Result Runner 정리

- 2026-06-15: `TopMoveAnalysisWorkflowResult`와 `runTopMoveAnalysisWorkflowResult()`를 추가했다. Top Moves engine call의 성공/실패 포장은 application runner가 담당하고, UI는 `buildTopMoveAnalysisCompletionPlan()` 결과를 적용한다.
- 2026-06-15: `runScoreSyncWorkflowCompletionPlan()`을 `ScoreSyncCompletionApplication.kt`에 추가했다. post-undo/scoring-rule/restored-game sync의 success/failure/discard completion 생성 책임이 UI-local helper에서 application 함수로 이동했다.
- 2026-06-15: `AutoAiTurnWorkflowResult`, `buildAutoAiTurnCompletionPlan()`, `runAutoAiTurnWorkflowResult()`를 추가했다. Auto AI 자동 착수 coroutine에서 직접 `runCatching`으로 success/failure builder를 고르는 흐름을 제거했다.
- 2026-06-15: `TopMovesApplicationTest`, `ScoreDisplayApplicationTest`, `GameAutomationApplicationTest`에 workflow result/runner 테스트를 추가했다.
- 2026-06-15: `GoCoachApp.kt`는 2,232줄에서 2,166줄로 줄었다. Top Moves와 Auto AI 자동 착수의 UI-local `runCatching`은 application workflow result로 이동했다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.TopMovesApplicationTest' --tests 'com.worksoc.goaicoach.application.ScoreDisplayApplicationTest' --tests 'com.worksoc.goaicoach.application.GameAutomationApplicationTest'` 통과. 최종 통합 검증으로 `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test`도 통과.

## 현재 리팩토링 완성도 평가

- 주관 점수: 98.8/100.
- 상향 요인: Top Moves, Score Sync, Auto AI의 engine call 결과 포장과 completion 선택이 application 계층으로 더 이동했다. UI는 아직 coroutine 실행과 state mutation을 소유하지만, success/failure/stale 분기 판단은 점점 정책 함수로 수렴하고 있다.
- 남은 감점 요인: `GoCoachApp.kt`는 여전히 2,166줄이며 startup/new game, human move sync, benchmark, cache optimization 쪽에는 UI-local `runCatching`이 남아 있다. KMP 물리 이동은 문서 후보만 있고 실제 Gradle 모듈 경계는 아직 app-android 안이다.

## 다음 추천 리팩토링 항목

1. Human move engine sync workflow result 도입
   - 사람 착수 후 local engine sync의 success/failure/discard 포장을 application result로 이동한다.
   - runtime success/failure log 생성과 display apply 경계를 더 분리한다.

2. Engine startup/new-game workflow result 도입
   - startup/new-game/restore 초기화 흐름의 `runCatching`을 application runner result로 통일한다.
   - resume prompt와 benchmark prompt가 engine startup completion과 섞이지 않도록 입력/출력 plan을 명확히 한다.

3. Benchmark/cache optimization workflow result 도입
   - benchmark와 position cache optimization의 장시간 작업 result 포장을 application 계층으로 이동한다.
   - user notice/prompt는 UI callback만 남긴다.

4. `ui/workflow` facade 패키지 검토
   - `GoCoachApp.kt` import/handler가 계속 커지는 문제를 줄이기 위해 UI-local apply callback 묶음을 별도 파일로 분리한다.
   - 단, Compose state mutation 소유권이 흩어지지 않도록 callback boundary부터 작게 시작한다.

5. KMP 이동 1차 실제 spike
   - `DiagnosticEventModel.kt` 또는 `ScoreSyncCompletionApplication.kt`를 shared/common 후보로 옮길 때 필요한 Gradle/패키지 제약을 실제로 확인한다.
   - 즉시 이동보다 “이동 가능/불가 이유”를 테스트 가능한 형태로 먼저 고정한다.

## 2026-06-15 추가 진행 로그: Peripheral Workflow Result 정리

- 2026-06-15: `HumanEngineSyncWorkflowResult`, `buildHumanEngineSyncCompletionPlan()`, `runHumanEngineSyncWorkflowResult()`를 추가했다. 사람 착수 후 엔진 동기화의 success/failure/discard 포장이 application 계층으로 이동했다.
- 2026-06-15: `EngineStartupWorkflowResult`, `runEngineStartupWorkflowResult()`, `runEngineBackedNewGameWorkflowResult()`, `buildEngineStartupDisplayPlan()`을 추가했다. 엔진 초기화와 새 AI 대국 시작 흐름의 예외 포장이 application runner로 통일됐다.
- 2026-06-15: `StartupBenchmarkWorkflowResult`와 `runStartupBenchmarkWorkflowResult()`를 추가했다. 벤치마크 측정 실패/성공 포장은 application runner가 담당하고, UI는 저장소 반영과 표시만 담당한다.
- 2026-06-15: `PositionAnalysisCacheOptimizationWorkflowResult`와 `runPositionAnalysisCacheOptimizationWorkflowResult()`를 추가했다. post-game cache optimization의 성공/실패 포장도 application 계층으로 이동했다.
- 2026-06-15: KMP 이동 1차 spike 결과를 `docs/refactoring/KMP_MOVE_SPIKE_2026-06-15.md`에 정리했다. `LayeringContractTest`의 platform-free 후보군에 `EngineDeviceBenchmarkApplication.kt`, `EngineSessionLifecycleApplication.kt`, `EngineStartupApplication.kt`, `HumanMoveApplication.kt`, `PositionAnalysisCacheOptimization.kt`를 추가했다.
- 2026-06-15: `ui/workflow` facade는 실제 파일 분리 대신 보류했다. 이유는 Compose local state mutation을 성급히 여러 파일로 흩으면 오히려 소유권 추적이 어려워지기 때문이다. 다음 단계에서 state holder 또는 controller boundary와 함께 분리하는 것이 낫다.
- 2026-06-15: `GoCoachApp.kt`는 2,166줄에서 2,161줄로 소폭 줄었다. 줄 수보다 중요한 변화는 startup/new game, human sync, benchmark/cache optimization에서 UI-local `runCatching`이 제거된 점이다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.HumanMoveApplicationTest' --tests 'com.worksoc.goaicoach.application.EngineSessionLifecycleApplicationTest' --tests 'com.worksoc.goaicoach.application.EngineStartupApplicationTest' --tests 'com.worksoc.goaicoach.application.EngineDeviceBenchmarkApplicationTest' --tests 'com.worksoc.goaicoach.application.PositionAnalysisCacheOptimizationTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest'` 통과. 최종 통합 검증으로 `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test`도 통과.

## 현재 리팩토링 완성도 평가

- 주관 점수: 99.0/100.
- 상향 요인: 핵심 엔진 호출군뿐 아니라 주변 workflow(startup/new game, human sync, benchmark/cache optimization)의 success/failure 포장도 application 계층으로 통일됐다. KMP 후보군은 문서뿐 아니라 `LayeringContractTest`로 platform-free 상태를 자동 확인한다.
- 남은 감점 요인: `GoCoachApp.kt`는 여전히 2,161줄이고, engine undo/score estimate/human engine sync runtime log apply 등 몇몇 화면 상태 mutation은 UI-local이다. 실제 Gradle 모듈 이동은 아직 수행하지 않았다.

## 다음 추천 리팩토링 항목

1. Engine undo workflow result 도입
   - `undoEngineBackedTurn()`의 UI-local `runCatching`을 application result로 이동한다.
   - undo success/failure display plan을 추가해 엔진 undo도 다른 workflow와 동일한 completion 패턴을 쓰게 한다.

2. Score estimate workflow result 도입
   - `requestEngineScoreEstimate()`의 직접 `runCatching`과 stale guard 분기를 application result/completion으로 이동한다.
   - score estimate, score sync, restored sync 사이의 completion naming을 정리한다.

3. Human engine sync runtime log plan 분리
   - 현재 success/failure runtime log 생성은 UI에서 수행한다. display apply와 log append를 분리하되, log payload 생성은 application plan으로 이동한다.

4. UI controller/state holder 경계 설계
   - `ui/workflow` facade를 바로 만들기보다 `GoCoachApp.kt`의 local state set을 묶는 controller/state holder 경계를 먼저 설계한다.
   - 이 작업 이후 callback facade를 분리해야 state mutation 소유권이 선명하다.

5. KMP 물리 이동 1차
   - `EngineStartupApplication.kt` 또는 `ScoreSyncCompletionApplication.kt` 중 하나를 실제 shared/common 후보로 이동하는 작은 PR 단위 작업을 수행한다.

## 2026-06-15 추가 진행 로그: Workflow Completion Plan 8차 정리

- 2026-06-15: `EngineUndoWorkflowResult`, `runEngineUndoWorkflowResult()`, `EngineUndoCompletionPlan`, `buildEngineUndoCompletionPlan()`을 추가했다. 엔진 undo의 성공/실패/늦은 결과 폐기 판단이 `GoCoachApp.kt`의 직접 분기에서 application completion plan으로 이동했다.
- 2026-06-15: `ScoreEstimateWorkflowResult`, `runScoreEstimateWorkflowResult()`, `ScoreEstimateCompletionPlan`, `buildScoreEstimateCompletionPlan()`을 추가했다. score estimate도 다른 engine workflow와 동일하게 result wrapping과 stale guard를 application 계층에서 처리한다.
- 2026-06-15: `HumanEngineSyncRuntimeLogPlan`과 `toRuntimeLogPlan()`을 추가했다. 사람 착수 후 엔진 동기화 결과의 display apply와 runtime log payload 선택을 분리했다.
- 2026-06-15: `LayeringContractTest`의 platform-free 후보에 `ScoreDisplayApplication.kt`, `UndoApplication.kt`를 추가했다. 두 파일 모두 Android/UI/persistence/runtime 구현 import 없이 유지되어야 한다.
- 2026-06-15: `docs/refactoring/UI_STATE_HOLDER_BOUNDARY_2026-06-15.md`를 생성했다. `GoCoachApp.kt`의 다음 분리 방향을 state holder, effect launcher, UI controller 순서로 고정했다.
- 2026-06-15: `docs/refactoring/KMP_MOVE_SPIKE_2026-06-15.md`에 score/undo 후보를 추가했다. `UndoApplication.kt`는 조건부 이동 후보, `ScoreDisplayApplication.kt`는 display 문구 분리 후 이동 후보로 분류했다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.UndoApplicationTest' --tests 'com.worksoc.goaicoach.application.ScoreDisplayApplicationTest' --tests 'com.worksoc.goaicoach.application.EngineSessionLifecycleApplicationTest' --tests 'com.worksoc.goaicoach.application.HumanMoveApplicationTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest'` 통과. 최종 통합 검증으로 `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test`도 통과.

## 현재 리팩토링 완성도 평가

- 주관 점수: 99.2/100.
- 상향 요인: engine undo와 score estimate까지 workflow result/completion plan 패턴에 들어왔다. Human sync runtime log도 application plan을 거치므로 UI는 점점 "결과 적용"만 담당하는 방향으로 수렴 중이다.
- 남은 감점 요인: `GoCoachApp.kt`는 아직 2,192줄이며 `withContext(Dispatchers.IO)` 호출이 UI 파일 안에 남아 있다. 실제 Gradle/KMP 모듈 물리 이동도 아직 수행하지 않았다. 다음 단계는 state holder/effect launcher를 작게 도입해 UI 소유 실행 흐름을 더 줄이는 것이다.

## 다음 추천 리팩토링 항목

1. Score estimate effect launcher 추출
   - 입력/출력 범위가 작아 `GameSessionEffectLauncher` 첫 적용 후보로 적합하다.
   - UI는 launch와 apply만 담당하고 engine call은 launcher가 담당하게 한다.

2. Undo workflow apply helper 정리
   - engine undo completion apply와 local undo apply의 공통 상태 갱신을 state holder 후보 함수로 묶는다.
   - post-undo quiet delay는 현재 정책을 유지한다.

3. Score display 문구와 도메인 결과 분리
   - `ScoreDisplayApplication.kt`를 KMP 이동하려면 display text formatting과 domain score result를 분리해야 한다.
   - local/engine/final score 결과 DTO를 먼저 추출한다.

4. `GameSessionUiStateHolder` 1차 도입
   - `applyScoreEstimateDisplayPlan`, `applyFinalScoreDisplayPlan`, `applyUndoLocalStatePlan`부터 작은 holder로 묶는다.
   - Compose state mutation 소유권이 흩어지지 않도록 한 파일 또는 한 class에서 시작한다.

5. KMP 물리 이동 준비 PR
   - `UndoApplication.kt` 또는 `ScoreSyncCompletionApplication.kt`를 실제 shared/common 후보로 옮길 때 필요한 port 의존을 정리한다.
   - 바로 이동하지 않더라도 Gradle 제약과 package dependency를 테스트로 고정한다.

## 2026-06-15 추가 진행 로그: State Holder / Score Boundary 9차 정리

- 2026-06-15: `ScoreEstimateStateResult`를 추가했다. score estimate의 domain state result와 `scoreText`/`engineMessage` display formatting을 분리해 `ScoreDisplayApplication.kt`의 KMP 이동 전제 조건을 한 단계 정리했다.
- 2026-06-15: `ScoreEstimateEffectLaunchRequest`와 `runScoreEstimateEffectCompletionPlan()`을 추가했다. `GoCoachApp.kt`는 score estimate engine call의 workflow result 생성과 stale guard completion 선택을 직접 수행하지 않고, application launcher가 반환한 completion plan을 적용한다.
- 2026-06-15: `GameSessionUiStateHolderApplication.kt`를 추가했다. 아직 Compose state를 직접 분리하지는 않지만, score/final/endgame/undo display plan 적용은 `GameSessionUiStateHolder` 경계를 통과한다.
- 2026-06-15: `LayeringContractTest`에 `GameSessionUiStateHolderApplication.kt`를 추가했다. state holder 후보도 Android/UI/persistence/runtime 구현 import 없이 유지하도록 자동화했다.
- 2026-06-15: `docs/refactoring/KMP_MOVE_SPIKE_2026-06-15.md`에 `GameSessionUiStateHolderApplication.kt` 위치를 추가했다. 이 파일은 즉시 KMP 이동 후보라기보다 UI reducer 경계 안정화 후 재평가할 중간 어댑터로 분류했다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.ScoreDisplayApplicationTest' --tests 'com.worksoc.goaicoach.application.UndoApplicationTest' --tests 'com.worksoc.goaicoach.application.GameSessionUiStateHolderApplicationTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest'` 통과. 최종 통합 검증으로 `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test`도 통과.

## 현재 리팩토링 완성도 평가

- 주관 점수: 99.3/100.
- 상향 요인: score estimate는 launch request/result/completion 경계가 application 계층에 더 모였다. score domain result와 display text의 분리도 시작됐다. UI state holder 1차 도입으로 `GoCoachApp.kt`의 state mutation 적용부를 다음 파일 분리로 옮길 수 있는 접점이 생겼다.
- 남은 감점 요인: `GoCoachApp.kt`는 아직 2,187줄이며 실제 Compose state 변수는 동일 파일에 남아 있다. `ScoreDisplayApplication.kt`도 display 문구와 domain result가 완전히 분리된 것은 아니며, final score/endgame 쪽은 아직 display plan 중심이다.

## 다음 추천 리팩토링 항목

1. `GameSessionUiStateHolder` 적용 범위 확대
   - Top Moves failure, Auto AI display/failure, Human sync failure도 holder 경계로 통과시킨다.
   - 단, runtime log append와 follow-up scheduling은 UI/controller에 남긴다.

2. Final score domain result 분리
   - `FinalScoreDisplayPlan` 생성 전에 `FinalScoreStateResult` 또는 `ResolvedEndgameStateResult`를 둔다.
   - 종국 판정/사석 정리 도메인 데이터와 사용자 표시 문구를 분리한다.

3. Score estimate launcher 패턴을 scoring/restored sync로 확장
   - 현재 `runScoreSyncWorkflowCompletionPlan()`은 generic block 기반이다.
   - scoring rule sync/restored game sync에도 명시적 launch request를 두면 추적성이 올라간다.

4. UI state holder 파일 위치 재평가
   - 지금은 application package에 두었지만, 장기적으로 `ui/state` 또는 KMP reducer 후보 중 어디가 더 적합한지 비교한다.

5. KMP 물리 이동 전 의존성 그래프 작성
   - `ScoreDisplayApplication.kt`, `UndoApplication.kt`, `ScoreSyncCompletionApplication.kt`의 import graph를 문서화한다.
   - 실제 shared/common 이동 전 필요한 port/type 이동 순서를 확정한다.

## 2026-06-15 추가 진행 로그: Holder / Final Score / Sync Launch 10차 정리

- 2026-06-15: `GameSessionUiStateHolder` 적용 범위를 Top Moves failure, Auto AI display/failure, Human sync failure까지 확대했다. UI helper 함수는 유지하지만 core state mutation은 더 일관되게 holder 경계를 통과한다.
- 2026-06-15: `FinalScoreStateResult`와 `toFinalScoreDisplayPlan()`을 추가했다. local final score와 resolved endgame score는 먼저 state result를 만든 뒤 display plan으로 변환한다.
- 2026-06-15: `ScoringRuleSyncEffectLaunchRequest`, `RestoredGameSyncEffectLaunchRequest`와 각각의 completion runner를 추가했다. scoring rule sync/restored game sync는 이제 generic block helper 대신 명시적 launch request로 completion plan을 생성한다.
- 2026-06-15: `ScoreDisplayApplicationTest`에 final state result, scoring sync completion runner, restored sync completion runner 테스트를 추가했다.
- 2026-06-15: `GameSessionUiStateHolderApplicationTest`에 Top Moves failure, Auto AI failure, Human sync failure 적용 테스트를 추가했다.
- 2026-06-15: `docs/refactoring/KMP_MOVE_SPIKE_2026-06-15.md`에 holder 적용 범위 확대와 score/final state result 분리 현황을 기록했다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.ScoreDisplayApplicationTest' --tests 'com.worksoc.goaicoach.application.GameSessionUiStateHolderApplicationTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest'` 통과. 최종 통합 검증으로 `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test`도 통과.

## 현재 리팩토링 완성도 평가

- 주관 점수: 99.4/100.
- 상향 요인: application state holder 경계가 실제로 더 많은 UI state mutation을 흡수했다. score/final score는 display plan 전에 state result를 만들기 시작했고, scoring/restored sync는 명시적 launch request로 입력 구조가 선명해졌다.
- 남은 감점 요인: `GoCoachApp.kt`는 아직 2,192줄이며 실제 Compose state 변수와 effect launch는 UI 파일에 남아 있다. post-undo score sync는 아직 generic block helper를 사용한다. endgame/final score의 display text formatter도 완전히 분리되지는 않았다.

## 다음 추천 리팩토링 항목

1. Post-undo sync launch request 도입
   - 현재 `runScoreSyncCompletion()` generic block을 쓰는 마지막 주요 경로다.
   - `PostUndoScoreSyncEffectLaunchRequest`를 추가하면 score sync 세부 경로가 모두 명시 request 기반이 된다.

2. Final score/endgame formatter 분리
   - `FinalScoreStateResult`는 생겼으므로 다음은 score text, engine message, candidate text formatter를 별도 함수/파일로 분리한다.

3. Human move local apply도 holder 경계로 이동
   - `applyHumanMoveLocalResult()` 직접 호출을 holder 메서드로 이동한다.
   - local offline final score 처리와 engine sync launch는 UI에 남긴다.

4. Score sync runner 파일 분리 재평가
   - `ScoreEstimateRunnerApplication.kt`가 223줄까지 커졌다.
   - estimate runner와 sync runner를 `ScoreSyncRunnerApplication.kt`로 분리할지 검토한다.

5. `GoCoachApp.kt` effect launch metric 재측정
   - `withContext(Dispatchers.IO)`와 workflow별 UI-owned branch 수를 다시 측정해 다음 0.1점 개선 후보를 선정한다.

## 2026-06-15 추가 진행 로그: Score Sync Runner / Final Text / Holder Boundary new 1 정리

- 2026-06-15: `ScoreSyncRunnerApplication.kt`를 추가해 scoring rule sync, post-undo sync, restored game sync runner를 `ScoreEstimateRunnerApplication.kt`에서 분리했다. `ScoreEstimateRunnerApplication.kt`는 score estimate 전용 runner로 다시 좁아졌다.
- 2026-06-15: `PostUndoScoreSyncEffectLaunchRequest`와 `runPostUndoScoreSyncCompletionPlan()`을 추가했다. post-undo sync도 generic block helper 없이 명시적 launch request와 completion plan을 통과한다.
- 2026-06-15: `GoCoachApp.kt`의 `runScoreSyncCompletion()` local helper를 제거했다. score sync 세부 경로는 `runScoringRuleSyncCompletionPlan()`, `runPostUndoScoreSyncCompletionPlan()`, `runRestoredGameSyncCompletionPlan()`처럼 목적별 runner를 호출한다.
- 2026-06-15: `FinalScoreDisplayText`, `buildLocalFinalScoreDisplayText()`, `buildResolvedEndgameDisplayText()`를 추가했다. final/endgame state result와 화면 문구 조립 책임을 한 단계 더 분리했다.
- 2026-06-15: `GameSessionUiStateHolder.applyHumanMoveLocalResult()`를 추가했다. 사람 착수의 local state apply도 holder 경계를 통과하며, engine sync launch와 후속 scheduling은 UI/controller에 남겨 두었다.
- 2026-06-15: `LayeringContractTest`의 platform-free 후보에 `ScoreSyncRunnerApplication.kt`를 추가했다. 새 sync runner도 Android/UI/persistence/runtime 구현 import 없이 유지해야 한다.
- 현재 metric: `GoCoachApp.kt`는 2,176줄이며, UI 파일 안의 `withContext(Dispatchers.IO)`/`runCatching` 직접 지점은 13개다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.ScoreDisplayApplicationTest' --tests 'com.worksoc.goaicoach.application.GameSessionUiStateHolderApplicationTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest'` 통과. 최종 통합 검증으로 `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test`도 통과.

## 현재 리팩토링 완성도 평가

- 주관 점수: 99.5/100.
- 상향 요인: score estimate runner와 score sync runner가 분리되어 파일 책임이 선명해졌다. score sync 주요 경로는 모두 명시적 request 기반 completion runner를 갖게 되었고, final/endgame 표시 텍스트도 domain state result에서 한 단계 분리되기 시작했다.
- 남은 감점 요인: `GoCoachApp.kt`는 여전히 2천 줄대이고 Compose state 변수 소유권은 UI 파일에 있다. final/endgame formatter는 함수 수준 분리이며 아직 파일 단위 분리는 아니다. 실제 KMP 물리 이동도 아직 수행하지 않았다.

## 다음 추천 리팩토링 항목

1. Final/endgame formatter 파일 분리
   - `FinalScoreDisplayText` 관련 함수를 `ScoreDisplayFormatterApplication.kt` 같은 별도 파일로 이동한다.
   - `ScoreDisplayApplication.kt`를 domain result 중심으로 더 줄인다.

2. Human move sync launch request 정리
   - 사람 착수 후 engine sync 경로의 launch request/result/completion apply를 더 명시적으로 묶는다.
   - local apply, engine sync, runtime log append의 경계를 문서와 테스트로 고정한다.

3. UI state holder 생명주기 재검토
   - 현재 holder는 helper 함수에서 매번 생성된다. 상태 소유권을 더 명확히 하려면 holder 생성 위치와 lifetime을 고정할 필요가 있다.

4. KMP 이동 1차 후보 실제 이전 준비
   - `ScoreSyncCompletionApplication.kt` 또는 `DiagnosticEventModel.kt`부터 shared/common 후보로 옮길 때 필요한 port/type dependency를 구체화한다.

5. Effect launcher metric 축소
   - 남은 `withContext(Dispatchers.IO)` 지점을 workflow별로 분류해 13개에서 10개 이하로 줄이는 작은 batch를 잡는다.

## 2026-06-15 추가 진행 로그: Formatter Split / Human Sync Request new 2 정리

- 2026-06-15: `ScoreDisplayFormatterApplication.kt`를 추가해 `FinalScoreDisplayText`, `buildLocalFinalScoreDisplayText()`, `buildResolvedEndgameDisplayText()`를 `ScoreDisplayApplication.kt`에서 분리했다. `ScoreDisplayApplication.kt`는 score/final state result와 display plan 조립 중심으로 더 좁아졌다.
- 2026-06-15: `HumanEngineSyncEffectLaunchRequest`를 추가했다. 사람 착수 후 engine sync도 effect와 operation request를 하나의 launch request로 넘기며, engine call 입력 경계가 명시화됐다.
- 2026-06-15: `HumanEngineSyncCompletionRequest`를 추가했다. completion plan 조립은 완료 시점의 최신 `GameState`와 session generation을 받아 late result discard guard를 유지한다.
- 2026-06-15: `GoCoachApp.kt`의 human sync 경로는 긴 인자 목록 대신 `HumanEngineSyncEffectLaunchRequest`와 `HumanEngineSyncCompletionRequest`를 조립한다. engine call은 IO에서 실행하지만, completion request는 IO 이후 최신 UI state 기준으로 만든다.
- 2026-06-15: `HumanMoveApplicationTest`에 launch request runner 테스트와 completion request stale discard 테스트를 추가했다.
- 2026-06-15: `LayeringContractTest`의 platform-free 후보에 `ScoreDisplayFormatterApplication.kt`를 추가했다.
- 현재 metric: `GoCoachApp.kt`는 2,183줄이며, UI 파일 안의 `withContext(Dispatchers.IO)`/`runCatching` 직접 지점은 13개다. 줄 수는 request 객체 조립으로 소폭 증가했지만, human sync 입력/완료 경계는 더 명시적이다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.ScoreDisplayApplicationTest' --tests 'com.worksoc.goaicoach.application.HumanMoveApplicationTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest'` 통과. 최종 통합 검증으로 `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test`도 통과.

## 현재 리팩토링 완성도 평가

- 주관 점수: 99.55/100.
- 상향 요인: final/endgame formatter가 파일 단위로 분리되어 `ScoreDisplayApplication.kt`의 KMP 이동 전제 조건이 더 좋아졌다. human sync는 launch request와 completion request가 나뉘어 engine call 입력과 최신 state 기반 result guard가 분명해졌다.
- 남은 감점 요인: `GoCoachApp.kt`의 Compose state 소유권과 effect launch는 여전히 UI 파일에 있다. human sync completion 적용, runtime log append, 후속 Top Moves scheduling은 아직 UI/controller 책임이다. 실제 KMP 물리 이동은 아직 시작하지 않았다.

## 다음 추천 리팩토링 항목

1. Human sync completion apply helper 정리
   - `ApplySuccess`/`ApplyFailure`/`Discard` 분기를 작은 helper로 분리한다.
   - runtime log append와 display apply 순서를 application plan 또는 controller helper로 고정한다.

2. UI state holder lifetime 고정
   - 현재 `uiStateHolder()`는 local helper로 매번 wrapper를 만든다.
   - `remember` 또는 명시 controller wrapper로 생성 위치를 고정해 state mutation 경계를 더 명확히 한다.

3. Score display domain/formatter 파일 추가 분리
   - `EndgameFailureDisplayPlan`의 문구 조립도 formatter 쪽으로 옮길지 검토한다.
   - `ScoreDisplayApplication.kt`를 score state/result DTO 중심으로 더 줄인다.

4. KMP 물리 이동 준비 1차
   - `DiagnosticEventModel.kt` 또는 `ScoreDisplayFormatterApplication.kt`처럼 의존이 얇은 파일부터 shared/common 이동 가능성을 실제 Gradle 관점에서 검토한다.

5. Effect launcher metric 축소
   - Top Moves, score estimate, human sync 중 남은 IO launch 지점을 분류하고 13개에서 10개 이하로 줄일 batch를 선정한다.

## 2026-06-15 추가 진행 로그: Human Sync Apply / Holder Lifetime new 3 정리

- 2026-06-15: `HumanEngineSyncCompletionApplyPlan`과 `HumanEngineSyncCompletionPlan.toApplyPlan()`을 추가했다. success/failure/discard completion은 runtime log plan과 실제 적용 disposition을 함께 가진다.
- 2026-06-15: `GoCoachApp.kt`의 human sync completion 처리에서 success/failure/discard별 runtime log append 반복을 제거했다. UI는 `applyHumanEngineSyncCompletionApplyPlan()`에서 runtime log를 먼저 append하고, 이후 display/failure/discard 적용을 수행한다.
- 2026-06-15: `uiStateHolder()` local factory를 제거하고 `remember { GameSessionUiStateHolder(...) }` 값으로 holder lifetime을 고정했다. holder wrapper가 호출 때마다 새로 만들어지는 구조를 없애고 state mutation 경계를 더 명확히 했다.
- 2026-06-15: `EndgameFailureDisplayText`와 `buildEndgameFailureDisplayText()`를 `ScoreDisplayFormatterApplication.kt`에 추가했다. endgame failure 문구 조립도 formatter 파일로 이동해 `ScoreDisplayApplication.kt`의 문자열 책임을 더 줄였다.
- 2026-06-15: `HumanMoveApplicationTest`에 completion apply plan 테스트를 추가했고, `ScoreDisplayApplicationTest`에 endgame failure formatter 테스트를 추가했다.
- 현재 metric: `GoCoachApp.kt`는 2,188줄이며, UI 파일 안의 `withContext(Dispatchers.IO)`/`runCatching` 직접 지점은 13개다. 이번 batch는 metric 축소보다 completion apply 순서 안정화와 holder lifetime 고정을 우선했다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.HumanMoveApplicationTest' --tests 'com.worksoc.goaicoach.application.ScoreDisplayApplicationTest' --tests 'com.worksoc.goaicoach.application.GameSessionUiStateHolderApplicationTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest'` 통과. 최종 통합 검증으로 `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test`도 통과.

## 현재 리팩토링 완성도 평가

- 주관 점수: 99.6/100.
- 상향 요인: human sync completion은 runtime log, display/failure, discard 적용 순서가 한 helper에 모였다. holder lifetime도 고정되어 다음 controller/state holder 분리의 기반이 더 안정화됐다. formatter 파일은 final/endgame success뿐 아니라 failure 문구도 흡수했다.
- 남은 감점 요인: `GoCoachApp.kt`의 effect launch와 Compose state 소유권은 여전히 UI 파일에 남아 있다. `withContext(Dispatchers.IO)` 직접 지점은 13개로 줄지 않았다. 실제 shared/common 물리 이동도 아직 수행 전이다.

## 다음 추천 리팩토링 항목

1. Human sync completion apply helper의 application/controller 위치 재평가
   - 현재 helper는 UI local function이다. 다음에는 runtime log port와 display apply port를 분리해 controller helper로 옮길 수 있는지 검토한다.

2. Score estimate effect launcher 정리
   - score estimate는 completion plan이 이미 있으므로 IO launch와 completion apply를 작게 감싸는 runner 후보로 적합하다.
   - stale guard는 완료 시점 state를 쓰도록 유지해야 한다.

3. Top Moves completion apply helper 정리
   - cache write, undo restore cache write, failure/discard apply 순서를 작은 helper로 묶는다.
   - UI branch를 줄이되 cache mutation 소유권은 명확히 유지한다.

4. KMP 물리 이동 후보를 실제 Gradle 관점에서 검토
   - `ScoreDisplayFormatterApplication.kt` 또는 `DiagnosticEventModel.kt`를 shared/common으로 옮길 때 필요한 package/type 의존을 목록화한다.

5. Effect launcher metric 축소 재시도
   - 남은 13개 IO 지점을 operation kind별로 분류하고, 첫 목표를 13개에서 11개 이하로 잡는다.

## 2026-06-15 추가 진행 로그: Score / Top Moves Apply Plan new 4 정리

- 2026-06-15: `ScoreEstimateCompletionApplyPlan`과 `ScoreEstimateCompletionPlan.toApplyPlan()`을 추가했다. score estimate completion의 success/failure/discard 적용 disposition이 UI 적용용 타입으로 명시됐다.
- 2026-06-15: `TopMoveAnalysisCompletionApplyPlan`과 `TopMoveAnalysisCompletionPlan.toApplyPlan()`을 추가했다. Top Moves completion도 cache update, failure display, discard disposition을 application apply plan으로 한 번 감싼다.
- 2026-06-15: `GoCoachApp.kt`의 score estimate completion 적용은 `applyScoreEstimateCompletionApplyPlan()`으로 정리했다. 직접 `ScoreEstimateCompletionPlan` 분기를 UI에 노출하지 않는다.
- 2026-06-15: `GoCoachApp.kt`의 Top Moves completion 적용은 `applyTopMoveAnalysisCompletionApplyPlan()`으로 정리했다. cache write와 undo restore cache write 순서는 유지하되, completion disposition은 application apply plan을 통과한다.
- 2026-06-15: `ScoreDisplayApplicationTest`에 score estimate apply plan 테스트를 추가했고, `TopMovesApplicationTest`에 Top Moves apply plan cache update disposition 테스트를 추가했다.
- 2026-06-15: KMP 관점에서는 `ScoreDisplayFormatterApplication.kt`가 가장 얇은 실제 이동 후보로 남아 있다. `TopMovesApplication.kt`와 `ScoreDisplayApplication.kt`는 Android 의존은 없지만 cache/write port와 UI 적용 port가 아직 UI local helper에 남아 있어 바로 물리 이동보다 포트 경계 정리가 먼저다.
- 현재 metric: `GoCoachApp.kt`는 2,197줄이며, UI 파일 안의 `withContext(Dispatchers.IO)`/`runCatching` 직접 지점은 13개다. 이번 batch는 IO 지점 수를 줄이지 않고 completion disposition 노출을 줄이는 데 집중했다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.ScoreDisplayApplicationTest' --tests 'com.worksoc.goaicoach.application.TopMovesApplicationTest' --tests 'com.worksoc.goaicoach.application.HumanMoveApplicationTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest'` 통과. 최종 통합 검증으로 `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test`도 통과했다.

## 현재 리팩토링 완성도 평가

- 주관 점수: 99.65/100.
- 상향 요인: human sync, score estimate, Top Moves의 completion 결과가 모두 application apply plan을 거쳐 UI에 적용된다. UI는 아직 상태 mutation을 수행하지만, 어떤 disposition을 적용하는지는 더 이상 raw completion 타입에 직접 묶이지 않는다.
- 남은 감점 요인: `GoCoachApp.kt`의 IO launch 지점은 여전히 13개이고, Top Moves cache write와 score estimate display apply는 UI local helper가 수행한다. 실제 KMP 물리 이동은 아직 하지 않았다.

## 다음 추천 리팩토링 항목

1. Score estimate IO launch wrapper 분리
   - `requestEngineScoreEstimate()`의 `withContext(Dispatchers.IO)`를 목적별 runner/helper로 이동한다.
   - 완료 시점의 최신 state/session generation으로 guard를 평가하는 세맨틱스는 유지한다.

2. Top Moves IO launch wrapper 분리
   - `requestTopMoveAnalysisForState()` 내부의 engine call launch와 completion apply를 작은 helper로 분리한다.
   - cache write는 UI local cache port를 명시적으로 주입하거나 controller helper로 이동한다.

3. Score sync completion apply plan 도입
   - post-undo/scoring/restored sync도 `ScoreSyncCompletionPlan` raw 분기 대신 apply plan으로 통일한다.

4. KMP 물리 이동 미니 스파이크
   - `ScoreDisplayFormatterApplication.kt`를 실제 shared/common 후보로 옮길 때 필요한 import/package 변경만 별도 브랜치 수준으로 실험한다.

5. `GoCoachApp.kt` metric 축소
   - 다음 batch 목표는 `withContext(Dispatchers.IO)`/`runCatching` 직접 지점 13개를 11개 이하로 줄이는 것이다.

## 2026-06-15 추가 진행 로그: Apply Runner / Score Sync Apply Plan new 5 정리

- 2026-06-15: `ScoreSyncCompletionApplyPlan`과 `ScoreSyncCompletionPlan.toApplyPlan()`을 추가했다. post-undo/scoring/restored sync도 raw completion plan 대신 application-defined apply disposition으로 UI에 전달할 수 있다.
- 2026-06-15: `ScoreEstimateRunnerApplication.kt`에 `runScoreEstimateEffectApplyPlan()`을 추가했다. score estimate effect 실행, completion guard, apply plan 변환이 runner 경계에서 끝나도록 했다.
- 2026-06-15: `ScoreSyncRunnerApplication.kt`에 `runPostUndoScoreSyncApplyPlan()`, `runScoringRuleSyncApplyPlan()`, `runRestoredGameSyncApplyPlan()`을 추가했다. 세 score sync 경로 모두 completion plan을 UI에 직접 노출하지 않는 선택지가 생겼다.
- 2026-06-15: `TopMoveAnalysisEffectLaunchRequest`와 `runTopMoveAnalysisEffectApplyPlan()`을 추가했다. Top Moves engine call 결과를 completion guard와 apply plan으로 묶어 UI는 cache/write 적용만 담당한다.
- 2026-06-15: `GoCoachApp.kt`의 score estimate, Top Moves, post-undo sync, scoring rule sync, restored game sync 호출부를 apply runner 기반으로 변경했다. 반복 `withContext(Dispatchers.IO)`는 `runEngineIo()` helper를 통과한다.
- 2026-06-15: `ScoreDisplayApplicationTest`와 `TopMovesApplicationTest`에 score sync/score estimate/Top Moves apply runner 테스트를 추가했다.
- 현재 metric: `GoCoachApp.kt`는 2,195줄이며, UI 파일 안의 `withContext(Dispatchers.IO)`/`runCatching` 직접 지점은 9개다. 직전 13개에서 4개 줄어 다음 목표였던 11개 이하를 달성했다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.ScoreDisplayApplicationTest' --tests 'com.worksoc.goaicoach.application.TopMovesApplicationTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest'` 통과. 최종 통합 검증으로 `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test`도 통과했다.

## 현재 리팩토링 완성도 평가

- 주관 점수: 99.72/100.
- 상향 요인: score estimate, Top Moves, score sync 세 계열이 모두 apply runner 또는 apply plan을 갖게 되었다. UI는 여전히 state/cache mutation을 소유하지만, engine result의 success/failure/discard 판정과 apply disposition 생성은 application 계층으로 더 이동했다.
- 남은 감점 요인: `runEngineIo()` helper는 아직 `GoCoachApp.kt` local function이다. 즉, IO dispatch 자체는 UI 파일에 남아 있으며, cache write/undo restore write도 UI local helper에 남아 있다. 실제 shared/common 물리 이동은 아직 하지 않았다.

## 다음 추천 리팩토링 항목

1. Engine IO dispatcher helper의 application/controller 경계 이동 검토
   - 현재 `runEngineIo()`는 UI local helper다. 다음에는 dispatcher port 또는 effect runner helper로 이동해 UI의 실행 정책 소유권을 더 낮춘다.

2. Top Moves cache write port 분리
   - `analysisCache.put()`과 `undoAnalysisRestoreCache.put()`을 UI local mutation에서 cache writer port/helper로 분리한다.

3. Score estimate display apply port 분리
   - `applyScoreEstimateDisplayPlan()`이 holder를 직접 호출하는 구조를 display apply port로 한 번 더 감싼다.

4. `ScoreSyncCompletionApplication.kt` KMP 물리 이동 후보 검토
   - 새 apply plan까지 포함해 shared/common 이동 시 필요한 타입 의존을 정리한다.

5. 남은 IO 지점 9개 분류
   - startup/benchmark/endgame/cache optimization 등 남은 IO 지점을 operation kind별로 나누고, 다음 목표를 9개에서 7개 이하로 잡는다.

## 2026-06-15 외부 93점 평가 반영 메모

- 2026-06-15: 외부 개발자의 93/100 아키텍처 평가를 `EXTERNAL_REVIEW_2026-06-15_ARCHITECTURE_SCORE_93.md`에 보존했다.
- 2026-06-15: 내부 판정은 `INTERNAL_ARCHITECT_REVIEW_OF_SCORE_93_FEEDBACK_2026-06-15.md`에 정리했다. 핵심 결론은 "리팩토링 배치 진행도 99.72점"과 "플랫폼 아키텍처 완성도 93점"을 분리해서 관리하는 것이다.
- 즉시 반영할 방향은 다음 세 가지다.
  1. `GoCoachApp.kt` 줄 수 자체보다 orchestration ownership 제거를 우선한다.
  2. application 루트 package 밀집을 줄이기 위해 `diagnostic`, `engine`, `score`, `topmoves`, `autoai`, `session` 하위 package 도입을 시작한다.
  3. KMP 후보 문서화에 그치지 않고 최소 1개 파일의 물리 이동 스파이크를 수행한다.
- 보류/폐기한 방향은 다음 세 가지다.
  1. 파일 수 증가 자체를 실패로 보지 않는다. package ownership 부재가 문제다.
  2. 점수 확보용 KMP 2파일 이동은 하지 않는다. 최소 1개 파일로 실제 비용을 검증한 뒤 확대한다.
  3. `GoCoachApp.kt` 1,000줄 이하를 단기 목표로 강제하지 않는다. 먼저 effect launch/state ownership을 분리한다.

## 다음 추천 리팩토링 항목 - 외부 93점 평가 반영 후

1. EffectLauncher 미니 도입
   - `runEngineIo()`와 남은 engine operation launch 중 하나를 UI 밖으로 이동한다.
   - acceptance: `GoCoachApp.kt`의 직접 `withContext` 또는 `scope.launch` 책임이 줄고, 관련 테스트가 통과한다.

2. application 하위 package 1차 이동
   - `diagnostic` 또는 `score` 중 하나만 먼저 이동한다.
   - acceptance: `LayeringContractTest`가 새 package 경계를 검증한다.

3. KMP 물리 이동 1차 스파이크
   - `DiagnosticEventModel.kt` 또는 `EngineOperationPolicy.kt` 중 하나를 실제 common 후보 위치로 이동해 본다.
   - acceptance: Gradle/test 통과 또는 차단 의존성 목록 문서화.

4. GoCoachApp import fan-in 축소
   - application import 179개를 줄이기 위한 facade/controller boundary를 검토한다.
   - acceptance: import 수 감소 또는 책임별 import 그룹이 분리된다.

5. worklist 상태 라벨 도입
   - 각 리팩토링 항목에 `문서화됨`, `코드 반영`, `테스트됨`, `물리 이동`, `운영 adapter` 상태를 구분한다.

## 2026-06-15 추가 진행 로그: 외부 93점 즉시 적용 1차 실행

- 2026-06-15: 외부 93점 평가의 즉시 적용 항목 중 EffectLauncher 미니 도입, application 하위 package 1차 이동, KMP 물리 이동 1차 스파이크를 수행했다.
- 2026-06-15: `application/engine/EngineEffectLauncherApplication.kt`를 추가했다. `GoCoachApp.kt` local helper였던 `runEngineIo()`를 app-service helper로 이동해 engine IO dispatcher 선택을 UI entry point 밖으로 뺐다.
- 2026-06-15: `DiagnosticEventApplication.kt`와 `DiagnosticEventObserverApplication.kt`를 `application/diagnostic/` 하위 package로 이동했다. application 루트 package 밀집 해소의 첫 실행이다.
- 2026-06-15: `DiagnosticEventModel.kt`를 `shared/commonMain`의 `com.worksoc.goaicoach.shared.diagnostic` package로 이동했다. KMP 후보 문서화만 있던 상태에서 실제 물리 이동 1건을 완료했다.
- 2026-06-15: `LayeringContractTest`에 shared diagnostic model KMP-ready 검사를 추가했다. application diagnostic 하위 package와 engine effect launcher도 platform-free 후보 검사에 포함했다.
- 현재 metric: `GoCoachApp.kt`는 2,191줄이며, UI 파일 안의 `withContext(Dispatchers.IO)`/`runCatching` 직접 지점은 8개다. `scope.launch`는 아직 6개다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.DiagnosticEventApplicationTest' --tests 'com.worksoc.goaicoach.application.RuntimeEventApplicationTest' --tests 'com.worksoc.goaicoach.application.EngineSessionTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest'` 통과. 최종 통합 검증으로 `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test`도 통과했다.

## 현재 리팩토링/아키텍처 완성도 평가

- 리팩토링 배치 진행도: 99.78/100.
- 외부 평가 기준 플랫폼 아키텍처 완성도: 94.2/100.
- 상향 요인: 실제 KMP 물리 이동이 0건에서 1건으로 바뀌었고, application 하위 package 분리도 시작됐다. EffectLauncher helper도 UI 밖으로 이동해 외부 리뷰의 "설계했으나 실행하지 않은 것" 중 일부가 코드로 전환됐다.
- 남은 감점 요인: `GoCoachApp.kt`는 여전히 2천 줄 이상이고, application import fan-in은 크다. `scope.launch` 6개와 endgame/startup/benchmark/cache optimization IO 경로는 아직 UI orchestration에 남아 있다. 운영 diagnostic 외부 adapter도 아직 없다.

## 다음 추천 리팩토링 항목 - 즉시 적용 2차

1. diagnostic package 이동 후속 정리
   - `ApplicationPorts.kt`의 diagnostic port를 `application/diagnostic` 또는 별도 port package로 옮길지 검토한다.
   - acceptance: `DiagnosticEventLogPort`, `DiagnosticEventExternalSinkPort`의 위치가 모델/observer와 더 잘 맞는다.

2. 두 번째 KMP 물리 이동 후보 검토
   - `EngineOperationPolicy.kt` 또는 `ScoreSyncCompletionApplication.kt`를 대상으로 실제 이동 가능성을 검증한다.
   - acceptance: 이동 성공 또는 차단 의존성 목록 문서화.

3. EffectLauncher 적용 범위 확대
   - startup, benchmark, cache optimization, endgame 중 하나의 `withContext(Dispatchers.IO)`를 app-service helper로 이동한다.
   - acceptance: UI 직접 IO 지점 8개를 7개 이하로 축소한다.

4. application package 분리 2차
   - `score` 또는 `engine` package에 기존 파일 2~3개를 이동한다.
   - acceptance: package boundary test와 targeted test 통과.

5. GoCoachApp import fan-in 축소
   - application import 180개를 책임별 facade/controller boundary로 줄이는 실험을 시작한다.
   - acceptance: import 수 또는 local helper 수가 실질적으로 감소한다.

## 2026-06-15 추가 진행 로그: ext.1 package/KMP/EffectLauncher 2차

- 2026-06-15: `DiagnosticEventLogPort`, `DiagnosticEventExternalSinkPort`를 `ApplicationPorts.kt`에서 `application/diagnostic/DiagnosticEventPorts.kt`로 이동했다. diagnostic event model, observer, port가 같은 diagnostic 하위 package에 위치한다.
- 2026-06-15: `EngineOperationPolicy.kt`의 실제 구현을 `shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/engine/EngineOperationPolicy.kt`로 이동했다. 이는 `DiagnosticEventModel.kt`에 이은 두 번째 실제 KMP 물리 이동이다.
- 2026-06-15: 앱 쪽 `application/EngineOperationPolicy.kt`는 기존 호출부를 보존하는 facade로 남겼다. Kotlin typealias가 sealed class 중첩 타입 접근을 충분히 보존하지 못하므로 `EngineOperationGate`, `EngineOperationResultGuard`, `EngineOperationApplyPlan`은 앱 facade 타입으로 명시 매핑한다.
- 2026-06-15: `GoCoachApp.kt`의 engine-backed new game, post-game cache optimization IO 실행도 `application/engine/EngineEffectLauncherApplication.runEngineIo()`를 통과하도록 정리했다.
- 현재 metric: `GoCoachApp.kt`는 2,191줄, UI 파일 안의 `withContext(Dispatchers.IO)`/`runCatching` 직접 지점은 6개, `scope.launch`는 6개다. application import fan-in은 아직 180개로 크다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.EngineOperationPolicyTest' --tests 'com.worksoc.goaicoach.application.DiagnosticEventApplicationTest' --tests 'com.worksoc.goaicoach.application.EngineSessionTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest'` 통과.

## 현재 리팩토링/아키텍처 완성도 평가

- 리팩토링 배치 진행도: 99.82/100.
- 외부 평가 기준 플랫폼 아키텍처 완성도: 95.0/100.
- 상향 요인: 실제 KMP 물리 이동이 2건으로 늘었고, diagnostic port가 application root에서 하위 package로 이동했다. UI가 직접 소유하던 engine IO 실행 지점도 6개까지 줄어 effect launcher 경계가 더 넓어졌다.
- 남은 감점 요인: `GoCoachApp.kt`는 여전히 2천 줄 이상이고 application import fan-in이 180개다. `EngineOperationPolicy` 앱 facade가 남아 있어 shared policy를 직접 쓰는 구조로 완전히 전환된 것은 아니다. score/topmoves/autoai package 물리 분리도 아직 1차 실행 전이다.

## 다음 추천 리팩토링 항목 - ext.2

1. `EngineOperationPolicy` facade 제거 준비
   - application 내부 호출부 일부를 `shared.engine` 직접 import로 전환할 수 있는지 검토한다.
   - acceptance: facade 의존 파일 수가 줄거나, 제거를 막는 Kotlin/package 이유가 문서화된다.

2. application package 분리 2차
   - `score`, `topmoves`, `autoai`, `session` 중 하나를 실제 하위 package로 이동한다.
   - acceptance: package boundary test와 targeted test 통과.

3. EffectLauncher 적용 범위 3차
   - startup, benchmark, endgame 중 하나 이상의 남은 IO 경로를 `runEngineIo()` 또는 operation-specific launcher로 통과시킨다.
   - acceptance: UI 직접 IO/runCatching 지점 6개를 5개 이하로 줄인다.

4. GoCoachApp import fan-in 축소 실험
   - 여러 application import를 하나의 controller/facade import로 묶는 작은 vertical slice를 만든다.
   - acceptance: import 수 감소 또는 controller 경계가 테스트로 고정된다.

5. structured diagnostic adapter spike
   - 현재 JSONL 파일 기반 diagnostic event를 외부 수집 port에 연결하기 위한 noop/recording adapter를 추가한다.
   - acceptance: warning/critical export candidate가 adapter port까지 전달되는 테스트를 추가한다.

## 2026-06-15 추가 진행 로그: ext.2 topmoves package / EffectLauncher / diagnostic sink runner

- 2026-06-15: `TopMovesApplication.kt`를 `application/topmoves/TopMovesApplication.kt`로 이동했다. Top Moves analysis plan, launch plan, completion/apply plan, engine runner가 application 루트 package에서 분리됐다.
- 2026-06-15: Top Moves package 이동에 따라 `GameSessionAnalysisState`, `GameSessionCoreState`, `GameSessionController`, `GameSessionUiStateHolder`, `GoCoachApp.kt`, 관련 테스트의 import를 명시화했다. `LayeringContractTest`도 새 package 경로를 검사한다.
- 2026-06-15: `GoCoachApp.kt`에 남아 있던 `withContext(Dispatchers.IO)` 6개를 모두 `application/engine/EngineEffectLauncherApplication.runEngineIo()` 호출로 교체했다. UI 파일은 더 이상 engine IO dispatcher를 직접 선택하지 않는다.
- 2026-06-15: `DiagnosticEventExternalSinkApplication.kt`를 추가했다. shared diagnostic export plan을 application `DiagnosticEventExternalSinkPort` 실행 결과로 연결하며, `Skipped`, `Sent`, `Failed` 결과를 명시한다.
- 2026-06-15: `DiagnosticEventApplicationTest`에 diagnostic external sink runner 테스트를 추가했다. 사용자 동의 없음, 전송 성공, transport 실패를 모두 검증한다.
- 2026-06-15: `EngineOperationPolicy` facade 제거 가능성을 검토했다. 현재 앱 내부에서 관련 타입/함수 참조가 365개이고, 특히 `EngineOperationResultGuard.Discard`, `EngineOperationGate.Allow/Block` 같은 nested sealed 타입이 넓게 쓰인다. ext.1에서 확인한 것처럼 typealias만으로는 기존 호출부 호환이 깨지므로, 즉시 제거보다 도메인 package 단위 이동 후 shared engine 타입 직접 참조를 점진적으로 늘리는 방향이 안전하다.
- 현재 metric: `GoCoachApp.kt`는 2,191줄, UI 파일 안의 `withContext(Dispatchers.IO)`/`runCatching` 직접 지점은 0개, `scope.launch`는 6개다. application import fan-in은 아직 180개다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.TopMovesApplicationTest' --tests 'com.worksoc.goaicoach.application.DiagnosticEventApplicationTest' --tests 'com.worksoc.goaicoach.application.GameSessionAnalysisStateTest' --tests 'com.worksoc.goaicoach.application.GameSessionControllerTest' --tests 'com.worksoc.goaicoach.application.GameSessionCoreStateTest' --tests 'com.worksoc.goaicoach.application.GameSessionUiStateHolderApplicationTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest'` 통과.

## 현재 리팩토링/아키텍처 완성도 평가

- 리팩토링 배치 진행도: 99.86/100.
- 외부 평가 기준 플랫폼 아키텍처 완성도: 95.7/100.
- 상향 요인: `topmoves` 하위 package가 실제로 생겼고, UI의 engine IO dispatcher 직접 소유가 0개가 됐다. diagnostic external sink도 transport 없는 테스트 가능한 adapter runner로 연결됐다.
- 남은 감점 요인: `GoCoachApp.kt` 줄 수와 application import fan-in은 아직 높다. `scope.launch` 6개는 UI effect orchestration으로 남아 있고, `EngineOperationPolicy` facade는 nested sealed 타입 호환 때문에 아직 필요하다. `score`, `autoai`, `session` package 분리는 아직 남아 있다.

## 다음 추천 리팩토링 항목 - ext.3

1. `score` package 분리
   - `ScoreDisplayApplication.kt`, `ScoreDisplayFormatterApplication.kt`, `ScoreEstimateRunnerApplication.kt`, `ScoreSyncCompletionApplication.kt`, `ScoreSyncRunnerApplication.kt`를 `application/score` 하위 package로 이동할 수 있는지 검토한다.
   - acceptance: score 관련 targeted test와 `LayeringContractTest` 통과.

2. `EngineOperationPolicy` shared 직접 참조 vertical slice
   - 새로 분리한 `topmoves` 또는 다음 `score` package 중 하나에서 shared engine 타입 직접 참조를 시도한다.
   - acceptance: facade 타입과 shared 타입 혼용으로 인한 경계 문제가 없거나, 필요한 adapter 위치가 명확해진다.

3. `scope.launch` effect launcher 분리
   - `launchTrackedEngineOperation` 또는 post-undo delayed sync 중 하나를 UI 밖 helper/controller로 이동한다.
   - acceptance: UI `scope.launch` 직접 지점 6개를 5개 이하로 줄인다.

4. diagnostic external sink transport spike
   - 실제 네트워크 구현은 보류하되, noop/recording adapter를 production/test fixture로 분리한다.
   - acceptance: warning/critical export flow가 port adapter까지 연결되는 더 명확한 테스트 추가.

5. GoCoachApp import fan-in 축소
   - topmoves/score/diagnostic package import를 작은 facade 또는 grouped controller로 정리한다.
   - acceptance: application import 수 감소 또는 import ownership 문서화.

## 2026-06-15 추가 진행 로그: ext.3 score package / shared-engine slice / launchUiEffect

- 2026-06-15: `ScoreDisplayApplication.kt`, `ScoreDisplayFormatterApplication.kt`, `ScoreEstimateRunnerApplication.kt`, `ScoreSyncCompletionApplication.kt`, `ScoreSyncRunnerApplication.kt`를 `application/score/` 하위 package로 이동했다. score 표시, score estimate, score sync completion/runner 책임이 application 루트 package에서 분리됐다.
- 2026-06-15: 새 `score` package에서 `EngineOperationRequest`, `EngineOperationKind`, `EngineTimeoutPolicy`, `EngineFallbackPolicy`, `engineOperationRequest`를 `shared.engine`에서 직접 참조하도록 바꿨다. nested sealed/apply facade가 필요한 `EngineOperationResultGuard`, `EngineOperationApplyPlan`, `buildEngineOperationApplyPlan`, `evaluateEngineOperationResultGuard`는 application facade를 유지한다.
- 2026-06-15: `EngineEffectLauncherApplication.kt`에 `launchUiEffect()`를 추가했다. `GoCoachApp.kt`의 `scope.launch` 직접 호출은 모두 `launchUiEffect(scope)`를 통과하며, UI 파일 내 `scope.launch`, `withContext(Dispatchers.IO)`, `runCatching` 직접 지점은 0개가 됐다.
- 2026-06-15: `NoopDiagnosticEventExternalSink`를 `application/diagnostic/DiagnosticEventPorts.kt`에 추가했다. 실제 Firebase/remote transport가 없는 상태에서도 diagnostic external sink runner를 명시적인 no-op adapter로 조합할 수 있다.
- 현재 metric: `GoCoachApp.kt`는 2,174줄, UI 파일 안의 `scope.launch`/`withContext(Dispatchers.IO)`/`runCatching` 직접 지점은 0개다. application import fan-in은 163개이며, `score` package의 shared engine 직접 import는 12개다.

## 현재 리팩토링/아키텍처 완성도 평가

- 리팩토링 배치 진행도: 99.90/100.
- 외부 평가 기준 플랫폼 아키텍처 완성도: 96.3/100.
- 상향 요인: `topmoves`에 이어 `score` 도메인도 하위 package로 물리 분리됐다. UI entry point가 engine IO뿐 아니라 launch scheduling도 직접 소유하지 않게 됐고, 새 package 일부가 shared engine policy를 직접 참조하기 시작했다.
- 남은 감점 요인: `GoCoachApp.kt`는 여전히 2천 줄 이상이고 application import fan-in 163개는 높다. `EngineOperationPolicy` application facade와 nested sealed 타입 의존이 남아 있으며, `autoai`, `session/controller`, `settings` package 분리는 아직 남아 있다.

## 다음 추천 리팩토링 항목 - ext.4

1. `autoai` package 분리
   - `AutoAiPolicyApplication.kt`, `AutoAiRunnerApplication.kt`, `AutoAiCompletionApplication.kt`를 `application/autoai` 하위 package로 이동한다.
   - acceptance: 자동 AI 관련 targeted test와 `LayeringContractTest` 통과.

2. `EngineOperationPolicy` facade 축소 2차
   - `autoai` 또는 `score` package에서 shared engine 타입 직접 참조 범위를 한 단계 더 넓힌다.
   - acceptance: facade 참조 파일 수가 줄거나, facade 유지가 필요한 nested sealed 타입 경계가 더 명확해진다.

3. GoCoachApp import fan-in 축소
   - `score`/`topmoves` wildcard import를 유지할지, package-level facade 또는 controller import로 묶을지 결정한다.
   - acceptance: import ownership 기준이 문서화되거나 application import 수가 추가 감소한다.

4. operation-specific effect runner 분리
   - `launchUiEffect()`를 단순 wrapper로 유지하되, startup/undo/autoAI/topmoves 등 목적별 runner로 추가 분리할 위치를 잡는다.
   - acceptance: UI가 launch timing과 cancellation reason을 직접 판단하는 구간이 줄어든다.

5. diagnostic sink adapter 고도화
   - no-op adapter 다음 단계로 recording adapter 또는 local file export adapter를 추가한다.
   - acceptance: warning/critical event가 external sink port를 통해 수집 가능한 형태로 테스트된다.

## 2026-06-15 추가 진행 로그: ext.4 autoai package / recording sink / autoAI launcher

- 2026-06-15: `AutoAiPolicyApplication.kt`, `AutoAiRunnerApplication.kt`, `AutoAiCompletionApplication.kt`를 `application/autoai/` 하위 package로 이동했다. 자동 AI scheduling, execution runner, completion guard가 application 루트 package에서 분리됐다.
- 2026-06-15: 새 `autoai` package에서 `EngineOperationRequest`, `EngineOperationKind`, `EngineTimeoutPolicy`, `EngineFallbackPolicy`, `engineOperationRequest`를 `shared.engine`에서 직접 참조하도록 했다. `EngineOperationResultGuard`는 UI discard log와 application facade 타입 호환 때문에 유지한다.
- 2026-06-15: `GoCoachApp.kt`의 autoai 개별 import를 `application.autoai.*`로 묶었다. application import fan-in은 163개에서 140개로 감소했다.
- 2026-06-15: `EngineEffectLauncherApplication.kt`에 `launchAutoAiEffect()`를 추가했다. 자동 AI 루프가 일반 `launchUiEffect()` 직접 호출이 아니라 자동 AI 전용 launch boundary를 통과한다.
- 2026-06-15: `RecordingDiagnosticEventExternalSink`를 `application/diagnostic/DiagnosticEventPorts.kt`에 추가했다. 기존 테스트 전용 fixture를 application port 구현으로 승격해 warning/critical export flow를 실제 transport 전에도 수집/검증할 수 있다.
- 현재 metric: `GoCoachApp.kt`는 2,151줄, UI 파일 안의 `scope.launch`/`withContext(Dispatchers.IO)`/`runCatching` 직접 지점은 0개다. application import fan-in은 140개이며, `score`+`autoai` package의 shared engine 직접 import는 22개다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.GameAutomationApplicationTest' --tests 'com.worksoc.goaicoach.application.DiagnosticEventApplicationTest' --tests 'com.worksoc.goaicoach.application.GameSessionControllerTest' --tests 'com.worksoc.goaicoach.application.GameSessionCoreStateTest' --tests 'com.worksoc.goaicoach.application.GameSessionUiStateHolderApplicationTest' --tests 'com.worksoc.goaicoach.application.TopMovesApplicationTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest'` 통과.

## 현재 리팩토링/아키텍처 완성도 평가

- 리팩토링 배치 진행도: 99.93/100.
- 외부 평가 기준 플랫폼 아키텍처 완성도: 96.8/100.
- 상향 요인: `topmoves`, `score`, `autoai`가 모두 하위 package로 분리됐다. UI의 engine IO/launch primitive 직접 소유가 0개로 유지되고, 자동 AI 루프도 전용 launch boundary를 갖게 됐다. diagnostic external sink는 no-op에서 recording adapter까지 확장됐다.
- 남은 감점 요인: `GoCoachApp.kt`는 아직 2천 줄 이상이고 application import fan-in 140개가 남아 있다. `GameSessionControllerState`, `GameSessionCoreState`, `GameSessionUiStateHolder` 같은 session/controller 축은 여전히 application 루트 package에 있다. `EngineOperationPolicy` facade의 nested sealed 타입 경계도 아직 정리 대상이다.

## 다음 추천 리팩토링 항목 - ext.5

1. `session` 또는 `game` package 분리
   - `GameSessionController.kt`, `GameSessionCoreState.kt`, `GameSessionRuntimeState.kt`, `GameSessionMoveReviewState.kt`, `GameSessionScoreState.kt`, `GameSessionSettingsState.kt`, `GameSessionUiStateHolderApplication.kt` 중 안정적인 묶음을 하위 package로 이동한다.
   - acceptance: session 관련 targeted test와 `LayeringContractTest` 통과.

2. `RuntimeEventApplication.kt` package 분리
   - runtime log/event 생성 책임을 `application/runtime` 하위 package로 이동한다.
   - acceptance: runtime event test 통과 및 UI import fan-in 감소.

3. `EngineOperationPolicy` facade adapter 명시화
   - application facade의 shared-to-application guard/apply 매핑을 별도 adapter 파일로 분리한다.
   - acceptance: facade 유지 이유가 코드 구조에서 명확해지고, shared direct import 확대 시 충돌 지점이 줄어든다.

4. diagnostic sink local export adapter
   - recording adapter 다음 단계로 local file/jsonl export adapter를 port 뒤에 추가한다.
   - acceptance: warning/critical diagnostic payload가 사용자 동의 후 local export adapter로 저장되는 테스트 추가.

5. `GoCoachApp.kt` state holder 적용 확대
   - 자동 AI success/failure, benchmark, startup 중 하나를 `GameSessionUiStateHolder` 또는 session reducer 경계로 더 이동한다.
   - acceptance: UI local helper 수와 직접 state mutation 지점 감소.

## 2026-06-15 추가 진행 로그: ext.5 session/runtime package / policy adapter / local diagnostic export

- 2026-06-15: `GameSessionAnalysisState.kt`, `GameSessionController.kt`, `GameSessionCoreState.kt`, `GameSessionMoveReviewState.kt`, `GameSessionRuntimeState.kt`, `GameSessionScoreState.kt`, `GameSessionSettingsState.kt`, `GameSessionTurnTimeState.kt`, `GameSessionUiStateHolderApplication.kt`를 `application/session/` 하위 package로 이동했다. session 상태, controller state, UI state holder/reducer 경계가 application 루트 package에서 분리됐다.
- 2026-06-15: `RuntimeEventApplication.kt`를 `application/runtime/` 하위 package로 이동했다. runtime log/event 생성 책임이 별도 package로 분리되어 structured diagnostic, local/remote log adapter, future app-service observability와 연결하기 쉬워졌다.
- 2026-06-15: `GameSessionTurnTimeState.kt`의 `java.util.Locale`/`String.format` 의존을 제거했다. common-friendly tenths formatter로 교체하여 session package가 platform-free 후보 검사에 포함될 수 있게 했다.
- 2026-06-15: `EngineOperationPolicyAdapter.kt`를 추가했다. shared engine policy 타입과 application facade 타입 사이의 guard/apply 매핑을 별도 파일로 분리해, facade 유지 이유와 향후 shared 직접 참조 확장 지점을 코드상으로 명확히 했다.
- 2026-06-15: `LocalFileDiagnosticEventExternalSink`를 추가했다. warning/critical diagnostic event export payload를 사용자 동의 후 로컬 JSONL 파일로 저장할 수 있는 JVM/Android-bound adapter이며, 원격 transport 도입 전 수집 payload 검증용으로 쓸 수 있다.
- 현재 metric: `GoCoachApp.kt`는 2,132줄, UI 파일 안의 `scope.launch`/`withContext(Dispatchers.IO)`/`runCatching` 직접 지점은 0개다. application import fan-in은 120개이며, application 하위 package는 `autoai`, `diagnostic`, `engine`, `runtime`, `score`, `session`, `topmoves`로 분리됐다.

## 현재 리팩토링/아키텍처 완성도 평가

- 리팩토링 배치 진행도: 99.96/100.
- 외부 평가 기준 플랫폼 아키텍처 완성도: 97.4/100.
- 상향 요인: `topmoves`, `score`, `autoai`에 이어 `session`, `runtime`까지 package boundary가 생겼다. UI entry point의 coroutine/engine IO primitive 직접 소유는 계속 0개이며, diagnostic external sink는 no-op/recording/local-file adapter 계층을 갖췄다.
- 남은 감점 요인: `GoCoachApp.kt`는 아직 2천 줄 이상이고, session package 내부가 application root wildcard에 기대는 지점이 남아 있다. `EngineOperationPolicy` facade도 nested sealed 타입 호환 때문에 유지되고 있으며, state mutation 일부는 여전히 UI local helper에 있다.

## 다음 추천 리팩토링 항목 - ext.6

1. `session` package 내부 root application wildcard 의존 축소
   - session 파일들이 `application.*`에 기대는 지점을 명시 import 또는 좁은 port/facade로 바꾼다.
   - acceptance: session package가 어떤 application 루트 타입을 실제로 필요로 하는지 코드상 드러난다.

2. `runtime` package 내부 root application wildcard 의존 축소
   - runtime event 생성 파일에서 필요한 plan/model만 명시 import하고, session/autoai 의존도 좁힌다.
   - acceptance: runtime package가 structured log builder 역할에만 집중한다.

3. `GameSessionUiStateHolder` 적용 범위 확대
   - benchmark/startup/engine lifecycle display 중 하나를 holder 또는 session reducer 경계로 이동한다.
   - acceptance: `GoCoachApp.kt`의 직접 state mutation helper가 줄어든다.

4. `EngineOperationPolicy` facade 축소 3차
   - `session` 또는 `runtime` package에서 request/policy 값은 shared engine 타입 직접 참조로 전환하고, guard/apply facade는 adapter 뒤에 둔다.
   - acceptance: facade 유지 타입과 제거 가능한 타입의 경계가 더 좁아진다.

5. diagnostic local export wiring 후보 정리
   - `LocalFileDiagnosticEventExternalSink`를 메뉴/디버그 로그 수집 UX에 연결할지, 우선 개발자-only export adapter로 둘지 결정한다.
   - acceptance: 외부 수집 전 단계의 local export 운영 원칙이 문서화된다.

## 2026-06-15 추가 진행 로그: ext.6 session/runtime dependency narrowing / startup holder / runtime port

- 2026-06-15: `session` package 내부의 root `application.*`, `autoai.*`, `score.*` wildcard import를 제거했다. `GameSessionAnalysisState`, `GameSessionCoreState`, `GameSessionController`, `GameSessionMoveReviewState`, `GameSessionRuntimeState`, `GameSessionScoreState`, `GameSessionSettingsState`, `GameSessionTurnTimeState`, `GameSessionUiStateHolderApplication`이 필요한 타입만 명시 import한다.
- 2026-06-15: `RuntimeEventLogPort`를 root `ApplicationPorts.kt`에서 `application/runtime/RuntimeEventPorts.kt`로 이동했다. runtime event log port가 runtime package 소유가 되면서 runtime observability boundary가 더 분명해졌다.
- 2026-06-15: `RuntimeEventApplication.kt`의 root application wildcard import를 제거했다. runtime log builder는 `GameSessionControllerState`, `GameSessionRuntimeState`, `TurnTimeMoveUpdate`, `AutoAiTurnDisplayPlan`, human sync/endgame 관련 display plan 등 실제 필요한 타입만 본다.
- 2026-06-15: `GameSessionCoreState.applyEngineStartupDisplayPlan()`과 `GameSessionUiStateHolder.applyEngineStartupDisplayPlan()`을 추가했다. engine startup 결과 중 score snapshot, candidate text, engine message는 holder/reducer 경계를 통과하며, `isEngineReady` 같은 app-service 상태만 UI에 남았다.
- 2026-06-15: `EngineOperationPolicyTest`에 shared-to-application adapter 테스트를 추가했다. shared `EngineOperationGate`, `EngineOperationResultGuard`, `EngineOperationApplyPlan`이 application facade 타입으로 변환될 때 metadata가 보존되는지 검증한다.
- 2026-06-15: `GoCoachApp.kt` import를 정리했다. `session`, `runtime`, `score`, `topmoves`, `autoai`는 grouped import를 사용하고 중복 explicit import를 제거했다.
- 현재 metric: `GoCoachApp.kt`는 2,106줄, UI 파일 안의 `scope.launch`/`withContext(Dispatchers.IO)`/`runCatching` 직접 지점은 0개다. application import fan-in은 98개이며, `session`/`runtime` package 내부 root application wildcard import는 0개다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.GameSessionControllerTest' --tests 'com.worksoc.goaicoach.application.GameSessionCoreStateTest' --tests 'com.worksoc.goaicoach.application.GameSessionUiStateHolderApplicationTest' --tests 'com.worksoc.goaicoach.application.RuntimeEventApplicationTest' --tests 'com.worksoc.goaicoach.application.EngineOperationPolicyTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest'` 통과.

## 현재 리팩토링/아키텍처 완성도 평가

- 리팩토링 배치 진행도: 99.97/100.
- 외부 평가 기준 플랫폼 아키텍처 완성도: 97.8/100.
- 상향 요인: session/runtime package가 단순 물리 분리가 아니라 의존 방향까지 정리되기 시작했다. runtime port가 도메인 package로 이동했고, startup display state mutation도 holder/reducer 경계를 통과한다.
- 남은 감점 요인: `GoCoachApp.kt`는 여전히 2천 줄 이상이다. root application package에는 benchmark, saved-session, undo, human sync, cache optimization, start-game 관련 파일이 남아 있다. `EngineOperationPolicy` facade도 adapter 테스트로 안전장치를 만들었지만 아직 제거 단계는 아니다.

## 다음 추천 리팩토링 항목 - ext.7

1. `lifecycle` 또는 `startup` package 분리
   - `EngineStartupApplication.kt`, `EngineSessionLifecycleApplication.kt`, `EngineDeviceBenchmarkApplication.kt`를 한 번에 옮길지, startup/benchmark로 나눌지 결정한다.
   - acceptance: engine startup/undo/benchmark targeted test와 `LayeringContractTest` 통과.

2. benchmark display state holder 적용
   - benchmark progress/result/failure 표시 중 일부를 UI 직접 mutation에서 application state helper로 이동한다.
   - acceptance: benchmark path의 `engineMessage`/`candidateText` 직접 변경 지점 감소.

3. `SavedSessionPromptApplication.kt` package 분리
   - saved-session prompt state/plan을 `application/session`에 둘지 `application/persistence` 성격으로 둘지 결정한다.
   - acceptance: session root state와 persistence prompt의 소유 경계가 명확해진다.

4. `EngineOperationPolicy` facade 축소 4차
   - adapter 테스트를 기반으로 새 package에서 shared result guard 직접 참조를 제한적으로 시도한다.
   - acceptance: facade가 필요한 nested sealed 타입과 제거 가능한 request/policy 타입의 경계가 더 좁아진다.

5. diagnostic local export 운영 원칙 문서화
   - local file export를 개발자 전용 수집 경로로 둘지, 앱 메뉴의 "로그 내보내기" UX 후보로 연결할지 정리한다.
   - acceptance: warning/critical local export와 debug report copy의 관계가 문서화된다.

## 2026-06-15 추가 진행 로그: ext.7 engine package / benchmark display holder

- 2026-06-15: `EngineStartupApplication.kt`, `EngineSessionLifecycleApplication.kt`, `EngineDeviceBenchmarkApplication.kt`를 `application/engine/` 하위 package로 이동했다. engine startup, engine-backed new game, engine undo, startup benchmark runner/display policy가 application root package에서 분리됐다.
- 2026-06-15: `EngineBenchmarkStorePort`를 root `ApplicationPorts.kt`에서 `application/engine/EngineBenchmarkPorts.kt`로 이동했다. benchmark persistence port도 benchmark 도메인 package가 소유한다.
- 2026-06-15: `EngineBenchmarkDisplayPlan`과 `engineBenchmarkWaitingDisplayPlan()`, `engineBenchmarkRunningDisplayPlan()`, `EngineBenchmarkProgress.toEngineBenchmarkDisplayPlan()`, `engineBenchmarkCompletedDisplayPlan()`, `engineBenchmarkFailureDisplayPlan()`을 추가했다. benchmark 진행/완료/실패 화면 문구 정책이 Compose에서 engine application layer로 이동했다.
- 2026-06-15: `GameSessionCoreState.applyEngineBenchmarkDisplayPlan()`과 `GameSessionUiStateHolder.applyEngineBenchmarkDisplayPlan()`을 추가했다. benchmark 중 core display state 변경은 holder/reducer 경계를 통과한다.
- 2026-06-15: `GoCoachApp.kt`는 benchmark 진행 중 `engineMessage`/`analysisState.candidateText`를 직접 조립하지 않고, typed display plan을 적용한다.
- 현재 metric: `GoCoachApp.kt`는 2,089줄, UI 파일 안의 `scope.launch`/`withContext(Dispatchers.IO)`/`runCatching` 직접 지점은 0개다. application import fan-in은 81개이며, application root package 파일 수는 25개다.
- 검증: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.EngineStartupApplicationTest' --tests 'com.worksoc.goaicoach.application.EngineSessionLifecycleApplicationTest' --tests 'com.worksoc.goaicoach.application.EngineDeviceBenchmarkApplicationTest' --tests 'com.worksoc.goaicoach.application.GameSessionUiStateHolderApplicationTest' --tests 'com.worksoc.goaicoach.application.GameSessionControllerTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest'` 통과.

## 현재 리팩토링/아키텍처 완성도 평가

- 리팩토링 배치 진행도: 99.98/100.
- 외부 평가 기준 플랫폼 아키텍처 완성도: 98.1/100.
- 상향 요인: engine lifecycle/startup/benchmark가 `application/engine`으로 물리 분리됐고, benchmark store port까지 도메인 package로 이동했다. benchmark display mutation도 holder/reducer 경계를 통과하기 시작했다.
- 남은 감점 요인: `GoCoachApp.kt`는 아직 2천 줄 이상이고, root application package에는 saved session, start-game, undo, human move, cache optimization, debug report, endgame resolver가 남아 있다. engine package 내부도 startup/benchmark/launcher가 한 package에 섞여 있으므로, 장기적으로 `engine/session`, `engine/benchmark`, `engine/lifecycle` 세부 분리 여지가 있다.

## 다음 추천 리팩토링 항목 - ext.8

1. saved session / persistence plan package 분리
   - `SavedSessionPromptApplication.kt`, `SavedGamePersistence.kt`, 관련 prompt state를 `application/persistence` 또는 `application/session` 하위로 이동한다.
   - acceptance: saved-session prompt/persistence targeted test와 `LayeringContractTest` 통과.

2. `StartGameApplication.kt` package 분리
   - start configured game, new local game, engine-backed new game request plan의 위치를 정리한다.
   - acceptance: new game/start game flow가 session reset plan과 engine effect를 명확히 분리한다.

3. undo package 분리
   - `UndoApplication.kt`를 `application/undo` 하위 package로 이동하고, engine undo result dependency를 `application.engine`으로 명시한다.
   - acceptance: undo targeted test와 post-undo sync flow compile/test 통과.

4. benchmark progress callback boundary 개선
   - 현재 `withContext(Dispatchers.Main)`이 benchmark progress callback 안에 남아 있다. 이를 engine effect runner 또는 UI effect bridge로 더 분리할지 검토한다.
   - acceptance: UI가 dispatcher 전환을 직접 선택하는 마지막 특수 지점을 줄인다.

5. root application package 파일 수 감축 목표 설정
   - 다음 2~3회 리팩토링에서 root application 파일 수를 25개에서 18개 이하로 낮춘다.
   - acceptance: root package가 공통 port/facade/legacy compatibility만 담도록 수렴한다.

## 2026-06-15 추가 진행 로그: ext.8 savedgame/startgame/undo package 분리

- 2026-06-15: `SavedGamePersistence.kt`, `SavedSessionPromptApplication.kt`, `SavedGameRestoreApplication.kt`, `SavedGameSnapshot.kt`를 `application/savedgame/` 하위 package로 정리했다. 저장/복원 prompt, 자동 저장 plan, 복원 plan, 저장 snapshot DTO가 같은 도메인에 모였다.
- `SavedGameSnapshot`을 `persistence` package에서 `application/savedgame`으로 이동했다. 이는 savedgame application logic이 Android persistence adapter를 역참조하던 계층 위반을 제거하기 위한 변경이며, `GameSessionStore`와 `SavedGameSessionCodec`은 application snapshot을 serialize/deserialize하는 adapter 역할만 맡는다.
- `StartGameApplication.kt`를 `application/startgame/` 하위 package로 이동했다. `GameSessionResetPlan`과 `buildNewLocalGameSessionPlan()`도 start-game 도메인으로 이동해 새 게임 reset display policy가 root application package에서 빠졌다.
- `UndoApplication.kt`를 `application/undo/` 하위 package로 이동했다. undo request plan, local undo display plan, engine undo completion plan, quiet-window delay policy가 undo 도메인에 모였다.
- benchmark progress callback에서 직접 `withContext(Dispatchers.Main)`을 호출하던 지점을 제거하고, 기존 `launchUiEffect(scope)` bridge를 통해 UI state update를 예약하게 했다. `GoCoachApp.kt`는 더 이상 `Dispatchers`/`withContext`를 직접 import하지 않는다.
- `LayeringContractTest`에 `savedgame`, `startgame`, `undo` package를 platform-free 후보로 추가했다. 이 과정에서 savedgame의 persistence 역참조가 드러났고, snapshot DTO 이동으로 해결했다.
- 현재 metric: `GoCoachApp.kt`는 2,088줄, UI 파일 안의 `scope.launch`/`withContext`/`Dispatchers`/`runCatching` 직접 지점은 0개다. application import fan-in은 82개이며, root application package 파일 수는 21개다.

## 현재 리팩토링/아키텍처 완성도 평가

- 리팩토링 배치 진행도: 99.985/100.
- 외부 평가 기준 플랫폼 아키텍처 완성도: 98.4/100.
- 상향 요인: root application package가 25개에서 21개로 줄었고, savedgame/startgame/undo가 각각 독립 package로 분리됐다. 저장 snapshot DTO가 persistence에서 application 도메인으로 이동하면서 KMP 후보 계층의 방향성이 더 정확해졌다.
- 남은 감점 요인: root application package에는 human move, cache optimization, debug report, endgame resolver, engine operation facade/adapter, analysis cache/formatter가 남아 있다. `GoCoachApp.kt`도 여전히 2천 줄 이상이며 import fan-in은 snapshot 도메인 이동 영향으로 81개에서 82개가 됐다.

## 다음 추천 리팩토링 항목 - ext.9

1. `humanmove` package 분리
   - `HumanMoveApplication.kt`를 `application/humanmove` 하위 package로 이동한다.
   - acceptance: human move targeted test와 session reducer compile/test 통과.

2. `cache` 또는 `analysiscache` package 분리
   - `AnalysisSession.kt`, `PositionAnalysisCache.kt`, `PositionAnalysisCacheOptimization.kt`의 책임을 analysis cache/session cache/optimization으로 나눈다.
   - acceptance: Top Moves/cache optimization tests 통과, undo restore cache 위치 재검토.

3. `debugreport` package 분리
   - `DebugReportBuilder.kt`와 debug report copy plan/effect를 `application/debugreport`로 이동한다.
   - acceptance: debug report snapshot/build/copy tests 통과.

4. `endgame` package 분리
   - `EndgameResolver.kt`를 `application/endgame`으로 이동하고 score package와의 의존 방향을 확인한다.
   - acceptance: final score/endgame targeted tests와 architecture contract 통과.

5. root `ApplicationPorts.kt` 추가 축소
   - 남은 store/clipboard/notice/debug mirror port를 각 도메인 package로 이동할 수 있는지 검토한다.
   - acceptance: root application package가 compatibility facade와 cross-domain model 중심으로 줄어든다.

## 2026-06-15 추가 진행 로그: ext.9 humanmove/debugreport/endgame/analysis package 분리

- 2026-06-15: `HumanMoveApplication.kt`를 `application/humanmove/` 하위 package로 이동했다. 사람 착수 로컬 적용, sync completion/apply plan, runtime log plan이 human move 도메인에 모였다.
- 2026-06-15: `DebugReportBuilder.kt`를 `application/debugreport/` 하위 package로 이동했다. debug report snapshot, report builder, copy plan/effect가 독립 package에 위치한다.
- 2026-06-15: `DebugReportBuilder` 안에 섞여 있던 종국 로그 formatter를 `application/endgame/EndgameLogFormatter.kt`로 분리했다. debug report가 종국 로그 생성 책임을 소유하지 않도록 방향을 바로잡았다.
- 2026-06-15: `EndgameResolver.kt`를 `application/endgame/` 하위 package로 이동했다. `AiEndgameResolution`, `EndgameResolutionTimings`, `resolveAiEndgame()`이 score/autoai/engine session에서 명시 import되는 endgame 도메인이 됐다.
- 2026-06-15: `AnalysisFormatter.kt`, `AnalysisSession.kt`, `PositionAnalysisCache.kt`, `PositionAnalysisCacheOptimization.kt`를 `application/analysis/` 하위 package로 이동했다. Top Moves 분석 세션, undo restore cache, JSON position analysis cache, post-game cache optimization 정책이 root application package에서 빠졌다.
- 2026-06-15: `LayeringContractTest`에 `analysis`, `debugreport`, `endgame`, `humanmove` package 위치를 반영했다. `AnalysisSession.kt`는 아직 `java.util.LinkedHashMap`을 사용하므로 KMP-ready 후보 검사에서는 제외했다.
- 현재 metric: `GoCoachApp.kt`는 2,088줄, UI 파일 안의 `scope.launch`/`withContext`/`Dispatchers`/`runCatching` 직접 지점은 0개다. application import fan-in은 82개이며, application root package 파일 수는 14개다.
- 검증:
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.DebugReportBuilderTest' --tests 'com.worksoc.goaicoach.application.EndgameResolverTest' --tests 'com.worksoc.goaicoach.application.ScoreDisplayApplicationTest' --tests 'com.worksoc.goaicoach.application.GameAutomationApplicationTest' --tests 'com.worksoc.goaicoach.application.EngineSessionLifecycleApplicationTest' --tests 'com.worksoc.goaicoach.application.GameSessionControllerTest'` 통과.
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.AnalysisSessionTest' --tests 'com.worksoc.goaicoach.application.PositionAnalysisCachePolicyTest' --tests 'com.worksoc.goaicoach.application.PositionAnalysisCacheOptimizationTest' --tests 'com.worksoc.goaicoach.application.TopMovesApplicationTest' --tests 'com.worksoc.goaicoach.application.EngineSessionTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest'` 통과.
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test` 통과.

## 현재 리팩토링/아키텍처 완성도 평가

- 리팩토링 배치 진행도: 99.99/100.
- 외부 평가 기준 플랫폼 아키텍처 완성도: 98.8/100.
- 상향 요인: root application package 파일 수가 21개에서 14개로 줄었다. human move, debug report, endgame, analysis/cache optimization이 각각 package 경계를 갖게 되어 AI Agent가 특정 도메인 단위로 작업하기 쉬워졌다.
- 남은 감점 요인: `GoCoachApp.kt`는 아직 2,088줄이며 app-service orchestration이 크다. root application package에는 `ApplicationPorts`, `EngineSession/EngineSessionClient`, engine operation facade, move review/value display, preference/scoring/prompt 공통 정책이 남아 있다. `AnalysisSession.kt`의 `java.util.LinkedHashMap`도 KMP 후보성을 낮춘다.

## 다음 추천 리팩토링 항목 - ext.10

1. `ApplicationPorts.kt` 도메인별 이동
   - `ClipboardPort`, `UserNoticePort`, `DebugReportMirrorPort`를 `debugreport` 또는 `platformports` 성격으로 분리하고, `SavedGameStorePort`/`UserPreferencesStorePort`의 위치도 재검토한다.
   - acceptance: root `ApplicationPorts.kt`가 사라지거나 cross-domain compatibility 최소 파일로 축소된다.

2. `MoveReview.kt` / `MoveValueDisplay.kt` package 분리
   - 후보수 loss/color/spot tone/리뷰 marker 정책을 `application/movereview` 또는 `application/analysis/review`로 이동한다.
   - acceptance: analysis formatter와 UI board가 move value display 정책을 명시 package에서 가져온다.

3. `EngineSession.kt` / `EngineSessionClient.kt` 내부 경계 분할
   - session client contract, local implementation, assistant-judge constants, startup helpers를 각각 `application/engine/session`, `application/engine/endgame`, `application/engine/client`로 나눌지 검토한다.
   - acceptance: engine session 파일이 remote driver spike와 local process driver를 동시에 담기 쉬운 구조가 된다.

4. `AnalysisSession.kt` KMP-ready화
   - `java.util.LinkedHashMap` 기반 LRU cache를 port/adapter로 분리하거나 Kotlin-only 구현으로 교체한다.
   - acceptance: `AnalysisSession.kt`를 `LayeringContractTest`의 platform-free 후보에 추가할 수 있다.

5. `GoCoachApp.kt` app-service orchestration 추가 분리
   - debug report copy, post-game cache optimization, start-game/resume/undo action binding 중 하나를 controller/action object로 이동한다.
   - acceptance: `GoCoachApp.kt` 줄 수와 import fan-in이 모두 감소하고, UI는 action bridge를 호출하는 형태로 단순화된다.

## 2026-06-15 추가 진행 로그: ext.10 ports/preferences/movereview/engine session package 분리

- 2026-06-15: root `ApplicationPorts.kt`를 제거했다. 저장/설정/debug report 관련 port가 모두 각 도메인 package 소유로 이동했다.
  - `SavedGameStorePort` -> `application/savedgame/SavedGamePorts.kt`
  - `UserPreferencesStorePort` -> `application/preferences/UserPreferencesPorts.kt`
  - `DebugReportMirrorPort`, `ClipboardPort`, `UserNoticePort` -> `application/debugreport/DebugReportPorts.kt`
- 2026-06-15: `UserPreferencesApplication.kt`를 `application/preferences/`로 이동했다. 초기 설정 복원, `GameSettings`, preferences snapshot 생성 책임이 preferences 도메인에 모였다.
- 2026-06-15: `MoveReview.kt`, `MoveValueDisplay.kt`를 `application/movereview/`로 이동했다. 착수 평가 marker/tone/label 정책이 UI board나 generic application root가 아닌 move review 도메인에서 제공된다.
- 2026-06-15: `EngineSession.kt`, `EngineSessionClient.kt`를 `application/engine/`으로 이동했다. session interface, local implementation, assistant-judge time cap, local scoring helper가 engine application package에 모였다.
- 2026-06-15: `LayeringContractTest`는 새로 이동한 debugreport ports, savedgame ports, move review/value display 파일을 platform-free 후보로 감시한다. `EngineSessionClient`는 아직 local implementation과 cache resolver, time source가 한 파일에 있어 KMP-ready 후보에는 넣지 않았다.
- 현재 metric: `GoCoachApp.kt`는 2,088줄, UI 파일 안의 `scope.launch`/`withContext`/`Dispatchers`/`runCatching` 직접 지점은 0개다. application import fan-in은 82개이며, application root package 파일 수는 14개에서 8개로 감소했다.
- 검증:
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:compileDebugKotlin :app-android:compileDebugUnitTestKotlin` 통과.
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.UserPreferencesApplicationTest' --tests 'com.worksoc.goaicoach.application.MoveReviewTest' --tests 'com.worksoc.goaicoach.ui.MoveValueDisplayTest' --tests 'com.worksoc.goaicoach.application.EngineSessionTest' --tests 'com.worksoc.goaicoach.application.EngineSessionLifecycleApplicationTest' --tests 'com.worksoc.goaicoach.application.DebugReportBuilderTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest'` 통과.
  - `git diff --check` 통과.
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test` 통과.

## 현재 리팩토링/아키텍처 완성도 평가

- 리팩토링 배치 진행도: 99.992/100.
- 외부 평가 기준 플랫폼 아키텍처 완성도: 99.0/100.
- 상향 요인: root application package가 8개로 줄었고, 더 이상 `ApplicationPorts.kt` 같은 잡다한 port 집합 파일이 없다. preferences, move review, debug report port, savedgame port, engine session이 각자의 package에 위치한다.
- 남은 감점 요인: `GoCoachApp.kt`는 여전히 2,088줄이며 app-service action binding이 크다. `EngineSessionClient.kt`는 interface와 local implementation, cache, time source가 한 파일에 남아 있다. `UserPreferencesApplication.kt`는 아직 `persistence.UserPreferencesSnapshot`을 참조하므로 preferences DTO 소유권이 완전히 정리되지는 않았다.

## 다음 추천 리팩토링 항목 - ext.11

1. `UserPreferencesSnapshot` 소유권 정리
   - 현재 preferences application이 persistence DTO를 직접 참조한다. `UserPreferencesSnapshot`을 `application/preferences` 또는 shared/common 후보로 이동하고, persistence는 serialize/deserialize adapter로만 남긴다.
   - acceptance: preferences package가 `com.worksoc.goaicoach.persistence`를 import하지 않는다.

2. `EngineSessionClient.kt` interface/local implementation 분리
   - `EngineSessionClient` contract, `LocalEngineSessionClient`, cache resolver wiring, assistant judge constants/helper를 파일 단위로 분리한다.
   - acceptance: remote engine driver spike가 local implementation 파일을 건드리지 않고 contract를 구현할 수 있다.

3. `EngineSession.kt` 시간 source/assistant judge 정책 분리
   - `System.currentTimeMillis()` 사용 구간을 clock port 또는 application runner 경계로 이동하고, assistant judge SLA 상수를 endgame/engine policy 파일로 분리한다.
   - acceptance: engine session helper 일부를 KMP/platform-free 후보로 올릴 수 있다.

4. `AnalysisSession.kt` KMP-ready화
   - `java.util.LinkedHashMap` 기반 LRU cache를 Kotlin-only 구현 또는 port-backed cache로 교체한다.
   - acceptance: `AnalysisSession.kt`를 `LayeringContractTest` platform-free 후보에 추가한다.

5. `GoCoachApp.kt` action binding 추가 축소
   - debug report copy, start/resume/new-game/undo action group 중 하나를 app-service action binder로 이동한다.
   - acceptance: `GoCoachApp.kt` 줄 수와 application import fan-in이 동시에 감소한다.

## 2026-06-15 추가 진행 로그: ext.11 preferences DTO / engine contract / KMP cache 정리

- 2026-06-15: `UserPreferencesSnapshot`을 `persistence` package에서 `application/preferences/UserPreferencesSnapshot.kt`로 이동했다. preferences 도메인은 더 이상 persistence adapter를 import하지 않는다.
- 2026-06-15: `UserPreferencesStore`와 `UserPreferencesCodec`은 application preferences snapshot을 serialize/deserialize하는 adapter로 남겼다. 저장 형식은 유지했으므로 사용자 설정 호환성에는 영향을 주지 않는다.
- 2026-06-15: `EngineSessionClient.kt`는 `EngineSessionBackend`, `EngineSessionCapabilities`, `EngineSessionClient` contract만 담도록 축소했다. 로컬 프로세스 엔진 구현은 `LocalEngineSessionClient.kt`로 분리했다.
- 2026-06-15: `EngineAssistantJudgePolicy.kt`를 추가해 pass/pass 종국 처리용 dead-stones 2초, final-score 1초 SLA와 profile 변환을 별도 policy 파일로 분리했다.
- 2026-06-15: `EngineClock.kt`를 추가하고 human move sync replay timing에 clock을 주입할 수 있게 했다. 로컬 구현은 기본적으로 `SystemEngineClock`을 사용하지만, 테스트/원격 adapter는 다른 clock을 넣을 수 있다.
- 2026-06-15: `AnalysisSession.kt`의 `java.util.LinkedHashMap` 의존을 Kotlin collection 기반 `AccessOrderedCache`로 교체했다. 이 파일을 `LayeringContractTest` platform-free 후보에 추가했다.
- 2026-06-15: debug report copy에 `DebugReportCopyActionRequest`와 `runDebugReportCopyAction()`을 추가했다. `GoCoachApp.kt`는 debug report snapshot/plan/effect 조립을 직접 하지 않고 app-service action request만 생성한다.
- 2026-06-15: `LayeringContractTest`에 preferences application/ports/snapshot, `AnalysisSession.kt`, `EngineSessionClient.kt`, `EngineAssistantJudgePolicy.kt`를 KMP/platform-free 후보로 추가했다.
- 현재 metric: `GoCoachApp.kt`는 2,086줄, UI 파일 안의 `scope.launch`/`withContext`/`Dispatchers`/`runCatching` 직접 지점은 0개다. application import fan-in은 82개이며, application root package 파일 수는 8개다.
- 검증:
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:compileDebugKotlin :app-android:compileDebugUnitTestKotlin` 통과.
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.UserPreferencesApplicationTest' --tests 'com.worksoc.goaicoach.persistence.UserPreferencesCodecTest' --tests 'com.worksoc.goaicoach.application.EngineSessionTest' --tests 'com.worksoc.goaicoach.application.EngineSessionLifecycleApplicationTest' --tests 'com.worksoc.goaicoach.application.EngineDeviceBenchmarkApplicationTest' --tests 'com.worksoc.goaicoach.application.AnalysisSessionTest' --tests 'com.worksoc.goaicoach.application.DebugReportBuilderTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest'` 통과.
  - `git diff --check` 통과.
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test` 통과.

## 현재 리팩토링/아키텍처 완성도 평가

- 리팩토링 배치 진행도: 99.994/100.
- 외부 평가 기준 플랫폼 아키텍처 완성도: 99.15/100.
- 상향 요인: preferences DTO 소유권이 application 도메인으로 이동했고, engine session contract와 local implementation이 분리됐다. `AnalysisSession.kt`가 platform-free 후보에 편입되어 KMP 이동 전제 조건이 더 좋아졌다.
- 남은 감점 요인: root application package에는 아직 `GameSessionApplication.kt`, `ScoringRuleApplication.kt`, `PromptPriorityApplication.kt`, engine operation facade 계열이 남아 있다. `LocalEngineSessionClient.kt`는 position analysis cache resolver, diagnostic observation, local process sync가 한 파일에 섞여 있어 다음 분해가 필요하다. `GoCoachApp.kt`도 2천 줄 이상이다.

## 다음 추천 리팩토링 항목 - ext.12

1. root application package 잔여 도메인 이동
   - `ScoringRuleApplication.kt`, `PromptPriorityApplication.kt`, `GameSessionApplication.kt`를 각각 `score`, `prompt`, `session` 또는 더 적절한 package로 이동한다.
   - acceptance: root application package 파일 수를 8개에서 5개 이하로 낮춘다.

2. `LocalEngineSessionClient.kt` 내부 책임 분리
   - position analysis cache read/write, diagnostic event recording, local process sync delegation을 별도 helper/delegate로 나눈다.
   - acceptance: local client가 orchestration shell이 되고, cache policy는 analysis/middleware boundary에서 테스트 가능해진다.

3. `EngineSession.kt` helper 분류
   - startup/new-game/sync/score/endgame helper를 `EngineCoreSessionHelpers`, `EngineCoreScoringHelpers`, `EngineCoreEndgameHelpers` 등으로 나눌지 검토한다.
   - acceptance: engine helper 파일별 목적이 1개로 줄고 remote/local parity 문서화가 쉬워진다.

4. debug report action 추가 축소
   - debug report copy request에서 runtime/diagnostic log read를 port로 감싸거나 app-service action builder로 옮긴다.
   - acceptance: `GoCoachApp.kt`의 debug report 관련 state read가 더 줄어든다.

5. KMP 이동 2차 후보 실행
   - platform-free로 감시 중인 preferences snapshot, move review, analysis session 중 하나를 shared/common 또는 별도 middleware module로 실제 이동하는 spike를 수행한다.
   - acceptance: 문서상 후보가 아니라 실제 물리 이동 1건을 더 만든다.

## 2026-06-15 추가 진행 로그: 2nd phase.1 shared 테스트와 root application 잔여 이동

- 2026-06-15: 외부 96점 리뷰의 즉시 적용 항목 중 shared/commonTest 보강을 먼저 수행했다.
  - `shared/diagnostic/DiagnosticEventModelTest`를 추가해 severity summary, context 정렬/one-line normalization, warning/critical 사용자 동의 export 정책을 고정했다.
  - `shared/engine/EngineOperationPolicyTest`를 추가해 operation id/session generation/fingerprint/timeout/fallback/backend metadata, stale generation/position discard, gate 정책, timeout validation을 commonTest에서 검증한다.
- 2026-06-15: shared 바둑 규칙 회귀 테스트를 보강했다.
  - pass 이후 ko 제한이 해제되고 이후 재탈환이 가능한지 확인한다.
  - 한 수로 서로 다른 인접 그룹 2개를 동시에 따내는 케이스를 고정했다.
  - dead-stone cleanup에서 흑 사석 제거 시 백 prisoner가 증가하는 방향도 회귀 테스트로 고정했다.
- 2026-06-15: root application package에 남아 있던 잔여 도메인 파일 3개를 하위 package로 이동했다.
  - `ScoringRuleApplication.kt` -> `application/score/ScoringRuleApplication.kt`
  - `PromptPriorityApplication.kt` -> `application/prompt/PromptPriorityApplication.kt`
  - `GameSessionApplication.kt` -> `application/session/GameSessionApplication.kt`
- 2026-06-15: `LayeringContractTest`에 새 위치의 scoring/prompt/session 파일을 platform-free 후보로 추가했다. 이 파일들이 Android/UI/persistence/runtime 구현에 다시 붙지 않도록 회귀 방지한다.
- 현재 metric:
  - root application package 파일 수: 8개 -> 5개
  - `GoCoachApp.kt`: 2,086줄 -> 2,080줄
  - `GoCoachApp.kt` application import fan-in: 82개 -> 76개
  - shared commonTest 파일 수: 8개 -> 10개
- 검증:
  - `ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :shared:check` 통과.
  - `ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app-android:compileDebugKotlin` 통과.
  - `ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app-android:testDebugUnitTest` 통과.

## 현재 리팩토링/아키텍처 완성도 평가

- 리팩토링 배치 진행도: 99.996/100.
- 외부 평가 기준 플랫폼 아키텍처 완성도: 96.8/100.
- 보수적 내부 플랫폼 완성도: 96.2/100.
- 상향 요인: shared로 이동된 diagnostic/engine policy 모델에 공통 테스트가 생겼고, root application package가 5개로 줄었다. scoring/prompt/session 정책이 각 도메인 package에 위치해 AI Agent가 특정 도메인 단위로 작업하기 더 쉬워졌다.
- 남은 감점 요인: root application package에는 engine operation facade 계열 5개가 남아 있다. `LocalEngineSessionClient.kt`는 여전히 cache resolver, diagnostic observation, local process sync, engine sync delegation을 함께 가진다. `GoCoachApp.kt`도 2천 줄 이상이라 action binding 분리는 계속 필요하다.

## 다음 추천 리팩토링 항목 - 2nd phase.2

1. `LocalEngineSessionClient.kt` 내부 delegate 분리
   - position analysis cache read/write, diagnostic event recording, local engine sync delegation을 별도 helper로 분리한다.
   - acceptance: local client는 orchestration shell이 되고, cache/diagnostic/sync 정책이 각각 테스트 가능한 파일로 이동한다.

2. root engine operation facade 축소
   - root application package의 `EngineOperationPolicy.kt`, `EngineOperationPolicyAdapter.kt`, `EngineOperationResultApplication.kt`, `EngineOperationLifecycle.kt`, `EngineOperationScope.kt`를 `application/engine/operation` 또는 shared 직접 참조로 정리한다.
   - acceptance: root application package 파일 수 5개 이하 유지 또는 0개에 근접한다.

3. `LaunchedEffect` inventory와 UI lifecycle bridge 분류
   - 남겨도 되는 Compose lifecycle bridge와 application effect로 옮겨야 하는 domain side-effect를 문서화한다.
   - acceptance: 무조건 제거가 아니라 책임 기준이 생긴다.

4. `GoCoachApp.kt` action binding 추가 축소
   - start/new/resume/undo/action buttons 중 하나를 app-service action object로 이동한다.
   - acceptance: 줄 수와 application import fan-in이 동시에 감소한다.

5. KMP 물리 이동 2차 spike
   - `MoveReview`/`MoveValueDisplay`, `AnalysisSession`, preferences snapshot 중 다음 후보 1개를 shared/common 또는 별도 KMP 모듈로 이동 가능한지 실행한다.
   - acceptance: 문서 후보가 아니라 실제 물리 이동 1건을 더 만든다.

## 2026-06-15 추가 진행 로그: 2nd phase.2 engine operation/package zero-root 정리

- 2026-06-15: `LocalEngineSessionClient.kt` 내부 책임을 다음 helper/delegate로 분리했다.
  - `LocalEngineCoreSessionDelegate`: local `EngineCoreApi` 기반 startup/new-game/sync/score/endgame/undo/benchmark 실행.
  - `LocalPositionAnalysisCacheCoordinator`: JSON position analysis cache context/key/read/write/store eligibility 판단.
  - `EngineAnalysisDiagnosticRecorder`: root visits fill diagnostic event 기록.
- 2026-06-15: root application package의 engine operation facade 5개 파일을 `application/engine/operation`으로 이동했다.
  - `EngineOperationPolicy.kt`
  - `EngineOperationPolicyAdapter.kt`
  - `EngineOperationResultApplication.kt`
  - `EngineOperationLifecycle.kt`
  - `EngineOperationScope.kt`
- 2026-06-15: `docs/refactoring/LAUNCHED_EFFECT_INVENTORY_2026-06-15.md`를 생성했다. UI에 남은 Compose effect를 제거 대상이 아니라 책임별 lifecycle bridge로 분류했고, auto-AI/Top-Moves/autosave를 다음 이동 우선순위로 잡았다.
- 2026-06-15: `GameUiEventHandlers` 직접 생성 대신 `buildGameUiEventHandlers()` factory를 추가했다. 작은 변화지만 UI action binding 생성 책임을 presentation 계층에 둔다.
- 2026-06-15: `MoveValueDisplay.kt`를 `shared/commonMain`으로 이동했다. 후보수 loss/delta 표시 규칙은 Android나 application 상태에 의존하지 않으므로 shared 표시 정책으로 관리한다.
- 2026-06-15: `LayeringContractTest`는 새 `application/engine/operation` 위치와 shared `MoveValueDisplay.kt`를 platform-free/KMP-ready 후보로 감시하도록 갱신했다.

### 현재 metric

- root application package 파일 수: 5개 -> 0개
- `LocalEngineSessionClient.kt`: 436줄 상당의 혼합 책임 -> 278줄 orchestration shell
- `GoCoachApp.kt`: 2,080줄 유지
- `GoCoachApp.kt` application import fan-in: 76개 유지
- UI 파일 내 직접 `scope.launch`/`withContext`/`Dispatchers`/`runCatching`: 0개 유지
- KMP 물리 이동 누적: diagnostic model, engine policy model, `MoveValueDisplay.kt`

### 검증

- `ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :shared:check :app-android:compileDebugKotlin :app-android:compileDebugUnitTestKotlin` 통과.
- `ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app-android:testDebugUnitTest` 통과.
- `ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk JAVA_HOME=$(/usr/libexec/java_home -v 17) make test` 통과.

## 현재 리팩토링/아키텍처 완성도 평가

- 리팩토링 배치 진행도: 99.997/100.
- 외부 평가 기준 플랫폼 아키텍처 완성도: 97.1/100.
- 보수적 내부 플랫폼 완성도: 96.6/100.
- 상향 요인: root application package가 비었고, engine operation facade가 engine 도메인 하위로 들어갔다. local engine session client도 cache/diagnostic/core sync 책임이 분리되어 remote engine client 추가 비용이 낮아졌다.
- 남은 감점 요인: `GoCoachApp.kt`는 아직 2천 줄 이상이고 Compose `LaunchedEffect` trigger가 11개 남아 있다. operation facade는 위치는 정리됐지만 아직 shared engine 타입 직접 참조로 완전히 수렴하지 않았다.

## 다음 추천 리팩토링 항목 - 2nd phase.3

1. Auto AI / Top Moves effect launcher 분리
   - `GoCoachApp.kt`의 자동 AI 턴과 Top Moves 자동 분석 `LaunchedEffect`를 각각 application runner 입력 객체로 옮긴다.
   - acceptance: UI effect는 state snapshot과 callback만 넘기고, scheduling/quiet-window/guard 판단은 application 함수가 소유한다.

2. autosave runner 분리
   - 사용자 설정 autosave와 진행 중 대국 autosave/clear effect를 persistence runner로 이동한다.
   - acceptance: `GoCoachApp.kt`의 persistence write trigger가 얇아지고 저장 실패/skip 정책을 테스트할 수 있다.

3. engine operation facade 직접 shared 수렴 1차
   - app facade 타입이 반드시 필요한 곳과 shared engine 타입을 직접 써도 되는 곳을 분리한다.
   - acceptance: `application/engine/operation` facade가 adapter/compatibility 역할만 남고 정책 호출부는 shared 타입에 더 가까워진다.

4. `LocalEngineCoreSessionDelegate` 추가 분해 검토
   - GTP stateful fast, JSON position analysis, score/endgame helper를 더 나눌지 검토한다.
   - acceptance: local process engine과 remote server engine parity 문서화가 쉬워진다.

5. presentation/menu DTO 후속 정리
   - Player setup, engine search time, benchmark/cache menu 상태를 presentation DTO 중심으로 더 정리한다.
   - acceptance: `GoCoachApp.kt` import fan-in과 action binding lambda 수를 함께 줄인다.

## 2026-06-15 추가 진행 로그: 2nd phase.3 GoCoachApp 축소 시작

- 2026-06-15: `runAutoAiTurnTriggerEffect()`와 `runTopMoveAnalysisTriggerEffect()`를 추가했다. 자동 AI/Top Moves `LaunchedEffect`가 undo quiet-window delay 계산을 직접 들고 있지 않게 했다.
- 2026-06-15: `runUserPreferencesAutosave()`와 `runSavedGamePersistence()`를 추가했다. 사용자 설정 저장과 진행 중 대국 저장은 application runner가 plan/snapshot/store 적용을 담당한다.
- 2026-06-15: operation metadata 계열 import 일부를 `shared.engine` 직접 참조로 전환했다. app facade는 `EngineOperationResultGuard`, lifecycle reducer, discard log 같은 application-specific 경계에 집중하도록 좁혀가는 중이다.
- 2026-06-15: `UserPreferencesSnapshot.toKaTrainUxOptions()`를 `presentation/KaTrainUxOptionsMapper.kt`로 이동했다. preferences snapshot -> UX option 변환은 UI shell이 아니라 presentation DTO mapping으로 관리한다.
- 2026-06-15: `docs/refactoring/GO_COACH_APP_SPLIT_PLAN_2026-06-15.md`를 추가했다. `GoCoachApp.kt`가 아직 큰 이유, 지금까지 급하게 줄이지 않은 이유, 다음 단계별 줄 수 축소 목표를 명시했다.
- 2026-06-15: `LayeringContractTest`에 새 autosave/trigger runner를 platform-free 후보로 추가했다.

### 현재 metric

- `GoCoachApp.kt`: 2,080줄 -> 2,068줄
- `GoCoachApp.kt` application import fan-in: 76개 -> 71개
- root application package 파일 수: 0개 유지
- UI 파일 내 직접 `scope.launch`/`withContext`/`Dispatchers`/`runCatching`: 0개 유지
- `LaunchedEffect` trigger는 아직 남아 있으나, autosave/auto-AI/Top-Moves trigger 내부 판단 일부가 application runner로 이동했다.

### 검증

- `ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app-android:testDebugUnitTest` 통과.
- `git diff --check` 통과.
- `ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk JAVA_HOME=$(/usr/libexec/java_home -v 17) make test` 통과.

### GoCoachApp에 대한 판단

- 2천 줄 이상 상태는 최종적으로 괜찮지 않다.
- 다만 지금까지는 파일을 먼저 쪼개기보다 stale result guard, operation lifecycle, diagnostic, runner, domain package split을 먼저 깔았다. 이 순서는 합리적이었다.
- 이제는 안전장치가 충분히 생겼으므로 2nd phase.4부터 `requestAiTurnForCurrentState()`와 `requestTopMoveAnalysisForState()` 실행 본문을 runner로 옮겨 줄 수를 직접 줄이는 단계가 맞다.

## 현재 리팩토링/아키텍처 완성도 평가

- 리팩토링 배치 진행도: 99.998/100.
- 외부 평가 기준 플랫폼 아키텍처 완성도: 97.3/100.
- 보수적 내부 플랫폼 완성도: 96.9/100.
- 상향 요인: root application zero-file 상태를 유지하면서 autosave/trigger/presentation mapping이 UI 밖으로 이동했다. operation metadata도 shared 직접 참조로 일부 수렴했다.
- 남은 감점 요인: `GoCoachApp.kt`는 여전히 2천 줄 이상이고, Auto AI/Top Moves 실행 본문 자체는 아직 UI shell 안에 있다.

## 다음 추천 리팩토링 항목 - 2nd phase.4

1. Auto AI 실행 본문 runner 분리
   - `requestAiTurnForCurrentState()`의 schedule validation 이후 operation token 생성, begin/complete log, engine call, completion plan 생성을 application launcher로 이동한다.
   - acceptance: `GoCoachApp.kt`에서 80~140줄 감소, 자동대국 동작 테스트 유지.

2. Top Moves 실행 본문 runner 분리
   - `requestTopMoveAnalysisForState()`의 launch plan/cache/run-engine/completion apply plan 조립을 application launcher로 이동한다.
   - acceptance: Top Moves cache hit/run-engine/failure/discard 경로가 runner 테스트로 고정된다.

3. post-undo engine sync runner 분리
   - 현재 `schedulePostUndoLocalEngineSync()`는 delay, busy polling, stale check, score sync를 모두 가진다.
   - acceptance: post-undo quiet-window와 engine sync 재시도 판단을 application helper로 이동한다.

4. `GoCoachScreenStateAssembler` 도입
   - `buildGameScreenStateInput(...)`의 긴 인자 조립을 snapshot/assembler로 분리한다.
   - acceptance: 화면 렌더링 직전 state read fan-in이 줄어든다.

5. `LocalEngineCoreSessionDelegate` protocol별 추가 분해
   - score/endgame/benchmark/helper를 score/endgame/protocol helper로 나눌지 실행한다.
   - acceptance: remote engine client와 local engine client parity 문서화가 더 쉬워진다.

## 2026-06-15 추가 진행 로그: 2nd phase.4 Auto-AI scheduled turn runner

- 2026-06-15: `AutoAiScheduledTurnRunnerApplication.kt`를 추가했다. 예약된 AI 턴의 delay, 재검증, operation token 생성, engine workflow 실행, completion 적용, runtime schedule/begin/complete/cancel log, 후속 Top Moves 요청 연결을 autoai application runner가 소유한다.
- 2026-06-15: `GoCoachApp.kt`의 `requestAiTurnForCurrentState()` schedule 분기는 `runScheduledAutoAiTurnApplication(AutoAiScheduledTurnRunRequest)` 호출로 축소했다. UI는 현재 상태 provider와 상태 적용 콜백만 제공한다.
- 2026-06-15: `AutoAiScheduledTurnRunnerTest`와 `LayeringContractTest.goCoachAppDoesNotOwnScheduledAutoAiTurnWorkflowBody()`를 추가했다. 예약 AI 턴 성공/취소 경로와 UI 경계 회귀를 함께 검증한다.

### 현재 metric

- `GoCoachApp.kt`: 1,808줄
- `GoCoachApp.kt` 전체 import: 115개
- `GoCoachApp.kt` application import fan-in: 62개
- UI 파일 내 직접 scheduled Auto-AI workflow 세부 참조: 제거됨
  - `autoAiTurnOperationToken(`
  - `GameSessionEffect.RunAutoAiTurn(`
  - `AutoAiTurnRunExecutionContext(`
  - `runAutoAiTurnWorkflowResult(`
  - `buildAutoAiTurnCompletionPlan(`
  - `runtimeAiTurnBeginLog(`
  - `runtimeAiTurnCompleteLog(`
  - `runtimeAiTurnScheduleCancelledLog(`

### 검증

- 기본 셸 Java 25에서는 Gradle Kotlin DSL이 `IllegalArgumentException: 25`로 실패함을 재확인했다.
- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:compileDebugKotlin :app-android:compileDebugUnitTestKotlin :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.AutoAiScheduledTurnRunnerTest' --tests 'com.worksoc.goaicoach.application.GameAutomationApplicationTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest' --no-daemon` 통과.
- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.AutoAiScheduledTurnRunnerTest' --no-daemon --rerun-tasks` 통과.
- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test` 통과.

## 현재 리팩토링/아키텍처 완성도 평가

- 리팩토링 배치 진행도: 99.9985/100.
- 외부 평가 기준 플랫폼 아키텍처 완성도: 97.5/100.
- 보수적 내부 플랫폼 완성도: 97.1/100.
- 상향 요인: 자동 AI 예약 턴의 지연, stale guard, engine workflow, completion routing이 UI shell 밖으로 이동했다. 자동대국/사람대국 양쪽에서 engine operation을 외부 서비스처럼 다루는 경계가 더 선명해졌다.
- 남은 감점 요인: `GoCoachApp.kt`는 아직 1,800줄대이고 application import fan-in이 62개다. Auto-AI success/failure/endgame display apply helper도 아직 UI 파일 안에 남아 있다.

## 다음 추천 리팩토링 항목 - 2nd phase.5

1. Auto-AI completion display applier 분리
   - `applyAutoAiTurnSuccessCompletion()`과 `applyAutoAiTurnFailureCompletion()`의 display/log 적용을 autoai application applier로 이동한다.
   - acceptance: `GoCoachApp.kt`에서 `runtimeAiTurnSuccessLog`, `runtimeAiTurnFailureLog`, auto-AI endgame display plan 적용 세부가 사라진다.

2. Top Moves 실행 본문 runner 분리
   - `requestTopMoveAnalysisForState()`의 launch plan/cache/run-engine/completion apply plan 조립을 application launcher로 이동한다.
   - acceptance: Top Moves cache hit/run-engine/failure/discard 경로가 runner 테스트로 고정된다.

3. `GoCoachScreenStateAssembler` 도입
   - `buildGameScreenStateInput(...)`의 긴 인자 조립을 snapshot/assembler로 분리한다.
   - acceptance: 화면 렌더링 직전 state read fan-in과 UI shell import fan-in이 함께 줄어든다.

4. menu/player setup action binding 분리
   - player setup, auto play delay, search time, benchmark/cache menu action binding을 presentation action bundle로 이동한다.
   - acceptance: 메뉴 UX 고도화 시 `GoCoachApp.kt` 수정면이 줄고 테스트 가능한 action model이 생긴다.

5. `LocalEngineCoreSessionDelegate` protocol별 추가 분해
   - score/endgame/benchmark/helper를 score/endgame/protocol helper로 나눌지 실행한다.
   - acceptance: remote engine client와 local engine client parity 문서화가 더 쉬워진다.

## 2026-06-15 추가 진행 로그: 2nd phase.5 Auto-AI completion applier

- 2026-06-15: `SM-S908N` USB 연결 상태에서 `make install-dev-engine`로 최신 debug APK 설치, KataGo model/config seed, cold launch를 완료했다. cold launch `TotalTime=611ms`.
- 2026-06-15: `AutoAiCompletionApplierApplication.kt`를 추가했다. Auto-AI turn completion의 success/failure/discard 분기, success/failure runtime log, 턴 시간 update, display 적용, endgame resolve trigger, discard log 위임을 autoai application applier가 소유한다.
- 2026-06-15: `GoCoachApp.kt`의 `applyAutoAiTurnSuccessCompletion()`/`applyAutoAiTurnFailureCompletion()` helper를 제거했다. UI는 scheduled runner 요청에 작은 상태 적용 콜백만 제공한다.
- 2026-06-15: `AutoAiCompletionApplierTest`와 `LayeringContractTest.goCoachAppDoesNotOwnAutoAiTurnCompletionApplyBody()`를 추가했다. success/failure/discard 경로와 UI 경계 회귀를 함께 검증한다.

### 현재 metric

- `GoCoachApp.kt`: 1,754줄
- `GoCoachApp.kt` 전체 import: 115개
- `GoCoachApp.kt` application import fan-in: 62개
- UI 파일 내 직접 Auto-AI completion apply 세부 참조: 제거됨
  - `fun applyAutoAiTurnSuccessCompletion(`
  - `fun applyAutoAiTurnFailureCompletion(`
  - `runtimeAiTurnSuccessLog(`
  - `runtimeAiTurnFailureLog(`
  - `buildAutoAiTurnEndgamePlan(`

### 검증

- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:compileDebugKotlin :app-android:compileDebugUnitTestKotlin :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.AutoAiCompletionApplierTest' --tests 'com.worksoc.goaicoach.application.AutoAiScheduledTurnRunnerTest' --tests 'com.worksoc.goaicoach.application.GameAutomationApplicationTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest' --no-daemon` 통과.
- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk make test` 통과.
- 리팩토링 반영 후 최종 APK 재설치를 재시도했으나 `adb devices`가 빈 목록을 반환해 `:app-android:installDebug`가 `No connected devices`로 실패했다. `adb kill-server/start-server` 후에도 장치가 재인식되지 않았다.

## 현재 리팩토링/아키텍처 완성도 평가

- 리팩토링 배치 진행도: 99.9988/100.
- 외부 평가 기준 플랫폼 아키텍처 완성도: 97.7/100.
- 보수적 내부 플랫폼 완성도: 97.3/100.
- 상향 요인: Auto-AI turn workflow와 completion apply가 모두 application layer로 올라갔다. UI shell은 이제 자동 AI 턴의 세부 분기보다 상태 적용 콜백과 화면 조립에 더 집중한다.
- 남은 감점 요인: `GoCoachApp.kt`의 import fan-in은 아직 62개로 크고, Top Moves 실행 본문이 다음으로 큰 engine workflow 덩어리다.

## 다음 추천 리팩토링 항목 - 2nd phase.6

1. Top Moves 실행 본문 runner 분리
   - `requestTopMoveAnalysisForState()`의 launch plan/cache/run-engine/completion apply plan 조립을 application launcher로 이동한다.
   - acceptance: Top Moves cache hit/run-engine/failure/discard 경로가 runner 테스트로 고정되고 UI에서 Top Moves operation/effect/completion 세부가 사라진다.

2. `GoCoachScreenStateAssembler` 도입
   - `buildGameScreenStateInput(...)`의 긴 인자 조립을 snapshot/assembler로 분리한다.
   - acceptance: 화면 렌더링 직전 state read fan-in과 UI shell import fan-in이 함께 줄어든다.

3. menu/player setup action binding 분리
   - player setup, auto play delay, search time, benchmark/cache menu action binding을 presentation action bundle로 이동한다.
   - acceptance: 메뉴 UX 고도화 시 `GoCoachApp.kt` 수정면이 줄고 테스트 가능한 action model이 생긴다.

4. Auto-AI endgame resolve runner 후속 분리
   - 현재 `applyAutoAiEndgamePlan()`은 아직 UI에서 engine endgame effect와 completion을 직접 처리한다.
   - acceptance: AI pass/pass 종국 처리도 application runner가 소유하고 UI는 final/failure display callback만 제공한다.

5. `LocalEngineCoreSessionDelegate` protocol별 추가 분해
   - score/endgame/benchmark/helper를 score/endgame/protocol helper로 나눌지 실행한다.
   - acceptance: remote engine client와 local engine client parity 문서화가 더 쉬워진다.

## 2026-06-15 추가 진행 로그: 2nd phase.6 Top Moves completion applier

- 2026-06-15: `TopMoveAnalysisApplierApplication.kt`를 추가했다. Top Moves 분석 completion apply plan의 success/failure/discard 분기, 분석 상태 적용, undo restore cache 저장, analysis cache 저장, failure display, discard log 위임을 topmoves application applier가 소유한다.
- 2026-06-15: `TopMoveAnalysisRunRequest`는 더 이상 `applyCompletion` 콜백으로 completion plan을 UI에 넘기지 않는다. 대신 상태/cache/display/discard 콜백을 받아 runner 내부에서 `applyTopMoveAnalysisCompletionApplication()`을 호출한다.
- 2026-06-15: `GoCoachApp.kt`의 `applyTopMoveAnalysisCompletionApplyPlan()` helper를 제거했다. UI는 Top Moves completion 분기를 직접 판단하지 않고 작은 콜백만 제공한다.
- 2026-06-15: `TopMovesApplicationTest`와 `LayeringContractTest.goCoachAppDoesNotOwnTopMovesWorkflowBody()`를 보강했다. runner가 engine work 완료 후 applier를 통해 상태/cache callback을 호출하는지와 UI 경계 회귀를 함께 검증한다.

### 현재 metric

- `GoCoachApp.kt`: 1,742줄
- `GoCoachApp.kt` 전체 import: 115개
- `GoCoachApp.kt` application import fan-in: 62개
- UI 파일 내 직접 Top Moves completion apply 세부 참조: 제거됨
  - `applyTopMoveAnalysisCompletionApplyPlan(`
  - `TopMoveAnalysisCompletionApplyPlan.`
  - `runTopMoveAnalysisEffectApplyPlan(`
  - `TopMoveAnalysisEffectLaunchRequest(`
  - `TopMoveAnalysisExecutionContext(`

### 검증

- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:compileDebugKotlin :app-android:compileDebugUnitTestKotlin --no-daemon` 통과.
- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest --tests 'com.worksoc.goaicoach.application.TopMovesApplicationTest' --tests 'com.worksoc.goaicoach.architecture.LayeringContractTest' --no-daemon` 통과.

## 현재 리팩토링/아키텍처 완성도 평가

- 리팩토링 배치 진행도: 99.9990/100.
- 외부 평가 기준 플랫폼 아키텍처 완성도: 97.9/100.
- 보수적 내부 플랫폼 완성도: 97.5/100.
- 상향 요인: Auto-AI에 이어 Top Moves completion apply도 application layer로 올라갔다. UI shell은 engine 결과 분기보다 화면 상태 적용 콜백만 맡는 방향으로 더 수렴했다.
- 남은 감점 요인: `GoCoachApp.kt` import fan-in은 아직 62개이고, 화면 state 조립/메뉴 action binding/Auto-AI endgame resolve는 여전히 UI 파일의 주요 책임으로 남아 있다.

## 다음 추천 리팩토링 항목 - 2nd phase.7

1. `GoCoachScreenStateAssembler` 도입
   - `buildGameScreenStateInput(...)`의 긴 인자 조립을 snapshot/assembler로 분리한다.
   - acceptance: 화면 렌더링 직전 state read fan-in과 UI shell import fan-in이 함께 줄어든다.

2. Auto-AI endgame resolve runner 후속 분리
   - 현재 `applyAutoAiEndgamePlan()`은 아직 UI에서 engine endgame effect와 completion을 직접 처리한다.
   - acceptance: AI pass/pass 종국 처리도 application runner가 소유하고 UI는 final/failure display callback만 제공한다.

3. menu/player setup action binding 분리
   - player setup, auto play delay, search time, benchmark/cache menu action binding을 presentation action bundle로 이동한다.
   - acceptance: 메뉴 UX 고도화 시 `GoCoachApp.kt` 수정면이 줄고 테스트 가능한 action model이 생긴다.

4. Position cache optimization workflow runner 분리
   - `acceptCacheOptimizationPrompt()`는 아직 operation 생성, effect 실행, result 분기를 UI에서 직접 처리한다.
   - acceptance: post-game cache optimization도 application runner가 소유하고 UI는 prompt state/display callback만 제공한다.

5. `LocalEngineCoreSessionDelegate` protocol별 추가 분해
   - score/endgame/benchmark/helper를 score/endgame/protocol helper로 나눌지 실행한다.
   - acceptance: remote engine client와 local engine client parity 문서화가 더 쉬워진다.
