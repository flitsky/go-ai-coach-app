package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoApplicationTest {
    @Test
    fun undoEngineInterventionQuietWindowUsesOneSecondDefault() {
        assertEquals(2_000L, undoEngineInterventionQuietUntilMillis(nowMillis = 1_000L))
        assertEquals(1_000L, undoEngineInterventionRemainingDelayMillis(nowMillis = 1_000L, quietUntilMillis = 2_000L))
        assertEquals(0L, undoEngineInterventionRemainingDelayMillis(nowMillis = 2_100L, quietUntilMillis = 2_000L))
    }

    @Test
    fun undoEngineInterventionQuietWindowAcceptsCustomDelayForTestsAndPolicies() {
        assertEquals(
            1_250L,
            undoEngineInterventionQuietUntilMillis(nowMillis = 1_000L, delayMillis = 250L),
        )
        assertEquals(
            1_000L,
            undoEngineInterventionQuietUntilMillis(nowMillis = 1_000L, delayMillis = -100L),
        )
    }

    @Test
    fun undoRequestPlanReportsEmptyMoveHistoryFirst() {
        val plan = buildUndoRequestPlan(
            currentState = GameState.empty(),
            matchMode = MatchMode.HumanVsAi,
            isEngineReady = true,
            isEngineBusy = false,
            humanSeatCount = 1,
        )

        assertEquals(UndoRequestPlan.ShowMessage("No move to undo."), plan)
    }

    @Test
    fun undoRequestPlanHandlesOfflineLocalTwoPlayerUndo() {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))

        val plan = buildUndoRequestPlan(
            currentState = state,
            matchMode = MatchMode.LocalTwoPlayer,
            isEngineReady = false,
            isEngineBusy = false,
            humanSeatCount = 2,
        )

        assertEquals(UndoRequestPlan.LocalTwoPlayerUndo(syncEngineAfterUndo = false), plan)
    }

    @Test
    fun undoRequestPlanBlocksBusyLocalTwoPlayerUndo() {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))

        val plan = buildUndoRequestPlan(
            currentState = state,
            matchMode = MatchMode.LocalTwoPlayer,
            isEngineReady = true,
            isEngineBusy = true,
            humanSeatCount = 2,
        )

        assertEquals(
            UndoRequestPlan.ShowMessage("Engine is busy. Undo after the current analysis."),
            plan,
        )
    }

    @Test
    fun undoRequestPlanComputesEngineUndoCountForSingleHumanGame() {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))
            .play(Move.Pass(StoneColor.Black))

        val plan = buildUndoRequestPlan(
            currentState = state,
            matchMode = MatchMode.HumanVsAi,
            isEngineReady = true,
            isEngineBusy = false,
            humanSeatCount = 1,
        )

        assertEquals(UndoRequestPlan.EngineUndo(undoCount = 2), plan)
    }

    @Test
    fun localTwoPlayerUndoPlanReplaysOneMoveBackAndRecordsLocalSnapshot() {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D5", BoardSize.Nine)))

        val plan = buildLocalTwoPlayerUndoPlan(
            currentState = state,
            scoreSnapshots = listOf(ScoreSnapshot(moveNumber = 2, source = ScoreSnapshotSource.EngineEstimate)),
        )

        assertEquals(1, plan.gameState.moves.size)
        assertEquals("Black E5", plan.lastMoveText)
        assertEquals("Captured: Black 0 / White 0", plan.candidateText)
        assertEquals("Score estimate not current.", plan.scoreText)
        assertEquals("Move review cleared by undo.", plan.moveReviewText)
        assertEquals(1, plan.scoreSnapshots.single().moveNumber)
        assertFalse(plan.reviewAnalysis.hasEngineCandidates)
    }

    @Test
    fun engineUndoPlanTrimsStateTimelineAndMoveReviewMarkers() {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D5", BoardSize.Nine)))
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("F5", BoardSize.Nine)))
        val markers = listOf(
            MoveReviewMarker(
                coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
                moveNumber = 1,
                tone = MoveReviewTone.Excellent,
            ),
            MoveReviewMarker(
                coordinate = BoardCoordinate.fromLabel("F5", BoardSize.Nine),
                moveNumber = 3,
                tone = MoveReviewTone.Blunder,
            ),
        )

        val plan = buildEngineUndoPlan(
            currentState = state,
            undoCount = 2,
            previousMoveReviews = markers,
            scoreSnapshots = listOf(
                ScoreSnapshot(moveNumber = 1, source = ScoreSnapshotSource.EngineEstimate),
                ScoreSnapshot(moveNumber = 3, source = ScoreSnapshotSource.EngineEstimate),
            ),
        )

        assertEquals(1, plan.gameState.moves.size)
        assertEquals("Black E5", plan.lastMoveText)
        assertEquals("Undo cleared current Top Moves.", plan.candidateText)
        assertEquals(listOf(markers.first()), plan.moveReviews)
        assertEquals(1, plan.scoreSnapshots.single().moveNumber)
        assertFalse(plan.reviewAnalysis.hasEngineCandidates)
    }

    @Test
    fun engineUndoCompletionPlanAppliesSuccessFailureOrDiscard() {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D5", BoardSize.Nine)))
        val changedState = state.play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("F5", BoardSize.Nine)))
        val operation = engineOperationRequest(
            kind = EngineOperationKind.EngineUndo,
            state = state,
            sessionGeneration = 9L,
        )

        val success = buildEngineUndoCompletionPlan(
            result = EngineUndoWorkflowResult.Success(EngineStatus.ready("undo complete")),
            operation = operation,
            currentState = state,
            currentSessionGeneration = 9L,
            undoCount = 1,
            previousMoveReviews = emptyList(),
            scoreSnapshots = emptyList(),
        )
        val failure = buildEngineUndoCompletionPlan(
            result = EngineUndoWorkflowResult.Failure(IllegalStateException("undo failed")),
            operation = operation,
            currentState = state,
            currentSessionGeneration = 9L,
            undoCount = 1,
            previousMoveReviews = emptyList(),
            scoreSnapshots = emptyList(),
        )
        val discard = buildEngineUndoCompletionPlan(
            result = EngineUndoWorkflowResult.Success(EngineStatus.ready("undo complete")),
            operation = operation,
            currentState = changedState,
            currentSessionGeneration = 9L,
            undoCount = 1,
            previousMoveReviews = emptyList(),
            scoreSnapshots = emptyList(),
        )

        assertTrue(success is EngineUndoCompletionPlan.ApplySuccess)
        assertEquals(1, (success as EngineUndoCompletionPlan.ApplySuccess).undo.gameState.moves.size)
        assertEquals("Undid 1 move(s) in local state and engine state.", success.engineMessage)
        assertTrue(failure is EngineUndoCompletionPlan.ApplyFailure)
        assertEquals("undo failed", (failure as EngineUndoCompletionPlan.ApplyFailure).engineMessage)
        assertTrue(discard is EngineUndoCompletionPlan.Discard)
    }
}
