package com.worksoc.goaicoach.application.undo

import com.worksoc.goaicoach.application.concurrency.launchUiEffect
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineScoringClient
import com.worksoc.goaicoach.application.movereview.MoveReviewMarker
import com.worksoc.goaicoach.application.score.PostUndoScoreSyncRunRequest
import com.worksoc.goaicoach.application.score.ScoreSyncCompletionApplyPlan
import com.worksoc.goaicoach.application.score.runPostUndoScoreSyncApplication
import com.worksoc.goaicoach.application.time.currentEpochMillis
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.policy.EngineOperationRequest
import com.worksoc.goaicoach.shared.policy.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

class UndoController(
    private val scope: CoroutineScope,
    /**
     * 대기 중인 재동기화의 자리 — 이 인스턴스보다 **오래 산다**(refactor backlog #107). 이 컨트롤러는 무르기 직후
     * `wiringContext`와 함께 새로 만들어지므로, 예약한 인스턴스와 취소하는 인스턴스가 다르다. 배선은 `GoCoachApp`이
     * 키 없는 `remember`로 한 번 만든 것을 넘긴다([PostUndoSyncSlot]).
     */
    private val pendingSync: PostUndoSyncSlot,
    private val engineClient: EngineScoringClient,
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
    private val runEngineOperation: suspend (EngineOperationRequest, suspend () -> Unit) -> Unit,
    private val applyUndo: (UndoLocalStatePlan) -> Unit,
    private val applyScoreSyncCompletion: (ScoreSyncCompletionApplyPlan) -> GameState?,
    private val requestFollowUpAnalysis: (GameState) -> Unit,
) {
    fun markQuiet(): Long {
        val quietUntil = undoEngineInterventionQuietUntilMillis(currentEpochMillis())
        onQuietUntil(quietUntil)
        return quietUntil
    }

    fun cancelPendingSync() {
        pendingSync.job?.cancel()
        pendingSync.job = null
        if (pendingSync.pending != null) {
            pendingSync.pending = null
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
        pendingSync.pending = pending
        onPendingSyncChanged(true)
        pendingSync.job?.cancel()
        pendingSync.job = launchUiEffect(scope) {
            val delayMillis = undoEngineInterventionRemainingDelayMillis(
                nowMillis = currentEpochMillis(),
                quietUntilMillis = pending.quietUntilMillis,
            )
            if (delayMillis > 0L) {
                delay(delayMillis)
            }
            while (pendingSync.pending == pending && currentGameState() == pending.targetState && isEngineBusy()) {
                delay(UndoEngineBusyPollIntervalMillis)
            }
            if (
                pendingSync.pending != pending ||
                currentGameState() != pending.targetState ||
                !isEngineReady()
            ) {
                if (pendingSync.pending == pending) {
                    pendingSync.pending = null
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
            if (pendingSync.pending == pending) {
                pendingSync.pending = null
                onPendingSyncChanged(false)
            }
        }
    }

    internal fun applyLocalUndo(plan: UndoRequestPlan.ApplyLocalUndo) {
        runApplyLocalUndoApplication(
            ApplyLocalUndoRunRequest(
                plan = plan,
                currentState = currentGameState(),
                previousMoveReviews = currentMoveReviews(),
                scoreSnapshots = currentScoreSnapshots(),
                applyUndo = applyUndo,
                markQuiet = ::markQuiet,
                setEngineMessage = onEngineMessage,
                cancelPendingPostUndoSync = ::cancelPendingSync,
                schedulePostUndoSync = ::schedulePostUndoSync,
            ),
        )
    }

    fun undoLastTurn() {
        runUndoLastTurnApplication(
            UndoLastTurnRunRequest(
                currentState = currentGameState(),
                matchMode = currentMatchMode(),
                isEngineReady = isEngineReady(),
                playerSetup = currentPlayerSetup(),
                showMessage = onEngineMessage,
                runApplyLocalUndo = ::applyLocalUndo,
            ),
        )
    }
}
