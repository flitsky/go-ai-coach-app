package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartGameApplicationTest {
    @Test
    fun aiSetupFallsBackToLocalResetWhenEngineIsNotReady() {
        val plan = buildStartConfiguredGamePlan(
            setup = PlayerSetup(
                black = SidePlayerSetup(controller = SeatController.Human),
                white = SidePlayerSetup(controller = SeatController.Ai),
            ),
            ruleset = Ruleset.Japanese,
            nextPlayer = StoneColor.Black,
            isEngineReady = false,
            isEngineBusy = false,
            currentProfile = EngineProfile(),
            defaultPlayLevel = PlayLevelSetting(),
        )

        assertEquals(
            StartConfiguredGamePlan.ResetLocalGame(
                message = "Player Setup includes AI, but engine is not ready.",
                ruleset = Ruleset.Japanese,
            ),
            plan,
        )
    }

    @Test
    fun busyEngineBlocksNewGameStart() {
        val plan = buildStartConfiguredGamePlan(
            setup = PlayerSetup(
                black = SidePlayerSetup(controller = SeatController.Human),
                white = SidePlayerSetup(controller = SeatController.Ai),
            ),
            ruleset = Ruleset.Chinese,
            nextPlayer = StoneColor.Black,
            isEngineReady = true,
            isEngineBusy = true,
            currentProfile = EngineProfile(),
            defaultPlayLevel = PlayLevelSetting(),
        )

        assertEquals(
            StartConfiguredGamePlan.ShowMessage(
                message = "Engine is busy. Start a new game after the current action.",
            ),
            plan,
        )
    }

    @Test
    fun localTwoPlayerCanResetWithoutEngine() {
        val plan = buildStartConfiguredGamePlan(
            setup = PlayerSetup(
                black = SidePlayerSetup(controller = SeatController.Human),
                white = SidePlayerSetup(controller = SeatController.Human),
            ),
            ruleset = Ruleset.Chinese,
            nextPlayer = StoneColor.Black,
            isEngineReady = false,
            isEngineBusy = false,
            currentProfile = EngineProfile(),
            defaultPlayLevel = PlayLevelSetting(),
        )

        assertEquals(
            StartConfiguredGamePlan.ResetLocalGame(
                message = "Local two-player game. Engine analysis is not connected.",
                ruleset = Ruleset.Chinese,
            ),
            plan,
        )
    }

    @Test
    fun readyEngineStartsWithCurrentAiRuntimeLevel() {
        val aiLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 5)
        val plan = buildStartConfiguredGamePlan(
            setup = PlayerSetup(
                black = SidePlayerSetup(controller = SeatController.Human),
                white = SidePlayerSetup(controller = SeatController.Ai, playLevel = aiLevel),
            ),
            ruleset = Ruleset.Japanese,
            nextPlayer = StoneColor.White,
            isEngineReady = true,
            isEngineBusy = false,
            currentProfile = EngineProfile(),
            defaultPlayLevel = PlayLevelSetting(),
        )

        assertTrue(plan is StartConfiguredGamePlan.StartEngineGame)
        val startPlan = plan as StartConfiguredGamePlan.StartEngineGame
        assertEquals(Ruleset.Japanese, startPlan.ruleset)
        assertEquals(aiLevel, startPlan.runtime.playLevel)
        assertEquals(aiLevel.group.difficulty, startPlan.runtime.engineProfile.difficulty)
        assertEquals(aiLevel.analysisPreset, startPlan.runtime.analysisPreset)
    }
}
