package com.worksoc.goaicoach.application.humanmove

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.runtime.runtimeHumanEngineSyncFailureLog
import com.worksoc.goaicoach.application.runtime.runtimeHumanEngineSyncSuccessLog
import com.worksoc.goaicoach.application.runtime.runtimeHumanMoveAcceptedLog
import com.worksoc.goaicoach.application.score.FinalScoreDisplayPlan
import com.worksoc.goaicoach.application.score.ScoreEstimateDisplayPlan
import com.worksoc.goaicoach.application.score.buildLocalFinalScoreDisplayPlan
import com.worksoc.goaicoach.application.session.GameSessionAnalysisState
import com.worksoc.goaicoach.application.session.GameSessionMoveReviewState
import com.worksoc.goaicoach.application.session.GameSessionScoreState
import com.worksoc.goaicoach.application.session.TurnTimeMoveUpdate
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreTimeline
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.describe
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy

internal class HumanMoveController(
    private val engineClient: EngineSessionClient,
    private val diagnosticEventLog: DiagnosticEventLogPort,
    private val runtimeEventLog: RuntimeEventLogPort,
    private val currentGameState: () -> GameState,
    private val currentPlayerSetup: () -> PlayerSetup,
    private val currentAnalysisState: () -> GameSessionAnalysisState,
    private val currentMoveReviewState: () -> GameSessionMoveReviewState,
    private val currentScoreSnapshots: () -> List<ScoreSnapshot>,
    private val currentScoreState: () -> GameSessionScoreState,
    private val currentSessionGeneration: () -> Long,
    private val currentEngineProfile: () -> EngineProfile,
    private val currentRuntimeLogContext: () -> RuntimeLogContext,
    private val isEngineReady: () -> Boolean,
    private val isEngineBusy: () -> Boolean,
    private val onEngineMessage: (String) -> Unit,
    private val onConsecutivePassesDetected: () -> Unit,
    private val clearUndoEngineInterventionQuietWindow: () -> Unit,
    private val recordTurnMove: (player: StoneColor, nowMillis: Long, nextPlayer: StoneColor) -> TurnTimeMoveUpdate,
    private val applyTurnTimeUpdate: (TurnTimeMoveUpdate) -> Unit,
    private val applyHumanMoveLocalResult: (HumanMoveLocalResult) -> Unit,
    private val replaceScoreState: (GameSessionScoreState) -> Unit,
    private val setAnalysisCandidateText: (String) -> Unit,
    private val applyFinalScoreDisplayPlan: (FinalScoreDisplayPlan) -> Unit,
    private val applyScoreEstimateDisplayPlan: (ScoreEstimateDisplayPlan) -> Unit,
    private val applyHumanEngineSyncFailurePlan: (HumanEngineSyncFailurePlan) -> Unit,
    private val appendEngineOperationDiscardLog: (EngineOperationResultGuard.Discard) -> Unit,
    private val timeoutPolicy: (EngineProfile) -> EngineTimeoutPolicy,
    private val launchEngineOperation: (EngineOperationRequest, suspend () -> Unit) -> Unit,
    private val requestFollowUpAnalysis: (GameState) -> Unit,
) {
    private fun applyHumanEngineSyncDisplayPlan(sync: HumanEngineSyncDisplayPlan): GameState? =
        when (sync) {
            is HumanEngineSyncDisplayPlan.FinalScore -> {
                applyFinalScoreDisplayPlan(sync.display)
                null
            }
            is HumanEngineSyncDisplayPlan.ScoreEstimate -> {
                applyScoreEstimateDisplayPlan(sync.display)
                setAnalysisCandidateText(sync.candidateText)
                sync.nextAnalysisState
            }
            HumanEngineSyncDisplayPlan.NoUpdate -> null
        }

    private fun appendHumanEngineSyncRuntimeLog(logPlan: HumanEngineSyncRuntimeLogPlan, elapsedMs: Long) {
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

    fun applyCompletion(applyPlan: HumanEngineSyncCompletionApplyPlan, elapsedMs: Long): GameState? {
        appendHumanEngineSyncRuntimeLog(applyPlan.runtimeLogPlan, elapsedMs)
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

    fun submitMove(move: Move) {
        val playerSetup = currentPlayerSetup()
        val gameState = currentGameState()
        if (playerSetup.sideFor(gameState.nextPlayer).controller != SeatController.Human) {
            onEngineMessage("It is not a human player's turn.")
            return
        }
        if (move.player != gameState.nextPlayer) {
            onEngineMessage("Move player does not match the current turn.")
            return
        }
        if (isEngineBusy()) {
            onEngineMessage("Engine is busy. Wait for the current analysis.")
            return
        }
        clearUndoEngineInterventionQuietWindow()

        val beforeMove = gameState
        val analysisState = currentAnalysisState()
        val previousReviewCandidates = analysisState.reviewCandidateMoves
        val localMove = applyHumanMoveLocally(
            beforeMove = beforeMove,
            move = move,
            reviewAnalysis = analysisState.reviewAnalysis,
            previousMoveReviews = currentMoveReviewState().moveReviews,
        )
            .onFailure { error ->
                onEngineMessage(error.message ?: "Illegal move.")
            }
            .getOrNull()
            ?: return
        val afterMove = localMove.afterMove
        if (afterMove.hasConsecutivePasses()) {
            onConsecutivePassesDetected()
        }
        val turnTimeUpdate = recordTurnMove(move.player, System.currentTimeMillis(), afterMove.nextPlayer)

        runtimeEventLog.append(
            runtimeHumanMoveAcceptedLog(
                context = currentRuntimeLogContext(),
                beforeMove = beforeMove,
                localMove = localMove,
                turnTimeUpdate = turnTimeUpdate,
            ),
        )
        applyTurnTimeUpdate(turnTimeUpdate)
        applyHumanMoveLocalResult(localMove)

        if (!isEngineReady()) {
            val updatedSnapshots = ScoreTimeline.record(currentScoreSnapshots(), localMove.localScoreSnapshot)
            replaceScoreState(currentScoreState().replaceSnapshots(updatedSnapshots))
            val localFinalScore = localMove.localFinalScore
            if (localFinalScore != null) {
                applyFinalScoreDisplayPlan(
                    buildLocalFinalScoreDisplayPlan(
                        source = "local-human-consecutive-pass",
                        state = afterMove,
                        finalScore = localFinalScore,
                        previousSnapshots = updatedSnapshots,
                        detail = "triggerMove=${move.describe(beforeMove.boardSize)}",
                        engineMessage = "Local game ended after two passes. ${localFinalScore.status.message}",
                        candidateText = "Game ended after two passes.",
                    ),
                )
            } else {
                setAnalysisCandidateText(localMove.capturedText)
                onEngineMessage("Local move accepted without engine sync: ${move.describe(beforeMove.boardSize)}.")
            }
            return
        }

        val profile = currentEngineProfile()
        runHumanEngineSyncApplication(
            HumanEngineSyncRunRequest(
                engineClient = engineClient,
                afterMove = afterMove,
                profile = profile,
                move = move,
                previousReviewCandidates = previousReviewCandidates,
                localMove = localMove,
                previousSnapshots = currentScoreSnapshots(),
                moveDescription = move.describe(beforeMove.boardSize),
                sessionGeneration = currentSessionGeneration(),
                timeoutPolicy = timeoutPolicy(profile),
                diagnosticEventLog = diagnosticEventLog,
                currentState = currentGameState,
                currentSessionGeneration = currentSessionGeneration,
                launchEngineOperation = launchEngineOperation,
                applyCompletion = ::applyCompletion,
                requestFollowUpAnalysis = requestFollowUpAnalysis,
            ),
        )
    }
}
