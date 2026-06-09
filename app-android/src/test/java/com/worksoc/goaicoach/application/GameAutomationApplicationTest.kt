package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.match.TurnOutcome
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAutomationApplicationTest {
    @Test
    fun aiTurnRunsOnlyForReadyIdleAiSeat() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Human),
        )

        assertTrue(
            shouldRequestAiTurn(
                isGameEnded = false,
                isEngineReady = true,
                isEngineBusy = false,
                shouldShowResumePrompt = false,
                playerSetup = setup,
                gameState = GameState.empty(),
            ),
        )
        assertFalse(
            shouldRequestAiTurn(
                isGameEnded = false,
                isEngineReady = true,
                isEngineBusy = true,
                shouldShowResumePrompt = false,
                playerSetup = setup,
                gameState = GameState.empty(),
            ),
        )
    }

    @Test
    fun topMoveAnalysisRunsOnlyForReadyIdleHumanSeatWithoutResumePrompt() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(controller = SeatController.Ai),
        )

        assertTrue(
            shouldRequestTopMoveAnalysis(
                isGameEnded = false,
                isEngineReady = true,
                isEngineBusy = false,
                shouldShowResumePrompt = false,
                playerSetup = setup,
                targetState = GameState.empty(),
            ),
        )
        assertFalse(
            shouldRequestTopMoveAnalysis(
                isGameEnded = false,
                isEngineReady = true,
                isEngineBusy = false,
                shouldShowResumePrompt = true,
                playerSetup = setup,
                targetState = GameState.empty(),
            ),
        )
    }

    @Test
    fun autoAiTurnDelayAppliesOnlyWhenBothSeatsAreAi() {
        val autoSetup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Ai),
        )
        val humanVsAiSetup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(controller = SeatController.Ai),
        )

        assertEquals(
            AutoPlayDelaySetting.Slow.millis,
            autoAiTurnDelayMillis(autoSetup, AutoPlayDelaySetting.Slow),
        )
        assertEquals(
            0L,
            autoAiTurnDelayMillis(humanVsAiSetup, AutoPlayDelaySetting.Slow),
        )
    }

    @Test
    fun autoAiTurnDisplayPlanUsesEngineEstimateWhenAvailable() {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
        val estimate = ScoreEstimate(
            status = EngineStatus.ready("estimate ready"),
            whiteScoreLead = 1.5,
            whiteWinRate = 0.6,
            summary = "estimate",
        )
        val result = autoAiTurnResult(state, estimate)

        val plan = buildAutoAiTurnDisplayPlan(
            result = result,
            previousSnapshots = emptyList(),
            previousReviewCandidates = emptyList(),
        )

        assertEquals(state, plan.gameState)
        assertEquals(result.playLevel.analysisPreset, plan.analysisPreset)
        assertEquals("candidate text", plan.candidateText)
        assertEquals(estimate, plan.scoreDisplay.scoreEstimate)
        assertEquals("estimate ready", plan.scoreDisplay.engineMessage)
        assertEquals(ScoreSnapshotSource.EngineEstimate, plan.scoreDisplay.scoreSnapshots.single().source)
        assertFalse(plan.shouldResolveEndgame)
        assertEquals(state, plan.nextAnalysisState)
    }

    @Test
    fun autoAiTurnDisplayPlanFallsBackToLocalSnapshotWhenEstimateIsMissing() {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
        val result = autoAiTurnResult(state, estimate = null)

        val plan = buildAutoAiTurnDisplayPlan(
            result = result,
            previousSnapshots = emptyList(),
            previousReviewCandidates = emptyList(),
        )

        assertNull(plan.scoreDisplay.scoreEstimate)
        assertEquals("Score estimate not current.", plan.scoreDisplay.scoreText)
        assertEquals("engine text", plan.scoreDisplay.engineMessage)
        assertEquals(ScoreSnapshotSource.LocalAreaEstimate, plan.scoreDisplay.scoreSnapshots.single().source)
    }

    @Test
    fun autoAiTurnDisplayPlanCarriesPrePassCandidatesForEndgameResolution() {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))
        val passCandidate = CandidateMove(Move.Pass(StoneColor.White), pointLoss = 0.0)
        val result = autoAiTurnResult(state, estimate = null)

        val plan = buildAutoAiTurnDisplayPlan(
            result = result,
            previousSnapshots = emptyList(),
            previousReviewCandidates = listOf(passCandidate),
        )

        assertTrue(plan.shouldResolveEndgame)
        assertNull(plan.nextAnalysisState)
        assertEquals(listOf(passCandidate), plan.endgamePrePassCandidates)
    }

    private fun autoAiTurnResult(
        state: GameState,
        estimate: ScoreEstimate?,
    ): AutoAiTurnResult =
        AutoAiTurnResult(
            turnOutcome = TurnOutcome(
                gameState = state,
                engineMessage = "engine text",
                candidateText = "candidate text",
                lastMoveText = "last move",
            ),
            scoreEstimate = estimate,
            profile = EngineProfile(),
            playLevel = PlayLevelSetting(),
        )
}
