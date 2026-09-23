package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class KataGoAnalysisContextTest {
    @Test
    fun replayStatePreservesHandicapStonesAndWhiteOpeningTurn() {
        val context = KataGoAnalysisContext(
            boardSize = BoardSize.Thirteen,
            ruleset = Ruleset.Japanese,
            nextPlayer = StoneColor.White,
            playedMoves = emptyList(),
            handicapCount = 2,
        )

        val state = context.replayState()

        assertEquals(2, state.handicapCount)
        assertEquals(2, state.stones.size)
        assertEquals(StoneColor.White, state.nextPlayer)
    }

    @Test
    fun replayStatePreservesTurnAfterMoveInHandicapGame() {
        val context = KataGoAnalysisContext(
            boardSize = BoardSize.Thirteen,
            ruleset = Ruleset.Japanese,
            nextPlayer = StoneColor.Black,
            playedMoves = listOf(
                Move.Play(StoneColor.White, BoardCoordinate.fromLabel("G7", BoardSize.Thirteen)),
            ),
            handicapCount = 2,
        )

        val state = context.replayState()

        assertEquals(StoneColor.Black, state.nextPlayer)
        assertEquals(3, state.stones.size)
    }

    @Test
    fun replayStatePreservesStaticInitialStones() {
        val stones = mapOf(
            BoardCoordinate.fromLabel("E5", BoardSize.Nine) to StoneColor.Black,
            BoardCoordinate.fromLabel("D4", BoardSize.Nine) to StoneColor.White,
        )
        val context = KataGoAnalysisContext(
            boardSize = BoardSize.Nine,
            ruleset = Ruleset.Japanese,
            nextPlayer = StoneColor.White,
            playedMoves = emptyList(),
            handicapCount = 0,
            initialStones = stones,
        )

        val state = context.replayState()

        assertEquals(StoneColor.White, state.nextPlayer)
        assertEquals(2, state.stones.size)
        assertEquals(StoneColor.Black, state.stoneAt(BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        assertEquals(StoneColor.White, state.stoneAt(BoardCoordinate.fromLabel("D4", BoardSize.Nine)))
    }

    @Test
    fun gtpCandidateFallbackUsesWhiteTurnForHandicapOpening() = runBlocking {
        val context = KataGoAnalysisContext(
            boardSize = BoardSize.Thirteen,
            ruleset = Ruleset.Japanese,
            nextPlayer = StoneColor.White,
            playedMoves = emptyList(),
            handicapCount = 2,
        )
        val client = KataGoGtpAnalysisClient(
            sendCommand = { _, _ -> "" },
            applySearchLimit = {},
            restoreSearchLimit = { AnalysisLimit() },
            contextProvider = { context },
        )
        val limit = AnalysisLimit(
            visits = 16,
            candidateCount = 1,
            includePolicy = false,
            minVisitsPerCandidate = 0,
            minTimeMillis = null,
        )

        val result = client.analyze(effectiveLimit = limit, requestedLimit = limit)

        assertEquals(StoneColor.White, (result.candidates.single().move as Move.Play).player)
    }
}
