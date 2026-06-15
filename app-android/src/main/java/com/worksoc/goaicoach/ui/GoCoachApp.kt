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
import com.worksoc.goaicoach.application.debugreport.DebugReportCopyActionRequest
import com.worksoc.goaicoach.application.debugreport.runDebugReportCopyAction
import com.worksoc.goaicoach.application.preferences.buildInitialUserPreferencesPlan
import com.worksoc.goaicoach.application.analysis.buildPositionAnalysisCacheOptimizationPlan
import com.worksoc.goaicoach.application.analysis.refreshPositionAnalysisCacheOptimizationPrompt
import com.worksoc.goaicoach.application.preferences.UserPreferencesAutosaveRequest
import com.worksoc.goaicoach.application.preferences.runUserPreferencesAutosave
import com.worksoc.goaicoach.shared.engine.EngineFallbackPolicy
import com.worksoc.goaicoach.application.engine.operation.EngineOperationGate
import com.worksoc.goaicoach.shared.engine.EngineOperationKind
import com.worksoc.goaicoach.application.engine.operation.EngineOperationLifecycleState
import com.worksoc.goaicoach.application.engine.operation.EngineOperationLifecycleTransition
import com.worksoc.goaicoach.application.engine.operation.EngineOperationLifecycleCallbacks
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
import com.worksoc.goaicoach.application.engine.operation.applyEngineOperationLifecycleTransition
import com.worksoc.goaicoach.application.debugreport.ClipboardPort
import com.worksoc.goaicoach.shared.engine.engineOperationRequest
import com.worksoc.goaicoach.application.engine.operation.evaluateScoringRuleChangeGate
import com.worksoc.goaicoach.application.engine.operation.evaluateSearchTimeChangeGate
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationWorkflowResult
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationUiState
import com.worksoc.goaicoach.application.analysis.PostGamePositionAnalysisCacheOptimizationPromptEnabled
import com.worksoc.goaicoach.application.engine.operation.recordEngineOperationDiscardLog
import com.worksoc.goaicoach.application.engine.localScoreSnapshot
import com.worksoc.goaicoach.application.engine.operation.runEngineOperationInScope
import com.worksoc.goaicoach.application.analysis.runPositionAnalysisCacheOptimizationWorkflowResult
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
import com.worksoc.goaicoach.application.startgame.StartConfiguredGamePlan
import com.worksoc.goaicoach.application.startgame.buildNewLocalGameSessionPlan
import com.worksoc.goaicoach.application.startgame.buildStartConfiguredGamePlan
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
import com.worksoc.goaicoach.presentation.KaTrainUxOptions
import com.worksoc.goaicoach.presentation.buildGameScreenStateInput
import com.worksoc.goaicoach.presentation.buildGameScreenState
import com.worksoc.goaicoach.presentation.buildGameUiEventHandlers
import com.worksoc.goaicoach.presentation.dispatchGameUiEvent
import com.worksoc.goaicoach.presentation.toKaTrainUxOptions
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineSearchMode
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
    var engineOperationLifecycleState by remember { mutableStateOf(EngineOperationLifecycleState()) }
    var isEngineBusy by remember { mutableStateOf(engineOperationLifecycleState.isEngineBusy) }
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

    fun markEngineOperationStarted(operationId: String) {
        engineOperationLifecycleState = applyEngineOperationLifecycleTransition(
            state = engineOperationLifecycleState,
            transition = EngineOperationLifecycleTransition.Started(operationId),
        )
        isEngineBusy = engineOperationLifecycleState.isEngineBusy
        runtimeEventLog.append(
            runtimeEngineOperationStartedLog(
                context = currentRuntimeLogContext(),
                operationId = operationId,
                activeOperationCount = engineOperationLifecycleState.activeOperationIds.size,
            ),
        )
    }

    fun markEngineOperationCompleted(operationId: String) {
        engineOperationLifecycleState = applyEngineOperationLifecycleTransition(
            state = engineOperationLifecycleState,
            transition = EngineOperationLifecycleTransition.Completed(operationId),
        )
        isEngineBusy = engineOperationLifecycleState.isEngineBusy
        runtimeEventLog.append(
            runtimeEngineOperationCompletedLog(
                context = currentRuntimeLogContext(),
                operationId = operationId,
                activeOperationCount = engineOperationLifecycleState.activeOperationIds.size,
            ),
        )
    }

    fun engineProfileTimeoutPolicy(profile: EngineProfile): EngineTimeoutPolicy =
        EngineTimeoutPolicy(
            timeoutMillis = profile.analysisLimit.timeMillis,
            label = "${profile.difficulty.label}:${profile.analysisLimit.visits}v",
        )

    fun engineOperationLifecycleCallbacks(): EngineOperationLifecycleCallbacks =
        EngineOperationLifecycleCallbacks(
            onStarted = { request -> markEngineOperationStarted(request.operationId) },
            onCompleted = { request -> markEngineOperationCompleted(request.operationId) },
        )

    fun launchTrackedEngineOperation(
        operation: EngineOperationRequest,
        block: suspend () -> Unit,
    ): Job =
        launchUiEffect(scope) {
            runEngineOperationInScope(
                request = operation,
                callbacks = engineOperationLifecycleCallbacks(),
            ) {
                block()
            }
        }

    suspend fun runTrackedEngineOperation(
        operation: EngineOperationRequest,
        block: suspend () -> Unit,
    ) {
        runEngineOperationInScope(
            request = operation,
            callbacks = engineOperationLifecycleCallbacks(),
        ) {
            block()
        }
    }

    fun appendEngineOperationDiscardLog(discard: EngineOperationResultGuard.Discard) {
        recordEngineOperationDiscardLog(
            context = currentRuntimeLogContext(),
            discard = discard,
            currentState = gameState,
            runtimeEventLog = runtimeEventLog,
            diagnosticEventLog = diagnosticEventLog,
        )
    }

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

    suspend fun runEngineBenchmark() {
        runEngineBenchmarkApplication(
            EngineBenchmarkRunRequest(
                engineClient = engineClient,
                store = benchmarkStore,
                state = gameState,
                sessionGeneration = runtimeState.sessionGeneration,
                isEngineReady = isEngineReady,
                isEngineBusy = isEngineBusy,
                benchmarkUiState = benchmarkUiState,
                diagnosticEventLog = diagnosticEventLog,
                lifecycleCallbacks = engineOperationLifecycleCallbacks(),
                onBlocked = { message -> engineMessage = message },
                onBenchmarkUiState = { state -> benchmarkUiState = state },
                onDisplayPlan = { plan -> displayStateApplier.applyEngineBenchmarkDisplayPlan(plan) },
                onProgress = { progress, displayPlan ->
                    launchUiEffect(scope) {
                        benchmarkUiState = benchmarkUiState.updateProgress(progress)
                        displayStateApplier.applyEngineBenchmarkDisplayPlan(displayPlan)
                    }
                    Unit
                },
            ),
        )
    }

    fun showEngineBenchmarkResult() {
        benchmarkStore.load()?.let { profile ->
            benchmarkUiState = benchmarkUiState.showResult(profile)
            return
        }
        launchUiEffect(scope) {
            runEngineBenchmark()
        }
    }

    fun rerunEngineBenchmark() {
        benchmarkUiState = benchmarkUiState.clearResult()
        launchUiEffect(scope) {
            runEngineBenchmark()
        }
    }

    LaunchedEffect(
        hasCompletedEngineStartup,
        isEngineReady,
    ) {
        if (
            !hasCompletedEngineStartup ||
            !isEngineReady ||
            !engineClient.capabilities.supportsDeviceBenchmark ||
            hasCheckedEngineBenchmark
        ) {
            return@LaunchedEffect
        }
        if (
            benchmarkStore.hasUsableProfile(
                samplesPerVisit = EngineBenchmarkDefaultSamplesPerVisit,
                timeCapMs = EngineBenchmarkDefaultTimeCapMs,
                measurementVersion = EngineBenchmarkMeasurementVersion,
                visitsTargets = EngineBenchmarkDefaultVisits,
            )
        ) {
            hasCheckedEngineBenchmark = true
            benchmarkUiState = benchmarkUiState.applyStoredProfile(
                benchmarkText = benchmarkStore.loadText(),
                profile = benchmarkStore.load(),
            )
            return@LaunchedEffect
        }

        hasCheckedEngineBenchmark = true
        runEngineBenchmark()
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

    fun applyTopMoveAnalysisFailureDisplayPlan(
        failure: TopMoveAnalysisFailureDisplayPlan,
    ) {
        displayStateApplier.applyTopMoveAnalysisFailureDisplayPlan(failure)
    }

    fun applyTopMoveAnalysisCompletionApplyPlan(applyPlan: TopMoveAnalysisCompletionApplyPlan) {
        when (applyPlan) {
            is TopMoveAnalysisCompletionApplyPlan.ApplySuccess -> {
                applyTopMoveAnalysisUpdate(applyPlan.update, applyPlan.analysisKey)
                applyPlan.update.undoRestoreResult?.let { cached ->
                    undoAnalysisRestoreCache.put(applyPlan.analysisKey, cached)
                }
                applyPlan.update.cachedResult?.let { cached ->
                    analysisCache.put(applyPlan.analysisKey, cached)
                }
            }

            is TopMoveAnalysisCompletionApplyPlan.ApplyFailure ->
                applyTopMoveAnalysisFailureDisplayPlan(applyPlan.display)

            is TopMoveAnalysisCompletionApplyPlan.Discard ->
                appendEngineOperationDiscardLog(applyPlan.discard)
        }
    }

    fun applyScoreEstimateDisplayPlan(score: ScoreEstimateDisplayPlan) {
        displayStateApplier.applyScoreEstimateDisplayPlan(score)
    }

    fun applyScoreEstimateCompletionApplyPlan(applyPlan: ScoreEstimateCompletionApplyPlan) {
        when (applyPlan) {
            is ScoreEstimateCompletionApplyPlan.ApplySuccess ->
                applyScoreEstimateDisplayPlan(applyPlan.display)

            is ScoreEstimateCompletionApplyPlan.ApplyFailure ->
                displayStateApplier.applyScoreEstimateFailureDisplayPlan(applyPlan.failure)

            is ScoreEstimateCompletionApplyPlan.Discard ->
                appendEngineOperationDiscardLog(applyPlan.discard)
        }
    }

    fun applyScoreSyncCompletionApplyPlan(applyPlan: ScoreSyncCompletionApplyPlan): GameState? =
        when (applyPlan) {
            is ScoreSyncCompletionApplyPlan.ApplySuccess -> {
                applyScoreEstimateDisplayPlan(applyPlan.display)
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

    fun applyFinalScoreDisplayPlan(final: FinalScoreDisplayPlan) {
        displayStateApplier.applyFinalScoreDisplayPlan(final)
    }

    fun applyEndgameFailureDisplayPlan(failure: EndgameFailureDisplayPlan) {
        displayStateApplier.applyEndgameFailureDisplayPlan(failure)
    }

    fun currentRuntimeSessionState(): GameSessionRuntimeState =
        runtimeState

    fun applyRuntimeSessionState(runtime: GameSessionRuntimeState) {
        runtimeState = runtime
    }

    fun applyRuntimePlayLevelSelection(selection: RuntimePlayLevelSelection) {
        applyRuntimeSessionState(currentRuntimeSessionState().applySelection(selection))
    }

    fun applyAutoAiTurnDisplayPlan(display: AutoAiTurnDisplayPlan): AutoAiTurnFollowUpPlan {
        return displayStateApplier.applyAutoAiTurnDisplayPlan(display)
    }

    fun applyAutoAiTurnFailureDisplayPlan(error: Throwable) {
        displayStateApplier.applyAutoAiTurnFailureDisplayPlan(
            buildAutoAiTurnFailureDisplayPlan(error),
        )
    }

    fun applyHumanEngineSyncDisplayPlan(sync: HumanEngineSyncDisplayPlan): GameState? =
        when (sync) {
            is HumanEngineSyncDisplayPlan.FinalScore -> {
                applyFinalScoreDisplayPlan(sync.display)
                null
            }
            is HumanEngineSyncDisplayPlan.ScoreEstimate -> {
                applyScoreEstimateDisplayPlan(sync.display)
                analysisState = analysisState.copy(candidateText = sync.candidateText)
                sync.nextAnalysisState
            }
            HumanEngineSyncDisplayPlan.NoUpdate -> null
    }

    fun applyHumanEngineSyncFailurePlan(failure: HumanEngineSyncFailurePlan) {
        displayStateApplier.applyHumanEngineSyncFailurePlan(failure)
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
                applyHumanEngineSyncFailurePlan(applyPlan.failure)
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
                applyCompletion = ::applyTopMoveAnalysisCompletionApplyPlan,
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

    fun requestScoreEstimate() {
        runScoreEstimateApplication(
            ScoreEstimateRunRequest(
                engineClient = engineClient,
                state = gameState,
                previousSnapshots = scoreState.scoreSnapshots,
                isEngineReady = isEngineReady,
                isEngineBusy = isEngineBusy,
                matchMode = matchMode,
                engineProfile = runtimeState.engineProfile,
                sessionGeneration = runtimeState.sessionGeneration,
                diagnosticEventLog = diagnosticEventLog,
                currentState = { gameState },
                currentSessionGeneration = { runtimeState.sessionGeneration },
                launchEngineOperation = { operation, block ->
                    launchTrackedEngineOperation(operation) {
                        block()
                    }
                },
                applyLaunchUpdate = { launch ->
                    launch.engineMessage?.let { message -> engineMessage = message }
                    launch.display?.let(::applyScoreEstimateDisplayPlan)
                },
                applyCompletion = ::applyScoreEstimateCompletionApplyPlan,
            ),
        )
    }

    fun startEngineBackedNewGame(plan: StartConfiguredGamePlan.StartEngineGame) {
        val targetRuleset = plan.ruleset
        val runtime = plan.runtime
        applyRuntimePlayLevelSelection(runtime)
        runtimeEventLog.append(
            runtimeEngineGameStartRequestLog(
                context = currentRuntimeLogContext(),
                ruleset = targetRuleset,
                runtime = runtime,
            ),
        )
        val operation = engineOperationRequest(
            kind = EngineOperationKind.EngineNewGame,
            state = gameState,
            sessionGeneration = runtimeState.sessionGeneration,
            timeoutPolicy = EngineTimeoutPolicy(label = "engine-new-game"),
            fallbackPolicy = EngineFallbackPolicy.LocalEngine,
        )
        launchTrackedEngineOperation(operation) {
            var nextAnalysisState: GameState? = null
            val startMillis = System.currentTimeMillis()
            val result =
                runEngineIo {
                    engineClient.runEngineBackedNewGameWorkflowResult(
                        effect = GameSessionEffect.StartEngineBackedGame(
                            currentState = gameState,
                            profile = runtime.engineProfile,
                            boardSize = BoardSize.Nine,
                            ruleset = targetRuleset,
                        ),
                        operationRequest = operation,
                        diagnosticEventLog = diagnosticEventLog,
                    )
                }
            when (result) {
                is EngineStartupWorkflowResult.Success -> {
                    runtimeEventLog.append(
                        runtimeEngineGameStartSuccessLog(
                            context = currentRuntimeLogContext(),
                            elapsedMs = System.currentTimeMillis() - startMillis,
                            message = result.result.message,
                        ),
                    )
                    resetLocalGame(result.result.message, targetRuleset)
                    scoreState = scoreState.replaceSnapshots(listOf(result.result.scoreSnapshot ?: localScoreSnapshot(gameState)))
                    nextAnalysisState = gameState
                }

                is EngineStartupWorkflowResult.Failure -> {
                    runtimeEventLog.append(
                        runtimeEngineGameStartFailureLog(
                            context = currentRuntimeLogContext(),
                            elapsedMs = System.currentTimeMillis() - startMillis,
                            error = result.error,
                        ),
                    )
                    resetLocalGame(result.error.message ?: "New AI game failed.", targetRuleset)
                }
            }
            requestTopMoveAnalysisForState(
                targetState = nextAnalysisState ?: gameState,
                automatic = true,
            )
        }
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
        val endgameOperationToken = autoAiEndgameOperationToken(
            endgamePlan,
            sessionGeneration = runtimeState.sessionGeneration,
        )
        isGameEnded = true
        runtimeEventLog.append(
            runtimeAiTurnEndgameDetectedLog(
                context = currentRuntimeLogContext(),
                state = endgamePlan.state,
            ),
        )
        val endgameDisplay = runEngineIo {
            engineClient.runAutoAiEndgameEffect(
                effect = GameSessionEffect.ResolveAutoAiEndgame(endgamePlan),
                previousSnapshots = scoreState.scoreSnapshots,
                operationRequest = endgameOperationToken.operation,
                diagnosticEventLog = diagnosticEventLog,
            )
        }
        val endgameCompletion = buildAutoAiEndgameCompletionPlan(
            token = endgameOperationToken,
            currentState = gameState,
            currentSessionGeneration = runtimeState.sessionGeneration,
            display = endgameDisplay,
        )
        when (endgameCompletion) {
            is AutoAiEndgameCompletionPlan.ApplyResolved -> {
                runtimeEventLog.append(
                    runtimeAiTurnEndgameSuccessLog(
                        context = currentRuntimeLogContext(),
                        state = endgamePlan.state,
                        endgame = endgameCompletion.display.resolution,
                    ),
                )
                applyFinalScoreDisplayPlan(endgameCompletion.display.display)
            }

            is AutoAiEndgameCompletionPlan.ApplyFailed -> {
                runtimeEventLog.append(
                    runtimeAiTurnEndgameFailureLog(
                        context = currentRuntimeLogContext(),
                        state = endgamePlan.state,
                        error = endgameCompletion.display.error,
                    ),
                )
                applyEndgameFailureDisplayPlan(endgameCompletion.display.display)
            }

            is AutoAiEndgameCompletionPlan.Discard ->
                appendEngineOperationDiscardLog(endgameCompletion.discard)
        }
    }

    suspend fun applyAutoAiTurnSuccessCompletion(
        completion: AutoAiTurnCompletionPlan,
        turnContext: AutoAiTurnExecutionContext,
        turnStartMillis: Long,
    ): AutoAiTurnFollowUpPlan =
        when (completion) {
            is AutoAiTurnCompletionPlan.ApplySuccess -> {
                val appliedDisplay = completion.display
                val turnTimeUpdate = turnTimeState.recordMove(
                    player = turnContext.aiPlayer,
                    nowMillis = System.currentTimeMillis(),
                    nextPlayer = appliedDisplay.gameState.nextPlayer,
                )
                runtimeEventLog.append(
                    runtimeAiTurnSuccessLog(
                        context = currentRuntimeLogContext(),
                        turnState = turnContext.turnState,
                        aiPlayer = turnContext.aiPlayer,
                        display = appliedDisplay,
                        turnElapsedMs = System.currentTimeMillis() - turnStartMillis,
                        turnTimeUpdate = turnTimeUpdate,
                    ),
                )
                turnTimeState = turnTimeUpdate.after
                val followUpPlan = applyAutoAiTurnDisplayPlan(appliedDisplay)
                when (val endgamePlan = buildAutoAiTurnEndgamePlan(appliedDisplay)) {
                    AutoAiTurnEndgamePlan.None -> Unit
                    is AutoAiTurnEndgamePlan.Resolve -> applyAutoAiEndgamePlan(endgamePlan)
                }
                followUpPlan
            }

            is AutoAiTurnCompletionPlan.ApplyFailure ->
                AutoAiTurnFollowUpPlan.None

            is AutoAiTurnCompletionPlan.Discard -> {
                appendEngineOperationDiscardLog(completion.discard)
                AutoAiTurnFollowUpPlan.None
            }
        }

    fun applyAutoAiTurnFailureCompletion(
        completion: AutoAiTurnCompletionPlan,
        turnContext: AutoAiTurnExecutionContext,
        turnStartMillis: Long,
    ) {
        when (completion) {
            is AutoAiTurnCompletionPlan.ApplySuccess -> Unit
            is AutoAiTurnCompletionPlan.ApplyFailure -> {
                runtimeEventLog.append(
                    runtimeAiTurnFailureLog(
                        context = currentRuntimeLogContext(),
                        turnState = turnContext.turnState,
                        aiPlayer = turnContext.aiPlayer,
                        turnElapsedMs = System.currentTimeMillis() - turnStartMillis,
                        error = completion.error,
                    ),
                )
                applyAutoAiTurnFailureDisplayPlan(completion.error)
            }

            is AutoAiTurnCompletionPlan.Discard ->
                appendEngineOperationDiscardLog(completion.discard)
        }
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
                autoAiTurnUiState = autoAiTurnUiState.applyAutoAiTurnRequestPlan(request)
                runtimeEventLog.append(
                    runtimeAiTurnScheduleLog(
                        context = currentRuntimeLogContext(),
                        gameState = gameState,
                        delayMillis = request.delayMillis,
                        autoPlayDelaySetting = autoPlayDelaySetting,
                        isEngineBusy = isEngineBusy,
                    ),
                )
                launchAutoAiEffect(scope) {
                    if (request.delayMillis > 0L) {
                        delay(request.delayMillis)
                    }
                    val turnRunPlan = when (
                        val validation = currentControllerSessionState().toAutoAiTurnScheduleValidationPlan(
                            isEngineReady = isEngineReady,
                            isEngineBusy = isEngineBusy,
                            scheduledDelayMillis = request.delayMillis,
                        )
                    ) {
                        AutoAiTurnScheduleValidationPlan.Cancel -> {
                            runtimeEventLog.append(
                                runtimeAiTurnScheduleCancelledLog(
                                    context = currentRuntimeLogContext(),
                                    gameState = gameState,
                                    isEngineReady = isEngineReady,
                                    isEngineBusy = isEngineBusy,
                                    isGameEnded = isGameEnded,
                                    shouldShowResumePrompt = shouldShowResumePrompt,
                                ),
                            )
                            autoAiTurnUiState = autoAiTurnUiState.applyAutoAiTurnScheduleValidationPlan(validation)
                            return@launchAutoAiEffect
                        }
                        is AutoAiTurnScheduleValidationPlan.Continue -> validation.runPlan
                    }
                    val turnContext = turnRunPlan.context
                    val turnOperationToken = autoAiTurnOperationToken(
                        turnRunPlan,
                        sessionGeneration = runtimeState.sessionGeneration,
                    )
                    val turnStartMillis = System.currentTimeMillis()
                    runtimeEventLog.append(
                        runtimeAiTurnBeginLog(
                            context = currentRuntimeLogContext(),
                            turnState = turnContext.turnState,
                            aiPlayer = turnContext.aiPlayer,
                            playLevel = turnContext.playLevel,
                            analysisLimit = turnContext.analysisLimit,
                            searchMode = turnContext.searchMode,
                            delayMillis = turnRunPlan.delayMillis,
                            isolateSearchCache = turnContext.isolateSearchCache,
                        ),
                    )
                    markEngineOperationStarted(turnOperationToken.operation.operationId)
                    var followUpPlan: AutoAiTurnFollowUpPlan = AutoAiTurnFollowUpPlan.None
                    val turnResult =
                        runEngineIo {
                            engineClient.runAutoAiTurnWorkflowResult(
                                effect = GameSessionEffect.RunAutoAiTurn(turnRunPlan),
                                executionContext = AutoAiTurnRunExecutionContext(
                                    currentProfile = runtimeState.engineProfile,
                                    searchTimeSettings = searchTimeSettings,
                                    previousSnapshots = scoreState.scoreSnapshots,
                                ),
                                operationRequest = turnOperationToken.operation,
                                diagnosticEventLog = diagnosticEventLog,
                            )
                        }
                    val turnCompletion = buildAutoAiTurnCompletionPlan(
                        result = turnResult,
                        token = turnOperationToken,
                        currentState = gameState,
                        currentSessionGeneration = runtimeState.sessionGeneration,
                    )
                    followUpPlan = when (turnCompletion) {
                        is AutoAiTurnCompletionPlan.ApplySuccess ->
                            applyAutoAiTurnSuccessCompletion(
                                completion = turnCompletion,
                                turnContext = turnContext,
                                turnStartMillis = turnStartMillis,
                            )

                        is AutoAiTurnCompletionPlan.ApplyFailure,
                        is AutoAiTurnCompletionPlan.Discard -> {
                            applyAutoAiTurnFailureCompletion(
                                completion = turnCompletion,
                                turnContext = turnContext,
                                turnStartMillis = turnStartMillis,
                            )
                            AutoAiTurnFollowUpPlan.None
                        }
                    }
                    markEngineOperationCompleted(turnOperationToken.operation.operationId)
                    autoAiTurnUiState = autoAiTurnUiState.completeAutoAiTurnRun()
                    runtimeEventLog.append(
                        runtimeAiTurnCompleteLog(
                            context = currentRuntimeLogContext(),
                            gameState = gameState,
                            isEngineBusy = isEngineBusy,
                            isAutoAiTurnPending = isAutoAiTurnPending,
                        ),
                    )
                    followUpPlan.toAutoAiTurnFollowUpRequest()
                        ?.let { request ->
                            requestTopMoveAnalysisForState(
                                targetState = request.targetState,
                                automatic = request.automatic,
                                deep = request.deep,
                            )
                        }
                }
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
                applyFinalScoreDisplayPlan(final)
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

    fun currentCacheOptimizationPlan() =
        buildPositionAnalysisCacheOptimizationPlan(
            finalState = gameState,
            playerSetup = playerSetup,
            searchTimeSettings = searchTimeSettings,
            qualityFor = { state, limit ->
                engineClient.positionAnalysisCacheQualityFor(
                    state = state,
                    limit = limit,
                    searchMode = EngineSearchMode.JsonPositionAnalysis,
                    nowMillis = System.currentTimeMillis(),
                )
            },
        )

    fun dismissCacheOptimizationPrompt() {
        positionCacheOptimizationState = positionCacheOptimizationState.dismiss(
            currentGameFingerprint = currentCacheOptimizationPlan().gameFingerprint,
        )
    }

    fun acceptCacheOptimizationPrompt() {
        val plan = currentCacheOptimizationPlan()
        val wasRunning = positionCacheOptimizationState.isRunning
        positionCacheOptimizationState = positionCacheOptimizationState.accept(plan)
        if (plan.isEmpty || isEngineBusy || wasRunning) {
            return
        }
        positionCacheOptimizationState = positionCacheOptimizationState.startRunning()
        val operation = engineOperationRequest(
            kind = EngineOperationKind.PositionCacheOptimization,
            state = plan.finalState,
            sessionGeneration = runtimeState.sessionGeneration,
            timeoutPolicy = EngineTimeoutPolicy(label = "position-cache-optimization"),
            fallbackPolicy = EngineFallbackPolicy.CachedAnalysis,
        )
        engineMessage = "Post-game cache optimization started: ${plan.targets.size} JSON position(s)."
        val effect = GameSessionEffect.RunPositionCacheOptimization(plan)
        launchUiEffect(scope) {
            runTrackedEngineOperation(operation) {
                val result =
                    runEngineIo {
                        engineClient.runPositionAnalysisCacheOptimizationWorkflowResult(
                            effect = effect,
                            operationRequest = operation,
                            diagnosticEventLog = diagnosticEventLog,
                        )
                    }
                when (result) {
                    is PositionAnalysisCacheOptimizationWorkflowResult.Success -> {
                        engineMessage = result.result.messageText()
                        analysisState = analysisState.copy(candidateText = result.result.messageText())
                    }

                    is PositionAnalysisCacheOptimizationWorkflowResult.Failure -> {
                        engineMessage = result.error.message ?: "Post-game cache optimization failed."
                    }
                }
                positionCacheOptimizationState = positionCacheOptimizationState.finishRunning()
            }
        }
    }

    fun copyDebugReport() {
        val nowMillis = System.currentTimeMillis()
        val result = runDebugReportCopyAction(
            request = DebugReportCopyActionRequest(
                controllerState = currentControllerSessionState(),
                engineName = engineName,
                engineDiagnostic = engineDiagnostic,
                analysisCacheStats = "${analysisCache.statsText()}, ${undoAnalysisRestoreCache.statsText()}",
                positionAnalysisCacheStats = engineClient.positionAnalysisCacheStatsText(nowMillis),
                isEngineReady = isEngineReady,
                isEngineBusy = isEngineBusy,
                turnTimeText = turnTimeState.summaryText(),
                turnTimeDebugText = turnTimeState.debugText(nowMillis),
                runtimeEventLogText = runtimeEventLog.readText(),
                diagnosticEventLogText = diagnosticEventLog.readText(),
            ),
            clipboard = clipboardPort,
            mirror = debugReportMirror,
            userNotice = userNoticePort,
        )
        engineMessage = result.engineMessage
    }

    fun dispatch(event: GameUiEvent) {
        dispatchGameUiEvent(
            event = event,
            handlers = buildGameUiEventHandlers(
                currentPlayer = { gameState.nextPlayer },
                isTopMovesEnabled = { topMovesEnabled },
                startConfiguredGame = ::startConfiguredGame,
                copyDebugReport = ::copyDebugReport,
                showEngineBenchmark = ::showEngineBenchmarkResult,
                requestScoreEstimate = ::requestScoreEstimate,
                showTopMoves = ::showTopMovesForCurrentState,
                hideTopMoves = ::hideTopMoves,
                undoLastTurn = ::undoLastTurn,
                submitMove = ::submitHumanMove,
                dismissResumePrompt = {
                    sessionStore.clear()
                    savedSessionUiState = savedSessionUiState.dismiss()
                },
                acceptCacheOptimizationPrompt = ::acceptCacheOptimizationPrompt,
                dismissCacheOptimizationPrompt = ::dismissCacheOptimizationPrompt,
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
        positionCacheOptimizationState = refreshPositionAnalysisCacheOptimizationPrompt(
            currentState = positionCacheOptimizationState,
            isGameEnded = isGameEnded,
            isEngineReady = isEngineReady,
            isEngineBusy = isEngineBusy,
            plan = currentCacheOptimizationPlan(),
            isPromptEnabled = PostGamePositionAnalysisCacheOptimizationPromptEnabled,
        )
    }

    val controllerState = currentControllerSessionState()
    val screenState = buildGameScreenState(
        buildGameScreenStateInput(
            controller = controllerState,
            uxOptions = uxOptions,
            engineName = engineName,
            engineDiagnostic = engineDiagnostic,
            isEngineReady = isEngineReady,
            isEngineBusy = isEngineBusy,
            analysisCacheStats = "${analysisCache.statsText()}, ${undoAnalysisRestoreCache.statsText()}",
            isScoreGraphExpanded = isScoreGraphExpanded,
            turnTimeText = turnTimeState.summaryText(),
            hasCompletedEngineStartup = hasCompletedEngineStartup,
        ),
    )

    GoCoachContent(
        screenState = screenState,
        benchmarkProgress = benchmarkUiState.progress,
        benchmarkResult = benchmarkUiState.resultToConfirm,
        onBenchmarkResultConfirmed = { benchmarkUiState = benchmarkUiState.clearConfirmedResult() },
        onBenchmarkRerun = ::rerunEngineBenchmark,
        isDisplayMenuExpanded = isDisplayMenuExpanded,
        onDisplayMenuExpandedChange = { expanded -> isDisplayMenuExpanded = expanded },
        onScoreGraphExpandedChange = { expanded -> isScoreGraphExpanded = expanded },
        onEvent = ::dispatch,
    )
}
