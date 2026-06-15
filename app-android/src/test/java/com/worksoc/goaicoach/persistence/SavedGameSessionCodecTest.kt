package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.match.HumanGameType
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedGameSessionCodecTest {
    @Test
    fun roundTripRestoresMoveHistoryAndPlayerSetup() {
        val gameState = GameState.empty(BoardSize.Nine, Ruleset.Japanese)
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D4", BoardSize.Nine)))
            .play(Move.Pass(StoneColor.Black))
        val setup = PlayerSetup(
            black = SidePlayerSetup(
                controller = SeatController.Human,
                humanGameType = HumanGameType.Teaching,
            ),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 4),
            ),
        )
        val snapshot = SavedGameSnapshot(
            gameState = gameState,
            playerSetup = setup,
            playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 4),
            topMovesEnabled = true,
            savedAtMillis = 1234L,
        )

        val restored = SavedGameSessionCodec.decode(SavedGameSessionCodec.encode(snapshot))

        assertEquals(gameState, restored?.gameState)
        assertEquals(setup, restored?.playerSetup)
        assertEquals(PlayLevelSetting(PlayLevelGroup.Beginner, level = 4), restored?.playLevel)
        assertEquals(true, restored?.topMovesEnabled)
        assertEquals(1234L, restored?.savedAtMillis)
    }

    @Test
    fun invalidJsonReturnsNull() {
        assertNull(SavedGameSessionCodec.decode("{broken"))
    }

    @Test
    fun onlyUnfinishedGamesAreResumable() {
        assertFalse(
            SavedGameSnapshot(
                gameState = GameState.empty(),
                playerSetup = PlayerSetup(),
                playLevel = PlayLevelSetting(),
                topMovesEnabled = false,
                savedAtMillis = 1L,
            ).isResumable,
        )

        val unfinished = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        assertTrue(
            SavedGameSnapshot(
                gameState = unfinished,
                playerSetup = PlayerSetup(),
                playLevel = PlayLevelSetting(),
                topMovesEnabled = false,
                savedAtMillis = 1L,
            ).isResumable,
        )

        val endedByPasses = unfinished
            .play(Move.Pass(StoneColor.White))
            .play(Move.Pass(StoneColor.Black))
        assertFalse(
            SavedGameSnapshot(
                gameState = endedByPasses,
                playerSetup = PlayerSetup(),
                playLevel = PlayLevelSetting(),
                topMovesEnabled = false,
                savedAtMillis = 1L,
            ).isResumable,
        )
    }
}
