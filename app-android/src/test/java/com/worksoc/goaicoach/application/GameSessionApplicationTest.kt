package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.persistence.SavedGameSnapshot
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GameSessionApplicationTest {
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
}
