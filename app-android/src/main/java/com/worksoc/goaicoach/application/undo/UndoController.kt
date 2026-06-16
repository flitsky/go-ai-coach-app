package com.worksoc.goaicoach.application.undo

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.launchUiEffect
import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.application.movereview.MoveReviewMarker
import com.worksoc.goaicoach.application.score.PostUndoScoreSyncRunRequest
import com.worksoc.goaicoach.application.score.ScoreSyncCompletionApplyPlan
import com.worksoc.goaicoach.application.score.runPostUndoScoreSyncApplication
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

private data class PendingPostUndoEngineSync(
    val targetState: GameState,
    val quietUntilMillis: Long,
)

internal class UndoController(
    private val scope: CoroutineScope,
    private val engineClient: EngineSessionClient,
    private val diagnosticEventLog: DiagnosticEventLogPort,
    private val currentGameState: () -> GameState,
    private val currentScoreSnapshots: () -> List<ScoreSnapshot>,
    private val currentMoveReviews: () -> List<MoveReviewMarker>,
    private val currentMatchMode: () -> MatchMode,
    private val currentPlayerSetup: () -> PlayerSetup,
    private val currentSessionGeneration: () -> Long,
    private val currentEngineProfile: () -> EngineProfile,
    private val timeoutPolicy: (EngineProfile) -> EngineTimeoutPolicy,
    private val isEngineReady: () -> Boolean,
    private val isEngineBusy: () -> Boolean,
    private val onEngineMessage: (String) -> Unit,
    private val onQuietUntil: (Long) -> Unit,
    private val onPendingSyncChanged: (Boolean) -> Unit,
    private val launchEngineOperation: (EngineOperationRequest, suspend () -> Unit) -> Unit,
    private val runEngineOperation: suspend (EngineOperationRequest, suspend () -> Unit) -> Unit,
    private val applyUndo: (UndoLocalStatePlan) -> Unit,
    private val applyScoreSyncCompletion: (ScoreSyncCompletionApplyPlan) -> GameState?,
    private val requestFollowUpAnalysis: (GameState) -> Unit,
    private val appendDiscardLog: (EngineOperationResultGuard.Discard) -> Unit,
) {
    private var pendingSync: PendingPostUndoEngineSync? = null
    private var pendingSyncJob: Job? = null

    fun markQuiet(): Long {
        val quietUntil = undoEngineInterventionQuietUntilMillis(System.currentTimeMillis())
        onQuietUntil(quietUntil)
        return quietUntil
    }

    fun cancelPendingSync() {
        pendingSyncJob?.cancel()
        pendingSyncJob = null
        if (pendingSync != null) {
            pendingSync = null
            onPendingSyncChanged(false)
        }
    }

    fun clearQuietWindow() {
        onQuietUntil(0L)
        cancelPendingSync()
    }

    fun schedulePostUndoSync(targetState: GameState, quietUntilMillis: Long) {
        val pending = PendingPostUndoEngineSync(
            targetState = targetState,
            quietUntilMillis = quietUntilMillis,
        )
        pendingSync = pending
        onPendingSyncChanged(true)
        pendingSyncJob?.cancel()
        pendingSyncJob = launchUiEffect(scope) {
            val delayMillis = undoEngineInterventionRemainingDelayMillis(
                nowMillis = System.currentTimeMillis(),
                quietUntilMillis = pending.quietUntilMillis,
            )
            if (delayMillis > 0L) {
                delay(delayMillis)
            }
            while (pendingSync == pending && currentGameState() == pending.targetState && isEngineBusy()) {
                delay(100L)
            }
            if (
                pendingSync != pending ||
                currentGameState() != pending.targetState ||
                !isEngineReady()
            ) {
                if (pendingSync == pending) {
                    pendingSync = null
                    onPendingSyncChanged(false)
                }
                return@launchUiEffect
            }

            runPostUndoScoreSyncApplication(
                PostUndoScoreSyncRunRequest(
                    engineClient = engineClient,
                    state = pending.targetState,
                    profile = currentEngineProfile(),
                    previousSnapshots = currentScoreSnapshots(),
                    sessionGeneration = currentSessionGeneration(),
                    timeoutPolicy = timeoutPolicy(currentEngineProfile()),
                    diagnosticEventLog = diagnosticEventLog,
                    currentState = currentGameState,
                    currentSessionGeneration = currentSessionGeneration,
                    runEngineOperation = runEngineOperation,
                    applyCompletion = applyScoreSyncCompletion,
                    requestFollowUpAnalysis = requestFollowUpAnalysis,
                ),
            )
            if (pendingSync == pending) {
                pendingSync = null
                onPendingSyncChanged(false)
            }
        }
    }

    fun undoLocalTwoPlayerTurn(plan: UndoRequestPlan.LocalTwoPlayerUndo) {
        runLocalTwoPlayerUndoApplication(
            LocalTwoPlayerUndoRunRequest(
                plan = plan,
                currentState = currentGameState(),
                scoreSnapshots = currentScoreSnapshots(),
                applyUndo = applyUndo,
                markQuiet = ::markQuiet,
                setEngineMessage = onEngineMessage,
                cancelPendingPostUndoSync = ::cancelPendingSync,
                schedulePostUndoSync = ::schedulePostUndoSync,
            ),
        )
    }

    fun undoEngineBackedTurn(plan: UndoRequestPlan.EngineUndo) {
        runEngineUndoApplication(
            EngineUndoRunRequest(
                engineClient = engineClient,
                plan = plan,
                currentState = currentGameState(),
                sessionGeneration = currentSessionGeneration(),
                previousMoveReviews = currentMoveReviews(),
                scoreSnapshots = currentScoreSnapshots(),
                diagnosticEventLog = diagnosticEventLog,
                launchEngineOperation = launchEngineOperation,
                currentStateProvider = currentGameState,
                currentSessionGenerationProvider = currentSessionGeneration,
                applyUndo = applyUndo,
                setEngineMessage = onEngineMessage,
                markQuiet = ::markQuiet,
                cancelPendingPostUndoSync = ::cancelPendingSync,
                appendDiscardLog = appendDiscardLog,
            ),
        )
    }

    fun undoLastTurn() {
        runUndoLastTurnApplication(
            UndoLastTurnRunRequest(
                currentState = currentGameState(),
                matchMode = currentMatchMode(),
                isEngineReady = isEngineReady(),
                isEngineBusy = isEngineBusy(),
                humanSeatCount = currentPlayerSetup().humanSeatCount(),
                showMessage = onEngineMessage,
                runLocalTwoPlayerUndo = ::undoLocalTwoPlayerTurn,
                runEngineUndo = ::undoEngineBackedTurn,
            ),
        )
    }
}
