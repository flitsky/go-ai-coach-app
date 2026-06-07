package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import kotlin.test.Test
import kotlin.test.assertEquals

class KataGoJsonAnalysisParserTest {
    @Test
    fun parsesBlackPerspectiveJsonCandidatesAndUsesRootScoreForPointLoss() {
        val json = """
            {
              "id": "q1",
              "moveInfos": [
                {"move":"E5","order":0,"scoreLead":0.5,"winrate":0.62,"visits":10,"prior":0.8},
                {"move":"F5","order":1,"scoreLead":0.2,"winrate":0.58,"visits":3,"prior":0.1}
              ],
              "rootInfo": {"scoreLead":1.0,"winrate":0.6}
            }
        """.trimIndent()

        val candidates = KataGoJsonAnalysisParser.parseCandidates(
            response = json,
            player = StoneColor.Black,
            boardSize = BoardSize.Nine,
            maxCandidates = 20,
        )

        assertEquals(2, candidates.size)
        assertEquals("E5", (candidates[0].move as Move.Play).coordinate.label(BoardSize.Nine))
        assertEquals(-0.5, candidates[0].scoreLead ?: error("missing score lead"), 0.000001)
        assertEquals(0.62, candidates[0].winRate ?: error("missing win rate"), 0.000001)
        assertEquals(0.5, candidates[0].pointLoss ?: error("missing point loss"), 0.000001)
        assertEquals("F5", (candidates[1].move as Move.Play).coordinate.label(BoardSize.Nine))
        assertEquals(-0.2, candidates[1].scoreLead ?: error("missing score lead"), 0.000001)
        assertEquals(0.8, candidates[1].pointLoss ?: error("missing point loss"), 0.000001)
    }

    @Test
    fun computesWhitePointLossWithOppositeScoreDirectionFromRootScore() {
        val json = """
            {
              "id": "q1",
              "moveInfos": [
                {"move":"D4","order":0,"scoreLead":-1.0,"winrate":0.35,"visits":9,"prior":0.4},
                {"move":"C4","order":1,"scoreLead":-0.25,"winrate":0.45,"visits":4,"prior":0.2}
              ],
              "rootInfo": {"scoreLead":-1.0,"winrate":0.35}
            }
        """.trimIndent()

        val candidates = KataGoJsonAnalysisParser.parseCandidates(
            response = json,
            player = StoneColor.White,
            boardSize = BoardSize.Nine,
            maxCandidates = 20,
        )

        assertEquals(2, candidates.size)
        assertEquals(1.0, candidates[0].scoreLead ?: error("missing score lead"), 0.000001)
        assertEquals(0.65, candidates[0].winRate ?: error("missing win rate"), 0.000001)
        assertEquals(0.0, candidates[0].pointLoss ?: error("missing point loss"), 0.000001)
        assertEquals(0.25, candidates[1].scoreLead ?: error("missing score lead"), 0.000001)
        assertEquals(0.75, candidates[1].pointLoss ?: error("missing point loss"), 0.000001)
    }

    @Test
    fun fallsBackToTopCandidateWhenRootInfoIsMissing() {
        val json = """
            {
              "id": "q1",
              "moveInfos": [
                {"move":"E5","order":0,"scoreLead":0.5,"winrate":0.62,"visits":10,"prior":0.8},
                {"move":"F5","order":1,"scoreLead":0.2,"winrate":0.58,"visits":3,"prior":0.1}
              ]
            }
        """.trimIndent()

        val candidates = KataGoJsonAnalysisParser.parseCandidates(
            response = json,
            player = StoneColor.Black,
            boardSize = BoardSize.Nine,
            maxCandidates = 20,
        )

        assertEquals(0.0, candidates[0].pointLoss ?: error("missing point loss"), 0.000001)
        assertEquals(0.3, candidates[1].pointLoss ?: error("missing point loss"), 0.000001)
    }
}
