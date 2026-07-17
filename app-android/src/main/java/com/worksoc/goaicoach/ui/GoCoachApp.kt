package com.worksoc.goaicoach.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
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
import com.worksoc.goaicoach.application.analysis.AnalysisCacheKey
import com.worksoc.goaicoach.application.analysis.AnalysisResultCache
import com.worksoc.goaicoach.application.analysis.PositionCacheOptimizationController
import com.worksoc.goaicoach.application.analysis.UndoAnalysisRestoreCache
import com.worksoc.goaicoach.application.autoai.AutoAiTurnController
import com.worksoc.goaicoach.application.autoai.applyAutoAiTurnRequestPlan
import com.worksoc.goaicoach.application.autoai.applyAutoAiTurnScheduleValidationPlan
import com.worksoc.goaicoach.application.autoai.buildAutoAiTurnFailureDisplayPlan
import com.worksoc.goaicoach.application.autoai.completeAutoAiTurnRun
import com.worksoc.goaicoach.application.debugreport.DebugReportController
import com.worksoc.goaicoach.application.debugreport.ClipboardPort
import com.worksoc.goaicoach.application.debugreport.DebugReportMirrorPort
import com.worksoc.goaicoach.application.debugreport.UserNoticePort
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineBenchmarkController
import com.worksoc.goaicoach.application.engine.EngineBenchmarkStorePort
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.EngineStartupRunRequest
import com.worksoc.goaicoach.application.engine.runEngineStartupApplication
import com.worksoc.goaicoach.application.engine.operation.EngineActivityIndicator
import com.worksoc.goaicoach.application.preferences.buildInitialUserPreferencesPlan
import com.worksoc.goaicoach.application.preferences.UserPreferencesAutosaveRequest
import com.worksoc.goaicoach.application.preferences.UserPreferencesStorePort
import com.worksoc.goaicoach.application.preferences.runUserPreferencesAutosave
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.runtime.runtimeAppStartLog
import com.worksoc.goaicoach.application.runtime.runtimeGameResetLog
import com.worksoc.goaicoach.application.runtime.toRuntimeLogContext
import com.worksoc.goaicoach.application.savedgame.SavedSessionController
import com.worksoc.goaicoach.application.startgame.NewGameController
import com.worksoc.goaicoach.application.score.ScoringRuleController
import com.worksoc.goaicoach.application.score.FinalScoreDisplayPlan
import com.worksoc.goaicoach.application.undo.UndoController
import com.worksoc.goaicoach.application.engine.operation.EngineOperationGate
import com.worksoc.goaicoach.application.engine.operation.EngineOperationLifecycleCallbacks
import com.worksoc.goaicoach.application.engine.operation.EngineOperationLifecycleController
import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.application.humanmove.HumanMoveController
import com.worksoc.goaicoach.application.savedgame.SavedGamePersistenceRunRequest
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.application.savedgame.SavedGameStorePort
import com.worksoc.goaicoach.application.savedgame.SavedSessionPromptRunRequest
import com.worksoc.goaicoach.application.savedgame.runSavedGamePersistenceApplication
import com.worksoc.goaicoach.application.savedgame.runSavedSessionPromptApplication
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.GameSessionCoreState
import com.worksoc.goaicoach.application.session.GameSessionDisplayStateApplier
import com.worksoc.goaicoach.application.session.GameSessionStateHolder
import com.worksoc.goaicoach.application.session.GameSessionTurnTimeState
import com.worksoc.goaicoach.application.session.GameSettingsController
import com.worksoc.goaicoach.application.session.runTurnAutomationTriggerEffect
import com.worksoc.goaicoach.application.topmoves.TopMovesController
import com.worksoc.goaicoach.application.topmoves.TopMoveAnalysisDeferral
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.persistence.GameSessionStore
import com.worksoc.goaicoach.persistence.EngineBenchmarkStore
import com.worksoc.goaicoach.persistence.DebugReportMirrorStore
import com.worksoc.goaicoach.persistence.RuntimeEventLog
import com.worksoc.goaicoach.persistence.UserPreferencesStore
import com.worksoc.goaicoach.presentation.GameUiEvent
import com.worksoc.goaicoach.presentation.GoCoachScreenStateAssembler
import com.worksoc.goaicoach.presentation.KaTrainUxOptions
import com.worksoc.goaicoach.presentation.applyEvalActivation
import com.worksoc.goaicoach.presentation.buildGameUiEventHandlers
import com.worksoc.goaicoach.presentation.dispatchGameUiEvent
import com.worksoc.goaicoach.presentation.toKaTrainUxOptions
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
import kotlinx.coroutines.Job
import java.io.File

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
        ProvideUiLanguage { selectedLanguage, onLanguageChange ->
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                GoCoachScreen(engineClient, engineName, engineDiagnostic, diagnosticEventLog, selectedLanguage, onLanguageChange)
            }
        }
    }
}

