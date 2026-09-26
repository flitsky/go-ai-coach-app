package com.worksoc.goaicoach

import com.worksoc.goaicoach.application.analysis.AnalysisResultCache
import com.worksoc.goaicoach.application.analysis.CachedAnalysisResult
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationUiState
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheQuality
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheQualityTier
import com.worksoc.goaicoach.application.analysis.UndoAnalysisRestoreCache
import com.worksoc.goaicoach.application.contract.AnalysisCacheKey
import com.worksoc.goaicoach.application.contract.GameSessionRuntimeState
import com.worksoc.goaicoach.application.debugreport.ClipboardPort
import com.worksoc.goaicoach.application.debugreport.DebugReportMirrorPort
import com.worksoc.goaicoach.application.debugreport.UserNoticePort
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProfile
import com.worksoc.goaicoach.application.engine.EngineBenchmarkStorePort
import com.worksoc.goaicoach.application.engine.EngineBenchmarkUiState
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.operation.EngineOperationLifecycleController
import com.worksoc.goaicoach.application.orchestration.GameSessionDisplayStateApplier
import com.worksoc.goaicoach.application.preferences.UserPreferencesSnapshot
import com.worksoc.goaicoach.application.preferences.UserPreferencesStorePort
import com.worksoc.goaicoach.application.preferences.buildInitialUserPreferencesPlan
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.savedgame.SavedSessionUiState
import com.worksoc.goaicoach.application.score.FinalScoreDisplayPlan
import com.worksoc.goaicoach.application.session.AutoAiTurnUiState
import com.worksoc.goaicoach.application.session.GameSessionAnalysisState
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.GameSessionCoreState
import com.worksoc.goaicoach.application.session.GameSessionMoveReviewState
import com.worksoc.goaicoach.application.session.GameSessionScoreState
import com.worksoc.goaicoach.application.session.GameSessionSettingsState
import com.worksoc.goaicoach.application.session.GameSessionStateHolder
import com.worksoc.goaicoach.application.session.GameSessionTurnTimeState
import com.worksoc.goaicoach.application.session.toRuntimeLogContext
import com.worksoc.goaicoach.application.topmoves.TopMoveAnalysisDeferral
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisPreset
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.policy.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.policy.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.testsupport.FakeEngineSessionClient
import com.worksoc.goaicoach.ui.buildInitialSessionState
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Delay
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob

