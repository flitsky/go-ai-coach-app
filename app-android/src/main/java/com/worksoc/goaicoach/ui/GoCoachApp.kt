package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.session.*

import com.worksoc.goaicoach.application.autoai.*
import com.worksoc.goaicoach.application.engine.*
import com.worksoc.goaicoach.application.runtime.*
import com.worksoc.goaicoach.application.score.*
import com.worksoc.goaicoach.application.topmoves.*
import com.worksoc.goaicoach.application.undo.*

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import com.worksoc.goaicoach.application.analysis.AnalysisCacheKey
import com.worksoc.goaicoach.application.analysis.AnalysisResultCache
import com.worksoc.goaicoach.application.debugreport.DebugReportCopyRunRequest
import com.worksoc.goaicoach.application.debugreport.runDebugReportCopyApplication
import com.worksoc.goaicoach.application.preferences.buildInitialUserPreferencesPlan
import com.worksoc.goaicoach.application.analysis.PositionCacheOptimizationController
import com.worksoc.goaicoach.application.preferences.UserPreferencesAutosaveRequest
import com.worksoc.goaicoach.application.preferences.runUserPreferencesAutosave
import com.worksoc.goaicoach.application.engine.operation.EngineOperationGate
import com.worksoc.goaicoach.application.engine.operation.EngineOperationLifecycleCallbacks
import com.worksoc.goaicoach.application.engine.operation.EngineOperationLifecycleController
import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
import com.worksoc.goaicoach.application.debugreport.DebugReportMirrorPort
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.humanmove.HumanEngineSyncCompletionApplyPlan
import com.worksoc.goaicoach.application.humanmove.HumanEngineSyncDisplayPlan
import com.worksoc.goaicoach.application.humanmove.HumanEngineSyncFailurePlan
import com.worksoc.goaicoach.application.humanmove.HumanEngineSyncRunRequest
import com.worksoc.goaicoach.application.humanmove.HumanEngineSyncRuntimeLogPlan
import com.worksoc.goaicoach.application.humanmove.applyHumanMoveLocally
import com.worksoc.goaicoach.application.humanmove.runHumanEngineSyncApplication
import com.worksoc.goaicoach.application.debugreport.ClipboardPort
import com.worksoc.goaicoach.application.engine.operation.evaluateScoringRuleChangeGate
import com.worksoc.goaicoach.application.engine.operation.evaluateSearchTimeChangeGate
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationUiState
import com.worksoc.goaicoach.application.engine.localScoreSnapshot
import com.worksoc.goaicoach.application.savedgame.SavedGamePersistenceRunRequest
import com.worksoc.goaicoach.application.savedgame.SavedGameRestorePlan
import com.worksoc.goaicoach.application.savedgame.SavedGameRestoreRunRequest
import com.worksoc.goaicoach.application.savedgame.SavedGameRestoreRunResult
import com.worksoc.goaicoach.application.savedgame.SavedSessionUiState
import com.worksoc.goaicoach.application.savedgame.SavedSessionPromptRunRequest
import com.worksoc.goaicoach.application.savedgame.runSavedGamePersistenceApplication
import com.worksoc.goaicoach.application.savedgame.runSavedGameRestoreApplication
import com.worksoc.goaicoach.application.savedgame.runSavedSessionPromptApplication
import com.worksoc.goaicoach.application.startgame.GameSessionResetPlan
import com.worksoc.goaicoach.application.startgame.StartEngineBackedGameRunRequest
import com.worksoc.goaicoach.application.startgame.StartConfiguredGamePlan
import com.worksoc.goaicoach.application.startgame.buildNewLocalGameSessionPlan
import com.worksoc.goaicoach.application.startgame.buildStartConfiguredGamePlan
import com.worksoc.goaicoach.application.startgame.runStartEngineBackedGameApplication
import com.worksoc.goaicoach.application.savedgame.SavedGameStorePort
import com.worksoc.goaicoach.application.analysis.UndoAnalysisRestoreCache
import com.worksoc.goaicoach.application.preferences.UserPreferencesStorePort
import com.worksoc.goaicoach.application.debugreport.UserNoticePort
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.persistence.GameSessionStore
import com.worksoc.goaicoach.persistence.EngineBenchmarkStore
import com.worksoc.goaicoach.persistence.DebugReportMirrorStore
import com.worksoc.goaicoach.persistence.RuntimeEventLog
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.persistence.UserPreferencesStore
import com.worksoc.goaicoach.presentation.GameUiEvent
import com.worksoc.goaicoach.presentation.GoCoachScreenStateAssembler
import com.worksoc.goaicoach.presentation.KaTrainUxOptions
import com.worksoc.goaicoach.presentation.buildGameUiEventHandlers
import com.worksoc.goaicoach.presentation.dispatchGameUiEvent
import com.worksoc.goaicoach.presentation.toKaTrainUxOptions
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreTimeline
import com.worksoc.goaicoach.shared.describe
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import java.io.File

private data class PendingPostUndoEngineSync(
    val targetState: GameState,
    val quietUntilMillis: Long,
)

/**
 * Bridges a Compose `var` to an off-Compose owner.
 *
 * Reads come from [get] (the current Compose mirror); writes go through [set]
 * (which updates the platform-independent [GameSessionStateHolder] and re-syncs
 * the mirror synchronously, preserving read-your-writes semantics within a
 * single callback). This lets the session state move out of the composable
 * without rewriting every call site at once.
 */
private class HolderBackedState<T>(
    private val get: () -> T,
    private val set: (T) -> Unit,
) : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = get()
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = set(value)
}

@Composable
internal fun GoCoachApp(
    engineClient: EngineSessionClient,
    engineName: String,
    engineDiagnostic: String,
    diagnosticEventLog: DiagnosticEventLogPort,
) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF2F6B4F),
            secondary = Color(0xFF546E7A),
            background = Color(0xFFF7F4EC),
            surface = Color(0xFFFFFCF4),
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            GoCoachScreen(
                engineClient = engineClient,
                engineName = engineName,
                engineDiagnostic = engineDiagnostic,
                diagnosticEventLog = diagnosticEventLog,
            )
        }
    }
}

