package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.movereview.*
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.CandidateMoveSource
import com.worksoc.goaicoach.shared.policy.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshotSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoveReviewTest {
    @Test
    fun buildMoveReviewCreatesMarkerForMatchedCandidate() {
        val coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val snapshot = MoveAnalysisSnapshot.from(
            state = GameState.empty(),
            candidates = listOf(
                CandidateMove(
                    move = Move.Play(StoneColor.Black, coordinate),
                    pointLoss = 0.2,
                    policyPrior = 0.84,
                    source = CandidateMoveSource.EngineSearch,
                ),
            ),
        )

        val review = buildMoveReview(
            move = Move.Play(StoneColor.Black, coordinate),
            analysis = snapshot,
            boardSize = BoardSize.Nine,
            moveNumber = 1,
        )

        assertEquals(coordinate, review.marker?.coordinate)
        assertEquals(MoveReviewTone.Excellent, review.marker?.tone)
        assertTrue(review.text.contains("E5 excellent"))
        assertTrue(review.text.contains("loss 0.2"))
        assertTrue(review.text.contains("policy 84%"))
    }

    @Test
    fun buildMoveReviewDoesNotCreateMarkerForPassOrMissingAnalysis() {
        val passReview = buildMoveReview(
            move = Move.Pass(StoneColor.Black),
            analysis = MoveAnalysisSnapshot.empty(GameState.empty()),
            boardSize = BoardSize.Nine,
            moveNumber = 1,
        )
        val playReview = buildMoveReview(
            move = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)),
            analysis = MoveAnalysisSnapshot.empty(GameState.empty()),
            boardSize = BoardSize.Nine,
            moveNumber = 1,
        )

        assertNull(passReview.marker)
        assertTrue(passReview.text.contains("pass/resign"))
        assertNull(playReview.marker)
        assertTrue(playReview.text.contains("no pre-move analysis cache"))
    }

    @Test
    fun buildMoveReviewDoesNotCreateMarkerWhenCandidateLacksReliableEngineSearch() {
        val coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val snapshot = MoveAnalysisSnapshot.from(
            state = GameState.empty(),
            candidates = listOf(
                CandidateMove(
                    move = Move.Play(StoneColor.Black, coordinate),
                    pointLoss = 9.0,
                    policyPrior = 0.05,
                    source = CandidateMoveSource.PolicyOnly,
                ),
            ),
        )

        val review = buildMoveReview(
            move = Move.Play(StoneColor.Black, coordinate),
            analysis = snapshot,
            boardSize = BoardSize.Nine,
            moveNumber = 1,
        )

        assertNull(review.marker)
    }

    @Test
    fun buildMoveReviewDoesNotCreateMarkerForUnanalyzedCoordinate() {
        val analyzedCoordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val playedCoordinate = BoardCoordinate.fromLabel("C3", BoardSize.Nine)
        val snapshot = MoveAnalysisSnapshot.from(
            state = GameState.empty(),
            candidates = listOf(
                CandidateMove(
                    move = Move.Play(StoneColor.Black, analyzedCoordinate),
                    pointLoss = 0.1,
                    source = CandidateMoveSource.EngineSearch,
                ),
            ),
        )

        val review = buildMoveReview(
            move = Move.Play(StoneColor.Black, playedCoordinate),
            analysis = snapshot,
            boardSize = BoardSize.Nine,
            moveNumber = 1,
        )

        assertNull(review.marker)
    }

    private fun leadSnapshot(moveNumber: Int, whiteScoreLead: Double) =
        ScoreSnapshot(
            moveNumber = moveNumber,
            whiteScoreLead = whiteScoreLead,
            source = ScoreSnapshotSource.EngineEstimate,
        )

    @Test
    fun deriveMoveReviewMarkersFromScoreSwingChargesTheLossToTheMoverOnly() {
        val black = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val white = BoardCoordinate.fromLabel("C3", BoardSize.Nine)
        val moves = listOf(Move.Play(StoneColor.Black, black), Move.Play(StoneColor.White, white))
        // 흑이 두자 백 리드가 +12 뛴다(흑에게 12집 손해) — 백이 두자 그대로다(백에게 손해 없음).
        val snapshots = listOf(leadSnapshot(0, 0.0), leadSnapshot(1, 12.0), leadSnapshot(2, 12.0))

        val markers = deriveMoveReviewMarkersFromScoreSwing(
            moves = moves,
            scoreSnapshots = snapshots,
            humanColors = setOf(StoneColor.Black, StoneColor.White),
        )

        assertEquals(2, markers.size)
        val blackMarker = markers.single { it.moveNumber == 1 }
        assertEquals(12.0, blackMarker.pointLoss)
        assertEquals(MoveReviewTone.Blunder, blackMarker.tone)
        val whiteMarker = markers.single { it.moveNumber == 2 }
        assertEquals(0.0, whiteMarker.pointLoss)
        assertEquals(MoveReviewTone.Excellent, whiteMarker.tone)
    }

    @Test
    fun deriveMoveReviewMarkersFromScoreSwingChargesAWhiteBlunderCorrectly() {
        val coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val moves = listOf(Move.Play(StoneColor.Black, coordinate), Move.Play(StoneColor.White, coordinate))
        // 백이 두자 백 리드가 10 떨어진다 — 백에게 10집 손해.
        val snapshots = listOf(leadSnapshot(0, 0.0), leadSnapshot(1, 0.0), leadSnapshot(2, -10.0))

        val markers = deriveMoveReviewMarkersFromScoreSwing(
            moves = moves,
            scoreSnapshots = snapshots,
            humanColors = setOf(StoneColor.White),
        )

        assertEquals(1, markers.size)
        assertEquals(10.0, markers.single().pointLoss)
    }

    @Test
    fun deriveMoveReviewMarkersFromScoreSwingSkipsMovesTheHumanDidNotPlay() {
        val coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val moves = listOf(Move.Play(StoneColor.White, coordinate))
        val snapshots = listOf(leadSnapshot(0, 0.0), leadSnapshot(1, 20.0))

        val markers = deriveMoveReviewMarkersFromScoreSwing(
            moves = moves,
            scoreSnapshots = snapshots,
            humanColors = setOf(StoneColor.Black),
        )

        assertTrue(markers.isEmpty())
    }

    @Test
    fun deriveMoveReviewMarkersFromScoreSwingSkipsMovesWithoutBothSnapshots() {
        val coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val moves = listOf(Move.Play(StoneColor.Black, coordinate))

        val markers = deriveMoveReviewMarkersFromScoreSwing(
            moves = moves,
            // 착수 뒤 스냅샷(moveNumber=1)이 없다 — 무료 대국에서 형세가 성기게 찍히는 경우.
            scoreSnapshots = listOf(leadSnapshot(0, 0.0)),
            humanColors = setOf(StoneColor.Black),
        )

        assertTrue(markers.isEmpty())
    }

    @Test
    fun withReviewMarkerReplacesMarkerFromSameMoveNumber() {
        val first = MoveReviewMarker(
            coordinate = BoardCoordinate.fromLabel("D4", BoardSize.Nine),
            moveNumber = 3,
            tone = MoveReviewTone.Mistake,
        )
        val replacement = first.copy(
            coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
            tone = MoveReviewTone.Excellent,
        )

        val markers = listOf(first).withReviewMarker(replacement)

        assertEquals(listOf(replacement), markers)
    }
}
