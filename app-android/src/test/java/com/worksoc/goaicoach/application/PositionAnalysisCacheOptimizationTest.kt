package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionAnalysisCacheOptimizationTest {
    @Test
    fun planSkipsFastBeginnerOnlySetupBecauseItDoesNotUseJsonCache() {
        val plan = buildPositionAnalysisCacheOptimizationPlan(
            finalState = shortFinishedGame(),
            playerSetup = PlayerSetup(
                black = SidePlayerSetup(
                    controller = SeatController.Ai,
                    playLevel = PlayLevelSetting(PlayLevelGroup.FastBeginner, 3),
                ),
                white = SidePlayerSetup(controller = SeatController.Human),
            ),
            searchTimeSettings = SearchTimeSettings(),
        )

        assertTrue(plan.isEmpty)
    }

    @Test
    fun planBuildsJsonTargetsForLearningLevelAndKeepsGameplayCacheLimit() {
        val settings = SearchTimeSettings(b32Millis = 2_000L)
        val plan = buildPositionAnalysisCacheOptimizationPlan(
            finalState = shortFinishedGame(),
            playerSetup = PlayerSetup(
                black = SidePlayerSetup(controller = SeatController.Human),
                white = SidePlayerSetup(
                    controller = SeatController.Ai,
                    playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, 7),
                ),
            ),
            searchTimeSettings = settings,
            maxTargets = 3,
        )

        assertFalse(plan.isEmpty)
        assertEquals(3, plan.targets.size)
        plan.targets.forEach { target ->
            assertEquals(32, target.cacheLimit.visits)
            assertEquals(2_000L, target.cacheLimit.timeMillis)
            assertNull(target.executionLimit.timeMillis)
        }
    }

    @Test
    fun planPrioritizesFirstTenOpeningMovesBeforeLaterMoves() {
        val plan = buildPositionAnalysisCacheOptimizationPlan(
            finalState = openingGame(moveCount = 20),
            playerSetup = beginnerAiSetup(),
            searchTimeSettings = SearchTimeSettings(),
            maxTargets = 10,
        )

        assertEquals((1..10).toList(), plan.targets.map { target -> target.moveNumber })
    }

    @Test
    fun planExtendsToMoveElevenOnlyAfterFirstTenAreComplete() {
        val plan = buildPositionAnalysisCacheOptimizationPlan(
            finalState = openingGame(moveCount = 20),
            playerSetup = beginnerAiSetup(),
            searchTimeSettings = SearchTimeSettings(),
            maxTargets = 5,
            qualityFor = { state, limit ->
                if (state.moves.size <= 10) {
                    PositionAnalysisCacheQuality.from(
                        rootVisits = limit.visits,
                        requestedRootVisits = limit.visits,
                    )
                } else {
                    null
                }
            },
        )

        assertEquals((11..15).toList(), plan.targets.map { target -> target.moveNumber })
    }

    @Test
    fun promptAppearsOnlyAfterStableEndedGameWithTargets() {
        val plan = buildPositionAnalysisCacheOptimizationPlan(
            finalState = shortFinishedGame(),
            playerSetup = PlayerSetup(
                black = SidePlayerSetup(controller = SeatController.Human),
                white = SidePlayerSetup(
                    controller = SeatController.Ai,
                    playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, 7),
                ),
            ),
            searchTimeSettings = SearchTimeSettings(),
            maxTargets = 2,
        )

        val prompt = buildPositionAnalysisCacheOptimizationPrompt(
            isGameEnded = true,
            isEngineReady = true,
            isEngineBusy = false,
            isOptimizationRunning = false,
            dismissedGameFingerprint = null,
            plan = plan,
        )

        assertEquals(2, prompt?.targetCount)
        assertNull(
            buildPositionAnalysisCacheOptimizationPrompt(
                isGameEnded = true,
                isEngineReady = true,
                isEngineBusy = false,
                isOptimizationRunning = false,
                dismissedGameFingerprint = plan.gameFingerprint,
                plan = plan,
            ),
        )
    }

    @Test
    fun uiStateTracksPromptDismissAcceptAndRunningFlag() {
        val plan = buildPositionAnalysisCacheOptimizationPlan(
            finalState = shortFinishedGame(),
            playerSetup = beginnerAiSetup(),
            searchTimeSettings = SearchTimeSettings(),
            maxTargets = 2,
        )
        val prompt = PositionAnalysisCacheOptimizationPrompt(
            gameFingerprint = plan.gameFingerprint,
            moveCount = plan.finalMoveCount,
            targetCount = plan.targets.size,
        )

        val prompted = PositionAnalysisCacheOptimizationUiState().withPrompt(prompt)
        val dismissed = prompted.dismiss(currentGameFingerprint = "fallback")
        val accepted = prompted.accept(plan)
        val running = accepted.startRunning()
        val finished = running.finishRunning()

        assertEquals(prompt, prompted.prompt)
        assertNull(dismissed.prompt)
        assertEquals(plan.gameFingerprint, dismissed.dismissedGameFingerprint)
        assertNull(accepted.prompt)
        assertEquals(plan.gameFingerprint, accepted.dismissedGameFingerprint)
        assertTrue(running.isRunning)
        assertFalse(finished.isRunning)
    }

    private fun shortFinishedGame(): GameState =
        GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("C5", BoardSize.Nine)))
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))

    private fun beginnerAiSetup(): PlayerSetup =
        PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, 7),
            ),
        )

    private fun openingGame(moveCount: Int): GameState {
        val coordinates = listOf(
            "A9", "C9", "E9", "G9", "J9",
            "B8", "D8", "F8", "H8",
            "A7", "C7", "E7", "G7", "J7",
            "B6", "D6", "F6", "H6",
            "A5", "C5", "E5", "G5", "J5",
            "B4", "D4",
        )
        return coordinates.take(moveCount).fold(GameState.empty()) { state, label ->
            state.play(
                Move.Play(
                    player = state.nextPlayer,
                    coordinate = BoardCoordinate.fromLabel(label, BoardSize.Nine),
                ),
            )
        }
    }
}
