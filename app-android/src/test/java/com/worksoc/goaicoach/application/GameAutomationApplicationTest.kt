package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.GameState
import org.junit.Assert.assertFalse
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
}
