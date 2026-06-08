package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.BoardCoordinate
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
    fun clampsNegativeRawJsonPointLossToZero() {
        val json = """
            {
              "id": "better-than-root",
              "moveInfos": [
                {"move":"E5","order":0,"scoreLead":1.0,"winrate":0.7,"visits":10,"prior":0.8},
                {"move":"F5","order":1,"scoreLead":0.25,"winrate":0.55,"visits":3,"prior":0.1}
              ],
              "rootInfo": {"scoreLead":0.5,"winrate":0.6}
            }
        """.trimIndent()

        val candidates = KataGoJsonAnalysisParser.parseCandidates(
            response = json,
            player = StoneColor.Black,
            boardSize = BoardSize.Nine,
            maxCandidates = 20,
        )

        assertEquals(0.0, candidates[0].pointLoss ?: error("missing point loss"), 0.000001)
        assertEquals(0.25, candidates[1].pointLoss ?: error("missing point loss"), 0.000001)
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

    @Test
    fun blackPointLossUsesKatrainRootMinusCandidateScoreLeadDirection() {
        val json = """
            {
              "id": "black-turn-after-16",
              "moveInfos": [
                {"move":"F5","order":0,"scoreLead":0.323012561,"winrate":0.740915671,"visits":1,"prior":0.425682873},
                {"move":"B3","order":1,"scoreLead":0.420630336,"winrate":0.555510387,"visits":1,"prior":0.279828697}
              ],
              "rootInfo": {"scoreLead":0.689856329,"winrate":0.751761888}
            }
        """.trimIndent()

        val candidates = KataGoJsonAnalysisParser.parseCandidates(
            response = json,
            player = StoneColor.Black,
            boardSize = BoardSize.Nine,
            maxCandidates = 20,
        )

        assertEquals("F5", (candidates[0].move as Move.Play).coordinate.label(BoardSize.Nine))
        assertEquals(0.366843768, candidates[0].pointLoss ?: error("missing F5 point loss"), 0.000001)
        assertEquals("B3", (candidates[1].move as Move.Play).coordinate.label(BoardSize.Nine))
        assertEquals(0.269225993, candidates[1].pointLoss ?: error("missing B3 point loss"), 0.000001)
    }

    @Test
    fun parsesJsonPolicyAsPolicyOnlyCandidates() {
        val policy = List(82) { index ->
            when (index) {
                0 -> 0.2
                10 -> 0.8
                80 -> 0.4
                else -> -1.0
            }
        }.joinToString(",")
        val json = """
            {
              "id": "q1",
              "moveInfos": [],
              "rootInfo": {"scoreLead":0.0,"winrate":0.5},
              "policy": [$policy]
            }
        """.trimIndent()

        val candidates = KataGoJsonAnalysisParser.parsePolicyCandidates(
            response = json,
            player = StoneColor.Black,
            boardSize = BoardSize.Nine,
            maxCandidates = 3,
            excludedCoordinates = setOf(point("A9")),
        )

        assertEquals(2, candidates.size)
        assertEquals("B8", (candidates[0].move as Move.Play).coordinate.label(BoardSize.Nine))
        assertEquals(0.8, candidates[0].policyPrior ?: error("missing prior"), 0.000001)
        assertEquals("J1", (candidates[1].move as Move.Play).coordinate.label(BoardSize.Nine))
        assertEquals(0.4, candidates[1].policyPrior ?: error("missing prior"), 0.000001)
    }

    @Test
    fun parsesRefinedRootInfoAsCandidateEvaluation() {
        val json = """
            {
              "id": "q1",
              "moveInfos": [
                {"move":"D4","order":0,"scoreLead":0.2,"winrate":0.55,"visits":2}
              ],
              "rootInfo": {"scoreLead":-2.0,"winrate":0.25,"visits":8}
            }
        """.trimIndent()

        val candidate = KataGoJsonAnalysisParser.parseRefinedCandidate(
            response = json,
            player = StoneColor.Black,
            move = Move.Play(StoneColor.Black, point("E5")),
            referenceScoreLead = -0.5,
            policyPrior = 0.42,
        ) ?: error("missing refined candidate")

        assertEquals("E5", (candidate.move as Move.Play).coordinate.label(BoardSize.Nine))
        assertEquals(2.0, candidate.scoreLead ?: error("missing score lead"), 0.000001)
        assertEquals(0.25, candidate.winRate ?: error("missing win rate"), 0.000001)
        assertEquals(2.5, candidate.pointLoss ?: error("missing point loss"), 0.000001)
        assertEquals(8, candidate.visits)
        assertEquals(0.42, candidate.policyPrior ?: error("missing prior"), 0.000001)
    }

    private fun point(label: String): BoardCoordinate =
        BoardCoordinate.fromLabel(label, BoardSize.Nine)
}
