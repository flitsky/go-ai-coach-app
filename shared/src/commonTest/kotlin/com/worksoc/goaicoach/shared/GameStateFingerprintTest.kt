package com.worksoc.goaicoach.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class GameStateFingerprintTest {
    @Test
    fun samePositionAfterReplayHasSameAnalysisFingerprint() {
        val state = GameState.empty(BoardSize.Nine, Ruleset.Japanese)
            .play(Move.Play(StoneColor.Black, point("E5")))
            .play(Move.Play(StoneColor.White, point("C5")))
            .play(Move.Play(StoneColor.Black, point("G6")))
        val replayed = GameStateReplayer.replay(
            boardSize = BoardSize.Nine,
            ruleset = Ruleset.Japanese,
            moves = state.moves,
        )

        assertEquals(state.analysisFingerprint(), replayed.analysisFingerprint())
    }

    @Test
    fun rulesetAndNextMoveAffectAnalysisFingerprint() {
        val japanese = GameState.empty(BoardSize.Nine, Ruleset.Japanese)
            .play(Move.Play(StoneColor.Black, point("E5")))
        val chinese = GameState.empty(BoardSize.Nine, Ruleset.Chinese)
            .play(Move.Play(StoneColor.Black, point("E5")))
        val nextMove = japanese
            .play(Move.Play(StoneColor.White, point("C5")))

        assertNotEquals(japanese.analysisFingerprint(), chinese.analysisFingerprint())
        assertNotEquals(japanese.analysisFingerprint(), nextMove.analysisFingerprint())
    }

    private fun point(label: String): BoardCoordinate =
        BoardCoordinate.fromLabel(label, BoardSize.Nine)
}
