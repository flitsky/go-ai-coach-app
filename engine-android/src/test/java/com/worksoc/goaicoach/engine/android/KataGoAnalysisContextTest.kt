package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor
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
    fun gtpCandidateFallbackUsesWhiteTurnForHandicapOpening() {
        val context = KataGoAnalysisContext(
            boardSize = BoardSize.Thirteen,
            ruleset = Ruleset.Japanese,
            nextPlayer = StoneColor.White,
            playedMoves = emptyList(),
            handicapCount = 2,
        )
        val client = KataGoGtpAnalysisClient(
            sendCommand = { "" },
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