/**
 * [GoCoachAppWiringContext]의 테스트 페이크 — **배선(`wire*Controller`)을 실제로 돌려 보기 위한** 것이다
 * (refactor backlog #43).
 *
 * ## 무엇을 흉내 내는가
 * `GoCoachApp.kt`의 익명 `object : GoCoachAppWiringContext`와 **같은 모양**이다.
 * - 세션 상태는 진짜 [GameSessionStateHolder] **하나**에 둔다. 게터(`gameState()`/`playerSetup()`/
 *   `matchMode()`/`shouldShowResumePrompt()`…)는 전부 그 홀더에서 파생되고, 세터는 그 홀더에 되쓴다 —
 *   앱의 `HolderBackedState`와 같은 단일 원천이다.
 * - 세션 스냅샷 밖의 상태(엔진 준비/바쁨, 무르기 조용한 구간, 엔진 이름…)는 평범한 `var`다.
 *   앱에서는 `by remember { mutableStateOf(...) }`인 것들이다.
 * - [lifecycleController]·[displayStateApplier]·캐시는 **진짜 공용 클래스**를 앱과 같은 인자로 만든다.
 *
 * ## ⚠️ 모든 게터는 호출될 때마다 지금 값을 읽는다 — 이것이 이 페이크의 존재 이유다
 * 그래야 배선이나 컨트롤러가 게터 람다(`{ context.playerSetup() }`) 대신 값을 붙잡으면 테스트가
 * 옛 값을 보고 빨개진다(함정 67). 붙잡는 때는 둘이다.
 * - **배선 시점**(`val setup = context.playerSetup(); … { setup }`) — [getterReads]가 잡는다.
 *   게터가 불릴 때마다 그 이름이 남으므로, 배선 **도중**에 남은 이름은 곧 붙잡힌 값이다.
 * - **첫 사용 시점**(`lazy { context.playerSetup() }`, 첫 결과를 저장하는 람다, 컨트롤러 안의
 *   지연 초기화 필드) — 배선 중에는 아무것도 읽지 않으니 [getterReads]는 조용하다. 이것은
 *   테스트가 **부르고 → 값을 바꾸고 → 다시 불러야** 드러난다(`GoCoachControllerWiringTest`의 탐침들).
 *
 * ⚠️ 이 페이크는 `GoCoachApp`의 **익명 객체 자체**가 컴포지션 지역 `val`을 붙잡는 것
 * (`override fun playerSetup() = playerSetup`)은 재현하지 않는다 — 그 자리는 컴포즈 안이다.
 * 그 자리는 소스 계약 `WiringContextFreezeContractTest`가 지킨다.
 *
 * ## 코루틴은 시킬 때만 돈다
 * [scope]는 [QueueOnlyDispatcher] 위에 있다 — `launch`는 [Runnable]을 줄에 세우기만 하고 실행하지
 * 않는다. 그래서 엔진 작업(스텁하지 않은 [FakeEngineSessionClient] 멤버는 터진다)에 닿지 않으면서도
 * **"실행이 걸렸다"** 는 사실은 [QueueOnlyDispatcher.queuedCount]로 단언할 수 있다. 줄에 선 블록을
 * 직접 돌려 보고 싶으면 [QueueOnlyDispatcher.runNext]를 부른다.
 * - `delay()`는 **영원히 멈춘다**([QueueOnlyDispatcher.parkedDelayMillis]에 기록만 남는다). 가상 시간이
 *   없으니 흘려보내지도, 실제 시간으로 새지도 않는다 — 결과가 타이밍에 기대지 않게 하려는 것이다.
 * - 엔진 작업은 `runEngineIo`(= `Dispatchers.IO`)로 **다른 스레드**를 한 번 다녀온다. 그 복귀는 이
 *   디스패처의 줄에 선다 — [QueueOnlyDispatcher.runNextArrivingWithin]으로 기다렸다 돌린다.
 *
 * ## 기록과 조작을 구분한다
 * - `…Writes` 목록은 **컨트롤러가 컨텍스트 세터를 부른 기록**이다. 단언은 여기에 한다.
 * - [changeSettings]/[changePlayerSetup]/[changeSession]/[changeCore]는 **테스트가 바깥에서 상태를
 *   바꾸는 손잡이**다(설정 화면이 바꾼 셈). 이것들은 기록에 남지 않는다 — 테스트가 스스로 넣은 값을
 *   스스로 확인하는 동어반복을 막는다.
 *
 * [androidContext]는 읽는 순간 [AndroidContextTouched]를 던진다 — 단위 테스트에는 진짜
 * `Context`가 없고, 배선은 그것을 람다 안에서만(디버그 리포트의 진동 진단) 읽어야 한다.
 *
 * [clearUndoEngineInterventionQuietWindow]는 앱처럼 [cancelUndoSync]도 부른다. 앱은 배선 **뒤에**
 * `cancelUndoSync = controllers.undoController::cancelPendingSync`로 그것을 묶는다. 지금은 어떤 테스트도
 * 이 페이크의 [cancelUndoSync]를 묶지 않는다(착수·새 대국·이어하기 탐침은 [quietWindowClears]만 센다) —
 * 앱 쪽의 두 줄(지역 함수 안의 `cancelUndoSync()` 호출과 배선 뒤의 묶기)은 `WiringContextFreezeContractTest`가
 * 소스로 지킨다.
 */
