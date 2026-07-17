package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.autoai.AutoAiTurnController
import com.worksoc.goaicoach.application.autoai.AutoAiTurnRequestPlan
import com.worksoc.goaicoach.application.autoai.AutoAiTurnScheduleValidationPlan
import com.worksoc.goaicoach.application.autoai.AutoAiTurnDisplayPlan
import com.worksoc.goaicoach.application.autoai.AutoAiTurnUiState
import com.worksoc.goaicoach.application.autoai.buildAutoAiTurnFailureDisplayPlan
import com.worksoc.goaicoach.application.engine.EngineBenchmarkController
import com.worksoc.goaicoach.application.engine.EngineBenchmarkUiState
import com.worksoc.goaicoach.application.autoai.applyAutoAiTurnRequestPlan
import com.worksoc.goaicoach.application.autoai.applyAutoAiTurnScheduleValidationPlan
import com.worksoc.goaicoach.application.autoai.completeAutoAiTurnRun
import com.worksoc.goaicoach.application.engine.EngineBenchmarkStorePort
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.debugreport.DebugReportController
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.operation.EngineOperationLifecycleController
import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.application.humanmove.HumanMoveController
import com.worksoc.goaicoach.application.startgame.NewGameController
import com.worksoc.goaicoach.application.startgame.GameSessionResetPlan
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.runtime.runtimeGameResetLog
import com.worksoc.goaicoach.application.savedgame.SavedGameStorePort
import com.worksoc.goaicoach.application.savedgame.SavedSessionController
import com.worksoc.goaicoach.application.savedgame.SavedSessionUiState
import com.worksoc.goaicoach.application.savedgame.SavedGameRestorePlan
import com.worksoc.goaicoach.application.analysis.PositionCacheOptimizationController
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationUiState
import com.worksoc.goaicoach.application.analysis.AnalysisResultCache
import com.worksoc.goaicoach.application.analysis.UndoAnalysisRestoreCache
import com.worksoc.goaicoach.application.score.ScoreEstimateController
import com.worksoc.goaicoach.application.score.ScoringRuleController
import com.worksoc.goaicoach.application.score.FinalScoreDisplayPlan
import com.worksoc.goaicoach.application.score.ScoringRuleChangePlan
import com.worksoc.goaicoach.application.session.GameSessionAnalysisState
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.GameSessionCoreState
import com.worksoc.goaicoach.application.session.GameSessionDisplayStateApplier
import com.worksoc.goaicoach.application.session.GameSessionRuntimeState
import com.worksoc.goaicoach.application.session.GameSessionScoreState
import com.worksoc.goaicoach.application.session.GameSessionSettingsState
import com.worksoc.goaicoach.application.session.GameSessionTurnTimeState
import com.worksoc.goaicoach.application.session.GameSessionMoveReviewState
import com.worksoc.goaicoach.application.session.GameSettingsController
import com.worksoc.goaicoach.application.session.TurnTimeMoveUpdate
import com.worksoc.goaicoach.application.session.RuntimePlayLevelSelection
import com.worksoc.goaicoach.application.preferences.UserPreferencesStorePort
import com.worksoc.goaicoach.application.topmoves.TopMovesController
import com.worksoc.goaicoach.application.topmoves.TopMoveAnalysisDeferral
import com.worksoc.goaicoach.application.undo.UndoController
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
import com.worksoc.goaicoach.application.debugreport.ClipboardPort
import com.worksoc.goaicoach.application.debugreport.UserNoticePort
import com.worksoc.goaicoach.application.debugreport.DebugReportMirrorPort
import kotlinx.coroutines.CoroutineScope

internal data class GoCoachControllers(
    val topMovesController: TopMovesController,
    val undoController: UndoController,
    val autoAiTurnController: AutoAiTurnController,
    val humanMoveController: HumanMoveController,
    val newGameController: NewGameController,
    val savedSessionController: SavedSessionController,
    val cacheOptController: PositionCacheOptimizationController,
    val scoreEstimateController: ScoreEstimateController,
    val scoringRuleController: ScoringRuleController,
    val settingsController: GameSettingsController,
    val debugReportController: DebugReportController,
    val benchmarkController: EngineBenchmarkController,
)

