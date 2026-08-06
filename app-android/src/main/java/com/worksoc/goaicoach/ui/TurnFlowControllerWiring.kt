package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.autoai.AutoAiTurnController
import com.worksoc.goaicoach.application.autoai.AutoAiTurnRequestPlan
import com.worksoc.goaicoach.application.autoai.AutoAiTurnScheduleValidationPlan
import com.worksoc.goaicoach.application.autoai.AutoAiTurnDisplayPlan
import com.worksoc.goaicoach.application.autoai.applyAutoAiTurnRequestPlan
import com.worksoc.goaicoach.application.autoai.applyAutoAiTurnScheduleValidationPlan
import com.worksoc.goaicoach.application.autoai.buildAutoAiTurnFailureDisplayPlan
import com.worksoc.goaicoach.application.autoai.completeAutoAiTurnRun
import com.worksoc.goaicoach.application.humanmove.HumanMoveController
import com.worksoc.goaicoach.application.session.TurnTimeMoveUpdate
import com.worksoc.goaicoach.application.topmoves.TopMovesController
import com.worksoc.goaicoach.application.undo.UndoController

/**
 * 착수 흐름 컨트롤러 그룹 — TopMoves(추천수)/Undo(무르기)/AutoAiTurn(AI 자동 착수)/HumanMove
 * (사람 착수)는 전부 [wireTopMovesController]가 반환하는 컨트롤러를 후속 분석 트리거로
 * 참조한다(`requestFollowUpAnalysis`) — 그래서 생성 순서가 고정돼 있고, 그 의존을
 * 매개변수로 명시했다(암묵적 클로저 캡처 대신).
 */
internal fun wireTopMovesController(context: GoCoachAppWiringContext): TopMovesController =
    TopMovesController(
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

internal fun wireUndoController(
    context: GoCoachAppWiringContext,
    topMovesController: TopMovesController,
): UndoController =
    UndoController(
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
    )

internal fun wireAutoAiTurnController(
    context: GoCoachAppWiringContext,
    topMovesController: TopMovesController,
): AutoAiTurnController =
    AutoAiTurnController(
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

internal fun wireHumanMoveController(
    context: GoCoachAppWiringContext,
    topMovesController: TopMovesController,
): HumanMoveController =
    HumanMoveController(
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
