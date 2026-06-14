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