internal class FakeGoCoachAppWiringContext(
    initial: GameSessionControllerState = inGameSession(),
    override val engineClient: EngineSessionClient = FakeEngineSessionClient(),
    override val benchmarkStore: EngineBenchmarkStorePort = FakeEngineBenchmarkStore(),
) : GoCoachAppWiringContext {
    val holder = GameSessionStateHolder(initial)
    val dispatcher = QueueOnlyDispatcher()

    // ── 세션 스냅샷 밖의 상태(앱에서는 `by remember { mutableStateOf(...) }`) ──
    var engineIsReady = false
    var engineIsBusy = false
    var engineIsBlockingBusy = false
    var moveReviewEnabled = false
    var quietUntil = 0L
    var undoSyncIsPending = false
    var currentEngineName = "engine-initial"
    var currentEngineDiagnostic = "diagnostic-initial"
    var savedSessionJson: String? = null
    var benchmarkRawText = "No engine benchmark file recorded."

    // ── 컨트롤러가 컨텍스트에 한 일의 기록 ──
    val engineMessages = mutableListOf<String>()
    val settingsWrites = mutableListOf<GameSessionSettingsState>()
    val runtimeWrites = mutableListOf<GameSessionRuntimeState>()
    val analysisWrites = mutableListOf<GameSessionAnalysisState>()
    val scoreWrites = mutableListOf<GameSessionScoreState>()
    val coreWrites = mutableListOf<GameSessionCoreState>()
    val autoAiTurnWrites = mutableListOf<AutoAiTurnUiState>()
    val cacheOptimizationWrites = mutableListOf<PositionAnalysisCacheOptimizationUiState>()
    val benchmarkWrites = mutableListOf<EngineBenchmarkUiState>()
    val turnTimeWrites = mutableListOf<GameSessionTurnTimeState>()
    val quietUntilWrites = mutableListOf<Long>()
    val pendingUndoSyncWrites = mutableListOf<Boolean>()

    /** 값 목록을 따로 두지 않는 세터 다섯(`setGameState`·`setMoveReviewState`·`setSavedSessionUiState`·`setIsGameEnded`·`setEngineReady`)이 불린 순서대로의 이름. */
    val otherSetterCalls = mutableListOf<String>()
    var quietWindowClears = 0
    var endgameReviewActivations = 0
    var androidContextReads = 0

    /** 앱의 `var cancelUndoSync`와 같은 자리 — 앱은 배선 뒤 `undoController::cancelPendingSync`를 묶는다. */
    var cancelUndoSync: () -> Unit = {}

    /** 상태 게터가 불린 순서대로의 이름. 배선 **도중**의 읽기는 곧 값 붙잡기다(함정 67). */
    val getterReads = mutableListOf<String>()

    fun reads(getter: String): Int = getterReads.count { it == getter }

    /**
     * 컨트롤러가 컨텍스트에 남긴 **모든** 기록의 개수. 배선만으로는 전부 0이어야 한다 —
     * 기록 목록이 늘면 여기에도 더한다(빠뜨리면 그 목록은 "배선은 아무것도 쓰지 않는다" 단언에서 빠진다).
     */
    fun recordCounts(): Map<String, Int> = mapOf(
        "engineMessages" to engineMessages.size,
        "settingsWrites" to settingsWrites.size,
        "runtimeWrites" to runtimeWrites.size,
        "analysisWrites" to analysisWrites.size,
        "scoreWrites" to scoreWrites.size,
        "coreWrites" to coreWrites.size,
        "autoAiTurnWrites" to autoAiTurnWrites.size,
        "cacheOptimizationWrites" to cacheOptimizationWrites.size,
        "benchmarkWrites" to benchmarkWrites.size,
        "turnTimeWrites" to turnTimeWrites.size,
        "quietUntilWrites" to quietUntilWrites.size,
        "pendingUndoSyncWrites" to pendingUndoSyncWrites.size,
        "otherSetterCalls" to otherSetterCalls.size,
        "quietWindowClears" to quietWindowClears,
        "endgameReviewActivations" to endgameReviewActivations,
        "androidContextReads" to androidContextReads,
        "runtimeLog" to runtimeLog.lines.size,
        "diagnosticLog" to diagnosticLog.events.size,
        "queuedCoroutines" to dispatcher.queuedCount,
        "parkedDelays" to dispatcher.parkedDelayMillis.size,
    )

    /**
     * 무르기 복원 캐시에 항목 하나를 넣는다 — 리셋·복원이 그것을 비우는지 보려는 것이다.
     * 캐시는 "완결된 엔진 후보가 있는 결과"만 받으므로([CachedAnalysisResult.canRestoreAfterUndo])
     * 그 조건을 채워 만든다.
     */
    fun seedUndoRestoreCache(positionFingerprint: String = "seeded-by-test") {
        val state = GameState.empty(boardSize = BoardSize.Nine)
        val candidate = CandidateMove(
            move = Move.Play(StoneColor.Black, BoardCoordinate(row = 4, column = 4)),
            pointLoss = 0.0,
        )
        undoAnalysisRestoreCache.put(
            AnalysisCacheKey(
                positionFingerprint = positionFingerprint,
                preset = AnalysisPreset.Lite,
                limit = AnalysisLimit(visits = 16, timeMillis = 250, candidateCount = 1),
                deep = false,
            ),
            CachedAnalysisResult(
                snapshot = MoveAnalysisSnapshot.from(state, listOf(candidate)),
                candidateText = "seeded",
                quality = PositionAnalysisCacheQuality(
                    tier = PositionAnalysisCacheQualityTier.Complete,
                    rootVisits = 16,
                    requestedRootVisits = 16,
                ),
            ),
        )
        check(undoRestoreEntries() >= 1) { "무르기 복원 캐시에 씨앗이 들어가지 않았다 — 캐시가 받는 조건이 바뀌었다." }
    }

    /** 무르기 복원 캐시의 항목 수(`statsText()`의 `undoRestoreEntries=N`). */
    fun undoRestoreEntries(): Int = undoAnalysisRestoreCache.statsText().substringAfter("undoRestoreEntries=").toInt()

    val runtimeLog = RecordingRuntimeEventLog()
    val diagnosticLog = RecordingDiagnosticEventLog()

    override val androidContext: android.content.Context
        get() {
            androidContextReads++
            throw AndroidContextTouched()
        }
    override val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
    override val diagnosticEventLog: DiagnosticEventLogPort = diagnosticLog
    override val runtimeEventLog: RuntimeEventLogPort = runtimeLog
    override val preferencesStore: UserPreferencesStorePort = UnusedPreferencesStore
    override val debugReportMirror: DebugReportMirrorPort = object : DebugReportMirrorPort {
        override fun save(report: String) = Unit
    }
    override val clipboardPort: ClipboardPort = object : ClipboardPort {
        override fun setText(label: String, text: String): Boolean = true
    }
    override val userNoticePort: UserNoticePort = object : UserNoticePort {
        override fun showShort(message: String) = Unit
    }

    // 앱(GoCoachApp.kt)의 `remember { EngineOperationLifecycleController(...) }`와 같은 인자.
    override val lifecycleController: EngineOperationLifecycleController = EngineOperationLifecycleController(
        scope = scope,
        runtimeEventLog = runtimeLog,
        diagnosticEventLog = diagnosticLog,
        currentRuntimeLogContext = { currentRuntimeLogContext() },
        currentState = { gameState() },
        currentSessionGeneration = { runtimeState().sessionGeneration },
        onBusyChanged = { busy, blocking, _, _ ->
            engineIsBusy = busy
            engineIsBlockingBusy = blocking
        },
    )

    // 앱의 `remember { GameSessionDisplayStateApplier(...) }`와 같은 인자.
    override val displayStateApplier: GameSessionDisplayStateApplier = GameSessionDisplayStateApplier(
        currentCoreState = { holder.current.core },
        applyCoreState = ::applyCoreSessionState,
        appendEngineOperationDiscardLog = lifecycleController::appendDiscardLog,
    )
    override val defaultPlayLevel: PlayLevelSetting = PlayLevelSetting()
    override val analysisCache: AnalysisResultCache = AnalysisResultCache(maxEntries = 96)
    override val undoAnalysisRestoreCache: UndoAnalysisRestoreCache = UndoAnalysisRestoreCache(maxEntries = 96)
    override val deferredTopMoveAnalysis: TopMoveAnalysisDeferral = TopMoveAnalysisDeferral()

    private fun <T> read(getter: String, value: () -> T): T {
        getterReads += getter
        return value()
    }

    // ── 테스트가 바깥에서 상태를 바꾸는 손잡이(기록에 남지 않는다) ──
    fun changeSession(transform: (GameSessionControllerState) -> GameSessionControllerState) = holder.update(transform)

    fun changeCore(transform: (GameSessionCoreState) -> GameSessionCoreState) = holder.updateCore(transform)

    fun changeSettings(transform: (GameSessionSettingsState) -> GameSessionSettingsState) =
        holder.update { it.withSettings(transform(it.settings)) }

    fun changePlayerSetup(setup: PlayerSetup) = changeSettings { it.applyPlayerSetup(setup) }

    // ── 게터: 전부 호출 시점의 값. 읽을 때마다 [getterReads]에 이름을 남긴다 ──
    override fun savedSessionRawJson(): String? = read("savedSessionRawJson") { savedSessionJson }
    override fun storedBenchmarkText(): String = read("storedBenchmarkText") { benchmarkRawText }
    override fun sessionSnapshot(): GameSessionControllerState = read("sessionSnapshot") { holder.current }
    override fun gameState(): GameState = read("gameState") { holder.current.gameState }
    override fun playerSetup(): PlayerSetup = read("playerSetup") { holder.current.settings.playerSetup }
    override fun analysisState(): GameSessionAnalysisState = read("analysisState") { holder.current.core.analysisState }
    override fun scoreState(): GameSessionScoreState = read("scoreState") { holder.current.core.scoreState }
    override fun moveReviewState(): GameSessionMoveReviewState = read("moveReviewState") { holder.current.core.moveReviewState }
    override fun runtimeState(): GameSessionRuntimeState = read("runtimeState") { holder.current.core.runtimeState }
    override fun settingsState(): GameSessionSettingsState = read("settingsState") { holder.current.settings }
    override fun autoAiTurnUiState(): AutoAiTurnUiState = read("autoAiTurnUiState") { holder.current.autoAiTurn }
    override fun positionCacheOptimizationState(): PositionAnalysisCacheOptimizationUiState =
        read("positionCacheOptimizationState") { holder.current.positionCacheOptimization }
    override fun benchmarkUiState(): EngineBenchmarkUiState = read("benchmarkUiState") { holder.current.benchmark }
    override fun savedSessionUiState(): SavedSessionUiState = read("savedSessionUiState") { holder.current.savedSession }
    override fun turnTimeState(): GameSessionTurnTimeState = read("turnTimeState") { holder.current.core.turnTimeState }
    override fun undoEngineInterventionQuietUntil(): Long = read("undoEngineInterventionQuietUntil") { quietUntil }
    override fun isPendingUndoSync(): Boolean = read("isPendingUndoSync") { undoSyncIsPending }
    override fun isEngineReady(): Boolean = read("isEngineReady") { engineIsReady }
    override fun isEngineBusy(): Boolean = read("isEngineBusy") { engineIsBusy }
    override fun isEngineBlockingBusy(): Boolean = read("isEngineBlockingBusy") { engineIsBlockingBusy }
    override fun isGameEnded(): Boolean = read("isGameEnded") { holder.current.isGameEnded }
    override fun shouldShowResumePrompt(): Boolean = read("shouldShowResumePrompt") { holder.current.savedSession.shouldShowResumePrompt }
    override fun matchMode(): MatchMode = read("matchMode") { holder.current.settings.matchMode }
    override fun topMovesEnabled(): Boolean = read("topMovesEnabled") { holder.current.settings.topMovesEnabled }
    override fun showMoveReviewEnabled(): Boolean = read("showMoveReviewEnabled") { moveReviewEnabled }
    override fun engineName(): String = read("engineName") { currentEngineName }
    override fun engineDiagnostic(): String = read("engineDiagnostic") { currentEngineDiagnostic }

    override fun currentRuntimeLogContext(): RuntimeLogContext = read("currentRuntimeLogContext") {
        holder.current.toRuntimeLogContext(
            engineName = currentEngineName,
            engineDiagnostic = currentEngineDiagnostic,
            isEngineReady = engineIsReady,
            isEngineBusy = engineIsBusy,
            analysisCacheStats = "${analysisCache.statsText()}, ${undoAnalysisRestoreCache.statsText()}",
            turnTimeText = holder.current.core.turnTimeState.runtimeText(),
        )
    }

    // ── 세터: 기록하고, 앱처럼 홀더에 되쓴다 ──
    override fun setGameState(value: GameState) {
        otherSetterCalls += "setGameState"
        holder.updateCore { it.copy(gameState = value) }
    }

    override fun setEngineMessage(value: String) {
        engineMessages += value
        holder.updateCore { it.copy(engineMessage = value) }
    }

    override fun setAnalysisState(value: GameSessionAnalysisState) {
        analysisWrites += value
        holder.updateCore { it.copy(analysisState = value) }
    }

    override fun setScoreState(value: GameSessionScoreState) {
        scoreWrites += value
        holder.updateCore { it.copy(scoreState = value) }
    }

    override fun setMoveReviewState(value: GameSessionMoveReviewState) {
        otherSetterCalls += "setMoveReviewState"
        holder.updateCore { it.copy(moveReviewState = value) }
    }

    override fun setRuntimeState(value: GameSessionRuntimeState) {
        runtimeWrites += value
        holder.updateCore { it.copy(runtimeState = value) }
    }

    override fun setSettingsState(value: GameSessionSettingsState) {
        settingsWrites += value
        holder.update { it.withSettings(value) }
    }

    override fun setAutoAiTurnUiState(value: AutoAiTurnUiState) {
        autoAiTurnWrites += value
        holder.update { it.withAutoAiTurn(value) }
    }

    override fun setPositionCacheOptimizationState(value: PositionAnalysisCacheOptimizationUiState) {
        cacheOptimizationWrites += value
        holder.update { it.withPositionCacheOptimization(value) }
    }

    override fun setBenchmarkUiState(value: EngineBenchmarkUiState) {
        benchmarkWrites += value
        holder.update { it.withBenchmark(value) }
    }

    override fun setSavedSessionUiState(value: SavedSessionUiState) {
        otherSetterCalls += "setSavedSessionUiState"
        holder.update { it.withSavedSession(value) }
    }

    override fun setTurnTimeState(value: GameSessionTurnTimeState) {
        turnTimeWrites += value
        holder.updateCore { it.copy(turnTimeState = value) }
    }

    override fun setUndoEngineInterventionQuietUntil(value: Long) {
        quietUntilWrites += value
        quietUntil = value
    }

    override fun setPendingUndoSync(value: Boolean) {
        pendingUndoSyncWrites += value
        undoSyncIsPending = value
    }

    override fun setEngineReady(value: Boolean) {
        otherSetterCalls += "setEngineReady"
        engineIsReady = value
    }

    override fun setIsGameEnded(value: Boolean) {
        otherSetterCalls += "setIsGameEnded"
        holder.updateCore { it.copy(isGameEnded = value) }
    }

    // ── 행동: GoCoachApp.kt의 지역 함수들과 같은 일을 한다 ──
    override fun applyCoreSessionState(next: GameSessionCoreState) {
        coreWrites += next
        holder.updateCore { next }
        if (!next.isGameEnded) {
            holder.update { it.withPositionCacheOptimization(it.positionCacheOptimization.clearPrompt()) }
        }
    }

    override fun activateEndgameJudgementReview() {
        endgameReviewActivations++
    }

    // 앱: `fun clearUndoEngineInterventionQuietWindow() { undoEngineInterventionQuietUntil = 0L; cancelUndoSync() }`
    override fun clearUndoEngineInterventionQuietWindow() {
        quietWindowClears++
        quietUntil = 0L
        cancelUndoSync()
    }

    override fun engineProfileTimeoutPolicy(profile: EngineProfile): EngineTimeoutPolicy =
        EngineTimeoutPolicy(
            timeoutMillis = profile.analysisLimit.timeMillis,
            label = "${profile.difficulty.label}:${profile.analysisLimit.visits}v",
        )

    override fun applyFinalScoreWithJudgement(final: FinalScoreDisplayPlan) {
        activateEndgameJudgementReview()
        displayStateApplier.applyFinalScoreDisplayPlan(final)
    }
}