@Composable
private fun GoCoachScreen(
    engineClient: EngineSessionClient,
    engineName: String,
    engineDiagnostic: String,
    diagnosticEventLog: DiagnosticEventLogPort,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sessionStore: SavedGameStorePort = remember(context) { GameSessionStore(context) }
    val preferencesStore: UserPreferencesStorePort = remember(context) { UserPreferencesStore(context) }
    val benchmarkStore: EngineBenchmarkStorePort = remember(context) { EngineBenchmarkStore(context) }
    val debugReportMirror: DebugReportMirrorPort = remember(context) { DebugReportMirrorStore(context) }
    val clipboardPort: ClipboardPort = remember(context) { AndroidClipboardPort(context) }
    val userNoticePort: UserNoticePort = remember(context) { AndroidUserNoticePort(context) }
    val runtimeEventLog: RuntimeEventLogPort = remember(context) {
        RuntimeEventLog(File(context.filesDir, RuntimeEventLog.FileName))
    }
    val initialPreferences = remember(preferencesStore) { preferencesStore.load() }
    val defaultPlayLevel = remember { PlayLevelSetting() }
    val initialPlan = remember(initialPreferences, defaultPlayLevel) {
        buildInitialUserPreferencesPlan(
            preferences = initialPreferences,
            defaultPlayLevel = defaultPlayLevel,
            currentProfile = EngineProfile(),
        )
    }
    // Session domain state is owned by a platform-independent holder. The
    // composable keeps a Compose snapshot mirror so reads still drive
    // recomposition; the delegated vars below read the mirror and write through
    // the holder, so existing call sites are unchanged.
    val sessionHolder = remember {
        GameSessionStateHolder(
            buildGameSessionControllerState(
                gameState = initialPlan.gameState,
                isGameEnded = false,
                analysisState = GameSessionAnalysisState.empty(
                    state = initialPlan.gameState,
                    candidateText = engineDiagnostic,
                ),
                scoreState = GameSessionScoreState.reset(
                    scoreText = "No score estimate yet.",
                    scoreSnapshots = listOf(localScoreSnapshot(initialPlan.gameState)),
                    endgameLog = "No endgame result recorded.",
                ),
                runtimeState = GameSessionRuntimeState(
                    playLevel = initialPlan.runtime.playLevel,
                    engineProfile = initialPlan.runtime.engineProfile,
                    analysisPreset = initialPlan.runtime.analysisPreset,
                ),
                moveReviewState = GameSessionMoveReviewState.reset(
                    moveReviewText = "No move review yet.",
                    lastMoveText = "None",
                ),
                engineMessage = "Engine not initialized.",
                settings = initialPlan.toGameSessionSettingsState(),
                benchmark = EngineBenchmarkUiState.initial(
                    benchmarkText = benchmarkStore.loadText(),
                    profile = benchmarkStore.load(),
                ),
                savedSession = SavedSessionUiState(),
                autoAiTurn = AutoAiTurnUiState(),
                positionCacheOptimization = PositionAnalysisCacheOptimizationUiState(),
            ),
        )
    }
    var sessionSnapshot by remember { mutableStateOf(sessionHolder.current) }

    LaunchedEffect(sessionHolder) {
        sessionHolder.state.collect { snapshot ->
            sessionSnapshot = snapshot
        }
    }

    fun mutateSession(transform: (GameSessionControllerState) -> GameSessionControllerState) {
        sessionHolder.update(transform)
        sessionSnapshot = sessionHolder.current
    }

    fun mutateCore(transform: (GameSessionCoreState) -> GameSessionCoreState) {
        sessionHolder.updateCore(transform)
        sessionSnapshot = sessionHolder.current
    }

    var gameState by HolderBackedState(
        { sessionSnapshot.gameState },
        { value -> mutateCore { it.copy(gameState = value) } },
    )
    var engineMessage by HolderBackedState(
        { sessionSnapshot.engineMessage },
        { value -> mutateCore { it.copy(engineMessage = value) } },
    )
    var analysisState by HolderBackedState(
        { sessionSnapshot.core.analysisState },
        { value -> mutateCore { it.copy(analysisState = value) } },
    )
    var scoreState by HolderBackedState(
        { sessionSnapshot.core.scoreState },
        { value -> mutateCore { it.copy(scoreState = value) } },
    )
    var moveReviewState by HolderBackedState(
        { sessionSnapshot.core.moveReviewState },
        { value -> mutateCore { it.copy(moveReviewState = value) } },
    )
    var runtimeState by HolderBackedState(
        { sessionSnapshot.core.runtimeState },
        { value -> mutateCore { it.copy(runtimeState = value) } },
    )
    var isGameEnded by HolderBackedState(
        { sessionSnapshot.isGameEnded },
        { value -> mutateCore { it.copy(isGameEnded = value) } },
    )
    var settingsState by HolderBackedState(
        { sessionSnapshot.settings },
        { value -> mutateSession { it.withSettings(value) } },
    )
    var benchmarkUiState by HolderBackedState(
        { sessionSnapshot.benchmark },
        { value -> mutateSession { it.withBenchmark(value) } },
    )
    var savedSessionUiState by HolderBackedState(
        { sessionSnapshot.savedSession },
        { value -> mutateSession { it.withSavedSession(value) } },
    )
    var autoAiTurnUiState by HolderBackedState(
        { sessionSnapshot.autoAiTurn },
        { value -> mutateSession { it.withAutoAiTurn(value) } },
    )
    var positionCacheOptimizationState by HolderBackedState(
        { sessionSnapshot.positionCacheOptimization },
        { value -> mutateSession { it.withPositionCacheOptimization(value) } },
    )

    var turnTimeState by remember {
        mutableStateOf(
            GameSessionTurnTimeState.reset(
                state = initialPlan.gameState,
                nowMillis = System.currentTimeMillis(),
            ),
        )
    }
    var isEngineBusy by remember { mutableStateOf(false) }
    var isEngineReady by remember { mutableStateOf(false) }
    val analysisCache = remember { AnalysisResultCache(maxEntries = 96) }
    val undoAnalysisRestoreCache = remember { UndoAnalysisRestoreCache(maxEntries = 96) }
    var uxOptions by remember { mutableStateOf(initialPreferences.toKaTrainUxOptions()) }
    var isDisplayMenuExpanded by remember { mutableStateOf(false) }
    var isScoreGraphExpanded by remember { mutableStateOf(false) }
    var hasCompletedEngineStartup by remember { mutableStateOf(false) }
    var hasCheckedEngineBenchmark by remember {
        mutableStateOf(
            benchmarkStore.hasUsableProfile(
                samplesPerVisit = EngineBenchmarkDefaultSamplesPerVisit,
                timeCapMs = EngineBenchmarkDefaultTimeCapMs,
                measurementVersion = EngineBenchmarkMeasurementVersion,
                visitsTargets = EngineBenchmarkDefaultVisits,
            ),
        )
    }

    val playerSetup = settingsState.playerSetup
    val matchMode = settingsState.matchMode
    val autoPlayDelaySetting = settingsState.autoPlayDelaySetting
    val searchTimeSettings = settingsState.searchTimeSettings
    val topMovesEnabled = settingsState.topMovesEnabled
    val pendingSavedSession = savedSessionUiState.pendingSavedSession
    val shouldShowResumePrompt = savedSessionUiState.shouldShowResumePrompt
    val hasCheckedSavedSession = savedSessionUiState.hasCheckedSavedSession
    val isAutoAiTurnPending = autoAiTurnUiState.isPending
    var undoEngineInterventionQuietUntil by remember { mutableStateOf(0L) }
    var pendingPostUndoEngineSync by remember { mutableStateOf<PendingPostUndoEngineSync?>(null) }
    var pendingPostUndoEngineSyncJob by remember { mutableStateOf<Job?>(null) }

    fun markUndoEngineInterventionQuiet(): Long {
        val quietUntil = undoEngineInterventionQuietUntilMillis(System.currentTimeMillis())
        undoEngineInterventionQuietUntil = quietUntil
        return quietUntil
    }

    fun cancelPendingPostUndoEngineSync() {
        pendingPostUndoEngineSyncJob?.cancel()
        pendingPostUndoEngineSyncJob = null
        pendingPostUndoEngineSync = null
    }

    fun clearUndoEngineInterventionQuietWindow() {
        undoEngineInterventionQuietUntil = 0L
        cancelPendingPostUndoEngineSync()
    }

    fun currentControllerSessionState(): GameSessionControllerState =
        buildGameSessionControllerState(
            gameState = gameState,
            isGameEnded = isGameEnded,
            analysisState = analysisState,
            scoreState = scoreState,
            runtimeState = runtimeState,
            moveReviewState = moveReviewState,
            engineMessage = engineMessage,
            settings = settingsState,
            benchmark = benchmarkUiState,
            savedSession = savedSessionUiState,
            autoAiTurn = autoAiTurnUiState,
            positionCacheOptimization = positionCacheOptimizationState,
        )

    fun currentRuntimeLogContext(): RuntimeLogContext {
        return currentControllerSessionState().toRuntimeLogContext(
            engineName = engineName,
            engineDiagnostic = engineDiagnostic,
            isEngineReady = isEngineReady,
            isEngineBusy = isEngineBusy,
            analysisCacheStats = "${analysisCache.statsText()}, ${undoAnalysisRestoreCache.statsText()}",
            turnTimeText = turnTimeState.runtimeText(),
        )
    }

    fun currentCoreSessionState(): GameSessionCoreState =
        currentControllerSessionState().core

    fun applyCoreSessionState(core: GameSessionCoreState) {
        gameState = core.gameState
        isGameEnded = core.isGameEnded
        analysisState = core.analysisState
        scoreState = core.scoreState
        runtimeState = core.runtimeState
        moveReviewState = core.moveReviewState
        engineMessage = core.engineMessage
        if (!core.isGameEnded) {
            positionCacheOptimizationState = positionCacheOptimizationState.clearPrompt()
        }
    }

    val displayStateApplier = remember {
        GameSessionDisplayStateApplier(
            currentCoreState = ::currentCoreSessionState,
            applyCoreState = ::applyCoreSessionState,
        )
    }

    // Remembered: the controller owns the single shared engine-operation
    // lifecycle state, so it must survive recomposition to track concurrent
    // operations correctly. Its closures read through stable remembered backing
    // stores, so they observe current values without re-keying.
    val lifecycleController = remember {
        EngineOperationLifecycleController(
            scope = scope,
            runtimeEventLog = runtimeEventLog,
            diagnosticEventLog = diagnosticEventLog,
            currentRuntimeLogContext = { currentRuntimeLogContext() },
            currentState = { gameState },
            onBusyChanged = { busy -> isEngineBusy = busy },
        )
    }

    fun engineProfileTimeoutPolicy(profile: EngineProfile): EngineTimeoutPolicy =
        EngineTimeoutPolicy(
            timeoutMillis = profile.analysisLimit.timeMillis,
            label = "${profile.difficulty.label}:${profile.analysisLimit.visits}v",
        )

    fun engineOperationLifecycleCallbacks(): EngineOperationLifecycleCallbacks =
        lifecycleController.callbacks()

    fun launchTrackedEngineOperation(
        operation: EngineOperationRequest,
        block: suspend () -> Unit,
    ): Job = lifecycleController.launchTracked(operation, block)

    suspend fun runTrackedEngineOperation(
        operation: EngineOperationRequest,
        block: suspend () -> Unit,
    ) = lifecycleController.runTracked(operation, block)

    fun appendEngineOperationDiscardLog(discard: EngineOperationResultGuard.Discard) =
        lifecycleController.appendDiscardLog(discard)

    LaunchedEffect(Unit) {
        runtimeEventLog.append(runtimeAppStartLog(currentRuntimeLogContext()))
    }

    fun applyEngineStartupDisplayPlan(startup: EngineStartupDisplayPlan) {
        isEngineReady = startup.isEngineReady
        displayStateApplier.applyEngineStartupDisplayPlan(startup)
    }

    LaunchedEffect(engineClient) {
        hasCompletedEngineStartup = false
        applyEngineStartupDisplayPlan(
            engineClient.runEngineStartupApplication(
                EngineStartupRunRequest(
                    state = gameState,
                    profile = runtimeState.engineProfile,
                    sessionGeneration = runtimeState.sessionGeneration,
                    engineDiagnostic = engineDiagnostic,
                    diagnosticEventLog = diagnosticEventLog,
                    lifecycleCallbacks = engineOperationLifecycleCallbacks(),
                ),
            ),
        )
        hasCompletedEngineStartup = true
    }

    LaunchedEffect(sessionStore) {
        runSavedSessionPromptApplication(
            SavedSessionPromptRunRequest(
                store = sessionStore,
                applyPrompt = { prompt ->
                    savedSessionUiState = savedSessionUiState.applyPrompt(prompt)
                },
            ),
        )
    }

    val benchmarkController = EngineBenchmarkController(
        scope = scope,
        engineClient = engineClient,
        store = benchmarkStore,
        diagnosticEventLog = diagnosticEventLog,
        lifecycleCallbacks = ::engineOperationLifecycleCallbacks,
        currentState = { gameState },
        sessionGeneration = { runtimeState.sessionGeneration },
        isEngineReady = { isEngineReady },
        isEngineBusy = { isEngineBusy },
        currentBenchmarkUiState = { benchmarkUiState },
        onBenchmarkUiState = { state -> benchmarkUiState = state },
        onEngineMessage = { message -> engineMessage = message },
        onDisplayPlan = { plan -> displayStateApplier.applyEngineBenchmarkDisplayPlan(plan) },
        onChecked = { hasCheckedEngineBenchmark = true },
    )

    LaunchedEffect(
        hasCompletedEngineStartup,
        isEngineReady,
    ) {
        benchmarkController.runStartupCheckIfNeeded(
            hasCompletedStartup = hasCompletedEngineStartup,
            engineReady = isEngineReady,
            supportsDeviceBenchmark = engineClient.capabilities.supportsDeviceBenchmark,
            hasCheckedBenchmark = hasCheckedEngineBenchmark,
        )
    }

    LaunchedEffect(
        preferencesStore,
        playerSetup,
        autoPlayDelaySetting,
        searchTimeSettings,
        topMovesEnabled,
        uxOptions,
        gameState.ruleset,
    ) {
        runUserPreferencesAutosave(
            request = UserPreferencesAutosaveRequest(
                settingsState = settingsState,
                ruleset = gameState.ruleset,
                showCoordinates = uxOptions.showCoordinates,
                showMoveNumbers = uxOptions.showMoveNumbers,
                showLastMoveRing = uxOptions.showLastMoveRing,
                showOwnershipOverlay = uxOptions.showOwnershipOverlay,
            ),
            store = preferencesStore,
        )
    }

    LaunchedEffect(
        savedSessionUiState,
        isGameEnded,
        gameState.moves.size,
        gameState.ruleset,
        playerSetup,
        runtimeState.playLevel,
        topMovesEnabled,
    ) {
        runSavedGamePersistenceApplication(
            SavedGamePersistenceRunRequest(
                savedSessionUiState = savedSessionUiState,
                isGameEnded = isGameEnded,
                gameState = gameState,
                playerSetup = playerSetup,
                playLevel = runtimeState.playLevel,
                topMovesEnabled = topMovesEnabled,
                nowMillis = System.currentTimeMillis(),
                store = sessionStore,
            ),
        )
    }

    fun currentAnalysisSessionState(): GameSessionAnalysisState = analysisState

    fun applyAnalysisSessionState(analysis: GameSessionAnalysisState) {
        analysisState = analysis
    }

    fun resetAnalysisSessionState(
        candidateText: String,
        reviewAnalysis: MoveAnalysisSnapshot,
    ) {
        applyAnalysisSessionState(
            GameSessionAnalysisState.reset(
                candidateText = candidateText,
                reviewAnalysis = reviewAnalysis,
            ),
        )
    }

    fun clearReviewAnalysis(state: GameState = gameState) {
        applyAnalysisSessionState(
            currentAnalysisSessionState()
                .clearReviewAnalysis(state)
                .copy(lastAnalysisKey = null),
        )
    }

    fun applyTopMoveAnalysisUpdate(
        update: TopMoveAnalysisUpdate,
        analysisKey: AnalysisCacheKey,
    ) {
        applyAnalysisSessionState(currentAnalysisSessionState().applyTopMoveAnalysisUpdate(update, analysisKey))
        engineMessage = update.engineMessage
    }

    fun applyScoreSyncCompletionApplyPlan(applyPlan: ScoreSyncCompletionApplyPlan): GameState? =
        when (applyPlan) {
            is ScoreSyncCompletionApplyPlan.ApplySuccess -> {
                displayStateApplier.applyScoreEstimateDisplayPlan(applyPlan.display)
                applyPlan.followUpAnalysisState
            }

            is ScoreSyncCompletionApplyPlan.ApplyFailure -> {
                engineMessage = applyPlan.engineMessage
                applyPlan.followUpAnalysisState
            }

            is ScoreSyncCompletionApplyPlan.Discard -> {
                appendEngineOperationDiscardLog(applyPlan.discard)
                null
            }
        }

    fun currentRuntimeSessionState(): GameSessionRuntimeState =
        runtimeState

    fun applyRuntimeSessionState(runtime: GameSessionRuntimeState) {
        runtimeState = runtime
    }

    fun applyRuntimePlayLevelSelection(selection: RuntimePlayLevelSelection) {
        applyRuntimeSessionState(currentRuntimeSessionState().applySelection(selection))
    }

    fun applyAutoAiTurnFailureDisplayPlan(error: Throwable) {
        displayStateApplier.applyAutoAiTurnFailureDisplayPlan(
            buildAutoAiTurnFailureDisplayPlan(error),
        )
    }

    fun applyHumanEngineSyncDisplayPlan(sync: HumanEngineSyncDisplayPlan): GameState? =
        when (sync) {
            is HumanEngineSyncDisplayPlan.FinalScore -> {
                displayStateApplier.applyFinalScoreDisplayPlan(sync.display)
                null
            }
            is HumanEngineSyncDisplayPlan.ScoreEstimate -> {
                displayStateApplier.applyScoreEstimateDisplayPlan(sync.display)
                analysisState = analysisState.copy(candidateText = sync.candidateText)
                sync.nextAnalysisState
            }
            HumanEngineSyncDisplayPlan.NoUpdate -> null
    }

    fun appendHumanEngineSyncRuntimeLog(
        logPlan: HumanEngineSyncRuntimeLogPlan,
        elapsedMs: Long,
    ) {
        when (logPlan) {
            is HumanEngineSyncRuntimeLogPlan.Success ->
                runtimeEventLog.append(
                    runtimeHumanEngineSyncSuccessLog(
                        context = currentRuntimeLogContext(),
                        sync = logPlan.display,
                        elapsedMs = elapsedMs,
                    ),
                )

            is HumanEngineSyncRuntimeLogPlan.Failure ->
                runtimeEventLog.append(
                    runtimeHumanEngineSyncFailureLog(
                        context = currentRuntimeLogContext(),
                        failure = logPlan.failure,
                        elapsedMs = elapsedMs,
                    ),
                )

            HumanEngineSyncRuntimeLogPlan.None -> Unit
        }
    }

    fun applyHumanEngineSyncCompletionApplyPlan(
        applyPlan: HumanEngineSyncCompletionApplyPlan,
        elapsedMs: Long,
    ): GameState? {
        appendHumanEngineSyncRuntimeLog(
            logPlan = applyPlan.runtimeLogPlan,
            elapsedMs = elapsedMs,
        )
        return when (applyPlan) {
            is HumanEngineSyncCompletionApplyPlan.ApplySuccess ->
                applyHumanEngineSyncDisplayPlan(applyPlan.display)

            is HumanEngineSyncCompletionApplyPlan.ApplyFailure -> {
                displayStateApplier.applyHumanEngineSyncFailurePlan(applyPlan.failure)
                null
            }

            is HumanEngineSyncCompletionApplyPlan.Discard -> {
                appendEngineOperationDiscardLog(applyPlan.discard)
                null
            }
        }
    }

    fun applyGameSessionResetPlan(reset: GameSessionResetPlan) {
        clearUndoEngineInterventionQuietWindow()
        undoAnalysisRestoreCache.clear()
        positionCacheOptimizationState = positionCacheOptimizationState.clearPrompt()
        applyCoreSessionState(currentCoreSessionState().applyGameSessionResetPlan(reset))
        turnTimeState = GameSessionTurnTimeState.reset(
            state = reset.gameState,
            nowMillis = System.currentTimeMillis(),
        )
        runtimeEventLog.append(
            runtimeGameResetLog(
                context = currentRuntimeLogContext(),
                reset = reset,
            ),
        )
    }

    fun applySavedGameRestorePlan(restore: SavedGameRestorePlan) {
        clearUndoEngineInterventionQuietWindow()
        undoAnalysisRestoreCache.clear()
        positionCacheOptimizationState = positionCacheOptimizationState.clearPrompt()
        settingsState = settingsState.applySavedGameRestore(
            restoredSetup = restore.playerSetup,
            restoredTopMovesEnabled = restore.topMovesEnabled,
        )
        applyCoreSessionState(currentCoreSessionState().applySavedGameRestorePlan(restore))
        turnTimeState = GameSessionTurnTimeState.reset(
            state = restore.gameState,
            nowMillis = System.currentTimeMillis(),
        )
    }

    fun applyUndoLocalStatePlan(undo: UndoLocalStatePlan) {
        displayStateApplier.applyUndoLocalStatePlan(undo)
        turnTimeState = turnTimeState.restartCurrentTurn(
            state = undo.gameState,
            nowMillis = System.currentTimeMillis(),
        )
    }

    fun applyScoringRuleChangePlan(ruleChange: ScoringRuleChangePlan) {
        applyCoreSessionState(currentCoreSessionState().applyScoringRuleChangePlan(ruleChange))
    }

    fun changePlayerSetup(nextSetup: PlayerSetup) {
        when (
            val plan = buildPlayerSetupChangePlan(
                nextSetup = nextSetup,
                currentState = gameState,
                currentProfile = runtimeState.engineProfile,
                defaultPlayLevel = defaultPlayLevel,
                isEngineBusy = isEngineBusy,
                searchTimeSettings = searchTimeSettings,
            )
        ) {
            is PlayerSetupChangePlan.ShowMessage -> {
                engineMessage = plan.message
            }
            is PlayerSetupChangePlan.Apply -> {
                clearUndoEngineInterventionQuietWindow()
                settingsState = settingsState.applyPlayerSetup(plan.playerSetup)
                applyCoreSessionState(currentCoreSessionState().applyPlayerSetupChangePlan(plan))
            }
        }
    }

    fun changeSearchTimeSettings(nextSettings: SearchTimeSettings) {
        when (val gate = evaluateSearchTimeChangeGate(isEngineBusy = isEngineBusy)) {
            EngineOperationGate.Allow -> Unit
            EngineOperationGate.NoOp -> return
            is EngineOperationGate.Block -> {
                engineMessage = gate.message
                return
            }
        }
        val normalized = nextSettings.normalized()
        clearUndoEngineInterventionQuietWindow()
        settingsState = settingsState.applySearchTimeSettings(normalized)
        applyRuntimePlayLevelSelection(
            selectRuntimePlayLevel(
                setup = playerSetup,
                nextPlayer = gameState.nextPlayer,
                currentProfile = runtimeState.engineProfile,
                defaultPlayLevel = defaultPlayLevel,
                searchTimeSettings = normalized,
            ),
        )
        runSearchTimeTopMovesResetApplication(
            SearchTimeTopMovesResetRunRequest(
                analysisState = currentAnalysisSessionState(),
                state = gameState,
                applyAnalysisState = ::applyAnalysisSessionState,
            ),
        )
    }

    fun requestTopMoveAnalysisForState(
        targetState: GameState,
        automatic: Boolean,
        deep: Boolean = false,
    ) {
        runTopMoveAnalysisApplication(
            TopMoveAnalysisRunRequest(
                engineClient = engineClient,
                controllerState = currentControllerSessionState(),
                targetState = targetState,
                deep = deep,
                automatic = automatic,
                pendingPostUndoEngineSync = pendingPostUndoEngineSync != null,
                isGameEnded = isGameEnded,
                isEngineReady = isEngineReady,
                isEngineBusy = isEngineBusy,
                shouldShowResumePrompt = shouldShowResumePrompt,
                playerSetup = playerSetup,
                analysisCacheEnabled = analysisCache.isEnabled,
                cachedResultFor = { key ->
                    undoAnalysisRestoreCache.get(key) ?: analysisCache.get(key)
                },
                currentState = { gameState },
                currentAnalysisKey = { analysisState.lastAnalysisKey },
                currentSessionGeneration = { runtimeState.sessionGeneration },
                launchEngineOperation = { operation, block ->
                    launchTrackedEngineOperation(operation) {
                        block()
                    }
                },
                applyLaunchUpdate = { launchUpdate ->
                    analysisState = launchUpdate.analysisState
                    launchUpdate.engineMessage?.let { message -> engineMessage = message }
                },
                applyTopMoveAnalysisUpdate = ::applyTopMoveAnalysisUpdate,
                putUndoRestoreCache = { key, cached ->
                    undoAnalysisRestoreCache.put(key, cached)
                },
                putAnalysisCache = { key, cached ->
                    analysisCache.put(key, cached)
                },
                applyFailureDisplay = displayStateApplier::applyTopMoveAnalysisFailureDisplayPlan,
                appendEngineOperationDiscardLog = ::appendEngineOperationDiscardLog,
            ),
        )
    }

    fun schedulePostUndoLocalEngineSync(
        targetState: GameState,
        quietUntilMillis: Long,
    ) {
        val pending = PendingPostUndoEngineSync(
            targetState = targetState,
            quietUntilMillis = quietUntilMillis,
        )
        pendingPostUndoEngineSync = pending
        pendingPostUndoEngineSyncJob?.cancel()
        pendingPostUndoEngineSyncJob = launchUiEffect(scope) {
            val delayMillis = undoEngineInterventionRemainingDelayMillis(
                nowMillis = System.currentTimeMillis(),
                quietUntilMillis = pending.quietUntilMillis,
            )
            if (delayMillis > 0L) {
                delay(delayMillis)
            }
            while (pendingPostUndoEngineSync == pending && gameState == pending.targetState && isEngineBusy) {
                delay(100L)
            }
            if (
                pendingPostUndoEngineSync != pending ||
                gameState != pending.targetState ||
                !isEngineReady
            ) {
                if (pendingPostUndoEngineSync == pending) {
                    pendingPostUndoEngineSync = null
                }
                return@launchUiEffect
            }

            runPostUndoScoreSyncApplication(
                PostUndoScoreSyncRunRequest(
                    engineClient = engineClient,
                    state = pending.targetState,
                    profile = runtimeState.engineProfile,
                    previousSnapshots = scoreState.scoreSnapshots,
                    sessionGeneration = runtimeState.sessionGeneration,
                    timeoutPolicy = engineProfileTimeoutPolicy(runtimeState.engineProfile),
                    diagnosticEventLog = diagnosticEventLog,
                    currentState = { gameState },
                    currentSessionGeneration = { runtimeState.sessionGeneration },
                    runEngineOperation = { operation, block ->
                        runTrackedEngineOperation(operation) {
                            block()
                        }
                    },
                    applyCompletion = ::applyScoreSyncCompletionApplyPlan,
                    requestFollowUpAnalysis = { state ->
                        requestTopMoveAnalysisForState(
                            targetState = state,
                            automatic = true,
                        )
                    },
                ),
            )
            if (pendingPostUndoEngineSync == pending) {
                pendingPostUndoEngineSync = null
            }
        }
    }

    fun applyShowTopMovesStateUpdate(update: ShowTopMovesStateUpdate) {
        settingsState = update.settingsState
        analysisState = update.analysisState
        update.engineMessage?.let { message -> engineMessage = message }
    }

    fun showTopMovesForCurrentState() {
        runShowTopMovesApplication(
            ShowTopMovesRunRequest(
                controllerState = currentControllerSessionState(),
                isGameEnded = isGameEnded,
                isEngineReady = isEngineReady,
                isEngineBusy = isEngineBusy,
                shouldShowResumePrompt = shouldShowResumePrompt,
                playerSetup = playerSetup,
                applyUpdate = ::applyShowTopMovesStateUpdate,
                requestAnalysis = { analysisRequest ->
                    requestTopMoveAnalysisForState(
                        targetState = analysisRequest.targetState,
                        automatic = false,
                        deep = analysisRequest.deep,
                    )
                },
            ),
        )
    }

    fun hideTopMoves() {
        runHideTopMovesApplication(
            HideTopMovesRunRequest(
                controllerState = currentControllerSessionState(),
                applyUpdate = ::applyShowTopMovesStateUpdate,
            ),
        )
    }

    fun changeScoringRule(nextRuleset: Ruleset) {
        when (
            val gate = evaluateScoringRuleChangeGate(
                currentRuleset = gameState.ruleset,
                nextRuleset = nextRuleset,
                isEngineBusy = isEngineBusy,
            )
        ) {
            EngineOperationGate.Allow -> Unit
            EngineOperationGate.NoOp -> return
            is EngineOperationGate.Block -> {
                engineMessage = gate.message
                return
            }
        }

        val ruleChange = buildScoringRuleChangePlan(
            currentState = gameState,
            nextRuleset = nextRuleset,
            isGameEnded = isGameEnded,
            matchMode = matchMode,
            isEngineReady = isEngineReady,
            previousSnapshots = scoreState.scoreSnapshots,
        )
        val nextState = ruleChange.gameState
        applyScoringRuleChangePlan(ruleChange)

        if (!ruleChange.requiresEngineSync) {
            return
        }

        runScoringRuleSyncApplication(
            ScoringRuleSyncRunRequest(
                engineClient = engineClient,
                state = nextState,
                profile = runtimeState.engineProfile,
                previousSnapshots = scoreState.scoreSnapshots,
                sessionGeneration = runtimeState.sessionGeneration,
                timeoutPolicy = engineProfileTimeoutPolicy(runtimeState.engineProfile),
                diagnosticEventLog = diagnosticEventLog,
                engineMessage = "Scoring rule changed to ${nextRuleset.scoringLabel}; engine rules synchronized.",
                currentState = { gameState },
                currentSessionGeneration = { runtimeState.sessionGeneration },
                runEngineOperation = { operation, block ->
                    launchTrackedEngineOperation(operation) {
                        block()
                    }
                },
                applyCompletion = ::applyScoreSyncCompletionApplyPlan,
                requestFollowUpAnalysis = { state ->
                    requestTopMoveAnalysisForState(
                        targetState = state,
                        automatic = true,
                    )
                },
            ),
        )
    }

    fun resetLocalGame(
        message: String,
        ruleset: Ruleset = gameState.ruleset,
    ) {
        applyGameSessionResetPlan(buildNewLocalGameSessionPlan(message, ruleset))
    }

    fun restoreSavedSession(snapshot: SavedGameSnapshot) {
        val result = runSavedGameRestoreApplication(
            SavedGameRestoreRunRequest(
                snapshot = snapshot,
                currentProfile = runtimeState.engineProfile,
                defaultPlayLevel = defaultPlayLevel,
                isEngineBusy = isEngineBusy,
                isEngineReady = isEngineReady,
                searchTimeSettings = searchTimeSettings,
                showMessage = { message -> engineMessage = message },
                applyRestore = ::applySavedGameRestorePlan,
            ),
        )
        if (result !is SavedGameRestoreRunResult.Restored || !result.syncEngineAfterRestore) {
            return
        }

        runRestoredGameSyncApplication(
            RestoredGameSyncRunRequest(
                engineClient = engineClient,
                state = result.gameState,
                profile = result.engineProfile,
                sessionGeneration = runtimeState.sessionGeneration + 1L,
                timeoutPolicy = engineProfileTimeoutPolicy(result.engineProfile),
                diagnosticEventLog = diagnosticEventLog,
                currentState = { gameState },
                currentSessionGeneration = { runtimeState.sessionGeneration },
                runEngineOperation = { operation, block ->
                    launchTrackedEngineOperation(operation) {
                        block()
                    }
                },
                applyCompletion = ::applyScoreSyncCompletionApplyPlan,
                requestFollowUpAnalysis = { state ->
                    requestTopMoveAnalysisForState(
                        targetState = state,
                        automatic = true,
                    )
                },
            ),
        )
    }

    fun startEngineBackedNewGame(plan: StartConfiguredGamePlan.StartEngineGame) {
        runStartEngineBackedGameApplication(
            StartEngineBackedGameRunRequest(
                plan = plan,
                engineClient = engineClient,
                currentState = gameState,
                sessionGeneration = runtimeState.sessionGeneration,
                runtimeContextProvider = ::currentRuntimeLogContext,
                runtimeEventLog = runtimeEventLog,
                diagnosticEventLog = diagnosticEventLog,
                applyRuntime = ::applyRuntimePlayLevelSelection,
                launchEngineOperation = { operation, block ->
                    launchTrackedEngineOperation(operation) {
                        block()
                    }
                },
                resetLocalGame = ::resetLocalGame,
                currentScoreStateProvider = { scoreState },
                replaceScoreState = { state -> scoreState = state },
                currentStateProvider = { gameState },
                requestFollowUpAnalysis = { state ->
                    requestTopMoveAnalysisForState(
                        targetState = state,
                        automatic = true,
                    )
                },
            ),
        )
    }

    fun startConfiguredGame() {
        val targetRuleset = gameState.ruleset
        val targetSetup = playerSetup
        when (
            val plan = buildStartConfiguredGamePlan(
                setup = targetSetup,
                ruleset = targetRuleset,
                nextPlayer = gameState.nextPlayer,
                isEngineReady = isEngineReady,
                isEngineBusy = isEngineBusy,
                currentProfile = runtimeState.engineProfile,
                defaultPlayLevel = defaultPlayLevel,
                searchTimeSettings = searchTimeSettings,
            )
        ) {
            is StartConfiguredGamePlan.ShowMessage -> {
                engineMessage = plan.message
                return
            }
            is StartConfiguredGamePlan.ResetLocalGame -> {
                resetLocalGame(plan.message, plan.ruleset)
                return
            }
            is StartConfiguredGamePlan.StartEngineGame -> {
                startEngineBackedNewGame(plan)
            }
        }
    }

    fun changeAutoPlayDelay(setting: AutoPlayDelaySetting) {
        runtimeEventLog.append(
            runtimeAutoPlayDelayChangeLog(
                context = currentRuntimeLogContext(),
                from = autoPlayDelaySetting,
                to = setting,
            ),
        )
        settingsState = settingsState.applyAutoPlayDelay(setting)
    }

    suspend fun applyAutoAiEndgamePlan(endgamePlan: AutoAiTurnEndgamePlan.Resolve) {
        runAutoAiEndgameApplication(
            AutoAiEndgameRunRequest(
                endgamePlan = endgamePlan,
                engineClient = engineClient,
                previousSnapshotsProvider = { scoreState.scoreSnapshots },
                currentStateProvider = { gameState },
                currentSessionGenerationProvider = { runtimeState.sessionGeneration },
                runtimeContextProvider = ::currentRuntimeLogContext,
                runtimeEventLog = runtimeEventLog,
                diagnosticEventLog = diagnosticEventLog,
                markGameEnded = { isGameEnded = true },
                applyResolvedDisplay = displayStateApplier::applyFinalScoreDisplayPlan,
                applyFailureDisplay = displayStateApplier::applyEndgameFailureDisplayPlan,
                appendEngineOperationDiscardLog = ::appendEngineOperationDiscardLog,
            ),
        )
    }

    fun requestAiTurnForCurrentState() {
        when (
            val request = currentControllerSessionState().toAutoAiTurnRequestPlan(
                isEngineReady = isEngineReady,
                isEngineBusy = isEngineBusy,
            )
        ) {
            AutoAiTurnRequestPlan.Skip -> return
            is AutoAiTurnRequestPlan.Schedule -> {
                runScheduledAutoAiTurnApplication(
                    AutoAiScheduledTurnRunRequest(
                        schedule = request,
                        controllerStateProvider = ::currentControllerSessionState,
                        engineClient = engineClient,
                        runtimeStateProvider = { runtimeState },
                        searchTimeSettingsProvider = { searchTimeSettings },
                        scoreSnapshotsProvider = { scoreState.scoreSnapshots },
                        isEngineReady = { isEngineReady },
                        isEngineBusy = { isEngineBusy },
                        isGameEnded = { isGameEnded },
                        shouldShowResumePrompt = { shouldShowResumePrompt },
                        runtimeContextProvider = ::currentRuntimeLogContext,
                        runtimeEventLog = runtimeEventLog,
                        diagnosticEventLog = diagnosticEventLog,
                        delayMillis = { millis -> delay(millis) },
                        launchAutoAiEffect = { block ->
                            launchAutoAiEffect(scope) {
                                block()
                            }
                        },
                        applyScheduled = { schedule ->
                            autoAiTurnUiState = autoAiTurnUiState.applyAutoAiTurnRequestPlan(schedule)
                        },
                        applyCancelled = { cancel ->
                            autoAiTurnUiState = autoAiTurnUiState.applyAutoAiTurnScheduleValidationPlan(cancel)
                        },
                        markEngineOperationStarted = lifecycleController::markStarted,
                        markEngineOperationCompleted = lifecycleController::markCompleted,
                        recordTurnMove = { player, nowMillis, nextPlayer ->
                            turnTimeState.recordMove(
                                player = player,
                                nowMillis = nowMillis,
                                nextPlayer = nextPlayer,
                            )
                        },
                        applyTurnTimeUpdate = { update ->
                            turnTimeState = update.after
                        },
                        applyTurnDisplay = displayStateApplier::applyAutoAiTurnDisplayPlan,
                        resolveEndgame = ::applyAutoAiEndgamePlan,
                        applyTurnFailureDisplay = ::applyAutoAiTurnFailureDisplayPlan,
                        appendEngineOperationDiscardLog = ::appendEngineOperationDiscardLog,
                        completeAutoAiTurnRun = {
                            autoAiTurnUiState = autoAiTurnUiState.completeAutoAiTurnRun()
                        },
                        requestFollowUpAnalysis = { followUp ->
                            requestTopMoveAnalysisForState(
                                targetState = followUp.targetState,
                                automatic = followUp.automatic,
                                deep = followUp.deep,
                            )
                        },
                        currentStateProvider = { gameState },
                        currentSessionGenerationProvider = { runtimeState.sessionGeneration },
                    ),
                )
            }
        }
    }

    fun submitHumanMove(move: Move) {
        if (playerSetup.sideFor(gameState.nextPlayer).controller != SeatController.Human) {
            engineMessage = "It is not a human player's turn."
            return
        }
        if (move.player != gameState.nextPlayer) {
            engineMessage = "Move player does not match the current turn."
            return
        }
        if (isEngineBusy) {
            engineMessage = "Engine is busy. Wait for the current analysis."
            return
        }
        clearUndoEngineInterventionQuietWindow()

        val beforeMove = gameState
        val previousReviewCandidates = analysisState.reviewCandidateMoves
        val localMove = applyHumanMoveLocally(
            beforeMove = beforeMove,
            move = move,
            reviewAnalysis = analysisState.reviewAnalysis,
            previousMoveReviews = moveReviewState.moveReviews,
        )
            .onFailure { error ->
                engineMessage = error.message ?: "Illegal move."
            }
            .getOrNull()
            ?: return
        val afterMove = localMove.afterMove
        val turnTimeUpdate = turnTimeState.recordMove(
            player = move.player,
            nowMillis = System.currentTimeMillis(),
            nextPlayer = afterMove.nextPlayer,
        )

        runtimeEventLog.append(
            runtimeHumanMoveAcceptedLog(
                context = currentRuntimeLogContext(),
                beforeMove = beforeMove,
                localMove = localMove,
                turnTimeUpdate = turnTimeUpdate,
            ),
        )
        turnTimeState = turnTimeUpdate.after
        displayStateApplier.applyHumanMoveLocalResult(localMove)

        if (!isEngineReady) {
            val updatedSnapshots = ScoreTimeline.record(scoreState.scoreSnapshots, localMove.localScoreSnapshot)
            scoreState = scoreState.replaceSnapshots(updatedSnapshots)
            val localFinalScore = localMove.localFinalScore
            if (localFinalScore != null) {
                val final = buildLocalFinalScoreDisplayPlan(
                    source = "local-human-consecutive-pass",
                    state = afterMove,
                    finalScore = localFinalScore,
                    previousSnapshots = updatedSnapshots,
                    detail = "triggerMove=${move.describe(beforeMove.boardSize)}",
                    engineMessage = "Local game ended after two passes. ${localFinalScore.status.message}",
                    candidateText = "Game ended after two passes.",
                )
                displayStateApplier.applyFinalScoreDisplayPlan(final)
            } else {
                analysisState = analysisState.copy(candidateText = localMove.capturedText)
                engineMessage = "Local move accepted without engine sync: ${move.describe(beforeMove.boardSize)}."
            }
            return
        }

        runHumanEngineSyncApplication(
            HumanEngineSyncRunRequest(
                engineClient = engineClient,
                afterMove = afterMove,
                profile = runtimeState.engineProfile,
                move = move,
                previousReviewCandidates = previousReviewCandidates,
                localMove = localMove,
                previousSnapshots = scoreState.scoreSnapshots,
                moveDescription = move.describe(beforeMove.boardSize),
                sessionGeneration = runtimeState.sessionGeneration,
                timeoutPolicy = engineProfileTimeoutPolicy(runtimeState.engineProfile),
                diagnosticEventLog = diagnosticEventLog,
                currentState = { gameState },
                currentSessionGeneration = { runtimeState.sessionGeneration },
                launchEngineOperation = { operation, block ->
                    launchTrackedEngineOperation(operation) {
                        block()
                    }
                },
                applyCompletion = ::applyHumanEngineSyncCompletionApplyPlan,
                requestFollowUpAnalysis = { state ->
                    requestTopMoveAnalysisForState(
                        targetState = state,
                        automatic = true,
                    )
                },
            ),
        )
    }

    fun undoLocalTwoPlayerTurn(plan: UndoRequestPlan.LocalTwoPlayerUndo) {
        runLocalTwoPlayerUndoApplication(
            LocalTwoPlayerUndoRunRequest(
                plan = plan,
                currentState = gameState,
                scoreSnapshots = scoreState.scoreSnapshots,
                applyUndo = ::applyUndoLocalStatePlan,
                markQuiet = ::markUndoEngineInterventionQuiet,
                setEngineMessage = { message -> engineMessage = message },
                cancelPendingPostUndoSync = ::cancelPendingPostUndoEngineSync,
                schedulePostUndoSync = { targetState, quietUntilMillis ->
                    schedulePostUndoLocalEngineSync(
                        targetState = targetState,
                        quietUntilMillis = quietUntilMillis,
                    )
                },
            ),
        )
    }

    fun undoEngineBackedTurn(plan: UndoRequestPlan.EngineUndo) {
        runEngineUndoApplication(
            EngineUndoRunRequest(
                engineClient = engineClient,
                plan = plan,
                currentState = gameState,
                sessionGeneration = runtimeState.sessionGeneration,
                previousMoveReviews = moveReviewState.moveReviews,
                scoreSnapshots = scoreState.scoreSnapshots,
                diagnosticEventLog = diagnosticEventLog,
                launchEngineOperation = { operation, block ->
                    launchTrackedEngineOperation(operation) {
                        block()
                    }
                },
                currentStateProvider = { gameState },
                currentSessionGenerationProvider = { runtimeState.sessionGeneration },
                applyUndo = ::applyUndoLocalStatePlan,
                setEngineMessage = { message -> engineMessage = message },
                markQuiet = ::markUndoEngineInterventionQuiet,
                cancelPendingPostUndoSync = ::cancelPendingPostUndoEngineSync,
                appendDiscardLog = ::appendEngineOperationDiscardLog,
            ),
        )
    }

    fun undoLastTurn() {
        runUndoLastTurnApplication(
            UndoLastTurnRunRequest(
                currentState = gameState,
                matchMode = matchMode,
                isEngineReady = isEngineReady,
                isEngineBusy = isEngineBusy,
                humanSeatCount = playerSetup.humanSeatCount(),
                showMessage = { message -> engineMessage = message },
                runLocalTwoPlayerUndo = ::undoLocalTwoPlayerTurn,
                runEngineUndo = ::undoEngineBackedTurn,
            ),
        )
    }

    val cacheOptController = PositionCacheOptimizationController(
        engineClient = engineClient,
        diagnosticEventLog = diagnosticEventLog,
        currentGameState = { gameState },
        currentPlayerSetup = { playerSetup },
        currentSearchTimeSettings = { searchTimeSettings },
        isEngineBusy = { isEngineBusy },
        currentSessionGeneration = { runtimeState.sessionGeneration },
        currentUiState = { positionCacheOptimizationState },
        onUiState = { state -> positionCacheOptimizationState = state },
        onEngineMessage = { message -> engineMessage = message },
        onAnalysisCandidateText = { message -> analysisState = analysisState.copy(candidateText = message) },
        launchEngineOperation = { operation, block -> launchTrackedEngineOperation(operation) { block() } },
    )

    val scoreEstimateController = ScoreEstimateController(
        engineClient = engineClient,
        diagnosticEventLog = diagnosticEventLog,
        currentGameState = { gameState },
        currentScoreSnapshots = { scoreState.scoreSnapshots },
        isEngineReady = { isEngineReady },
        isEngineBusy = { isEngineBusy },
        currentMatchMode = { matchMode },
        currentEngineProfile = { runtimeState.engineProfile },
        currentSessionGeneration = { runtimeState.sessionGeneration },
        launchEngineOperation = { operation, block -> launchTrackedEngineOperation(operation) { block() } },
        onEngineMessage = { message -> engineMessage = message },
        onScoreEstimateDisplayPlan = displayStateApplier::applyScoreEstimateDisplayPlan,
        onScoreEstimateFailureDisplayPlan = displayStateApplier::applyScoreEstimateFailureDisplayPlan,
        appendDiscardLog = ::appendEngineOperationDiscardLog,
    )

    fun copyDebugReport() {
        runDebugReportCopyApplication(
            DebugReportCopyRunRequest(
                controllerState = currentControllerSessionState(),
                engineName = engineName,
                engineDiagnostic = engineDiagnostic,
                analysisCacheStatsText = {
                    "${analysisCache.statsText()}, ${undoAnalysisRestoreCache.statsText()}"
                },
                positionAnalysisCacheStatsText = { nowMillis ->
                    engineClient.positionAnalysisCacheStatsText(nowMillis)
                },
                isEngineReady = isEngineReady,
                isEngineBusy = isEngineBusy,
                turnTimeText = { turnTimeState.summaryText() },
                turnTimeDebugText = { nowMillis -> turnTimeState.debugText(nowMillis) },
                runtimeEventLog = runtimeEventLog,
                diagnosticEventLog = diagnosticEventLog,
                clipboard = clipboardPort,
                mirror = debugReportMirror,
                userNotice = userNoticePort,
                applyEngineMessage = { message -> engineMessage = message },
            ),
        )
    }

    fun dispatch(event: GameUiEvent) {
        dispatchGameUiEvent(
            event = event,
            handlers = buildGameUiEventHandlers(
                currentPlayer = { gameState.nextPlayer },
                isTopMovesEnabled = { topMovesEnabled },
                startConfiguredGame = ::startConfiguredGame,
                copyDebugReport = ::copyDebugReport,
                showEngineBenchmark = benchmarkController::showResult,
                requestScoreEstimate = scoreEstimateController::request,
                showTopMoves = ::showTopMovesForCurrentState,
                hideTopMoves = ::hideTopMoves,
                undoLastTurn = ::undoLastTurn,
                submitMove = ::submitHumanMove,
                dismissResumePrompt = {
                    sessionStore.clear()
                    savedSessionUiState = savedSessionUiState.dismiss()
                },
                acceptCacheOptimizationPrompt = cacheOptController::accept,
                dismissCacheOptimizationPrompt = cacheOptController::dismiss,
                restoreSavedSession = { snapshot ->
                    savedSessionUiState = savedSessionUiState.dismiss()
                    restoreSavedSession(snapshot)
                },
                changePlayerSetup = ::changePlayerSetup,
                changeAutoPlayDelay = ::changeAutoPlayDelay,
                changeSearchTimeSettings = ::changeSearchTimeSettings,
                changeScoringRule = ::changeScoringRule,
                changeUxOptions = { options -> uxOptions = options },
            ),
        )
    }

    LaunchedEffect(
        isEngineReady,
        isEngineBusy,
        playerSetup,
        searchTimeSettings,
        isGameEnded,
        shouldShowResumePrompt,
        undoEngineInterventionQuietUntil,
        gameState.nextPlayer,
        gameState.moves.size,
    ) {
        runTurnAutomationTriggerEffect(
            quietUntilMillis = undoEngineInterventionQuietUntil,
            topMoveTargetState = gameState,
            requestAiTurn = ::requestAiTurnForCurrentState,
            requestTopMoveAnalysis = { targetState ->
                requestTopMoveAnalysisForState(
                    targetState = targetState,
                    automatic = true,
                )
            },
        )
    }

    LaunchedEffect(
        isGameEnded,
        isEngineReady,
        isEngineBusy,
        positionCacheOptimizationState.isRunning,
        playerSetup,
        searchTimeSettings,
        gameState.moves.size,
    ) {
        cacheOptController.refreshPromptIfNeeded(
            isGameEnded = isGameEnded,
            isEngineReady = isEngineReady,
            isEngineBusy = isEngineBusy,
        )
    }

    val controllerState = currentControllerSessionState()
    val screenState = GoCoachScreenStateAssembler.assemble(
        GoCoachScreenStateAssembler.Input(
            controller = controllerState,
            uxOptions = uxOptions,
            engineRuntime = GoCoachScreenStateAssembler.EngineRuntime(
                name = engineName,
                diagnostic = engineDiagnostic,
                isReady = isEngineReady,
                isBusy = isEngineBusy,
                hasCompletedStartup = hasCompletedEngineStartup,
            ),
            displayRuntime = GoCoachScreenStateAssembler.DisplayRuntime(
                analysisCacheStats = "${analysisCache.statsText()}, ${undoAnalysisRestoreCache.statsText()}",
                isScoreGraphExpanded = isScoreGraphExpanded,
                turnTimeText = turnTimeState.summaryText(),
            ),
        ),
    )

    GoCoachContent(
        screenState = screenState,
        benchmarkProgress = benchmarkUiState.progress,
        benchmarkResult = benchmarkUiState.resultToConfirm,
        onBenchmarkResultConfirmed = { benchmarkUiState = benchmarkUiState.clearConfirmedResult() },
        onBenchmarkRerun = benchmarkController::rerun,
        isDisplayMenuExpanded = isDisplayMenuExpanded,
        onDisplayMenuExpandedChange = { expanded -> isDisplayMenuExpanded = expanded },
        onScoreGraphExpandedChange = { expanded -> isScoreGraphExpanded = expanded },
        onEvent = ::dispatch,
    )
}