internal interface GoCoachAppWiringContext {
    val scope: CoroutineScope
    val engineClient: EngineSessionClient
    val diagnosticEventLog: DiagnosticEventLogPort
    val runtimeEventLog: RuntimeEventLogPort
    val sessionStore: SavedGameStorePort
    val preferencesStore: UserPreferencesStorePort
    val benchmarkStore: EngineBenchmarkStorePort
    val debugReportMirror: DebugReportMirrorPort
    val clipboardPort: ClipboardPort
    val userNoticePort: UserNoticePort
    val lifecycleController: EngineOperationLifecycleController
    val displayStateApplier: GameSessionDisplayStateApplier
    val defaultPlayLevel: PlayLevelSetting
    val analysisCache: AnalysisResultCache
    val undoAnalysisRestoreCache: UndoAnalysisRestoreCache
    val deferredTopMoveAnalysis: TopMoveAnalysisDeferral

    // Snapshot
    fun sessionSnapshot(): GameSessionControllerState

    // State getters
    fun gameState(): GameState
    fun playerSetup(): PlayerSetup
    fun analysisState(): GameSessionAnalysisState
    fun scoreState(): GameSessionScoreState
    fun moveReviewState(): GameSessionMoveReviewState
    fun runtimeState(): GameSessionRuntimeState
    fun settingsState(): GameSessionSettingsState
    fun autoAiTurnUiState(): AutoAiTurnUiState
    fun positionCacheOptimizationState(): PositionAnalysisCacheOptimizationUiState
    fun benchmarkUiState(): EngineBenchmarkUiState
    fun savedSessionUiState(): SavedSessionUiState
    fun turnTimeState(): GameSessionTurnTimeState
    fun undoEngineInterventionQuietUntil(): Long
    fun isPendingUndoSync(): Boolean
    fun isEngineReady(): Boolean
    fun isEngineBusy(): Boolean
    fun isEngineBlockingBusy(): Boolean
    fun isGameEnded(): Boolean
    fun shouldShowResumePrompt(): Boolean
    fun matchMode(): MatchMode
    fun topMovesEnabled(): Boolean
    fun currentRuntimeLogContext(): RuntimeLogContext
    fun engineName(): String
    fun engineDiagnostic(): String

    // State setters
    fun setGameState(value: GameState)
    fun setEngineMessage(value: String)
    fun setAnalysisState(value: GameSessionAnalysisState)
    fun setScoreState(value: GameSessionScoreState)
    fun setMoveReviewState(value: GameSessionMoveReviewState)
    fun setRuntimeState(value: GameSessionRuntimeState)
    fun setSettingsState(value: GameSessionSettingsState)
    fun setAutoAiTurnUiState(value: AutoAiTurnUiState)
    fun setPositionCacheOptimizationState(value: PositionAnalysisCacheOptimizationUiState)
    fun setBenchmarkUiState(value: EngineBenchmarkUiState)
    fun setSavedSessionUiState(value: SavedSessionUiState)
    fun setTurnTimeState(value: GameSessionTurnTimeState)
    fun setUndoEngineInterventionQuietUntil(value: Long)
    fun setPendingUndoSync(value: Boolean)
    fun setEngineReady(value: Boolean)
    fun setIsGameEnded(value: Boolean)

    // Side-effects / Actions
    fun applyCoreSessionState(next: GameSessionCoreState)
    fun applyCoreState(next: GameSessionCoreState)
    fun activateEndgameJudgementReview()
    fun clearUndoEngineInterventionQuietWindow()
    fun engineProfileTimeoutPolicy(profile: EngineProfile): EngineTimeoutPolicy
    fun applyFinalScoreWithJudgement(final: FinalScoreDisplayPlan)
}