/**
 * 앱이 시작할 때와 **같은 길**(`buildInitialUserPreferencesPlan` → `buildInitialSessionState`)로 만든
 * 세션을, 대국이 진행 중인 상태로 돌려준다. 앱의 첫 상태는 대국 전 미리보기(`isGameEnded = true`)라
 * 그대로 쓰면 대국 중에만 도는 흐름이 전부 막힌다.
 */
internal fun inGameSession(
    playerSetup: PlayerSetup = PlayerSetup(),
    ruleset: Ruleset = Ruleset.Japanese,
    boardSize: BoardSize = BoardSize.Thirteen,
): GameSessionControllerState {
    val plan = buildInitialUserPreferencesPlan(
        preferences = UserPreferencesSnapshot(boardSize = boardSize, playerSetup = playerSetup, ruleset = ruleset),
        defaultPlayLevel = PlayLevelSetting(),
        currentProfile = EngineProfile(),
    )
    val preview = buildInitialSessionState(
        initialPlan = plan,
        engineDiagnostic = "diagnostic-initial",
        benchmarkStore = FakeEngineBenchmarkStore(),
        storedBenchmarkText = "No engine benchmark file recorded.",
    )
    return preview.withCore(preview.core.copy(isGameEnded = false))
}

/**
 * `launch`를 받으면 [Runnable]을 줄에 세우기만 하고 **실행하지 않는** 디스패처.
 * kotlinx-coroutines-test가 이 모듈에 없어서 직접 둔다 — "실행이 걸렸는가"와 "걸린 블록을 원할 때
 * 한 번 돌린다"만 있으면 된다.
 *
 * ## ⚠️ [Delay]를 직접 구현한다 — `delay()`는 기록만 남기고 영원히 멈춘다
 * 구현하지 않으면 `delay()`는 kotlinx의 `DefaultExecutor` 스레드로 새서 **실제 시간이 지난 뒤** 이
 * 줄에 끼어든다 — 테스트가 타이밍에 기대게 된다. 여기서는 멈춘 길이만 [parkedDelayMillis]에 남기고
 * 재개하지 않는다. 멈춘 코루틴이 나중에 취소되면 그 재개(취소 예외)는 평소처럼 줄에 선다.
 *
 * 줄은 스레드 안전하다 — `runEngineIo`가 `Dispatchers.IO`에서 돌아올 때 **그 스레드가** 여기에 넣는다.
 */
