package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.undo.*

import com.worksoc.goaicoach.application.movereview.MoveReviewMarker
import com.worksoc.goaicoach.application.movereview.MoveReviewTone
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.replayWithoutLastMoves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val HumanBlack = PlayerSetup() // black=Human, white=Ai (default)
private val HumanWhite = PlayerSetup(
    black = SidePlayerSetup(controller = SeatController.Ai),
    white = SidePlayerSetup(controller = SeatController.Human),
)

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
            playerSetup = HumanBlack,
        )

        assertEquals(UndoRequestPlan.ShowMessage("No move to undo."), plan)
    }

    @Test
    fun undoRequestPlanBlocksAiVsAiRegardlessOfMoveHistory() {
        val state = GameState.empty().play(Move.Pass(StoneColor.Black))

        val plan = buildUndoRequestPlan(
            currentState = state,
            matchMode = MatchMode.AiVsAi,
            isEngineReady = true,
            playerSetup = PlayerSetup(
                black = SidePlayerSetup(controller = SeatController.Ai),
                white = SidePlayerSetup(controller = SeatController.Ai),
            ),
        )

        assertEquals(
            UndoRequestPlan.ShowMessage("Undo is not available while AI controls both sides."),
            plan,
        )
    }

    @Test
    fun undoRequestPlanAppliesLocalTwoPlayerUndoWithoutEngineSyncWhenOffline() {
        val state = GameState.empty().play(Move.Pass(StoneColor.Black))

        val plan = buildUndoRequestPlan(
            currentState = state,
            matchMode = MatchMode.LocalTwoPlayer,
            isEngineReady = false,
            playerSetup = HumanBlack,
        )

        assertEquals(UndoRequestPlan.ApplyLocalUndo(undoCount = 1, syncEngineAfterUndo = false), plan)
    }

    @Test
    fun undoRequestPlanAppliesLocalTwoPlayerUndoRegardlessOfEngineBusyState() {
        // No isEngineBusy parameter exists anymore for this branch -- Undo is
        // always actionable now, including while a background engine op (e.g.
        // analysis) is running. isEngineReady alone decides whether a sync follows.
        val state = GameState.empty().play(Move.Pass(StoneColor.Black))

        val plan = buildUndoRequestPlan(
            currentState = state,
            matchMode = MatchMode.LocalTwoPlayer,
            isEngineReady = true,
            playerSetup = HumanBlack,
        )

        assertEquals(UndoRequestPlan.ApplyLocalUndo(undoCount = 1, syncEngineAfterUndo = true), plan)
    }

    @Test
    fun undoRequestPlanUndoesOneMoveRightAfterHumanBlackJustMoved() {
        // 3 moves played (Black, White, Black) -- it's White/AI's turn next.
        // The human (Black) just moved, so undo should land on 2 (before that
        // move), not 1 (which would also discard White's earlier reply).
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))
            .play(Move.Pass(StoneColor.Black))

        val plan = buildUndoRequestPlan(
            currentState = state,
            matchMode = MatchMode.HumanVsAi,
            isEngineReady = true,
            playerSetup = HumanBlack,
        )

        assertEquals(UndoRequestPlan.ApplyLocalUndo(undoCount = 1, syncEngineAfterUndo = true), plan)
    }

    @Test
    fun undoRequestPlanUndoesTwoMovesWhenItIsAlreadyHumanBlacksTurn() {
        // 4 moves played -- it's Black/human's turn next. Nothing of the human's
        // to undo at this exact boundary, so undo steps back a full round (AI's
        // reply + the human's move before it) to the previous human-turn boundary.
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))

        val plan = buildUndoRequestPlan(
            currentState = state,
            matchMode = MatchMode.HumanVsAi,
            isEngineReady = true,
            playerSetup = HumanBlack,
        )

        assertEquals(UndoRequestPlan.ApplyLocalUndo(undoCount = 2, syncEngineAfterUndo = true), plan)
    }

    @Test
    fun undoRequestPlanRepeatedPressesWalkBackOneHumanTurnAtATime() {
        // Simulates two undo presses in a row on a 4-move Black-human game: each
        // call recomputes from whatever the *current* state is, exactly like two
        // separate button presses would -- this is the "여러 수 반복 무르기" case.
        val fourMoves = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))

        val first = buildUndoRequestPlan(
            currentState = fourMoves,
            matchMode = MatchMode.HumanVsAi,
            isEngineReady = true,
            playerSetup = HumanBlack,
        ) as UndoRequestPlan.ApplyLocalUndo
        assertEquals(2, first.undoCount)

        val afterFirstUndo = fourMoves.replayWithoutLastMoves(first.undoCount)
        val second = buildUndoRequestPlan(
            currentState = afterFirstUndo,
            matchMode = MatchMode.HumanVsAi,
            isEngineReady = true,
            playerSetup = HumanBlack,
        ) as UndoRequestPlan.ApplyLocalUndo
        assertEquals(2, second.undoCount)
        assertEquals(0, afterFirstUndo.replayWithoutLastMoves(second.undoCount).moves.size)
    }

    @Test
    fun undoRequestPlanUndoesOneMoveRightAfterHumanWhiteJustMoved() {
        // Black(AI) then White(human) -- it's Black/AI's turn next. The human
        // (White) just moved, so undo should land on 1 (before that move).
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))

        val plan = buildUndoRequestPlan(
            currentState = state,
            matchMode = MatchMode.AiVsHuman,
            isEngineReady = true,
            playerSetup = HumanWhite,
        )

        assertEquals(UndoRequestPlan.ApplyLocalUndo(undoCount = 1, syncEngineAfterUndo = true), plan)
    }

    @Test
    fun undoRequestPlanUndoesTwoMovesWhenItIsAlreadyHumanWhitesTurn() {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))
            .play(Move.Pass(StoneColor.Black))

        val plan = buildUndoRequestPlan(
            currentState = state,
            matchMode = MatchMode.AiVsHuman,
            isEngineReady = true,
            playerSetup = HumanWhite,
        )

        assertEquals(UndoRequestPlan.ApplyLocalUndo(undoCount = 2, syncEngineAfterUndo = true), plan)
    }

    @Test
    fun undoRequestPlanReportsNothingToUndoWhenHumanWhiteHasNotMovedYet() {
        // Only AI's opening move exists. The human hasn't played at all yet, so
        // there's no human move for undo to discard.
        val state = GameState.empty().play(Move.Pass(StoneColor.Black))

        val plan = buildUndoRequestPlan(
            currentState = state,
            matchMode = MatchMode.AiVsHuman,
            isEngineReady = true,
            playerSetup = HumanWhite,
        )

        assertEquals(UndoRequestPlan.ShowMessage("No human move to undo yet."), plan)
    }

    @Test
    fun undoRequestPlanUsesActualFirstMoverNotBlackForHandicapGames() {
        // Handicap games start with White to move (GameState.withHandicap),
        // human plays Black. moves.first().player (White) -- not a hardcoded
        // "Black moves first" assumption -- must drive the parity so this still
        // lands on the state right before the human's (Black's) move.
        val state = GameState.withHandicap(BoardSize.Nine, Ruleset.Japanese, handicapCount = 2)
            .play(Move.Pass(StoneColor.White))
            .play(Move.Pass(StoneColor.Black))

        val plan = buildUndoRequestPlan(
            currentState = state,
            matchMode = MatchMode.HumanVsAi,
            isEngineReady = true,
            playerSetup = HumanBlack,
        )

        assertEquals(UndoRequestPlan.ApplyLocalUndo(undoCount = 1, syncEngineAfterUndo = true), plan)
    }

    @Test
    fun undoRequestPlanAppliesLocallyWithoutSyncWhenEngineIsNotReady() {
        // Same "apply locally, engine sync is best-effort" fallback used
        // elsewhere (HumanMoveController.submitMove) when the engine isn't ready
        // -- undo should not simply refuse in this case.
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))
            .play(Move.Pass(StoneColor.Black))

        val plan = buildUndoRequestPlan(
            currentState = state,
            matchMode = MatchMode.HumanVsAi,
            isEngineReady = false,
            playerSetup = HumanBlack,
        )

        assertEquals(UndoRequestPlan.ApplyLocalUndo(undoCount = 1, syncEngineAfterUndo = false), plan)
    }

    @Test
    fun buildUndoLocalStatePlanReplaysOneMoveBackAndRecordsLocalSnapshot() {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D5", BoardSize.Nine)))

        val plan = buildUndoLocalStatePlan(
            currentState = state,
            undoCount = 1,
            previousMoveReviews = emptyList(),
            scoreSnapshots = emptyList(),
        )

        assertEquals(1, plan.gameState.moves.size)
        assertEquals("Black E5", plan.lastMoveText)
        assertEquals("Undo cleared current Top Moves.", plan.candidateText)
        assertEquals("Score estimate not current.", plan.scoreText)
        assertEquals(1, plan.scoreSnapshots.single().moveNumber)
        assertFalse(plan.reviewAnalysis.hasEngineCandidates)
    }

    @Test
    fun buildUndoLocalStatePlanTrimsStateTimelineAndMoveReviewMarkers() {
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

        val plan = buildUndoLocalStatePlan(
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
    fun undoLastTurnRunnerDispatchesApplyLocalUndoPlan() {
        val state = GameState.empty().play(Move.Pass(StoneColor.Black))
        var message: String? = null
        var appliedPlan: UndoRequestPlan.ApplyLocalUndo? = null

        runUndoLastTurnApplication(
            UndoLastTurnRunRequest(
                currentState = state,
                matchMode = MatchMode.LocalTwoPlayer,
                isEngineReady = true,
                playerSetup = HumanBlack,
                showMessage = { value -> message = value },
                runApplyLocalUndo = { plan -> appliedPlan = plan },
            ),
        )

        assertEquals(null, message)
        assertEquals(UndoRequestPlan.ApplyLocalUndo(undoCount = 1, syncEngineAfterUndo = true), appliedPlan)
    }

    @Test
    fun undoLastTurnRunnerShowsMessageInsteadOfDispatchingForAiVsAi() {
        val state = GameState.empty().play(Move.Pass(StoneColor.Black))
        var message: String? = null
        var dispatchCalled = false

        runUndoLastTurnApplication(
            UndoLastTurnRunRequest(
                currentState = state,
                matchMode = MatchMode.AiVsAi,
                isEngineReady = true,
                playerSetup = PlayerSetup(
                    black = SidePlayerSetup(controller = SeatController.Ai),
                    white = SidePlayerSetup(controller = SeatController.Ai),
                ),
                showMessage = { value -> message = value },
                runApplyLocalUndo = { dispatchCalled = true },
            ),
        )

        assertEquals("Undo is not available while AI controls both sides.", message)
        assertFalse(dispatchCalled)
    }

    @Test
    fun applyLocalUndoRunnerAppliesImmediatelyAndSchedulesSettledEngineSync() {
        // This is the property that makes undo safe to trigger while the engine
        // is mid-turn: applyUndo runs synchronously here, before anything that
        // could wait on or be discarded by an in-flight engine operation.
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D5", BoardSize.Nine)))
        var applied: UndoLocalStatePlan? = null
        var engineMessage: String? = null
        var cancelCalled = false
        var scheduledState: GameState? = null
        var scheduledQuietUntilMillis: Long? = null

        runApplyLocalUndoApplication(
            ApplyLocalUndoRunRequest(
                plan = UndoRequestPlan.ApplyLocalUndo(undoCount = 1, syncEngineAfterUndo = true),
                currentState = state,
                previousMoveReviews = emptyList(),
                scoreSnapshots = emptyList(),
                applyUndo = { undo -> applied = undo },
                markQuiet = { 2_000L },
                setEngineMessage = { message -> engineMessage = message },
                cancelPendingPostUndoSync = { cancelCalled = true },
                schedulePostUndoSync = { targetState, quietUntilMillis ->
                    scheduledState = targetState
                    scheduledQuietUntilMillis = quietUntilMillis
                },
            ),
        )

        assertEquals(1, applied?.gameState?.moves?.size)
        assertEquals(applied?.gameState, scheduledState)
        assertEquals(2_000L, scheduledQuietUntilMillis)
        assertEquals("Local undo completed. Engine analysis will resume after undo input settles.", engineMessage)
        assertFalse(cancelCalled)
    }

    @Test
    fun applyLocalUndoRunnerCancelsEngineSyncWhenOffline() {
        val state = GameState.empty().play(Move.Pass(StoneColor.Black))
        var applied: UndoLocalStatePlan? = null
        var engineMessage: String? = null
        var cancelCalled = false
        var scheduleCalled = false

        runApplyLocalUndoApplication(
            ApplyLocalUndoRunRequest(
                plan = UndoRequestPlan.ApplyLocalUndo(undoCount = 1, syncEngineAfterUndo = false),
                currentState = state,
                previousMoveReviews = emptyList(),
                scoreSnapshots = emptyList(),
                applyUndo = { undo -> applied = undo },
                markQuiet = { 2_000L },
                setEngineMessage = { message -> engineMessage = message },
                cancelPendingPostUndoSync = { cancelCalled = true },
                schedulePostUndoSync = { _, _ -> scheduleCalled = true },
            ),
        )

        assertEquals(0, applied?.gameState?.moves?.size)
        assertEquals("Local undo completed without engine sync.", engineMessage)
        assertTrue(cancelCalled)
        assertFalse(scheduleCalled)
    }
}
