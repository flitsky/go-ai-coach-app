package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.savedgame.*
import com.worksoc.goaicoach.application.session.*
import com.worksoc.goaicoach.application.startgame.*

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeLimit
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionApplicationTest {
    @Test
    fun buildPlayerSetupChangePlanBlocksWhileEngineIsBusy() {
        val plan = buildPlayerSetupChangePlan(
            nextSetup = PlayerSetup(),
            currentState = GameState.empty(),
            currentProfile = EngineProfile(),
            defaultPlayLevel = PlayLevelSetting(),
            isEngineBusy = true,
        )

        assertEquals(
            PlayerSetupChangePlan.ShowMessage("Engine is busy. Change Player Setup after the current action."),
            plan,
        )
    }

    @Test
    fun buildPlayerSetupChangePlanSelectsRuntimeAndClearsReviewAnalysis() {
        val level = PlayLevelSetting(group = PlayLevelGroup.Beginner, level = 6)
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(controller = SeatController.Ai, playLevel = level),
        )
        val state = GameState.empty(nextPlayer = StoneColor.White)

        val plan = buildPlayerSetupChangePlan(
            nextSetup = setup,
            currentState = state,
            currentProfile = EngineProfile(),
            defaultPlayLevel = PlayLevelSetting(),
            isEngineBusy = false,
        )

        assertTrue(plan is PlayerSetupChangePlan.Apply)
        val apply = plan as PlayerSetupChangePlan.Apply
        assertEquals(setup, apply.playerSetup)
        assertEquals(level, apply.runtime.playLevel)
        assertFalse(apply.reviewAnalysis.hasEngineCandidates)
        assertTrue(apply.topMoveClearMessage.startsWith("Player Setup changed."))
    }

    @Test
    fun selectRuntimePlayLevelPrefersCurrentAiSide() {
        val blackLevel = PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = 1)
        val whiteLevel = PlayLevelSetting(group = PlayLevelGroup.Beginner, level = 4)
        val setup = PlayerSetup(
            black = SidePlayerSetup(
                controller = SeatController.Human,
                playLevel = blackLevel,
            ),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = whiteLevel,
            ),
        )

        val runtime = selectRuntimePlayLevel(
            setup = setup,
            nextPlayer = StoneColor.White,
            currentProfile = EngineProfile(),
            defaultPlayLevel = blackLevel,
        )

        assertEquals(whiteLevel, runtime.playLevel)
        assertEquals(whiteLevel.analysisPreset, runtime.analysisPreset)
        assertEquals(whiteLevel.group.difficulty, runtime.engineProfile.difficulty)
    }

    @Test
    fun selectRuntimePlayLevelAppliesSearchTimeSettings() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(group = PlayLevelGroup.Beginner, level = 7),
            ),
        )

        val runtime = selectRuntimePlayLevel(
            setup = setup,
            nextPlayer = StoneColor.White,
            currentProfile = EngineProfile(),
            defaultPlayLevel = PlayLevelSetting(),
            searchTimeSettings = SearchTimeSettings(SearchTimeLimit.WithinFiveSeconds),
        )

        assertEquals(32, runtime.engineProfile.analysisLimit.visits)
        assertEquals(5_000L, runtime.engineProfile.analysisLimit.timeMillis)
    }

    @Test
    fun selectRuntimePlayLevelFallsBackToDefaultWhenNoAiSideExists() {
        val default = PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = 2)
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(controller = SeatController.Human),
        )

        val runtime = selectRuntimePlayLevel(
            setup = setup,
            nextPlayer = StoneColor.Black,
            currentProfile = EngineProfile(),
            defaultPlayLevel = default,
        )

        assertEquals(default, runtime.playLevel)
    }

    @Test
    fun buildNewLocalGameSessionPlanReturnsEmptyBoardDefaults() {
        val plan = buildNewLocalGameSessionPlan(
            message = "New game",
            ruleset = Ruleset.Japanese,
            boardSize = BoardSize.Nine,
        )

        assertEquals(0, plan.gameState.moves.size)
        assertEquals("No analysis yet.", plan.candidateText)
        assertEquals("No score estimate yet.", plan.scoreText)
        assertEquals("No move review yet.", plan.moveReviewText)
        assertEquals("None", plan.lastMoveText)
        assertEquals("New game", plan.engineMessage)
        assertEquals(1, plan.scoreSnapshots.size)
        assertFalse(plan.reviewAnalysis.hasEngineCandidates)
    }

    @Test
    fun buildSavedGameRestorePlanPreservesSetupMoveAndTopMovesFlag() {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(group = PlayLevelGroup.Beginner, level = 3),
            ),
        )
        val snapshot = SavedGameSnapshot(
            gameState = state,
            playerSetup = setup,
            playLevel = PlayLevelSetting(),
            topMovesEnabled = true,
            savedAtMillis = 123L,
        )

        val plan = buildSavedGameRestorePlan(
            snapshot = snapshot,
            currentProfile = EngineProfile(),
            defaultPlayLevel = PlayLevelSetting(),
        )

        assertEquals(state, plan.gameState)
        assertEquals(setup, plan.playerSetup)
        assertEquals(setup.white.playLevel, plan.runtime.playLevel)
        assertEquals(true, plan.topMovesEnabled)
        assertEquals("Black E5", plan.lastMoveText)
        assertEquals("Previous game restored at move 1.", plan.engineMessage)
        assertEquals("Score estimate not current.", plan.scoreText)
    }

    @Test
    fun buildSavedGameRestoreRequestPlanBlocksWhileEngineIsBusy() {
        val plan = buildSavedGameRestoreRequestPlan(
            snapshot = savedGameSnapshot(),
            currentProfile = EngineProfile(),
            defaultPlayLevel = PlayLevelSetting(),
            isEngineBusy = true,
            isEngineReady = true,
        )

        assertEquals(
            SavedGameRestoreRequestPlan.ShowMessage(
                "Engine is busy. Restore the saved game after the current action.",
            ),
            plan,
        )
    }

    @Test
    fun buildSavedGameRestoreRequestPlanRestoresLocallyWhenEngineIsNotReady() {
        val plan = buildSavedGameRestoreRequestPlan(
            snapshot = savedGameSnapshot(),
            currentProfile = EngineProfile(),
            defaultPlayLevel = PlayLevelSetting(),
            isEngineBusy = false,
            isEngineReady = false,
        )

        assertTrue(plan is SavedGameRestoreRequestPlan.Restore)
        val restore = plan as SavedGameRestoreRequestPlan.Restore
        assertFalse(restore.syncEngineAfterRestore)
        assertEquals("Previous game restored at move 1.", restore.restore.engineMessage)
    }

    @Test
    fun buildSavedGameRestoreRequestPlanRestoresAndRequestsEngineSyncWhenReady() {
        val plan = buildSavedGameRestoreRequestPlan(
            snapshot = savedGameSnapshot(),
            currentProfile = EngineProfile(),
            defaultPlayLevel = PlayLevelSetting(),
            isEngineBusy = false,
            isEngineReady = true,
        )

        assertTrue(plan is SavedGameRestoreRequestPlan.Restore)
        val restore = plan as SavedGameRestoreRequestPlan.Restore
        assertTrue(restore.syncEngineAfterRestore)
        assertEquals("Black E5", restore.restore.lastMoveText)
    }

    private fun savedGameSnapshot(): SavedGameSnapshot {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        return SavedGameSnapshot(
            gameState = state,
            playerSetup = PlayerSetup(
                black = SidePlayerSetup(controller = SeatController.Human),
                white = SidePlayerSetup(
                    controller = SeatController.Ai,
                    playLevel = PlayLevelSetting(group = PlayLevelGroup.Beginner, level = 3),
                ),
            ),
            playLevel = PlayLevelSetting(),
            topMovesEnabled = true,
            savedAtMillis = 123L,
        )
    }
}