@OptIn(InternalCoroutinesApi::class)
internal class QueueOnlyDispatcher : CoroutineDispatcher(), Delay {
    private val queue = LinkedBlockingDeque<Runnable>()
    private val parked = CopyOnWriteArrayList<Long>()

    val queuedCount: Int get() = queue.size

    /** `delay()`가 멈춘 길이들(밀리초), 멈춘 순서대로. */
    val parkedDelayMillis: List<Long> get() = parked.toList()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.addLast(block)
    }

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        parked += timeMillis
    }

    /** 줄의 맨 앞 블록을 **이 스레드에서** 한 번 돌린다. 돌린 것이 있으면 `true`. */
    fun runNext(): Boolean {
        val next = queue.pollFirst() ?: return false
        next.run()
        return true
    }

    /**
     * 줄에 블록이 설 때까지 최대 [timeoutMillis] 기다렸다가 **이 스레드에서** 한 번 돌린다.
     * 엔진 작업이 `Dispatchers.IO`에서 돌아오는 복귀를 받는 데 쓴다. 기한 안에 아무것도 안 오면 `false`.
     */
    fun runNextArrivingWithin(timeoutMillis: Long = 5_000L): Boolean {
        val next = queue.pollFirst(timeoutMillis, TimeUnit.MILLISECONDS) ?: return false
        next.run()
        return true
    }
}

