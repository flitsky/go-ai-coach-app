package com.worksoc.goaicoach.match

import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchRefereeTest {
    @Test
    fun playAppliesLegalMoveThroughGameStateRules() {
        val coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val result = MatchReferee.play(
            state = GameState.empty(),
            move = Move.Play(StoneColor.Black, coordinate),
        )

        assertTrue(result.isSuccess)
        assertEquals(StoneColor.White, result.getOrThrow().nextPlayer)
        assertEquals(StoneColor.Black, result.getOrThrow().stoneAt(coordinate))
    }

    @Test
    fun playRejectsIllegalMoveThroughGameStateRules() {
        val coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, coordinate))

        val result = MatchReferee.play(
            state = state,
            move = Move.Play(StoneColor.White, coordinate),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun endgameReasonIsPassesOnlyAfterConsecutivePasses() {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))

        assertTrue(MatchReferee.shouldResolveEndgame(state))
        assertEquals("Game ended after consecutive passes.", MatchReferee.endgameReasonText(state))
        assertNotNull(MatchReferee.localFinalScoreIfGameEndedByPasses(state))
    }

    @Test
    fun nonTerminalStateHasNoEndgameReasonOrFinalScore() {
        val state = GameState.empty()

        assertFalse(MatchReferee.shouldResolveEndgame(state))
        assertNull(MatchReferee.endgameReasonText(state))
        assertNull(MatchReferee.localFinalScoreIfGameEndedByPasses(state))
    }
}