@Composable
private fun GoCoachScreen(
    engineClient: EngineSessionClient,
    engineName: String,
    engineDiagnostic: String,
    diagnosticEventLog: DiagnosticEventLogPort,
    selectedLanguage: UiLanguage,
    onLanguageChange: (UiLanguage) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var currentDestination by remember { mutableStateOf(ScreenDestination.Home) }
    var showResignConfirmFromBack by remember { mutableStateOf(false) }
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
    val sessionHolder = remember {
        GameSessionStateHolder(
            buildInitialSessionState(
                initialPlan = initialPlan,
                engineDiagnostic = engineDiagnostic,
                benchmarkStore = benchmarkStore,
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
    var turnTimeState by HolderBackedState(
        { sessionSnapshot.core.turnTimeState },
        { value -> mutateCore { it.copy(turnTimeState = value) } },
    )
    ObserveTimerLifecycle(turnTimeState) { turnTimeState = it }
    var isEngineBusy by remember { mutableStateOf(false) }
    var isEngineBlockingBusy by remember { mutableStateOf(false) }
    var engineActivityIndicator by remember { mutableStateOf<EngineActivityIndicator?>(EngineActivityIndicator.Preparing) }
    var isEngineReady by remember { mutableStateOf(false) }
    val analysisCache = remember { AnalysisResultCache(maxEntries = 96) }
    val undoAnalysisRestoreCache = remember { UndoAnalysisRestoreCache(maxEntries = 96) }
    var uxOptions by remember { mutableStateOf(initialPreferences.toKaTrainUxOptions()) }
    var isDisplayMenuExpanded by remember { mutableStateOf(false) }
    var isScoreGraphExpanded by remember { mutableStateOf(false) }
    var hasCompletedEngineStartup by remember { mutableStateOf(false) }

    val playerSetup = settingsState.playerSetup
    val matchMode = settingsState.matchMode
    val autoPlayDelaySetting = settingsState.autoPlayDelaySetting
    val searchTimeSettings = settingsState.searchTimeSettings
    val topMovesEnabled = settingsState.topMovesEnabled
    val shouldShowResumePrompt = savedSessionUiState.shouldShowResumePrompt
    var undoEngineInterventionQuietUntil by remember { mutableStateOf(0L) }
    var isPendingUndoSync by remember { mutableStateOf(false) }
    var cancelUndoSync: () -> Unit = {}
    fun clearUndoEngineInterventionQuietWindow() { undoEngineInterventionQuietUntil = 0L; cancelUndoSync() }
    fun activateEndgameJudgementReview() { uxOptions = uxOptions.copy(showOwnershipOverlay = true) }
    fun currentRuntimeLogContext(): RuntimeLogContext {
        return sessionSnapshot.toRuntimeLogContext(
            engineName = engineName,
            engineDiagnostic = engineDiagnostic,
            isEngineReady = isEngineReady,
            isEngineBusy = isEngineBusy,
            analysisCacheStats = "${analysisCache.statsText()}, ${undoAnalysisRestoreCache.statsText()}",
            turnTimeText = turnTimeState.runtimeText(),
        )
    }
    fun applyCoreSessionState(core: GameSessionCoreState) {
        mutateCore { core }
        if (!core.isGameEnded) {
            positionCacheOptimizationState = positionCacheOptimizationState.clearPrompt()
        }
    }
    fun refreshNewGamePreview() = applyCoreSessionState(
        sessionSnapshot.core.applyGameSetupPreview(
            ruleset = gameState.ruleset,
            boardSize = settingsState.boardSize,
            handicapCount = settingsState.handicapCount,
        ),
    )
    val exitToHome = {
        isGameEnded = true
        refreshNewGamePreview()
        currentDestination = ScreenDestination.Home
    }
    val lifecycleController = remember {
        EngineOperationLifecycleController(
            scope = scope,
            runtimeEventLog = runtimeEventLog,
            diagnosticEventLog = diagnosticEventLog,
            currentRuntimeLogContext = { currentRuntimeLogContext() },
            currentState = { gameState },
            onBusyChanged = { busy, blocking, activityIndicator ->
                isEngineBusy = busy
                isEngineBlockingBusy = blocking
                engineActivityIndicator = activityIndicator
            },
        )
    }
    fun engineProfileTimeoutPolicy(profile: EngineProfile): EngineTimeoutPolicy =
        EngineTimeoutPolicy(
            timeoutMillis = profile.analysisLimit.timeMillis,
            label = "${profile.difficulty.label}:${profile.analysisLimit.visits}v",
        )
    val displayStateApplier = remember {
        GameSessionDisplayStateApplier(
            currentCoreState = { sessionSnapshot.core },
            applyCoreState = ::applyCoreSessionState,
            appendEngineOperationDiscardLog = lifecycleController::appendDiscardLog,
        )
    }
    fun applyFinalScoreWithJudgement(final: FinalScoreDisplayPlan) { activateEndgameJudgementReview(); displayStateApplier.applyFinalScoreDisplayPlan(final) }
    LaunchedEffect(Unit) {
        runtimeEventLog.append(runtimeAppStartLog(currentRuntimeLogContext()))
    }
    LaunchedEffect(engineClient) {
        hasCompletedEngineStartup = false
        val startup = engineClient.runEngineStartupApplication(
            EngineStartupRunRequest(
                state = gameState,
                profile = runtimeState.engineProfile,
                sessionGeneration = runtimeState.sessionGeneration,
                engineDiagnostic = engineDiagnostic,
                diagnosticEventLog = diagnosticEventLog,
                lifecycleCallbacks = lifecycleController.callbacks(),
            ),
        )
        isEngineReady = startup.isEngineReady
        displayStateApplier.applyEngineStartupDisplayPlan(startup)
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
        lifecycleCallbacks = { lifecycleController.callbacks() },
        currentState = { gameState },
        sessionGeneration = { runtimeState.sessionGeneration },
        isEngineReady = { isEngineReady },
        isEngineBusy = { isEngineBusy },
        currentBenchmarkUiState = { benchmarkUiState },
        onBenchmarkUiState = { state -> benchmarkUiState = state },
        onEngineMessage = { message -> engineMessage = message },
        onDisplayPlan = { plan -> displayStateApplier.applyEngineBenchmarkDisplayPlan(plan) },
    )
    LaunchedEffect(
        preferencesStore,
        settingsState,
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
                isDirectPlayEnabled = uxOptions.isDirectPlayEnabled,
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
        scoreState.scoreSnapshots,
    ) {
        runSavedGamePersistenceApplication(
            SavedGamePersistenceRunRequest(
                savedSessionUiState = savedSessionUiState,
                isGameEnded = isGameEnded,
                gameState = gameState,
                playerSetup = playerSetup,
                playLevel = runtimeState.playLevel,
                topMovesEnabled = topMovesEnabled,
                scoreSnapshots = scoreState.scoreSnapshots,
                nowMillis = System.currentTimeMillis(),
                store = sessionStore,
            ),
        )
    }
    val deferredTopMoveAnalysis = remember { TopMoveAnalysisDeferral() }
    val topMovesController = TopMovesController(
        engineClient = engineClient,
        currentControllerState = { sessionSnapshot },
        isGameEnded = { isGameEnded },
        isEngineReady = { isEngineReady },
        isEngineBusy = { isEngineBusy },
        shouldShowResumePrompt = { shouldShowResumePrompt },
        currentPlayerSetup = { playerSetup },
        pendingPostUndoEngineSync = { isPendingUndoSync },
        analysisCacheEnabled = { analysisCache.isEnabled },
        cachedResultFor = { key -> undoAnalysisRestoreCache.get(key) ?: analysisCache.get(key) },
        currentGameState = { gameState },
        currentAnalysisKey = { analysisState.lastAnalysisKey },
        currentSessionGeneration = { runtimeState.sessionGeneration },
        launchEngineOperation = { operation, block -> lifecycleController.launchTracked(operation) { block() } },
        applyLaunchUpdate = { launchUpdate ->
            analysisState = launchUpdate.analysisState
            launchUpdate.engineMessage?.let { message -> engineMessage = message }
        },
        applyTopMoveAnalysisUpdate = { update, analysisKey ->
            analysisState = analysisState.applyTopMoveAnalysisUpdate(update, analysisKey)
            engineMessage = update.engineMessage
        },
        putUndoRestoreCache = { key, cached -> undoAnalysisRestoreCache.put(key, cached) },
        putAnalysisCache = { key, cached -> analysisCache.put(key, cached) },
        applyFailureDisplay = displayStateApplier::applyTopMoveAnalysisFailureDisplayPlan,
        appendEngineOperationDiscardLog = lifecycleController::appendDiscardLog,
        applyShowTopMovesStateUpdate = { update ->
            settingsState = update.settingsState
            analysisState = update.analysisState
            update.engineMessage?.let { message -> engineMessage = message }
        },
        deferredAutomaticAnalysis = deferredTopMoveAnalysis,
    )
    val undoController = remember {
        UndoController(
            scope = scope,
            engineClient = engineClient,
            diagnosticEventLog = diagnosticEventLog,
            currentGameState = { gameState },
            currentScoreSnapshots = { scoreState.scoreSnapshots },
            currentMoveReviews = { moveReviewState.moveReviews },
            currentMatchMode = { matchMode },
            currentPlayerSetup = { playerSetup },
            currentSessionGeneration = { runtimeState.sessionGeneration },
            currentEngineProfile = { runtimeState.engineProfile },
            timeoutPolicy = ::engineProfileTimeoutPolicy,
            isEngineReady = { isEngineReady },
            isEngineBusy = { isEngineBusy },
            onEngineMessage = { message -> engineMessage = message },
            onQuietUntil = { quietUntil -> undoEngineInterventionQuietUntil = quietUntil },
            onPendingSyncChanged = { pending -> isPendingUndoSync = pending },
            launchEngineOperation = { operation, block -> lifecycleController.launchTracked(operation) { block() } },
            runEngineOperation = { operation, block -> lifecycleController.runTracked(operation) { block() } },
            applyUndo = { undo ->
                displayStateApplier.applyUndoLocalStatePlan(undo)
                turnTimeState = turnTimeState.restartCurrentTurn(
                    state = undo.gameState,
                    nowMillis = System.currentTimeMillis(),
                )
            },
            applyScoreSyncCompletion = displayStateApplier::applyScoreSyncCompletion,
            requestFollowUpAnalysis = { state -> topMovesController.requestAnalysis(state, automatic = true) },
            appendDiscardLog = lifecycleController::appendDiscardLog,
        )
    }
    cancelUndoSync = undoController::cancelPendingSync
    val autoAiTurnController = AutoAiTurnController(
        scope = scope,
        engineClient = engineClient,
        diagnosticEventLog = diagnosticEventLog,
        runtimeEventLog = runtimeEventLog,
        currentControllerState = { sessionSnapshot },
        currentRuntimeState = { runtimeState },
        currentSearchTimeSettings = { searchTimeSettings },
        currentScoreSnapshots = { scoreState.scoreSnapshots },
        isEngineReady = { isEngineReady },
        isEngineBusy = { isEngineBusy },
        isGameEnded = { isGameEnded },
        shouldShowResumePrompt = { shouldShowResumePrompt },
        currentRuntimeLogContext = ::currentRuntimeLogContext,
        currentGameState = { gameState },
        currentSessionGeneration = { runtimeState.sessionGeneration },
        markEngineOperationStarted = lifecycleController::markStarted,
        markEngineOperationCompleted = lifecycleController::markCompleted,
        applyAutoAiTurnScheduled = { schedule -> autoAiTurnUiState = autoAiTurnUiState.applyAutoAiTurnRequestPlan(schedule) },
        applyAutoAiTurnCancelled = { cancel -> autoAiTurnUiState = autoAiTurnUiState.applyAutoAiTurnScheduleValidationPlan(cancel) },
        recordTurnMove = { player, nowMillis, nextPlayer -> turnTimeState.recordMove(player = player, nowMillis = nowMillis, nextPlayer = nextPlayer) },
        applyTurnTimeUpdate = { update -> turnTimeState = update.after },
        applyTurnDisplay = { display -> if (display.shouldResolveEndgame) activateEndgameJudgementReview(); displayStateApplier.applyAutoAiTurnDisplayPlan(display) },
        applyTurnFailureDisplay = { error -> displayStateApplier.applyAutoAiTurnFailureDisplayPlan(buildAutoAiTurnFailureDisplayPlan(error)) },
        completeAutoAiTurnRun = { autoAiTurnUiState = autoAiTurnUiState.completeAutoAiTurnRun() },
        appendEngineOperationDiscardLog = lifecycleController::appendDiscardLog,
        requestFollowUpAnalysis = { followUp -> topMovesController.requestAnalysis(followUp.targetState, automatic = followUp.automatic, deep = followUp.deep) },
        markGameEnded = { activateEndgameJudgementReview(); isGameEnded = true },
        applyFinalScoreDisplayPlan = ::applyFinalScoreWithJudgement,
        applyEndgameFailureDisplayPlan = displayStateApplier::applyEndgameFailureDisplayPlan,
    )
    val humanMoveController = HumanMoveController(
        engineClient = engineClient,
        diagnosticEventLog = diagnosticEventLog,
        runtimeEventLog = runtimeEventLog,
        currentGameState = { gameState },
        currentPlayerSetup = { playerSetup },
        currentAnalysisState = { analysisState },
        currentMoveReviewState = { moveReviewState },
        currentScoreSnapshots = { scoreState.scoreSnapshots },
        currentScoreState = { scoreState },
        currentSessionGeneration = { runtimeState.sessionGeneration },
        currentEngineProfile = { runtimeState.engineProfile },
        currentRuntimeLogContext = ::currentRuntimeLogContext,
        isEngineReady = { isEngineReady },
        isEngineBlockingBusy = { isEngineBlockingBusy },
        cancelBackgroundOperations = lifecycleController::cancelBackgroundOperations,
        onEngineMessage = { message -> engineMessage = message },
        onConsecutivePassesDetected = ::activateEndgameJudgementReview,
        clearUndoEngineInterventionQuietWindow = ::clearUndoEngineInterventionQuietWindow,
        recordTurnMove = { player, nowMillis, nextPlayer -> turnTimeState.recordMove(player = player, nowMillis = nowMillis, nextPlayer = nextPlayer) },
        applyTurnTimeUpdate = { update -> turnTimeState = update.after },
        applyHumanMoveLocalResult = displayStateApplier::applyHumanMoveLocalResult,
        replaceScoreState = { state -> scoreState = state },
        setAnalysisCandidateText = { text -> analysisState = analysisState.copy(candidateText = text) },
        applyFinalScoreDisplayPlan = ::applyFinalScoreWithJudgement,
        applyScoreEstimateDisplayPlan = displayStateApplier::applyScoreEstimateDisplayPlan,
        applyHumanEngineSyncFailurePlan = displayStateApplier::applyHumanEngineSyncFailurePlan,
        appendEngineOperationDiscardLog = lifecycleController::appendDiscardLog,
        timeoutPolicy = ::engineProfileTimeoutPolicy,
        launchEngineOperation = { operation, block -> lifecycleController.launchTracked(operation) { block() } },
        requestFollowUpAnalysis = { state -> topMovesController.requestAnalysis(state, automatic = true) },
    )
    val newGameController = NewGameController(
        engineClient = engineClient,
        diagnosticEventLog = diagnosticEventLog,
        runtimeEventLog = runtimeEventLog,
        defaultPlayLevel = defaultPlayLevel,
        isEngineReady = { isEngineReady },
        isEngineBusy = { isEngineBusy },
        currentGameState = { gameState },
        currentPlayerSetup = { playerSetup },
        currentEngineProfile = { runtimeState.engineProfile }, currentSearchTimeSettings = { searchTimeSettings }, currentBoardSize = { settingsState.boardSize },
        currentHandicapCount = { settingsState.handicapCount },
        currentSessionGeneration = { runtimeState.sessionGeneration },
        currentScoreState = { scoreState },
        currentRuntimeLogContext = ::currentRuntimeLogContext,
        launchEngineOperation = { operation, block -> lifecycleController.launchTracked(operation) { block() } },
        applyGameSessionResetPlan = { reset ->
            clearUndoEngineInterventionQuietWindow()
            undoAnalysisRestoreCache.clear()
            positionCacheOptimizationState = positionCacheOptimizationState.clearPrompt()
            applyCoreSessionState(sessionSnapshot.core.applyGameSessionResetPlan(reset))
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
        },
        applyRuntimePlayLevelSelection = { selection -> runtimeState = runtimeState.applySelection(selection) },
        replaceScoreState = { state -> scoreState = state },
        requestFollowUpAnalysis = { state -> topMovesController.requestAnalysis(state, automatic = true) },
        onEngineMessage = { message -> engineMessage = message },
    )
    val savedSessionController = SavedSessionController(
        engineClient = engineClient,
        diagnosticEventLog = diagnosticEventLog,
        defaultPlayLevel = defaultPlayLevel,
        isEngineBusy = { isEngineBusy },
        isEngineReady = { isEngineReady },
        currentSearchTimeSettings = { searchTimeSettings },
        currentEngineProfile = { runtimeState.engineProfile },
        currentSessionGeneration = { runtimeState.sessionGeneration },
        timeoutPolicy = ::engineProfileTimeoutPolicy,
        currentGameState = { gameState },
        onEngineMessage = { message -> engineMessage = message },
        applySavedGameRestorePlan = { restore ->
            clearUndoEngineInterventionQuietWindow()
            undoAnalysisRestoreCache.clear()
            positionCacheOptimizationState = positionCacheOptimizationState.clearPrompt()
            settingsState = settingsState.applySavedGameRestore(
                restoredSetup = restore.playerSetup,
                restoredTopMovesEnabled = restore.topMovesEnabled,
            )
            applyCoreSessionState(sessionSnapshot.core.applySavedGameRestorePlan(restore))
            turnTimeState = GameSessionTurnTimeState.reset(
                state = restore.gameState,
                nowMillis = System.currentTimeMillis(),
            )
        },
        launchEngineOperation = { operation, block -> lifecycleController.launchTracked(operation) { block() } },
        applyScoreSyncCompletion = displayStateApplier::applyScoreSyncCompletion,
        requestFollowUpAnalysis = { state -> topMovesController.requestAnalysis(state, automatic = true) },
    )
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
        launchEngineOperation = { operation, block -> lifecycleController.launchTracked(operation) { block() } },
    )

    val scoreEstimateController = buildScoreEstimateController(
        engineClient, diagnosticEventLog, { gameState }, { scoreState.scoreSnapshots }, { isEngineReady }, { isEngineBusy }, { matchMode }, { runtimeState.engineProfile }, { runtimeState.sessionGeneration },
        { operation, block -> lifecycleController.launchTracked(operation) { block() } }, { message -> engineMessage = message }, displayStateApplier, lifecycleController::appendDiscardLog,
    )

    val scoringRuleController = ScoringRuleController(
        engineClient = engineClient,
        diagnosticEventLog = diagnosticEventLog,
        currentGameState = { gameState },
        currentMatchMode = { matchMode },
        isEngineReady = { isEngineReady },
        isEngineBusy = { isEngineBusy },
        currentScoreSnapshots = { scoreState.scoreSnapshots },
        currentEngineProfile = { runtimeState.engineProfile },
        currentSessionGeneration = { runtimeState.sessionGeneration },
        timeoutPolicy = ::engineProfileTimeoutPolicy,
        onEngineMessage = { message -> engineMessage = message },
        applyScoringRuleChangePlan = { ruleChange ->
            applyCoreSessionState(sessionSnapshot.core.applyScoringRuleChangePlan(ruleChange))
        },
        applyScoreSyncCompletionApplyPlan = displayStateApplier::applyScoreSyncCompletion,
        requestFollowUpAnalysis = { state -> topMovesController.requestAnalysis(state, automatic = true) },
        launchEngineOperation = { operation, block -> lifecycleController.launchTracked(operation) { block() } },
        appendDiscardLog = lifecycleController::appendDiscardLog,
    )

    val settingsController = GameSettingsController(
        currentGameState = { gameState },
        currentPlayerSetup = { playerSetup },
        currentEngineProfile = { runtimeState.engineProfile },
        currentSearchTimeSettings = { searchTimeSettings },
        currentAnalysisState = { analysisState },
        currentAutoPlayDelaySetting = { autoPlayDelaySetting },
        defaultPlayLevel = defaultPlayLevel,
        isEngineBusy = { isEngineBusy },
        runtimeEventLog = runtimeEventLog,
        currentRuntimeLogContext = ::currentRuntimeLogContext,
        onEngineMessage = { message -> engineMessage = message },
        applyPlayerSetup = { setup -> settingsState = settingsState.applyPlayerSetup(setup) },
        applyCoreSessionState = ::applyCoreSessionState,
        currentCoreSessionState = { sessionSnapshot.core },
        applyRuntimePlayLevelSelection = { selection -> runtimeState = runtimeState.applySelection(selection) },
        applyAnalysisState = { analysis -> analysisState = analysis },
        applySettingsAutoPlayDelay = { setting -> settingsState = settingsState.applyAutoPlayDelay(setting) },
        applySettingsSearchTimeSettings = { settings -> settingsState = settingsState.applySearchTimeSettings(settings) },
        clearUndoEngineInterventionQuietWindow = undoController::clearQuietWindow,
    )
    val debugReportController = DebugReportController(
        engineName = engineName,
        engineDiagnostic = engineDiagnostic,
        runtimeEventLog = runtimeEventLog,
        diagnosticEventLog = diagnosticEventLog,
        clipboard = clipboardPort,
        mirror = debugReportMirror,
        userNotice = userNoticePort,
        currentControllerState = { sessionSnapshot },
        isEngineReady = { isEngineReady },
        isEngineBusy = { isEngineBusy },
        analysisCacheStatsText = { "${analysisCache.statsText()}, ${undoAnalysisRestoreCache.statsText()}" },
        positionAnalysisCacheStatsText = engineClient::positionAnalysisCacheStatsText,
        turnTimeText = { turnTimeState.summaryText() },
        turnTimeDebugText = { nowMillis -> turnTimeState.debugText(nowMillis) },
        onEngineMessage = { message -> engineMessage = message },
    )
    fun dispatch(event: GameUiEvent) {
        dispatchGameUiEvent(
            event = event,
            handlers = buildGameUiEventHandlers(
                currentPlayer = { gameState.nextPlayer },
                isTopMovesEnabled = { topMovesEnabled },
                startConfiguredGame = newGameController::startConfiguredGame,
                copyDebugReport = debugReportController::copy,
                showEngineBenchmark = benchmarkController::showResult,
                requestScoreEstimate = scoreEstimateController::request,
                toggleEvalWithGradient = { uxOptions = uxOptions.applyEvalActivation(onEvalGradientActivated = scoreEstimateController::request) },
                showTopMoves = topMovesController::showForCurrentState,
                hideTopMoves = topMovesController::hide,
                undoLastTurn = undoController::undoLastTurn,
                submitMove = humanMoveController::submitMove,
                resignCurrentGame = { resignCurrentGameIfAllowed(isGameEnded, isEngineBusy, gameState.nextPlayer, humanMoveController::submitMove) { isGameEnded = true } },
                dismissResumePrompt = { sessionStore.clear(); savedSessionUiState = savedSessionUiState.dismiss() },
                acceptCacheOptimizationPrompt = cacheOptController::accept,
                dismissCacheOptimizationPrompt = cacheOptController::dismiss,
                restoreSavedSession = { snap ->
                    savedSessionUiState = savedSessionUiState.dismiss()
                    savedSessionController.restore(snap)
                    currentDestination = ScreenDestination.InGame
                },
                changePlayerSetup = settingsController::changePlayerSetup, changeAutoPlayDelay = settingsController::changeAutoPlayDelay,
                changeSearchTimeSettings = settingsController::changeSearchTimeSettings,
                changeBoardSize = { size ->
                    if (isGameEnded) {
                        settingsState = settingsState.applyBoardSize(size)
                        refreshNewGamePreview()
                    }
                },
                changeScoringRule = scoringRuleController::change,
                changeUxOptions = { options -> uxOptions = options },
                changeHandicapCount = { count ->
                    if (isGameEnded) {
                        settingsState = settingsState.applyHandicap(count)
                        refreshNewGamePreview()
                    }
                },
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
        topMovesController.resumeDeferredAnalysisIfIdle()
        runTurnAutomationTriggerEffect(
            quietUntilMillis = undoEngineInterventionQuietUntil,
            topMoveTargetState = gameState,
            requestAiTurn = autoAiTurnController::requestAiTurn,
            requestTopMoveAnalysis = { targetState -> topMovesController.requestAnalysis(targetState, automatic = true) },
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

    val screenState = GoCoachScreenStateAssembler.assemble(
        GoCoachScreenStateAssembler.Input(
            controller = sessionSnapshot,
            uxOptions = uxOptions,
            engineRuntime = GoCoachScreenStateAssembler.EngineRuntime(
                name = engineName,
                diagnostic = engineDiagnostic,
                isReady = isEngineReady,
                isBusy = isEngineBusy,
                isBlockingBusy = isEngineBlockingBusy,
                activityIndicator = engineActivityIndicator,
                hasCompletedStartup = hasCompletedEngineStartup,
            ),
            displayRuntime = GoCoachScreenStateAssembler.DisplayRuntime(
                analysisCacheStats = "${analysisCache.statsText()}, ${undoAnalysisRestoreCache.statsText()}",
                isScoreGraphExpanded = isScoreGraphExpanded,
                turnTimeText = turnTimeState.summaryText(),
            ),
        ),
    )

    val savedSessionToPrompt = if (benchmarkUiState.progress == null && benchmarkUiState.resultToConfirm == null) {
        screenState.resumePrompt?.snapshot
    } else {
        null
    }

    if (savedSessionToPrompt != null) {
        ResumeSavedSessionDialog(
            snapshot = savedSessionToPrompt,
            engineName = screenState.engine.name,
            strings = LocalUiStrings.current,
            onResume = { dispatch(GameUiEvent.ResumeSavedSession(savedSessionToPrompt)) },
            onDismiss = { dispatch(GameUiEvent.DismissResumePrompt) },
        )
    }

    BackHandler(enabled = currentDestination != ScreenDestination.Home) {
        if (currentDestination == ScreenDestination.InGame && !isGameEnded) {
            showResignConfirmFromBack = true
        } else {
            exitToHome()
        }
    }

    if (showResignConfirmFromBack) {
        val strings = LocalUiStrings.current
        AlertDialog(
            onDismissRequest = { showResignConfirmFromBack = false },
            title = { Text(strings.resignConfirmTitle) },
            text = { Text(strings.resignConfirmMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResignConfirmFromBack = false
                        dispatch(GameUiEvent.ResignCurrentGame)
                        exitToHome()
                    },
                ) {
                    Text(strings.resign)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResignConfirmFromBack = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    DirectPlayRecommendationDialog(
        boardSize = settingsState.boardSize,
        isDirectPlayEnabled = uxOptions.isDirectPlayEnabled,
        onConfirm = { enabled -> uxOptions = uxOptions.copy(isDirectPlayEnabled = enabled) }
    )

    when (currentDestination) {
        ScreenDestination.Home -> {
            GoCoachHomeScreen(
                onStartMatchClick = { currentDestination = ScreenDestination.GameSetup }
            )
        }
        ScreenDestination.GameSetup -> {
            GameSetupLobby(
                screenState = screenState,
                onEvent = ::dispatch,
                onBackClick = exitToHome,
                onStartMatch = { currentDestination = ScreenDestination.InGame }
            )
        }
        ScreenDestination.InGame -> {
            GoCoachContent(
                screenState = screenState,
                benchmarkProgress = benchmarkUiState.progress,
                benchmarkResult = benchmarkUiState.resultToConfirm,
                onBenchmarkResultConfirmed = { benchmarkUiState = benchmarkUiState.clearConfirmedResult() },
                onBenchmarkRerun = benchmarkController::rerun,
                isDisplayMenuExpanded = isDisplayMenuExpanded,
                onDisplayMenuExpandedChange = { expanded -> isDisplayMenuExpanded = expanded },
                onScoreGraphExpandedChange = { expanded -> isScoreGraphExpanded = expanded },
                onFinalJudgementReview = ::activateEndgameJudgementReview,
                selectedLanguage = selectedLanguage,
                onLanguageChange = onLanguageChange,
                turnTimeState = turnTimeState,
                onEvent = ::dispatch,
            )
        }
    }
}

internal enum class ScreenDestination {
    Home,
    GameSetup,
    InGame
}