/** 배선이 [GoCoachAppWiringContext.androidContext]를 읽었다는 표지. */
internal class AndroidContextTouched : IllegalStateException("androidContext was read")

internal class RecordingRuntimeEventLog : RuntimeEventLogPort {
    val lines = mutableListOf<String>()

    override fun append(event: String, nowMillis: Long) {
        lines += event
    }

    override fun readText(): String = lines.joinToString("\n")

    override fun clear() = lines.clear()
}

internal class RecordingDiagnosticEventLog : DiagnosticEventLogPort {
    val events = mutableListOf<DiagnosticEvent>()

    override fun append(event: DiagnosticEvent, nowMillis: Long) {
        events += event
    }

    override fun readText(): String = events.joinToString("\n") { it.code }

    override fun clear() = events.clear()
}

internal class FakeEngineBenchmarkStore(
    var profile: EngineBenchmarkProfile? = null,
) : EngineBenchmarkStorePort {
    override fun exists(): Boolean = profile != null

    override fun save(profile: EngineBenchmarkProfile) {
        this.profile = profile
    }

    override fun load(): EngineBenchmarkProfile? = profile
}

/** 배선은 환경설정 저장소를 어떤 컨트롤러에도 넘기지 않는다 — 닿으면 그 자체가 새 배선이다. */
private object UnusedPreferencesStore : UserPreferencesStorePort {
    override fun save(snapshot: UserPreferencesSnapshot) = error("preferencesStore is not wired to any controller")

    override fun load(): UserPreferencesSnapshot = error("preferencesStore is not wired to any controller")
}
