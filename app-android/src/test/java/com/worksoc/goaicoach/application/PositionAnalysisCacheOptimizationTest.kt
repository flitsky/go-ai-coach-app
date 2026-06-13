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

    private fun shortFinishedGame(): GameState =
        GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("C5", BoardSize.Nine)))
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))
}
