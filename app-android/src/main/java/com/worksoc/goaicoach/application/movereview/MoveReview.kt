package com.worksoc.goaicoach.application.movereview

import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot

internal data class MoveReviewMarker(
    val coordinate: BoardCoordinate,
    val moveNumber: Int,
    val tone: MoveReviewTone,
)

internal enum class MoveReviewTone {
    Excellent,
    Good,
    Inaccuracy,
    Mistake,
    Blunder,
    Unknown,
}

internal data class MoveReviewResult(
    val marker: MoveReviewMarker?,
    val text: String,
)

internal fun buildMoveReview(
    move: Move,
    analysis: MoveAnalysisSnapshot,
    boardSize: BoardSize,
    moveNumber: Int,
): MoveReviewResult {
    val play = move as? Move.Play
        ?: return MoveReviewResult(
            marker = null,
            text = "Move review: pass/resign has no board spot evaluation.",
        )

    if (!analysis.hasEngineCandidates) {
        return MoveReviewResult(
            marker = null,
            text = "Move review: no pre-move analysis cache was ready.",
        )
    }

    val matchedCandidate = analysis.candidateAt(play.coordinate)
    if (matchedCandidate == null) {
        return MoveReviewResult(
            marker = MoveReviewMarker(
                coordinate = play.coordinate,
                moveNumber = moveNumber,
                tone = MoveReviewTone.Unknown,
            ),
            text = "Move review: ${play.coordinate.label(boardSize)} was not legal in the pre-move analysis snapshot.",
        )
    }

    val pointLoss = matchedCandidate.pointLoss
    val tone = moveReviewToneFor(pointLoss)
    val lossText = matchedCandidate.pointLossLabel()
        ?.let { "loss $it point(s)" }
        ?: "score loss pending"
    val priorText = matchedCandidate.policyPrior
        ?.let { ", policy ${(it * 100).toInt()}%" }
        .orEmpty()

    return MoveReviewResult(
        marker = MoveReviewMarker(
            coordinate = play.coordinate,
            moveNumber = moveNumber,
            tone = tone,
        ),
        text = "Move review: ${play.coordinate.label(boardSize)} ${moveReviewTextFor(pointLoss)} ($lossText$priorText).",
    )
}

internal fun List<MoveReviewMarker>.withReviewMarker(
    marker: MoveReviewMarker?,
): List<MoveReviewMarker> =
    if (marker == null) {
        this
    } else {
        filterNot { existing -> existing.moveNumber == marker.moveNumber } + marker
    }

internal fun moveReviewToneFor(pointLoss: Double?): MoveReviewTone =
    when {
        pointLoss == null -> MoveReviewTone.Unknown
        pointLoss <= 0.5 -> MoveReviewTone.Excellent
        pointLoss <= 1.5 -> MoveReviewTone.Good
        pointLoss <= 3.0 -> MoveReviewTone.Inaccuracy
        pointLoss <= 6.0 -> MoveReviewTone.Mistake
        else -> MoveReviewTone.Blunder
    }

internal fun topMoveDisplayToneFor(
    pointLoss: Double?,
    bestShownPointLoss: Double?,
    worstShownPointLoss: Double?,
): MoveReviewTone {
    val absoluteTone = moveReviewToneFor(pointLoss)
    val loss = pointLoss ?: return absoluteTone
    val bestLoss = bestShownPointLoss ?: return absoluteTone
    if (bestLoss <= 3.0) {
        return absoluteTone
    }

    val worstLoss = worstShownPointLoss ?: bestLoss
    if (worstLoss <= bestLoss) {
        return MoveReviewTone.Inaccuracy
    }

    val relativeLoss = ((loss - bestLoss) / (worstLoss - bestLoss)).coerceIn(0.0, 1.0)
    return when {
        relativeLoss <= 0.15 -> MoveReviewTone.Inaccuracy
        relativeLoss >= 0.85 -> MoveReviewTone.Blunder
        else -> MoveReviewTone.Mistake
    }
}

internal fun moveReviewTextFor(pointLoss: Double?): String =
    when (moveReviewToneFor(pointLoss)) {
        MoveReviewTone.Excellent -> "excellent"
        MoveReviewTone.Good -> "good"
        MoveReviewTone.Inaccuracy -> "inaccuracy"
        MoveReviewTone.Mistake -> "mistake"
        MoveReviewTone.Blunder -> "blunder"
        MoveReviewTone.Unknown -> "unknown"
    }