internal fun wireGoCoachControllers(
    context: GoCoachAppWiringContext
): GoCoachControllers {
    val topMovesController = TopMovesController(
        engineClient = context.engineClient,
        currentControllerState = { context.sessionSnapshot() },
        isGameEnded = { context.isGameEnded() },
        isEngineReady = { context.isEngineReady() },
        isEngineBusy = { context.isEngineBusy() },
        shouldShowResumePrompt = { context.shouldShowResumePrompt() },
        currentPlayerSetup = { context.playerSetup() },
        pendingPostUndoEngineSync = { context.isPendingUndoSync() },
        analysisCacheEnabled = { context.analysisCache.isEnabled },
        cachedResultFor = { key -> context.undoAnalysisRestoreCache.get(key) ?: context.analysisCache.get(key) },
        currentGameState = { context.gameState() },
        currentAnalysisKey = { context.analysisState().lastAnalysisKey },
        currentSessionGeneration = { context.runtimeState().sessionGeneration },
        launchEngineOperation = { operation, block -> context.lifecycleController.launchTracked(operation) { block() } },
        applyLaunchUpdate = { launchUpdate ->
            context.setAnalysisState(launchUpdate.analysisState)
            launchUpdate.engineMessage?.let { message -> context.setEngineMessage(message) }
        },
        applyTopMoveAnalysisUpdate = { update, analysisKey ->
            context.setAnalysisState(context.analysisState().applyTopMoveAnalysisUpdate(update, analysisKey))
            context.setEngineMessage(update.engineMessage)
        },
        putUndoRestoreCache = { key, cached -> context.undoAnalysisRestoreCache.put(key, cached) },
        putAnalysisCache = { key, cached -> context.analysisCache.put(key, cached) },
        applyFailureDisplay = context.displayStateApplier::applyTopMoveAnalysisFailureDisplayPlan,
        appendEngineOperationDiscardLog = context.lifecycleController::appendDiscardLog,
        applyShowTopMovesStateUpdate = { update ->
            context.setSettingsState(update.settingsState)
            context.setAnalysisState(update.analysisState)
            update.engineMessage?.let { message -> context.setEngineMessage(message) }
        },
        deferredAutomaticAnalysis = context.deferredTopMoveAnalysis,
    )

    val undoController = UndoController(
        scope = context.scope,
        engineClient = context.engineClient,
        diagnosticEventLog = context.diagnosticEventLog,
        currentGameState = { context.gameState() },
        currentScoreSnapshots = { context.scoreState().scoreSnapshots },
        currentMoveReviews = { context.moveReviewState().moveReviews },
        currentMatchMode = { context.matchMode() },
        currentPlayerSetup = { context.playerSetup() },
        currentSessionGeneration = { context.runtimeState().sessionGeneration },
        currentEngineProfile = { context.runtimeState().engineProfile },
        timeoutPolicy = context::engineProfileTimeoutPolicy,
        isEngineReady = { context.isEngineReady() },
        isEngineBusy = { context.isEngineBusy() },
        onEngineMessage = { message -> context.setEngineMessage(message) },
        onQuietUntil = { quietUntil -> context.setUndoEngineInterventionQuietUntil(quietUntil) },
        onPendingSyncChanged = { pending -> context.setPendingUndoSync(pending) },
        launchEngineOperation = { operation, block -> context.lifecycleController.launchTracked(operation) { block() } },
        runEngineOperation = { operation, block -> context.lifecycleController.runTracked(operation) { block() } },
        applyUndo = { undo ->
            context.displayStateApplier.applyUndoLocalStatePlan(undo)
            context.setTurnTimeState(
                context.turnTimeState().restartCurrentTurn(
                    state = undo.gameState,
                    nowMillis = System.currentTimeMillis(),
                )
            )
        },
        applyScoreSyncCompletion = context.displayStateApplier::applyScoreSyncCompletion,
        requestFollowUpAnalysis = { state -> topMovesController.requestAnalysis(state, automatic = true) },
        appendDiscardLog = context.lifecycleController::appendDiscardLog,
    )

    val autoAiTurnController = AutoAiTurnController(
        scope = context.scope,
        engineClient = context.engineClient,
        diagnosticEventLog = context.diagnosticEventLog,
        runtimeEventLog = context.runtimeEventLog,
        currentControllerState = { context.sessionSnapshot() },
        currentRuntimeState = { context.runtimeState() },
        currentSearchTimeSettings = { context.sessionSnapshot().settings.searchTimeSettings },
        currentScoreSnapshots = { context.scoreState().scoreSnapshots },
        isEngineReady = { context.isEngineReady() },
        isEngineBusy = { context.isEngineBusy() },
        isGameEnded = { context.isGameEnded() },
        shouldShowResumePrompt = { context.shouldShowResumePrompt() },
        currentRuntimeLogContext = context::currentRuntimeLogContext,
        currentGameState = { context.gameState() },
        currentSessionGeneration = { context.runtimeState().sessionGeneration },
        markEngineOperationStarted = context.lifecycleController::markStarted,
        markEngineOperationCompleted = context.lifecycleController::markCompleted,
        applyAutoAiTurnScheduled = { schedule: AutoAiTurnRequestPlan.Schedule -> context.setAutoAiTurnUiState(context.autoAiTurnUiState().applyAutoAiTurnRequestPlan(schedule)) },
        applyAutoAiTurnCancelled = { cancel: AutoAiTurnScheduleValidationPlan -> context.setAutoAiTurnUiState(context.autoAiTurnUiState().applyAutoAiTurnScheduleValidationPlan(cancel)) },
        recordTurnMove = { player, nowMillis, nextPlayer -> context.turnTimeState().recordMove(player = player, nowMillis = nowMillis, nextPlayer = nextPlayer) },
        applyTurnTimeUpdate = { update: TurnTimeMoveUpdate -> context.setTurnTimeState(update.after) },
        applyTurnDisplay = { display: AutoAiTurnDisplayPlan -> if (display.shouldResolveEndgame) context.activateEndgameJudgementReview(); context.displayStateApplier.applyAutoAiTurnDisplayPlan(display) },
        applyTurnFailureDisplay = { error: Throwable -> context.displayStateApplier.applyAutoAiTurnFailureDisplayPlan(buildAutoAiTurnFailureDisplayPlan(error)) },
        completeAutoAiTurnRun = { context.setAutoAiTurnUiState(context.autoAiTurnUiState().completeAutoAiTurnRun()) },
        appendEngineOperationDiscardLog = context.lifecycleController::appendDiscardLog,
        requestFollowUpAnalysis = { followUp -> topMovesController.requestAnalysis(followUp.targetState, automatic = followUp.automatic, deep = followUp.deep) },
        markGameEnded = { context.activateEndgameJudgementReview(); context.setIsGameEnded(true) },
        applyFinalScoreDisplayPlan = context::applyFinalScoreWithJudgement,
        applyEndgameFailureDisplayPlan = context.displayStateApplier::applyEndgameFailureDisplayPlan,
    )

    val humanMoveController = HumanMoveController(
        engineClient = context.engineClient,
        diagnosticEventLog = context.diagnosticEventLog,
        runtimeEventLog = context.runtimeEventLog,
        currentGameState = { context.gameState() },
        currentPlayerSetup = { context.playerSetup() },
        currentAnalysisState = { context.analysisState() },
        currentMoveReviewState = { context.moveReviewState() },
        currentScoreSnapshots = { context.scoreState().scoreSnapshots },
        currentScoreState = { context.scoreState() },
        currentSessionGeneration = { context.runtimeState().sessionGeneration },
        currentEngineProfile = { context.runtimeState().engineProfile },
        currentRuntimeLogContext = context::currentRuntimeLogContext,
        isEngineReady = { context.isEngineReady() },
        isEngineBlockingBusy = { context.isEngineBlockingBusy() },
        cancelBackgroundOperations = context.lifecycleController::cancelBackgroundOperations,
        onEngineMessage = { message -> context.setEngineMessage(message) },
        onConsecutivePassesDetected = context::activateEndgameJudgementReview,
        clearUndoEngineInterventionQuietWindow = context::clearUndoEngineInterventionQuietWindow,
        recordTurnMove = { player, nowMillis, nextPlayer -> context.turnTimeState().recordMove(player = player, nowMillis = nowMillis, nextPlayer = nextPlayer) },
        applyTurnTimeUpdate = { update: TurnTimeMoveUpdate -> context.setTurnTimeState(update.after) },
        applyHumanMoveLocalResult = context.displayStateApplier::applyHumanMoveLocalResult,
        replaceScoreState = { state -> context.setScoreState(state) },
        setAnalysisCandidateText = { text -> context.setAnalysisState(context.analysisState().copy(candidateText = text)) },
        applyFinalScoreDisplayPlan = context::applyFinalScoreWithJudgement,
        applyScoreEstimateDisplayPlan = context.displayStateApplier::applyScoreEstimateDisplayPlan,
        applyHumanEngineSyncFailurePlan = context.displayStateApplier::applyHumanEngineSyncFailurePlan,
        appendEngineOperationDiscardLog = context.lifecycleController::appendDiscardLog,
        timeoutPolicy = context::engineProfileTimeoutPolicy,
        launchEngineOperation = { operation, block -> context.lifecycleController.launchTracked(operation) { block() } },
        requestFollowUpAnalysis = { state -> topMovesController.requestAnalysis(state, automatic = true) },
    )

    val newGameController = NewGameController(
        engineClient = context.engineClient,
        diagnosticEventLog = context.diagnosticEventLog,
        runtimeEventLog = context.runtimeEventLog,
        defaultPlayLevel = context.defaultPlayLevel,
        isEngineReady = { context.isEngineReady() },
        isEngineBusy = { context.isEngineBusy() },
        currentGameState = { context.gameState() },
        currentPlayerSetup = { context.playerSetup() },
        currentEngineProfile = { context.runtimeState().engineProfile },
        currentSearchTimeSettings = { context.sessionSnapshot().settings.searchTimeSettings },
        currentBoardSize = { context.settingsState().boardSize },
        currentHandicapCount = { context.settingsState().handicapCount },
        currentSessionGeneration = { context.runtimeState().sessionGeneration },
        currentScoreState = { context.scoreState() },
        currentRuntimeLogContext = context::currentRuntimeLogContext,
        launchEngineOperation = { operation, block -> context.lifecycleController.launchTracked(operation) { block() } },
        applyGameSessionResetPlan = { reset: GameSessionResetPlan ->
            context.clearUndoEngineInterventionQuietWindow()
            context.undoAnalysisRestoreCache.clear()
            context.setPositionCacheOptimizationState(context.positionCacheOptimizationState().clearPrompt())
            context.applyCoreSessionState(context.sessionSnapshot().core.applyGameSessionResetPlan(reset))
            context.setTurnTimeState(
                GameSessionTurnTimeState.reset(
                    state = reset.gameState,
                    nowMillis = System.currentTimeMillis(),
                )
            )
            context.runtimeEventLog.append(
                runtimeGameResetLog(
                    context = context.currentRuntimeLogContext(),
                    reset = reset,
                )
            )
        },
        applyRuntimePlayLevelSelection = { selection: RuntimePlayLevelSelection -> context.setRuntimeState(context.runtimeState().applySelection(selection)) },
        replaceScoreState = { state -> context.setScoreState(state) },
        requestFollowUpAnalysis = { state -> topMovesController.requestAnalysis(state, automatic = true) },
        onEngineMessage = { message -> context.setEngineMessage(message) },
    )

    val savedSessionController = SavedSessionController(
        engineClient = context.engineClient,
        diagnosticEventLog = context.diagnosticEventLog,
        defaultPlayLevel = context.defaultPlayLevel,
        isEngineBusy = { context.isEngineBusy() },
        isEngineReady = { context.isEngineReady() },
        currentSearchTimeSettings = { context.sessionSnapshot().settings.searchTimeSettings },
        currentEngineProfile = { context.runtimeState().engineProfile },
        currentSessionGeneration = { context.runtimeState().sessionGeneration },
        timeoutPolicy = context::engineProfileTimeoutPolicy,
        currentGameState = { context.gameState() },
        onEngineMessage = { message -> context.setEngineMessage(message) },
        applySavedGameRestorePlan = { restore: SavedGameRestorePlan ->
            context.clearUndoEngineInterventionQuietWindow()
            context.undoAnalysisRestoreCache.clear()
            context.setPositionCacheOptimizationState(context.positionCacheOptimizationState().clearPrompt())
            context.setSettingsState(
                context.settingsState().applySavedGameRestore(
                    restoredSetup = restore.playerSetup,
                    restoredTopMovesEnabled = restore.topMovesEnabled,
                )
            )
            context.applyCoreSessionState(context.sessionSnapshot().core.applySavedGameRestorePlan(restore))
            context.setTurnTimeState(
                GameSessionTurnTimeState.reset(
                    state = restore.gameState,
                    nowMillis = System.currentTimeMillis(),
                )
            )
        },
        launchEngineOperation = { operation, block -> context.lifecycleController.launchTracked(operation) { block() } },
        applyScoreSyncCompletion = context.displayStateApplier::applyScoreSyncCompletion,
        requestFollowUpAnalysis = { state -> topMovesController.requestAnalysis(state, automatic = true) },
    )

    val cacheOptController = PositionCacheOptimizationController(
        engineClient = context.engineClient,
        diagnosticEventLog = context.diagnosticEventLog,
        currentGameState = { context.gameState() },
        currentPlayerSetup = { context.playerSetup() },
        currentSearchTimeSettings = { context.sessionSnapshot().settings.searchTimeSettings },
        isEngineBusy = { context.isEngineBusy() },
        currentSessionGeneration = { context.runtimeState().sessionGeneration },
        currentUiState = { context.positionCacheOptimizationState() },
        onUiState = { state -> context.setPositionCacheOptimizationState(state) },
        onEngineMessage = { message -> context.setEngineMessage(message) },
        onAnalysisCandidateText = { message -> context.setAnalysisState(context.analysisState().copy(candidateText = message)) },
        launchEngineOperation = { operation, block -> context.lifecycleController.launchTracked(operation) { block() } },
    )

    val scoreEstimateController = ScoreEstimateController(
        engineClient = context.engineClient,
        diagnosticEventLog = context.diagnosticEventLog,
        currentGameState = { context.gameState() },
        currentScoreSnapshots = { context.scoreState().scoreSnapshots },
        isEngineReady = { context.isEngineReady() },
        isEngineBusy = { context.isEngineBusy() },
        currentMatchMode = { context.matchMode() },
        currentEngineProfile = { context.runtimeState().engineProfile },
        currentSessionGeneration = { context.runtimeState().sessionGeneration },
        launchEngineOperation = { operation, block -> context.lifecycleController.launchTracked(operation) { block() } },
        onEngineMessage = { message -> context.setEngineMessage(message) },
        onScoreEstimateDisplayPlan = context.displayStateApplier::applyScoreEstimateDisplayPlan,
        onScoreEstimateFailureDisplayPlan = context.displayStateApplier::applyScoreEstimateFailureDisplayPlan,
        appendDiscardLog = context.lifecycleController::appendDiscardLog,
    )

    val scoringRuleController = ScoringRuleController(
        engineClient = context.engineClient,
        diagnosticEventLog = context.diagnosticEventLog,
        currentGameState = { context.gameState() },
        currentMatchMode = { context.matchMode() },
        isEngineReady = { context.isEngineReady() },
        isEngineBusy = { context.isEngineBusy() },
        currentScoreSnapshots = { context.scoreState().scoreSnapshots },
        currentEngineProfile = { context.runtimeState().engineProfile },
        currentSessionGeneration = { context.runtimeState().sessionGeneration },
        timeoutPolicy = context::engineProfileTimeoutPolicy,
        onEngineMessage = { message -> context.setEngineMessage(message) },
        applyScoringRuleChangePlan = { ruleChange: ScoringRuleChangePlan ->
            context.applyCoreSessionState(context.sessionSnapshot().core.applyScoringRuleChangePlan(ruleChange))
        },
        applyScoreSyncCompletionApplyPlan = context.displayStateApplier::applyScoreSyncCompletion,
        requestFollowUpAnalysis = { state -> topMovesController.requestAnalysis(state, automatic = true) },
        launchEngineOperation = { operation, block -> context.lifecycleController.launchTracked(operation) { block() } },
        appendDiscardLog = context.lifecycleController::appendDiscardLog,
    )

    val settingsController = GameSettingsController(
        currentGameState = { context.gameState() },
        currentPlayerSetup = { context.playerSetup() },
        currentEngineProfile = { context.runtimeState().engineProfile },
        currentSearchTimeSettings = { context.sessionSnapshot().settings.searchTimeSettings },
        currentAnalysisState = { context.analysisState() },
        currentAutoPlayDelaySetting = { context.sessionSnapshot().settings.autoPlayDelaySetting },
        defaultPlayLevel = context.defaultPlayLevel,
        isEngineBusy = { context.isEngineBusy() },
        runtimeEventLog = context.runtimeEventLog,
        currentRuntimeLogContext = context::currentRuntimeLogContext,
        onEngineMessage = { message -> context.setEngineMessage(message) },
        applyPlayerSetup = { setup: PlayerSetup -> context.setSettingsState(context.settingsState().applyPlayerSetup(setup)) },
        applyCoreSessionState = context::applyCoreSessionState,
        currentCoreSessionState = { context.sessionSnapshot().core },
        applyRuntimePlayLevelSelection = { selection: RuntimePlayLevelSelection -> context.setRuntimeState(context.runtimeState().applySelection(selection)) },
        applyAnalysisState = { analysis: GameSessionAnalysisState -> context.setAnalysisState(analysis) },
        applySettingsAutoPlayDelay = { setting: AutoPlayDelaySetting -> context.setSettingsState(context.settingsState().applyAutoPlayDelay(setting)) },
        applySettingsSearchTimeSettings = { settings: SearchTimeSettings -> context.setSettingsState(context.settingsState().applySearchTimeSettings(settings)) },
        clearUndoEngineInterventionQuietWindow = undoController::clearQuietWindow,
    )

    val debugReportController = DebugReportController(
        engineName = context.engineName(),
        engineDiagnostic = context.engineDiagnostic(),
        runtimeEventLog = context.runtimeEventLog,
        diagnosticEventLog = context.diagnosticEventLog,
        clipboard = context.clipboardPort,
        mirror = context.debugReportMirror,
        userNotice = context.userNoticePort,
        currentControllerState = { context.sessionSnapshot() },
        isEngineReady = { context.isEngineReady() },
        isEngineBusy = { context.isEngineBusy() },
        analysisCacheStatsText = { "${context.analysisCache.statsText()}, ${context.undoAnalysisRestoreCache.statsText()}" },
        positionAnalysisCacheStatsText = context.engineClient::positionAnalysisCacheStatsText,
        turnTimeText = { context.turnTimeState().summaryText() },
        turnTimeDebugText = { nowMillis -> context.turnTimeState().debugText(nowMillis) },
        onEngineMessage = { message -> context.setEngineMessage(message) },
        currentSavedSessionJson = { context.sessionStore.readRawJson() },
    )

    val benchmarkController = EngineBenchmarkController(
        scope = context.scope,
        engineClient = context.engineClient,
        store = context.benchmarkStore,
        diagnosticEventLog = context.diagnosticEventLog,
        lifecycleCallbacks = { context.lifecycleController.callbacks() },
        currentState = { context.gameState() },
        sessionGeneration = { context.runtimeState().sessionGeneration },
        isEngineReady = { context.isEngineReady() },
        isEngineBusy = { context.isEngineBusy() },
        currentBenchmarkUiState = { context.benchmarkUiState() },
        onBenchmarkUiState = { state -> context.setBenchmarkUiState(state) },
        onEngineMessage = { message -> context.setEngineMessage(message) },
        onDisplayPlan = { plan -> context.displayStateApplier.applyEngineBenchmarkDisplayPlan(plan) },
    )

    return GoCoachControllers(
        topMovesController = topMovesController,
        undoController = undoController,
        autoAiTurnController = autoAiTurnController,
        humanMoveController = humanMoveController,
        newGameController = newGameController,
        savedSessionController = savedSessionController,
        cacheOptController = cacheOptController,
        scoreEstimateController = scoreEstimateController,
        scoringRuleController = scoringRuleController,
        settingsController = settingsController,
        debugReportController = debugReportController,
        benchmarkController = benchmarkController,
    )
}
