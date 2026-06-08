package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.BoardCoordinate

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
