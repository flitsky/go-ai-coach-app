package com.worksoc.goaicoach.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GameStateTest {
    @Test
    fun playMoveAddsStoneAndAlternatesTurn() {
        val coordinate = BoardCoordinate(row = 4, column = 4)

        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, coordinate))

        assertEquals(StoneColor.Black, state.stoneAt(coordinate))
        assertEquals(StoneColor.White, state.nextPlayer)
    }

    @Test
    fun occupiedPointIsRejected() {
        val coordinate = BoardCoordinate(row = 4, column = 4)
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, coordinate))

        assertFailsWith<IllegalArgumentException> {
            state.play(Move.Play(StoneColor.White, coordinate))
        }
    }

    @Test
    fun passAlternatesTurnWithoutAddingStone() {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))

        assertEquals(StoneColor.White, state.nextPlayer)
        assertEquals(0, state.stones.size)
        assertEquals(1, state.moves.size)
    }

    @Test
    fun consecutivePassesEndGame() {
        val onePass = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
        val twoPasses = onePass
            .play(Move.Pass(StoneColor.White))

        assertFalse(onePass.hasConsecutivePasses())
        assertTrue(twoPasses.hasConsecutivePasses())
    }

    @Test
    fun fullBoardIsDetected() {
        val boardSize = BoardSize.Nine
        val stones = buildMap {
            for (row in 0 until boardSize.value) {
                for (column in 0 until boardSize.value) {
                    put(BoardCoordinate(row, column), StoneColor.Black)
                }
            }
        }

        assertTrue(
            GameState.empty(boardSize = boardSize)
                .copy(stones = stones)
                .isBoardFull(),
        )
        assertFalse(GameState.empty(boardSize = boardSize).isBoardFull())
    }

    @Test
    fun coordinateLabelRoundTrips() {
        val boardSize = BoardSize.Nine
        val coordinate = BoardCoordinate(row = 4, column = 4)

        assertEquals("E5", coordinate.label(boardSize))
        assertEquals(coordinate, BoardCoordinate.fromLabel("E5", boardSize))
    }

    @Test
    fun surroundedSingleStoneIsCaptured() {
        val state = GameState.empty()
            .play(play(StoneColor.Black, "D5"))
            .play(play(StoneColor.White, "E5"))
            .play(play(StoneColor.Black, "E4"))
            .play(play(StoneColor.White, "A9"))
            .play(play(StoneColor.Black, "F5"))
            .play(play(StoneColor.White, "A8"))
            .play(play(StoneColor.Black, "E6"))

        assertEquals(null, state.stoneAt(point("E5")))
        assertEquals(StoneColor.Black, state.stoneAt(point("E6")))
        assertEquals(1, state.capturedBy(StoneColor.Black))
    }

    @Test
    fun surroundedGroupIsCaptured() {
        val state = GameState.empty().copy(
            nextPlayer = StoneColor.Black,
            stones = mapOf(
                point("E5") to StoneColor.White,
                point("F5") to StoneColor.White,
                point("D5") to StoneColor.Black,
                point("E4") to StoneColor.Black,
                point("F4") to StoneColor.Black,
                point("G5") to StoneColor.Black,
                point("E6") to StoneColor.Black,
            ),
        ).play(play(StoneColor.Black, "F6"))

        assertEquals(null, state.stoneAt(point("E5")))
        assertEquals(null, state.stoneAt(point("F5")))
        assertEquals(2, state.capturedBy(StoneColor.Black))
    }

    @Test
    fun suicideMoveIsRejected() {
        val state = GameState.empty().copy(
            nextPlayer = StoneColor.White,
            stones = mapOf(
                point("D5") to StoneColor.Black,
                point("F5") to StoneColor.Black,
                point("E4") to StoneColor.Black,
                point("E6") to StoneColor.Black,
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            state.play(play(StoneColor.White, "E5"))
        }
    }

    @Test
    fun captureMoveIsNotRejectedAsSuicide() {
        val state = GameState.empty().copy(
            nextPlayer = StoneColor.Black,
            stones = mapOf(
                point("E5") to StoneColor.White,
                point("D5") to StoneColor.Black,
                point("F5") to StoneColor.Black,
                point("E4") to StoneColor.Black,
            ),
        ).play(play(StoneColor.Black, "E6"))

        assertEquals(null, state.stoneAt(point("E5")))
        assertEquals(StoneColor.Black, state.stoneAt(point("E6")))
    }

    @Test
    fun immediateKoRecaptureIsRejected() {
        val afterCapture = GameState.empty().copy(
            nextPlayer = StoneColor.Black,
            stones = mapOf(
                point("D5") to StoneColor.White,
                point("E4") to StoneColor.White,
                point("E6") to StoneColor.White,
                point("F5") to StoneColor.White,
                point("C5") to StoneColor.Black,
                point("D4") to StoneColor.Black,
                point("D6") to StoneColor.Black,
            ),
        ).play(play(StoneColor.Black, "E5"))

        assertEquals(point("D5"), afterCapture.koPoint)
        assertEquals(StoneColor.White, afterCapture.koForbiddenFor)
        assertFailsWith<IllegalArgumentException> {
            afterCapture.play(play(StoneColor.White, "D5"))
        }
    }

    @Test
    fun passClearsKoRestrictionAndAllowsLaterRecapture() {
        val afterCapture = GameState.empty().copy(
            nextPlayer = StoneColor.Black,
            stones = mapOf(
                point("D5") to StoneColor.White,
                point("E4") to StoneColor.White,
                point("E6") to StoneColor.White,
                point("F5") to StoneColor.White,
                point("C5") to StoneColor.Black,
                point("D4") to StoneColor.Black,
                point("D6") to StoneColor.Black,
            ),
        ).play(play(StoneColor.Black, "E5"))

        val afterTwoPasses = afterCapture
            .play(Move.Pass(StoneColor.White))
            .play(Move.Pass(StoneColor.Black))
        val recaptured = afterTwoPasses.play(play(StoneColor.White, "D5"))

        assertEquals(null, afterTwoPasses.koPoint)
        assertEquals(null, afterTwoPasses.koForbiddenFor)
        assertEquals(StoneColor.White, recaptured.stoneAt(point("D5")))
        assertEquals(null, recaptured.stoneAt(point("E5")))
    }

    @Test
    fun singleMoveCanCaptureMultipleAdjacentGroups() {
        val state = GameState.empty().copy(
            nextPlayer = StoneColor.Black,
            stones = mapOf(
                point("D5") to StoneColor.White,
                point("F5") to StoneColor.White,
                point("C5") to StoneColor.Black,
                point("D4") to StoneColor.Black,
                point("D6") to StoneColor.Black,
                point("F4") to StoneColor.Black,
                point("F6") to StoneColor.Black,
                point("G5") to StoneColor.Black,
            ),
        ).play(play(StoneColor.Black, "E5"))

        assertEquals(StoneColor.Black, state.stoneAt(point("E5")))
        assertEquals(null, state.stoneAt(point("D5")))
        assertEquals(null, state.stoneAt(point("F5")))
        assertEquals(2, state.capturedBy(StoneColor.Black))
    }

    @Test
    fun replayWithoutLastMoveRestoresCapturedStone() {
        val beforeCapture = GameState.empty()
            .play(play(StoneColor.Black, "D5"))
            .play(play(StoneColor.White, "E5"))
            .play(play(StoneColor.Black, "E4"))
            .play(play(StoneColor.White, "A9"))
            .play(play(StoneColor.Black, "F5"))
            .play(play(StoneColor.White, "A8"))
        val afterCapture = beforeCapture.play(play(StoneColor.Black, "E6"))

        val undone = afterCapture.replayWithoutLastMoves(1)

        assertEquals(beforeCapture, undone)
        assertEquals(StoneColor.White, undone.stoneAt(point("E5")))
        assertEquals(0, undone.capturedBy(StoneColor.Black))
        assertEquals(StoneColor.Black, undone.nextPlayer)
    }

    @Test
    fun replayWithoutLastTwoMovesRestoresPreviousTurnPair() {
        val state = GameState.empty()
            .play(play(StoneColor.Black, "E5"))
            .play(play(StoneColor.White, "C5"))
            .play(play(StoneColor.Black, "D5"))
            .play(play(StoneColor.White, "C6"))

        val undone = state.replayWithoutLastMoves(2)

        assertEquals(2, undone.moves.size)
        assertEquals(StoneColor.Black, undone.stoneAt(point("E5")))
        assertEquals(StoneColor.White, undone.stoneAt(point("C5")))
        assertEquals(StoneColor.Black, undone.nextPlayer)
    }

    @Test
    fun replayWithoutMoreMovesThanHistoryResetsBoard() {
        val state = GameState.empty()
            .play(play(StoneColor.Black, "E5"))
            .play(play(StoneColor.White, "C5"))

        val undone = state.replayWithoutLastMoves(99)

        assertEquals(GameState.empty(), undone)
    }

    @Test
    fun localAreaScorerCountsSingleColorRegion() {
        val stones = buildMap {
            for (row in 0 until BoardSize.Nine.value) {
                for (column in 0 until BoardSize.Nine.value) {
                    put(BoardCoordinate(row, column), StoneColor.White)
                }
            }
            remove(point("B8"))
            put(point("A8"), StoneColor.Black)
            put(point("B9"), StoneColor.Black)
            put(point("C8"), StoneColor.Black)
            put(point("B7"), StoneColor.Black)
        }
        val state = GameState.empty(boardSize = BoardSize.Nine).copy(stones = stones)

        val score = BoardAreaScorer.score(state)

        assertEquals(5.0, score.blackArea)
        assertEquals(82.5, score.whiteAreaWithKomi)
        assertEquals(StoneColor.White, score.winner)
        assertEquals(77.5, score.margin)
    }

    @Test
    fun territoryScorerCountsTerritoryAndPrisoners() {
        val stones = buildMap {
            for (row in 0 until BoardSize.Nine.value) {
                for (column in 0 until BoardSize.Nine.value) {
                    put(BoardCoordinate(row, column), StoneColor.White)
                }
            }
            remove(point("B8"))
            put(point("A8"), StoneColor.Black)
            put(point("B9"), StoneColor.Black)
            put(point("C8"), StoneColor.Black)
            put(point("B7"), StoneColor.Black)
        }
        val state = GameState.empty(boardSize = BoardSize.Nine, ruleset = Ruleset.Japanese).copy(
            stones = stones,
            capturedByBlack = 2,
            capturedByWhite = 1,
        )

        val score = BoardScorer.score(state)

        assertEquals("W+4.5", score.rawScore)
        assertEquals(3.0, score.blackArea)
        assertEquals(7.5, score.whiteAreaWithKomi)
    }

    @Test
    fun deadStoneCleanerRemovesEngineMarkedStonesAndUpdatesPrisoners() {
        val state = GameState.empty().copy(
            stones = mapOf(
                point("D5") to StoneColor.Black,
                point("E5") to StoneColor.White,
                point("F5") to StoneColor.White,
            ),
            capturedByBlack = 2,
            capturedByWhite = 1,
            koPoint = point("A1"),
            koForbiddenFor = StoneColor.White,
        )

        val cleanup = DeadStoneCleaner.apply(
            state = state,
            deadStoneCoordinates = listOf(point("E5"), point("E5"), point("A9")),
        )

        assertEquals(1, cleanup.removedCount)
        assertEquals(null, cleanup.state.stoneAt(point("E5")))
        assertEquals(StoneColor.White, cleanup.state.stoneAt(point("F5")))
        assertEquals(StoneColor.Black, cleanup.state.stoneAt(point("D5")))
        assertEquals(3, cleanup.state.capturedBy(StoneColor.Black))
        assertEquals(1, cleanup.state.capturedBy(StoneColor.White))
        assertEquals(null, cleanup.state.koPoint)
        assertEquals(null, cleanup.state.koForbiddenFor)
    }

    @Test
    fun deadStoneCleanerRemovingBlackStonesCreditsWhitePrisoners() {
        val state = GameState.empty().copy(
            stones = mapOf(
                point("D5") to StoneColor.Black,
                point("E5") to StoneColor.White,
            ),
            capturedByBlack = 1,
            capturedByWhite = 2,
        )

        val cleanup = DeadStoneCleaner.apply(
            state = state,
            deadStoneCoordinates = listOf(point("D5")),
        )

        assertEquals(1, cleanup.removedCount)
        assertEquals(null, cleanup.state.stoneAt(point("D5")))
        assertEquals(StoneColor.White, cleanup.state.stoneAt(point("E5")))
        assertEquals(1, cleanup.state.capturedBy(StoneColor.Black))
        assertEquals(3, cleanup.state.capturedBy(StoneColor.White))
    }

    @Test
    fun handicapGameReplayPreservesHandicapStonesAndWhiteTurn() {
        val boardSize = BoardSize.Nine
        val ruleset = Ruleset.Japanese
        val handicapCount = 5

        // 접바둑 5점 시작
        val initial = GameState.withHandicap(boardSize, ruleset, handicapCount)
        assertEquals(5, initial.stones.size)
        assertEquals(StoneColor.White, initial.nextPlayer)

        // 백 1수 착수 (화점 C3 피해서 D3에 착수)
        val state = initial.play(Move.Play(StoneColor.White, point("D3")))
        assertEquals(6, state.stones.size)
        assertEquals(StoneColor.Black, state.nextPlayer)

        // 1수 무르기
        val replayed = state.replayWithoutLastMoves(1)
        assertEquals(handicapCount, replayed.handicapCount)
        assertEquals(5, replayed.stones.size)
        assertEquals(StoneColor.White, replayed.nextPlayer)
    }

    @Test
    fun handicapGameReplayPreservesHandicapStonesWhenMultipleMovesPlayed() {
        val boardSize = BoardSize.Nine
        val ruleset = Ruleset.Japanese
        val handicapCount = 5

        // 접바둑 5점 시작 -> 백 1수 착수 -> 흑 1수 착수 (화점 피해 D3, F3에 착수)
        val state = GameState.withHandicap(boardSize, ruleset, handicapCount)
            .play(Move.Play(StoneColor.White, point("D3")))
            .play(Move.Play(StoneColor.Black, point("F3")))

        assertEquals(7, state.stones.size)
        assertEquals(2, state.moves.size)

        // 2수 무르기
        val replayed = state.replayWithoutLastMoves(2)
        assertEquals(handicapCount, replayed.handicapCount)
        assertEquals(5, replayed.stones.size)
        assertEquals(StoneColor.White, replayed.nextPlayer)
        assertEquals(0, replayed.moves.size)
    }

    private fun play(
        player: StoneColor,
        label: String,
    ): Move.Play = Move.Play(player, point(label))

    private fun point(label: String): BoardCoordinate =
        BoardCoordinate.fromLabel(label, BoardSize.Nine)
}
