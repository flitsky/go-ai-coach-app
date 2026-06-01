package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KataGoAnalysisParserTest {
    @Test
    fun parsesKataGoInfoMoves() {
        val response = """
            info move C5 visits 2 winrate 0.182844 scoreLead 0.00741314 order 0 pv C5 G4 info move G5 visits 1 winrate 0.48 scoreLead -0.4 order 1 pv G5
            play C5
        """.trimIndent()

        val candidates = KataGoAnalysisParser.parseCandidates(
            response = response,
            player = StoneColor.White,
            boardSize = BoardSize.Nine,
        )

        assertEquals(2, candidates.size)
        assertEquals(2, candidates[0].visits)
        assertEquals(0.182844, candidates[0].winRate)
        assertEquals(0.00741314, candidates[0].scoreLead)
        assertEquals("KataGo order 0", candidates[0].note)

        val firstMove = assertIs<Move.Play>(candidates[0].move)
        assertEquals(StoneColor.White, firstMove.player)
        assertEquals("C5", firstMove.coordinate.label(BoardSize.Nine))

        val secondMove = assertIs<Move.Play>(candidates[1].move)
        assertEquals("G5", secondMove.coordinate.label(BoardSize.Nine))
    }

    @Test
    fun normalizesLegacyIntegerWinRate() {
        val response = "info move D4 visits 10 winrate 5234 scoreLead 1.5 order 0 pv D4"

        val candidates = KataGoAnalysisParser.parseCandidates(
            response = response,
            player = StoneColor.Black,
            boardSize = BoardSize.Nine,
        )

        assertEquals(0.5234, candidates.single().winRate)
    }

    @Test
    fun respectsMaxCandidateCount() {
        val response = """
            info move C5 visits 2 winrate 0.5 scoreLead 0 order 0 pv C5
            info move D5 visits 2 winrate 0.4 scoreLead 0 order 1 pv D5
            info move E5 visits 2 winrate 0.3 scoreLead 0 order 2 pv E5
        """.trimIndent()

        val candidates = KataGoAnalysisParser.parseCandidates(
            response = response,
            player = StoneColor.Black,
            boardSize = BoardSize.Nine,
            maxCandidates = 2,
        )

        assertEquals(2, candidates.size)
    }
}
