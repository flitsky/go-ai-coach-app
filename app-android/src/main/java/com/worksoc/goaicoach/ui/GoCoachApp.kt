package com.worksoc.goaicoach.ui

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
import com.worksoc.goaicoach.application.AnalysisCacheKey
import com.worksoc.goaicoach.application.AnalysisResultCache
import com.worksoc.goaicoach.application.AutoAiTurnDisplayPlan
import com.worksoc.goaicoach.application.AutoAiTurnEndgameDisplayPlan
import com.worksoc.goaicoach.application.AutoAiTurnEndgamePlan
import com.worksoc.goaicoach.application.AutoAiTurnFollowUpPlan
import com.worksoc.goaicoach.application.AutoAiTurnRequestPlan
import com.worksoc.goaicoach.application.AutoAiTurnRunExecutionContext
import com.worksoc.goaicoach.application.AutoAiTurnScheduleValidationPlan
import com.worksoc.goaicoach.application.AutoAiTurnUiState
import com.worksoc.goaicoach.application.autoAiEndgameOperationToken
import com.worksoc.goaicoach.application.autoAiTurnOperationToken
import com.worksoc.goaicoach.application.buildAutoAiTurnFailureDisplayPlan
import com.worksoc.goaicoach.application.buildAutoAiTurnEndgamePlan
import com.worksoc.goaicoach.application.buildAutoAiTurnFollowUpPlan
import com.worksoc.goaicoach.application.buildDebugReportCopyPlan
import com.worksoc.goaicoach.application.buildEngineStartupFailureDisplayPlan
import com.worksoc.goaicoach.application.buildEngineStartupSuccessDisplayPlan
import com.worksoc.goaicoach.application.buildEngineUndoPlan
import com.worksoc.goaicoach.application.buildEngineOperationApplyPlan
import com.worksoc.goaicoach.application.buildHumanEngineSyncFailureCompletionPlan
import com.worksoc.goaicoach.application.buildHumanEngineSyncSuccessCompletionPlan
import com.worksoc.goaicoach.application.buildLocalFinalScoreDisplayPlan
import com.worksoc.goaicoach.application.buildLocalTwoPlayerUndoPlan
import com.worksoc.goaicoach.application.buildUndoRequestPlan
import com.worksoc.goaicoach.application.buildNewLocalGameSessionPlan
import com.worksoc.goaicoach.application.buildSavedGameRestoreRequestPlan
import com.worksoc.goaicoach.application.buildSavedSessionCheckPlan
import com.worksoc.goaicoach.application.buildScoreEstimateFailureDisplayPlan
import com.worksoc.goaicoach.application.buildScoreEstimateRequestPlan
import com.worksoc.goaicoach.application.buildScoringRuleChangePlan
import com.worksoc.goaicoach.application.buildStartConfiguredGamePlan
import com.worksoc.goaicoach.application.buildInitialUserPreferencesPlan
import com.worksoc.goaicoach.application.buildPlayerSetupChangePlan
import com.worksoc.goaicoach.application.buildPositionAnalysisCacheOptimizationPlan
import com.worksoc.goaicoach.application.buildPositionAnalysisCacheOptimizationPrompt
import com.worksoc.goaicoach.application.buildGameSessionControllerState
import com.worksoc.goaicoach.application.buildTopMoveAnalysisFailureDisplayPlan
import com.worksoc.goaicoach.application.buildUserPreferencesSnapshot
import com.worksoc.goaicoach.application.EndgameFailureDisplayPlan
import com.worksoc.goaicoach.application.EngineBenchmarkDefaultSamplesPerVisit
import com.worksoc.goaicoach.application.EngineBenchmarkDefaultTimeCapMs
import com.worksoc.goaicoach.application.EngineBenchmarkDefaultVisits
import com.worksoc.goaicoach.application.EngineBenchmarkMeasurementVersion
import com.worksoc.goaicoach.application.EngineBenchmarkUiState
import com.worksoc.goaicoach.application.EngineFallbackPolicy
import com.worksoc.goaicoach.application.EngineOperationGate
import com.worksoc.goaicoach.application.EngineOperationKind
import com.worksoc.goaicoach.application.EngineOperationLifecycleState
import com.worksoc.goaicoach.application.EngineOperationLifecycleTransition
import com.worksoc.goaicoach.application.EngineOperationLifecycleCallbacks
import com.worksoc.goaicoach.application.EngineOperationApplyPlan
import com.worksoc.goaicoach.application.EngineOperationResultGuard
import com.worksoc.goaicoach.application.EngineOperationRequest
import com.worksoc.goaicoach.application.EngineSessionClient
import com.worksoc.goaicoach.application.EngineTimeoutPolicy
import com.worksoc.goaicoach.application.DebugReportMirrorPort
import com.worksoc.goaicoach.application.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.applyHumanMoveLocally
import com.worksoc.goaicoach.application.applyAutoAiTurnRequestPlan
import com.worksoc.goaicoach.application.applyAutoAiTurnScheduleValidationPlan
import com.worksoc.goaicoach.application.applyEngineOperationLifecycleTransition
import com.worksoc.goaicoach.application.EngineBenchmarkStorePort
import com.worksoc.goaicoach.application.ClipboardPort
import com.worksoc.goaicoach.application.applyTopMoveAnalysisLaunchPlan
import com.worksoc.goaicoach.application.completeAutoAiTurnRun
import com.worksoc.goaicoach.application.engineOperationRequest
import com.worksoc.goaicoach.application.engineOperationDiscardedDiagnosticEvent
import com.worksoc.goaicoach.application.evaluateEngineBenchmarkGate
import com.worksoc.goaicoach.application.evaluateAutoAiEndgameResultGuard
import com.worksoc.goaicoach.application.evaluateAutoAiTurnResultGuard
import com.worksoc.goaicoach.application.evaluateScoringRuleChangeGate
import com.worksoc.goaicoach.application.evaluateSearchTimeChangeGate
import com.worksoc.goaicoach.application.evaluateScoreEstimateResultGuard
import com.worksoc.goaicoach.application.evaluateTopMoveAnalysisResultGuard
import com.worksoc.goaicoach.application.FinalScoreDisplayPlan
import com.worksoc.goaicoach.application.GameSessionResetPlan
import com.worksoc.goaicoach.application.GameSessionAnalysisState
import com.worksoc.goaicoach.application.GameSessionControllerState
import com.worksoc.goaicoach.application.GameSessionCoreState
import com.worksoc.goaicoach.application.GameSessionEffect
import com.worksoc.goaicoach.application.GameSessionMoveReviewState
import com.worksoc.goaicoach.application.GameSessionRuntimeState
import com.worksoc.goaicoach.application.GameSessionScoreState
import com.worksoc.goaicoach.application.GameSessionTurnTimeState
import com.worksoc.goaicoach.application.HumanEngineSyncFailurePlan
import com.worksoc.goaicoach.application.HumanEngineSyncDisplayPlan
import com.worksoc.goaicoach.application.HumanEngineSyncCompletionPlan
import com.worksoc.goaicoach.application.HumanEngineSyncRunPlan
import com.worksoc.goaicoach.application.EngineStartupDisplayPlan
import com.worksoc.goaicoach.application.PlayerSetupChangePlan
import com.worksoc.goaicoach.application.PositionAnalysisCacheOptimizationUiState
import com.worksoc.goaicoach.application.PostGamePositionAnalysisCacheOptimizationPromptEnabled
import com.worksoc.goaicoach.application.localScoreSnapshot
import com.worksoc.goaicoach.application.selectRuntimePlayLevel
import com.worksoc.goaicoach.application.shouldRequestTopMoveAnalysis
import com.worksoc.goaicoach.application.planSavedGamePersistence
import com.worksoc.goaicoach.application.RuntimePlayLevelSelection
import com.worksoc.goaicoach.application.RuntimeEventLogPort
import com.worksoc.goaicoach.application.TopMoveAnalysisExecutionContext
import com.worksoc.goaicoach.application.runTopMoveAnalysisEffect
import com.worksoc.goaicoach.application.ScoringRuleChangePlan
import com.worksoc.goaicoach.application.runAutoAiEndgameEffect
import com.worksoc.goaicoach.application.RestoredGameSyncExecutionContext
import com.worksoc.goaicoach.application.runRestoredGameSyncEffect
import com.worksoc.goaicoach.application.runAutoAiTurnEffect
import com.worksoc.goaicoach.application.runEngineBackedNewGameEffect
import com.worksoc.goaicoach.application.runEngineOperationInScope
import com.worksoc.goaicoach.application.runEngineStartupEffect
import com.worksoc.goaicoach.application.runEngineUndoEffect
import com.worksoc.goaicoach.application.runHumanEngineSyncEffect
import com.worksoc.goaicoach.application.runPositionAnalysisCacheOptimizationEffect
import com.worksoc.goaicoach.application.runScoreEstimateEffect
import com.worksoc.goaicoach.application.runStartupBenchmarkEffect
import com.worksoc.goaicoach.application.runScoringRuleSyncDisplayPlan
import com.worksoc.goaicoach.application.runtimeAiTurnBeginLog
import com.worksoc.goaicoach.application.runtimeAiTurnCompleteLog
import com.worksoc.goaicoach.application.runtimeAiTurnEndgameDetectedLog
import com.worksoc.goaicoach.application.runtimeAiTurnEndgameFailureLog
import com.worksoc.goaicoach.application.runtimeAiTurnEndgameSuccessLog
import com.worksoc.goaicoach.application.runtimeAiTurnFailureLog
import com.worksoc.goaicoach.application.runtimeAiTurnScheduleCancelledLog
import com.worksoc.goaicoach.application.runtimeAiTurnScheduleLog
import com.worksoc.goaicoach.application.runtimeAiTurnSuccessLog
import com.worksoc.goaicoach.application.runtimeAppStartLog
import com.worksoc.goaicoach.application.runtimeAutoPlayDelayChangeLog
import com.worksoc.goaicoach.application.runtimeEngineGameStartFailureLog
import com.worksoc.goaicoach.application.runtimeEngineGameStartRequestLog
import com.worksoc.goaicoach.application.runtimeEngineGameStartSuccessLog
import com.worksoc.goaicoach.application.runtimeEngineOperationCompletedLog
import com.worksoc.goaicoach.application.runtimeEngineOperationDiscardedLog
import com.worksoc.goaicoach.application.runtimeEngineOperationStartedLog
import com.worksoc.goaicoach.application.runtimeGameResetLog
import com.worksoc.goaicoach.application.runtimeHumanEngineSyncFailureLog
import com.worksoc.goaicoach.application.runtimeHumanEngineSyncSuccessLog
import com.worksoc.goaicoach.application.runtimeHumanMoveAcceptedLog
import com.worksoc.goaicoach.application.RuntimeLogContext
import com.worksoc.goaicoach.application.StartupBenchmarkExecutionContext
import com.worksoc.goaicoach.application.toDebugReportSnapshot
import com.worksoc.goaicoach.application.runDebugReportCopyEffect
import com.worksoc.goaicoach.application.toAutoAiTurnRequestPlan
import com.worksoc.goaicoach.application.toAutoAiTurnScheduleValidationPlan
import com.worksoc.goaicoach.application.toAutoAiTurnFollowUpRequest
import com.worksoc.goaicoach.application.toShowTopMovesPlan
import com.worksoc.goaicoach.application.toTopMoveAnalysisLaunchPlan
import com.worksoc.goaicoach.application.ShowTopMovesPlan
import com.worksoc.goaicoach.application.ScoreEstimateDisplayPlan
import com.worksoc.goaicoach.application.ScoreEstimateRequestPlan
import com.worksoc.goaicoach.application.scoreEstimateOperationToken
import com.worksoc.goaicoach.application.toScoreEstimateLaunchStateUpdate
import com.worksoc.goaicoach.application.SavedGameRestorePlan
import com.worksoc.goaicoach.application.SavedGameRestoreRequestPlan
import com.worksoc.goaicoach.application.SavedGamePersistencePlan
import com.worksoc.goaicoach.application.SavedGameStorePort
import com.worksoc.goaicoach.application.SavedSessionPromptPlan
import com.worksoc.goaicoach.application.SavedSessionUiState
import com.worksoc.goaicoach.application.StartConfiguredGamePlan
import com.worksoc.goaicoach.application.TopMoveAnalysisUpdate
import com.worksoc.goaicoach.application.topMoveAnalysisOperationToken
import com.worksoc.goaicoach.application.toGameSessionSettingsState
import com.worksoc.goaicoach.application.toRuntimeLogContext
import com.worksoc.goaicoach.application.undoEngineInterventionQuietUntilMillis
import com.worksoc.goaicoach.application.undoEngineInterventionRemainingDelayMillis
import com.worksoc.goaicoach.application.UndoAnalysisRestoreCache
import com.worksoc.goaicoach.application.UndoRequestPlan
import com.worksoc.goaicoach.application.UndoLocalStatePlan
import com.worksoc.goaicoach.application.UserPreferencesStorePort
import com.worksoc.goaicoach.application.UserNoticePort
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.persistence.GameSessionStore
import com.worksoc.goaicoach.persistence.EngineBenchmarkStore
import com.worksoc.goaicoach.persistence.DebugReportMirrorStore
import com.worksoc.goaicoach.persistence.RuntimeEventLog
import com.worksoc.goaicoach.persistence.SavedGameSnapshot
import com.worksoc.goaicoach.persistence.UserPreferencesSnapshot
import com.worksoc.goaicoach.persistence.UserPreferencesStore
import com.worksoc.goaicoach.presentation.GameUiEvent
import com.worksoc.goaicoach.presentation.GameUiEventHandlers
import com.worksoc.goaicoach.presentation.KaTrainUxOptions
import com.worksoc.goaicoach.presentation.buildGameScreenStateInput
import com.worksoc.goaicoach.presentation.buildGameScreenState
import com.worksoc.goaicoach.presentation.dispatchGameUiEvent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class PendingPostUndoEngineSync(
    val targetState: GameState,
    val quietUntilMillis: Long,
)

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
    var gameState by remember { mutableStateOf(initialPlan.gameState) }
    var engineMessage by remember { mutableStateOf("Engine not initialized.") }
    var analysisState by remember {
        mutableStateOf(
            GameSessionAnalysisState.empty(
                state = initialPlan.gameState,
                candidateText = engineDiagnostic,
            ),
        )
    }
    var scoreState by remember {
        mutableStateOf(
            GameSessionScoreState.reset(
                scoreText = "No score estimate yet.",
                scoreSnapshots = listOf(localScoreSnapshot(initialPlan.gameState)),
                endgameLog = "No endgame result recorded.",
            ),
        )
    }
    var moveReviewState by remember {
        mutableStateOf(
            GameSessionMoveReviewState.reset(
                moveReviewText = "No move review yet.",
                lastMoveText = "None",
            ),
        )
    }
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
    var runtimeState by remember {
        mutableStateOf(
            GameSessionRuntimeState(
                playLevel = initialPlan.runtime.playLevel,
                engineProfile = initialPlan.runtime.engineProfile,
                analysisPreset = initialPlan.runtime.analysisPreset,
            ),
        )
    }
    var settingsState by remember {
        mutableStateOf(initialPlan.toGameSessionSettingsState())
    }
    val playerSetup = settingsState.playerSetup
    val matchMode = settingsState.matchMode
    val autoPlayDelaySetting = settingsState.autoPlayDelaySetting
    val searchTimeSettings = settingsState.searchTimeSettings
    val topMovesEnabled = settingsState.topMovesEnabled
    val analysisCache = remember { AnalysisResultCache(maxEntries = 96) }
    val undoAnalysisRestoreCache = remember { UndoAnalysisRestoreCache(maxEntries = 96) }
    var uxOptions by remember { mutableStateOf(initialPreferences.toKaTrainUxOptions()) }
    var isDisplayMenuExpanded by remember { mutableStateOf(false) }
    var isScoreGraphExpanded by remember { mutableStateOf(false) }
    var isGameEnded by remember { mutableStateOf(false) }
    var hasCompletedEngineStartup by remember { mutableStateOf(false) }
    var savedSessionUiState by remember { mutableStateOf(SavedSessionUiState()) }
    val pendingSavedSession = savedSessionUiState.pendingSavedSession
    val shouldShowResumePrompt = savedSessionUiState.shouldShowResumePrompt
    val hasCheckedSavedSession = savedSessionUiState.hasCheckedSavedSession
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
    var benchmarkUiState by remember {
        mutableStateOf(
            EngineBenchmarkUiState.initial(
                benchmarkText = benchmarkStore.loadText(),
                profile = benchmarkStore.load(),
            ),
        )
    }
    var autoAiTurnUiState by remember { mutableStateOf(AutoAiTurnUiState()) }
    val isAutoAiTurnPending = autoAiTurnUiState.isPending
    var positionCacheOptimizationState by remember {
        mutableStateOf(PositionAnalysisCacheOptimizationUiState())
    }
    var undoEngineInterventionQuietUntil by remember { mutableStateOf(0L) }
    var pendingPostUndoEngineSync by remember { mutableStateOf<PendingPostUndoEngineSync?>(null) }
    var pendingPostUndoEngineSyncJob by remember { mutableStateOf<Job?>(null) }

    fun markUndoEngineInterventionQuiet(): Long {
        val quietUntil = undoEngineInterventionQuietUntilMillis(System.currentTimeMillis())
        undoEngineInterventionQuietUntil = quietUntil
        return quietUntil
    }

    suspend fun waitForUndoEngineInterventionQuietWindow() {
        val delayMillis = undoEngineInterventionRemainingDelayMillis(
            nowMillis = System.currentTimeMillis(),
            quietUntilMillis = undoEngineInterventionQuietUntil,
        )
        if (delayMillis > 0L) {
            delay(delayMillis)
        }
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
        scope.launch {
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
        runtimeEventLog.append(
            runtimeEngineOperationDiscardedLog(
                context = currentRuntimeLogContext(),
                discard = discard,
            ),
        )
        diagnosticEventLog.append(
            engineOperationDiscardedDiagnosticEvent(
                discard = discard,
                currentState = gameState,
            ),
        )
    }

    fun shouldApplyEngineOperationResult(operation: EngineOperationRequest): Boolean =
        when (
            val plan = buildEngineOperationApplyPlan(
                request = operation,
                currentState = gameState,
                currentSessionGeneration = runtimeState.sessionGeneration,
            )
        ) {
            EngineOperationApplyPlan.Apply -> true
            is EngineOperationApplyPlan.Discard -> {
                appendEngineOperationDiscardLog(plan.discard)
                false
            }
        }

    LaunchedEffect(Unit) {
        runtimeEventLog.append(runtimeAppStartLog(currentRuntimeLogContext()))
    }

    fun applyEngineStartupDisplayPlan(startup: EngineStartupDisplayPlan) {
        isEngineReady = startup.isEngineReady
        if (startup.scoreSnapshots.isNotEmpty()) {
            scoreState = scoreState.replaceSnapshots(startup.scoreSnapshots)
        }
        engineMessage = startup.engineMessage
        startup.candidateText?.let { text -> analysisState = analysisState.copy(candidateText = text) }
    }

    fun applySavedSessionPromptPlan(prompt: SavedSessionPromptPlan) {
        savedSessionUiState = savedSessionUiState.applyPrompt(prompt)
    }

    LaunchedEffect(engineClient) {
        hasCompletedEngineStartup = false
        val operation = engineOperationRequest(
            kind = EngineOperationKind.EngineStartup,
            state = gameState,
            sessionGeneration = runtimeState.sessionGeneration,
            timeoutPolicy = EngineTimeoutPolicy(label = "engine-startup"),
            fallbackPolicy = EngineFallbackPolicy.None,
        )
        runTrackedEngineOperation(operation) {
            runCatching {
                withContext(Dispatchers.IO) {
                    engineClient.runEngineStartupEffect(
                        effect = GameSessionEffect.StartEngineSession(
                            state = gameState,
                            profile = runtimeState.engineProfile,
                        ),
                        operationRequest = operation,
                        diagnosticEventLog = diagnosticEventLog,
                    )
                }
            }.onSuccess { result ->
                applyEngineStartupDisplayPlan(
                    buildEngineStartupSuccessDisplayPlan(
                        state = gameState,
                        result = result,
                    ),
                )
            }.onFailure { error ->
                applyEngineStartupDisplayPlan(
                    buildEngineStartupFailureDisplayPlan(
                        errorMessage = error.message,
                        engineDiagnostic = engineDiagnostic,
                    ),
                )
            }
        }
        hasCompletedEngineStartup = true
    }

    LaunchedEffect(sessionStore) {
        applySavedSessionPromptPlan(buildSavedSessionCheckPlan(sessionStore.load()))
    }

    suspend fun runEngineBenchmark() {
        when (
            val gate = evaluateEngineBenchmarkGate(
                isEngineReady = isEngineReady,
                supportsDeviceBenchmark = engineClient.capabilities.supportsDeviceBenchmark,
                isEngineBusy = isEngineBusy,
                isBenchmarkRunning = benchmarkUiState.isRunning,
            )
        ) {
            EngineOperationGate.Allow -> Unit
            EngineOperationGate.NoOp -> return
            is EngineOperationGate.Block -> {
                engineMessage = gate.message
                return
            }
        }

        benchmarkUiState = benchmarkUiState.clearResult()
        val operation = engineOperationRequest(
            kind = EngineOperationKind.StartupBenchmark,
            state = gameState,
            sessionGeneration = runtimeState.sessionGeneration,
            timeoutPolicy = EngineTimeoutPolicy(label = "startup-benchmark"),
            fallbackPolicy = EngineFallbackPolicy.None,
        )
        runTrackedEngineOperation(operation) {
            engineMessage = "엔진 벤치마크 시작 전 안정화 대기 중입니다."
            benchmarkUiState = benchmarkUiState.startWaitingForEngineSettle()
            analysisState = analysisState.copy(candidateText = "Engine benchmark waiting for startup settle delay.")
            delay(EngineBenchmarkStartupSettleDelayMillis)

            engineMessage = "최초 실행환경에서 최적 플레이를 위해 벤치마크 테스트가 진행중입니다."
            analysisState = analysisState.copy(
                candidateText = "Engine benchmark running: B16/B32/B64, ${EngineBenchmarkDefaultSamplesPerVisit} samples each.",
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    engineClient
                        .runStartupBenchmarkEffect(
                            effect = GameSessionEffect.RunStartupBenchmark,
                            context = StartupBenchmarkExecutionContext(
                                restoreState = gameState,
                                nowMillis = System.currentTimeMillis(),
                            ),
                            operationRequest = operation,
                            diagnosticEventLog = diagnosticEventLog,
                            onProgress = { progress ->
                                withContext(Dispatchers.Main) {
                                    benchmarkUiState = benchmarkUiState.updateProgress(progress)
                                    engineMessage = progress.stageText
                                    analysisState = analysisState.copy(
                                        candidateText = "Engine benchmark running: ${progress.progressText}, ${progress.sampleText}.",
                                    )
                                }
                            }
                        )
                        .also { profile -> benchmarkStore.save(profile) }
                }
            }.onSuccess { profile ->
                benchmarkUiState = benchmarkUiState.completeWithProfile(
                    benchmarkText = benchmarkStore.loadText(),
                    profile = profile,
                )
                engineMessage = "Engine benchmark saved to ${benchmarkStore.path()}."
                analysisState = analysisState.copy(candidateText = "Engine benchmark complete.\n${profile.toSummaryText()}")
            }.onFailure { error ->
                engineMessage = "Engine benchmark failed: ${error.message ?: "unknown error"}"
                analysisState = analysisState.copy(candidateText = "Engine benchmark failed. The app will continue with built-in defaults.")
                benchmarkUiState = benchmarkUiState.failWithoutProfile()
            }
        }
    }

    fun showEngineBenchmarkResult() {
        benchmarkStore.load()?.let { profile ->
            benchmarkUiState = benchmarkUiState.showResult(profile)
            return
        }
        scope.launch {
            runEngineBenchmark()
        }
    }

    fun rerunEngineBenchmark() {
        benchmarkUiState = benchmarkUiState.clearResult()
        scope.launch {
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
        preferencesStore.save(
            buildUserPreferencesSnapshot(
                settingsState = settingsState,
                ruleset = gameState.ruleset,
                showCoordinates = uxOptions.showCoordinates,
                showMoveNumbers = uxOptions.showMoveNumbers,
                showLastMoveRing = uxOptions.showLastMoveRing,
                showOwnershipOverlay = uxOptions.showOwnershipOverlay,
            ),
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
        when (
            val plan = planSavedGamePersistence(
                savedSessionUiState = savedSessionUiState,
                isGameEnded = isGameEnded,
                gameState = gameState,
                playerSetup = playerSetup,
                playLevel = runtimeState.playLevel,
                topMovesEnabled = topMovesEnabled,
                nowMillis = System.currentTimeMillis(),
            )
        ) {
            SavedGamePersistencePlan.Skip -> Unit
            SavedGamePersistencePlan.Clear -> sessionStore.clear()
            is SavedGamePersistencePlan.Save -> sessionStore.save(plan.snapshot)
        }
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

    fun clearTopMoveSpots(message: String? = null) {
        applyAnalysisSessionState(currentAnalysisSessionState().clearTopMoveSpots(message))
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

    fun applyTopMoveAnalysisFailureDisplayPlan(
        targetState: GameState,
        error: Throwable,
        topMovesEnabled: Boolean,
    ) {
        applyCoreSessionState(
            currentCoreSessionState().applyTopMoveAnalysisFailureDisplayPlan(
                buildTopMoveAnalysisFailureDisplayPlan(
                    targetState = targetState,
                    error = error,
                    topMovesEnabled = topMovesEnabled,
                ),
            ),
        )
    }

    fun applyScoreEstimateDisplayPlan(score: ScoreEstimateDisplayPlan) {
        applyCoreSessionState(currentCoreSessionState().applyScoreEstimateDisplayPlan(score))
    }

    fun applyScoreEstimateFailureDisplayPlan(error: Throwable) {
        applyCoreSessionState(
            currentCoreSessionState().applyScoreEstimateFailureDisplayPlan(
                buildScoreEstimateFailureDisplayPlan(error),
            ),
        )
    }

    fun applyFinalScoreDisplayPlan(final: FinalScoreDisplayPlan) {
        applyCoreSessionState(currentCoreSessionState().applyFinalScoreDisplayPlan(final))
    }

    fun applyEndgameFailureDisplayPlan(failure: EndgameFailureDisplayPlan) {
        applyCoreSessionState(currentCoreSessionState().applyEndgameFailureDisplayPlan(failure))
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
        applyCoreSessionState(currentCoreSessionState().applyAutoAiTurnDisplayPlan(display))
        return buildAutoAiTurnFollowUpPlan(display)
    }

    fun applyAutoAiTurnFailureDisplayPlan(error: Throwable) {
        applyCoreSessionState(
            currentCoreSessionState().applyAutoAiTurnFailureDisplayPlan(
                buildAutoAiTurnFailureDisplayPlan(error),
            ),
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
        applyCoreSessionState(currentCoreSessionState().applyHumanEngineSyncFailurePlan(failure))
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
        applyCoreSessionState(currentCoreSessionState().applyUndoLocalStatePlan(undo))
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
        clearTopMoveSpots("Search time changed. Analysis cache will rebuild with the new time cap.")
        clearReviewAnalysis(gameState)
    }

    fun requestTopMoveAnalysisForState(
        targetState: GameState,
        automatic: Boolean,
        deep: Boolean = false,
    ) {
        if (automatic && pendingPostUndoEngineSync != null) {
            return
        }
        val currentTopMovesEnabled = settingsState.topMovesEnabled
        if (
            !shouldRequestTopMoveAnalysis(
                isGameEnded = isGameEnded,
                isEngineReady = isEngineReady,
                isEngineBusy = isEngineBusy,
                shouldShowResumePrompt = shouldShowResumePrompt,
                playerSetup = playerSetup,
                targetState = targetState,
            )
        ) {
            return
        }

        val launchPlan = currentControllerSessionState().toTopMoveAnalysisLaunchPlan(
            targetState = targetState,
            deep = deep,
            automatic = automatic,
            cachedResultFor = { key ->
                undoAnalysisRestoreCache.get(key) ?: analysisCache.get(key)
            },
        )
        val launchUpdate = analysisState.applyTopMoveAnalysisLaunchPlan(launchPlan) ?: return
        analysisState = launchUpdate.analysisState
        launchUpdate.engineMessage?.let { message -> engineMessage = message }
        val effect = launchUpdate.effect ?: return
        val plan = effect.plan
        val operationToken = topMoveAnalysisOperationToken(
            targetState = targetState,
            plan = plan,
            sessionGeneration = runtimeState.sessionGeneration,
        )

        launchTrackedEngineOperation(operationToken.operation) {
            runCatching {
                withContext(Dispatchers.IO) {
                    engineClient.runTopMoveAnalysisEffect(
                        effect = effect,
                        context = TopMoveAnalysisExecutionContext(
                            targetState = targetState,
                            engineProfile = runtimeState.engineProfile,
                            analysisPreset = runtimeState.analysisPreset,
                            topMovesEnabled = currentTopMovesEnabled,
                            cacheEnabled = analysisCache.isEnabled,
                        ),
                    )
                }
            }.onSuccess { update ->
                when (val guard =
                    evaluateTopMoveAnalysisResultGuard(
                        token = operationToken,
                        currentState = gameState,
                        currentAnalysisKey = analysisState.lastAnalysisKey,
                        currentSessionGeneration = runtimeState.sessionGeneration,
                    )
                ) {
                    EngineOperationResultGuard.Apply -> {
                        applyTopMoveAnalysisUpdate(update, plan.analysisKey)
                        update.undoRestoreResult?.let { cached ->
                            undoAnalysisRestoreCache.put(plan.analysisKey, cached)
                        }
                        update.cachedResult?.let { cached ->
                            analysisCache.put(plan.analysisKey, cached)
                        }
                    }
                    is EngineOperationResultGuard.Discard -> appendEngineOperationDiscardLog(guard)
                }
            }.onFailure { error ->
                when (val guard =
                    evaluateTopMoveAnalysisResultGuard(
                        token = operationToken,
                        currentState = gameState,
                        currentAnalysisKey = analysisState.lastAnalysisKey,
                        currentSessionGeneration = runtimeState.sessionGeneration,
                    )
                ) {
                    EngineOperationResultGuard.Apply -> {
                        applyTopMoveAnalysisFailureDisplayPlan(
                            targetState = targetState,
                            error = error,
                            topMovesEnabled = currentTopMovesEnabled,
                        )
                    }
                    is EngineOperationResultGuard.Discard -> appendEngineOperationDiscardLog(guard)
                }
            }
        }
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
        pendingPostUndoEngineSyncJob = scope.launch {
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
                return@launch
            }

            val operation = engineOperationRequest(
                kind = EngineOperationKind.PostUndoSync,
                state = pending.targetState,
                sessionGeneration = runtimeState.sessionGeneration,
                timeoutPolicy = engineProfileTimeoutPolicy(runtimeState.engineProfile),
                fallbackPolicy = EngineFallbackPolicy.LocalRules,
            )
            var shouldRequestFollowUpAnalysis = false
            runTrackedEngineOperation(operation) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        engineClient.runScoringRuleSyncDisplayPlan(
                            state = pending.targetState,
                            profile = runtimeState.engineProfile,
                            previousSnapshots = scoreState.scoreSnapshots,
                            engineMessage = "Local undo settled; engine analysis synced.",
                            operationRequest = operation,
                            diagnosticEventLog = diagnosticEventLog,
                        )
                    }
                }.onSuccess { score ->
                    if (shouldApplyEngineOperationResult(operation)) {
                        applyScoreEstimateDisplayPlan(score)
                        shouldRequestFollowUpAnalysis = true
                    }
                }.onFailure { error ->
                    if (shouldApplyEngineOperationResult(operation)) {
                        engineMessage = error.message ?: "Local undo engine sync failed."
                        shouldRequestFollowUpAnalysis = true
                    }
                }
            }
            if (pendingPostUndoEngineSync == pending) {
                pendingPostUndoEngineSync = null
            }
            if (shouldRequestFollowUpAnalysis) {
                requestTopMoveAnalysisForState(
                    targetState = pending.targetState,
                    automatic = true,
                )
            }
        }
    }

    fun showTopMovesForCurrentState() {
        if (
            !shouldRequestTopMoveAnalysis(
                isGameEnded = isGameEnded,
                isEngineReady = isEngineReady,
                isEngineBusy = isEngineBusy,
                shouldShowResumePrompt = shouldShowResumePrompt,
                playerSetup = playerSetup,
                targetState = gameState,
            )
        ) {
            settingsState = settingsState.hideTopMoves()
            clearTopMoveSpots()
            engineMessage = "Top Moves is available only on human turns."
            return
        }
        settingsState = settingsState.showTopMoves()
        when (
            val plan = currentControllerSessionState().toShowTopMovesPlan(
                isEngineBusy = isEngineBusy,
            )
        ) {
            is ShowTopMovesPlan.ShowCached -> {
                analysisState = analysisState.copy(candidateMoves = plan.candidateMoves)
                engineMessage = plan.engineMessage
            }
            is ShowTopMovesPlan.RequestAnalysis -> {
                analysisState = analysisState.copy(candidateMoves = plan.candidateMoves)
                plan.engineMessage?.let { message -> engineMessage = message }
                requestTopMoveAnalysisForState(
                    targetState = gameState,
                    automatic = false,
                    deep = plan.deep,
                )
            }
        }
    }

    fun hideTopMoves() {
        settingsState = settingsState.hideTopMoves()
        clearTopMoveSpots()
        engineMessage = "Top Moves hidden. Background move review keeps using fast best-1 analysis."
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

        val ruleSyncOperation = engineOperationRequest(
            kind = EngineOperationKind.ScoringRuleSync,
            state = nextState,
            sessionGeneration = runtimeState.sessionGeneration,
            timeoutPolicy = engineProfileTimeoutPolicy(runtimeState.engineProfile),
            fallbackPolicy = EngineFallbackPolicy.LocalRules,
        )
        launchTrackedEngineOperation(ruleSyncOperation) {
            var shouldRequestFollowUpAnalysis = false
            runCatching {
                withContext(Dispatchers.IO) {
                    engineClient.runScoringRuleSyncDisplayPlan(
                        state = nextState,
                        profile = runtimeState.engineProfile,
                        previousSnapshots = scoreState.scoreSnapshots,
                        engineMessage = "Scoring rule changed to ${nextRuleset.scoringLabel}; engine rules synchronized.",
                        operationRequest = ruleSyncOperation,
                        diagnosticEventLog = diagnosticEventLog,
                    )
                }
            }.onSuccess { score ->
                if (shouldApplyEngineOperationResult(ruleSyncOperation)) {
                    applyScoreEstimateDisplayPlan(score)
                    shouldRequestFollowUpAnalysis = true
                }
            }.onFailure { error ->
                if (shouldApplyEngineOperationResult(ruleSyncOperation)) {
                    engineMessage = error.message ?: "Scoring rule changed, but engine rule sync failed."
                    shouldRequestFollowUpAnalysis = true
                }
            }
            if (shouldRequestFollowUpAnalysis) {
                requestTopMoveAnalysisForState(
                    targetState = gameState,
                    automatic = true,
                )
            }
        }
    }

    fun resetLocalGame(
        message: String,
        ruleset: Ruleset = gameState.ruleset,
    ) {
        applyGameSessionResetPlan(buildNewLocalGameSessionPlan(message, ruleset))
    }

    fun restoreSavedSession(snapshot: SavedGameSnapshot) {
        val request = buildSavedGameRestoreRequestPlan(
            snapshot = snapshot,
            currentProfile = runtimeState.engineProfile,
            defaultPlayLevel = defaultPlayLevel,
            isEngineBusy = isEngineBusy,
            isEngineReady = isEngineReady,
            searchTimeSettings = searchTimeSettings,
        )
        if (request is SavedGameRestoreRequestPlan.ShowMessage) {
            engineMessage = request.message
            return
        }

        val restoreRequest = request as SavedGameRestoreRequestPlan.Restore
        val restore = restoreRequest.restore
        val restoredState = restore.gameState
        val restoredProfile = restore.runtime.engineProfile
        val restoreOperation = engineOperationRequest(
            kind = EngineOperationKind.RestoredGameSync,
            state = restoredState,
            sessionGeneration = runtimeState.sessionGeneration + 1L,
            timeoutPolicy = engineProfileTimeoutPolicy(restoredProfile),
            fallbackPolicy = EngineFallbackPolicy.LocalRules,
        )

        applySavedGameRestorePlan(restore)

        if (!restoreRequest.syncEngineAfterRestore) {
            return
        }

        launchTrackedEngineOperation(restoreOperation) {
            var shouldRequestFollowUpAnalysis = false
            runCatching {
                withContext(Dispatchers.IO) {
                    engineClient.runRestoredGameSyncEffect(
                        effect = GameSessionEffect.SyncRestoredGame(restoredState),
                        context = RestoredGameSyncExecutionContext(
                            profile = restoredProfile,
                        ),
                        operationRequest = restoreOperation,
                        diagnosticEventLog = diagnosticEventLog,
                    )
                }
            }.onSuccess { score ->
                if (shouldApplyEngineOperationResult(restoreOperation)) {
                    applyScoreEstimateDisplayPlan(score)
                    shouldRequestFollowUpAnalysis = true
                }
            }.onFailure { error ->
                if (shouldApplyEngineOperationResult(restoreOperation)) {
                    engineMessage = error.message ?: "Saved game restored locally, but engine sync failed."
                    shouldRequestFollowUpAnalysis = true
                }
            }
            if (shouldRequestFollowUpAnalysis) {
                requestTopMoveAnalysisForState(
                    targetState = restoredState,
                    automatic = true,
                )
            }
        }
    }

    fun requestEngineScoreEstimate(effect: GameSessionEffect.RunScoreEstimate) {
        val request = effect.request
        val operationToken = scoreEstimateOperationToken(
            request,
            sessionGeneration = runtimeState.sessionGeneration,
        )
        launchTrackedEngineOperation(operationToken.operation) {
            runCatching {
                withContext(Dispatchers.IO) {
                    engineClient.runScoreEstimateEffect(
                        effect = effect,
                        previousSnapshots = scoreState.scoreSnapshots,
                        operationRequest = operationToken.operation,
                        diagnosticEventLog = diagnosticEventLog,
                    )
                }
            }.onSuccess { score ->
                when (val guard =
                    evaluateScoreEstimateResultGuard(
                        token = operationToken,
                        currentState = gameState,
                        currentSessionGeneration = runtimeState.sessionGeneration,
                    )
                ) {
                    EngineOperationResultGuard.Apply -> applyScoreEstimateDisplayPlan(score)
                    is EngineOperationResultGuard.Discard -> appendEngineOperationDiscardLog(guard)
                }
            }.onFailure { error ->
                when (val guard =
                    evaluateScoreEstimateResultGuard(
                        token = operationToken,
                        currentState = gameState,
                        currentSessionGeneration = runtimeState.sessionGeneration,
                    )
                ) {
                    EngineOperationResultGuard.Apply -> {
                        applyScoreEstimateFailureDisplayPlan(error)
                    }
                    is EngineOperationResultGuard.Discard -> appendEngineOperationDiscardLog(guard)
                }
            }
        }
    }

    fun requestScoreEstimate() {
        val plan = buildScoreEstimateRequestPlan(
            state = gameState,
            previousSnapshots = scoreState.scoreSnapshots,
            isEngineReady = isEngineReady,
            isEngineBusy = isEngineBusy,
            matchMode = matchMode,
            engineProfile = runtimeState.engineProfile,
        )
        val launch = plan.toScoreEstimateLaunchStateUpdate()
        launch.engineMessage?.let { message -> engineMessage = message }
        launch.display?.let(::applyScoreEstimateDisplayPlan)
        launch.effect?.let(::requestEngineScoreEstimate)
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
            runCatching {
                withContext(Dispatchers.IO) {
                    engineClient.runEngineBackedNewGameEffect(
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
            }.onSuccess { result ->
                runtimeEventLog.append(
                    runtimeEngineGameStartSuccessLog(
                        context = currentRuntimeLogContext(),
                        elapsedMs = System.currentTimeMillis() - startMillis,
                        message = result.message,
                    ),
                )
                resetLocalGame(result.message, targetRuleset)
                scoreState = scoreState.replaceSnapshots(listOf(result.scoreSnapshot ?: localScoreSnapshot(gameState)))
                nextAnalysisState = gameState
            }.onFailure { error ->
                runtimeEventLog.append(
                    runtimeEngineGameStartFailureLog(
                        context = currentRuntimeLogContext(),
                        elapsedMs = System.currentTimeMillis() - startMillis,
                        error = error,
                    ),
                )
                resetLocalGame(error.message ?: "New AI game failed.", targetRuleset)
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
                scope.launch {
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
                            return@launch
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
                    runCatching {
                        withContext(Dispatchers.IO) {
                            engineClient.runAutoAiTurnEffect(
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
                    }.onSuccess { display ->
                        when (val guard =
                            evaluateAutoAiTurnResultGuard(
                                token = turnOperationToken,
                                currentState = gameState,
                                currentSessionGeneration = runtimeState.sessionGeneration,
                            )
                        ) {
                            EngineOperationResultGuard.Apply -> {
                                val turnTimeUpdate = turnTimeState.recordMove(
                                    player = turnContext.aiPlayer,
                                    nowMillis = System.currentTimeMillis(),
                                    nextPlayer = display.gameState.nextPlayer,
                                )
                                runtimeEventLog.append(
                                    runtimeAiTurnSuccessLog(
                                        context = currentRuntimeLogContext(),
                                        turnState = turnContext.turnState,
                                        aiPlayer = turnContext.aiPlayer,
                                        display = display,
                                        turnElapsedMs = System.currentTimeMillis() - turnStartMillis,
                                        turnTimeUpdate = turnTimeUpdate,
                                    ),
                                )
                                turnTimeState = turnTimeUpdate.after
                                followUpPlan = applyAutoAiTurnDisplayPlan(display)
                                when (val endgamePlan = buildAutoAiTurnEndgamePlan(display)) {
                                    AutoAiTurnEndgamePlan.None -> Unit
                                    is AutoAiTurnEndgamePlan.Resolve -> {
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
                                        when (
                                            val endgameDisplay = withContext(Dispatchers.IO) {
                                                engineClient.runAutoAiEndgameEffect(
                                                    effect = GameSessionEffect.ResolveAutoAiEndgame(endgamePlan),
                                                    previousSnapshots = scoreState.scoreSnapshots,
                                                    operationRequest = endgameOperationToken.operation,
                                                    diagnosticEventLog = diagnosticEventLog,
                                                )
                                            }
                                        ) {
                                            is AutoAiTurnEndgameDisplayPlan.Resolved -> {
                                                when (val guard =
                                                    evaluateAutoAiEndgameResultGuard(
                                                        token = endgameOperationToken,
                                                        currentState = gameState,
                                                        currentSessionGeneration = runtimeState.sessionGeneration,
                                                    )
                                                ) {
                                                    EngineOperationResultGuard.Apply -> {
                                                        runtimeEventLog.append(
                                                            runtimeAiTurnEndgameSuccessLog(
                                                                context = currentRuntimeLogContext(),
                                                                state = endgamePlan.state,
                                                                endgame = endgameDisplay.resolution,
                                                            ),
                                                        )
                                                        applyFinalScoreDisplayPlan(endgameDisplay.display)
                                                    }
                                                    is EngineOperationResultGuard.Discard -> appendEngineOperationDiscardLog(guard)
                                                }
                                            }

                                            is AutoAiTurnEndgameDisplayPlan.Failed -> {
                                                when (val guard =
                                                    evaluateAutoAiEndgameResultGuard(
                                                        token = endgameOperationToken,
                                                        currentState = gameState,
                                                        currentSessionGeneration = runtimeState.sessionGeneration,
                                                    )
                                                ) {
                                                    EngineOperationResultGuard.Apply -> {
                                                        runtimeEventLog.append(
                                                            runtimeAiTurnEndgameFailureLog(
                                                                context = currentRuntimeLogContext(),
                                                                state = endgamePlan.state,
                                                                error = endgameDisplay.error,
                                                            ),
                                                        )
                                                        applyEndgameFailureDisplayPlan(endgameDisplay.display)
                                                    }
                                                    is EngineOperationResultGuard.Discard -> appendEngineOperationDiscardLog(guard)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            is EngineOperationResultGuard.Discard -> appendEngineOperationDiscardLog(guard)
                        }
                    }.onFailure { error ->
                        when (val guard =
                            evaluateAutoAiTurnResultGuard(
                                token = turnOperationToken,
                                currentState = gameState,
                                currentSessionGeneration = runtimeState.sessionGeneration,
                            )
                        ) {
                            EngineOperationResultGuard.Apply -> {
                                runtimeEventLog.append(
                                    runtimeAiTurnFailureLog(
                                        context = currentRuntimeLogContext(),
                                        turnState = turnContext.turnState,
                                        aiPlayer = turnContext.aiPlayer,
                                        turnElapsedMs = System.currentTimeMillis() - turnStartMillis,
                                        error = error,
                                    ),
                                )
                                applyAutoAiTurnFailureDisplayPlan(error)
                            }
                            is EngineOperationResultGuard.Discard -> appendEngineOperationDiscardLog(guard)
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
        applyCoreSessionState(currentCoreSessionState().applyHumanMoveLocalResult(localMove))

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

        val humanSyncOperation = engineOperationRequest(
            kind = EngineOperationKind.HumanMoveSync,
            state = afterMove,
            sessionGeneration = runtimeState.sessionGeneration,
            timeoutPolicy = engineProfileTimeoutPolicy(runtimeState.engineProfile),
            fallbackPolicy = EngineFallbackPolicy.LocalRules,
        )
        launchTrackedEngineOperation(humanSyncOperation) {
            var nextAnalysisState: GameState? = null
            val syncStartMillis = System.currentTimeMillis()
            runCatching {
                withContext(Dispatchers.IO) {
                    engineClient.runHumanEngineSyncEffect(
                        GameSessionEffect.SyncHumanMove(
                            HumanEngineSyncRunPlan(
                                afterMove = afterMove,
                                profile = runtimeState.engineProfile,
                                move = move,
                                previousReviewCandidates = previousReviewCandidates,
                            ),
                        ),
                        operationRequest = humanSyncOperation,
                        diagnosticEventLog = diagnosticEventLog,
                    )
                }
            }.onSuccess { result ->
                when (val completion = buildHumanEngineSyncSuccessCompletionPlan(
                    operation = humanSyncOperation,
                    currentState = gameState,
                    currentSessionGeneration = runtimeState.sessionGeneration,
                    afterMove = afterMove,
                    moveDescription = move.describe(beforeMove.boardSize),
                    result = result,
                    localMove = localMove,
                    previousSnapshots = scoreState.scoreSnapshots,
                )) {
                    is HumanEngineSyncCompletionPlan.ApplySuccess -> {
                        runtimeEventLog.append(
                            runtimeHumanEngineSyncSuccessLog(
                                context = currentRuntimeLogContext(),
                                sync = completion.display,
                                elapsedMs = System.currentTimeMillis() - syncStartMillis,
                            ),
                        )
                        nextAnalysisState = applyHumanEngineSyncDisplayPlan(completion.display)
                    }
                    is HumanEngineSyncCompletionPlan.ApplyFailure -> Unit
                    is HumanEngineSyncCompletionPlan.Discard -> appendEngineOperationDiscardLog(completion.discard)
                }
            }.onFailure { error ->
                when (val completion = buildHumanEngineSyncFailureCompletionPlan(
                    operation = humanSyncOperation,
                    currentState = gameState,
                    currentSessionGeneration = runtimeState.sessionGeneration,
                    localMove = localMove,
                    previousSnapshots = scoreState.scoreSnapshots,
                    errorMessage = error.message,
                )) {
                    is HumanEngineSyncCompletionPlan.ApplySuccess -> Unit
                    is HumanEngineSyncCompletionPlan.ApplyFailure -> {
                        runtimeEventLog.append(
                            runtimeHumanEngineSyncFailureLog(
                                context = currentRuntimeLogContext(),
                                failure = completion.failure,
                                elapsedMs = System.currentTimeMillis() - syncStartMillis,
                            ),
                        )
                        applyHumanEngineSyncFailurePlan(completion.failure)
                    }
                    is HumanEngineSyncCompletionPlan.Discard -> appendEngineOperationDiscardLog(completion.discard)
                }
            }
            nextAnalysisState?.let { state ->
                requestTopMoveAnalysisForState(
                    targetState = state,
                    automatic = true,
                )
            }
        }
    }

    fun undoLocalTwoPlayerTurn(plan: UndoRequestPlan.LocalTwoPlayerUndo) {
        val undo = buildLocalTwoPlayerUndoPlan(
            currentState = gameState,
            scoreSnapshots = scoreState.scoreSnapshots,
        )
        val nextState = undo.gameState
        applyUndoLocalStatePlan(undo)
        val quietUntilMillis = markUndoEngineInterventionQuiet()
        if (!plan.syncEngineAfterUndo) {
            engineMessage = "Local undo completed without engine sync."
            cancelPendingPostUndoEngineSync()
            return
        }
        engineMessage = "Local undo completed. Engine analysis will resume after undo input settles."
        schedulePostUndoLocalEngineSync(
            targetState = nextState,
            quietUntilMillis = quietUntilMillis,
        )
    }

    fun undoEngineBackedTurn(plan: UndoRequestPlan.EngineUndo) {
        val undoCount = plan.undoCount
        val operation = engineOperationRequest(
            kind = EngineOperationKind.EngineUndo,
            state = gameState,
            sessionGeneration = runtimeState.sessionGeneration,
            timeoutPolicy = EngineTimeoutPolicy(label = "engine-undo"),
            fallbackPolicy = EngineFallbackPolicy.LocalEngine,
        )
        launchTrackedEngineOperation(operation) {
            runCatching {
                withContext(Dispatchers.IO) {
                    engineClient.runEngineUndoEffect(
                        effect = GameSessionEffect.UndoEngineMoves(
                            state = gameState,
                            undoCount = undoCount,
                        ),
                        operationRequest = operation,
                        diagnosticEventLog = diagnosticEventLog,
                    )
                }
            }.onSuccess {
                val undo = buildEngineUndoPlan(
                    currentState = gameState,
                    undoCount = undoCount,
                    previousMoveReviews = moveReviewState.moveReviews,
                    scoreSnapshots = scoreState.scoreSnapshots,
                )
                val nextState = undo.gameState
                applyUndoLocalStatePlan(undo)
                engineMessage = "Undid $undoCount move(s) in local state and engine state."
                markUndoEngineInterventionQuiet()
                cancelPendingPostUndoEngineSync()
            }.onFailure { error ->
                engineMessage = error.message ?: "Undo failed."
            }
        }
    }

    fun undoLastTurn() {
        when (
            val plan = buildUndoRequestPlan(
                currentState = gameState,
                matchMode = matchMode,
                isEngineReady = isEngineReady,
                isEngineBusy = isEngineBusy,
                humanSeatCount = playerSetup.humanSeatCount(),
            )
        ) {
            is UndoRequestPlan.ShowMessage -> {
                engineMessage = plan.message
                return
            }
            is UndoRequestPlan.LocalTwoPlayerUndo -> {
                undoLocalTwoPlayerTurn(plan)
                return
            }
            is UndoRequestPlan.EngineUndo -> {
                undoEngineBackedTurn(plan)
            }
        }
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
        scope.launch {
            runTrackedEngineOperation(operation) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        engineClient.runPositionAnalysisCacheOptimizationEffect(
                            effect = effect,
                            operationRequest = operation,
                            diagnosticEventLog = diagnosticEventLog,
                        )
                    }
                }.onSuccess { result ->
                    engineMessage = result.messageText()
                    analysisState = analysisState.copy(candidateText = result.messageText())
                }.onFailure { error ->
                    engineMessage = error.message ?: "Post-game cache optimization failed."
                }
                positionCacheOptimizationState = positionCacheOptimizationState.finishRunning()
            }
        }
    }

    fun copyDebugReport() {
        val plan = buildDebugReportCopyPlan(
            currentControllerSessionState().toDebugReportSnapshot(
                engineName = engineName,
                engineDiagnostic = engineDiagnostic,
                analysisCacheStats = "${analysisCache.statsText()}, ${undoAnalysisRestoreCache.statsText()}",
                positionAnalysisCacheStats = engineClient.positionAnalysisCacheStatsText(System.currentTimeMillis()),
                isEngineReady = isEngineReady,
                isEngineBusy = isEngineBusy,
                turnTimeText = turnTimeState.summaryText(),
                turnTimeDebugText = turnTimeState.debugText(System.currentTimeMillis()),
                runtimeEventLogText = runtimeEventLog.readText(),
                diagnosticEventLogText = diagnosticEventLog.readText(),
            ),
        )
        val result = runDebugReportCopyEffect(
            effect = GameSessionEffect.CopyDebugReport(plan),
            clipboard = clipboardPort,
            mirror = debugReportMirror,
            userNotice = userNoticePort,
        )
        engineMessage = result.engineMessage
    }

    fun dispatch(event: GameUiEvent) {
        dispatchGameUiEvent(
            event = event,
            handlers = GameUiEventHandlers(
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
        isGameEnded,
        shouldShowResumePrompt,
        playerSetup,
        autoPlayDelaySetting,
        searchTimeSettings,
        undoEngineInterventionQuietUntil,
        gameState.nextPlayer,
        gameState.moves.size,
    ) {
        waitForUndoEngineInterventionQuietWindow()
        requestAiTurnForCurrentState()
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
        waitForUndoEngineInterventionQuietWindow()
        requestTopMoveAnalysisForState(
            targetState = gameState,
            automatic = true,
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
        if (!PostGamePositionAnalysisCacheOptimizationPromptEnabled) {
            positionCacheOptimizationState = positionCacheOptimizationState.clearPrompt()
            return@LaunchedEffect
        }
        val plan = currentCacheOptimizationPlan()
        val prompt = buildPositionAnalysisCacheOptimizationPrompt(
            isGameEnded = isGameEnded,
            isEngineReady = isEngineReady,
            isEngineBusy = isEngineBusy,
            isOptimizationRunning = positionCacheOptimizationState.isRunning,
            dismissedGameFingerprint = positionCacheOptimizationState.dismissedGameFingerprint,
            plan = plan,
        )
        positionCacheOptimizationState = positionCacheOptimizationState.withPrompt(prompt)
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

private fun UserPreferencesSnapshot.toKaTrainUxOptions(): KaTrainUxOptions =
    KaTrainUxOptions(
        showCoordinates = showCoordinates,
        showMoveNumbers = showMoveNumbers,
        showLastMoveRing = showLastMoveRing,
        showOwnershipOverlay = showOwnershipOverlay,
    )

private const val EngineBenchmarkStartupSettleDelayMillis = 1_500L
