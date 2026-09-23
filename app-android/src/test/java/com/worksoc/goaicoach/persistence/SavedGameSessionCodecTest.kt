package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.match.HumanGameType
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import org.json.JSONObject
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

    /**
     * 덤은 점수 계산의 입력이다 — 이어하기에서 유실되면 승패가 뒤집힌다. 기본값(6.5)이
     * 아닌 값을 양쪽으로 하나씩 골라 왕복을 단언한다(2026-09-23 회귀).
     */
    @Test
    fun roundTripPreservesNonDefaultKomi() {
        for (komi in listOf(0.5, 7.5)) {
            val gameState = GameState.empty(BoardSize.Nine, Ruleset.Japanese, komi = komi)
                .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
            val snapshot = SavedGameSnapshot(
                gameState = gameState,
                playerSetup = PlayerSetup(),
                playLevel = PlayLevelSetting(),
                topMovesEnabled = false,
                savedAtMillis = 1L,
            )

            val restored = SavedGameSessionCodec.decode(SavedGameSessionCodec.encode(snapshot))

            assertEquals(komi, restored?.gameState?.komi)
            assertEquals(gameState, restored?.gameState)
        }
    }

    /** 접바둑(핸디캡) 경로도 같은 replay 호출을 타므로 함께 고정한다. */
    @Test
    fun roundTripPreservesKomiOnHandicapGame() {
        val gameState = GameState.withHandicap(BoardSize.Nine, Ruleset.Japanese, handicapCount = 2, komi = 0.5)
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val snapshot = SavedGameSnapshot(
            gameState = gameState,
            playerSetup = PlayerSetup(),
            playLevel = PlayLevelSetting(),
            topMovesEnabled = false,
            savedAtMillis = 1L,
        )

        val restored = SavedGameSessionCodec.decode(SavedGameSessionCodec.encode(snapshot))

        assertEquals(0.5, restored?.gameState?.komi)
        assertEquals(gameState, restored?.gameState)
    }

    /**
     * komi 키가 없던 **옛 저장분**은 지금까지 6.5로 복원돼 왔다. 스키마 번호를 올리지 않고
     * 흡수하기로 했으므로, 키가 없는 JSON은 여전히 6.5여야 한다.
     */
    @Test
    fun legacyJsonWithoutKomiFallsBackToDefault() {
        val gameState = GameState.empty(BoardSize.Nine, Ruleset.Japanese)
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val encoded = JSONObject(
            SavedGameSessionCodec.encode(
                SavedGameSnapshot(
                    gameState = gameState,
                    playerSetup = PlayerSetup(),
                    playLevel = PlayLevelSetting(),
                    topMovesEnabled = false,
                    savedAtMillis = 1L,
                ),
            ),
        ).apply { remove("komi") }.toString()

        val restored = SavedGameSessionCodec.decode(encoded)

        assertEquals(DefaultKomi, restored?.gameState?.komi)
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
