package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.BoardCoordinate

internal data class MoveReviewMarker(
    val coordinate: BoardCoordinate,
    val moveNumber: Int,
    val tone: MoveReviewTone,
)

internal enum class MoveReviewTone {
    Good,
    Inaccuracy,
    Mistake,
    Unknown,
}

internal fun moveReviewToneFor(pointLoss: Double?): MoveReviewTone =
    when {
        pointLoss == null -> MoveReviewTone.Unknown
        pointLoss <= 0.5 -> MoveReviewTone.Good
        pointLoss <= 3.0 -> MoveReviewTone.Inaccuracy
        else -> MoveReviewTone.Mistake
    }

internal fun moveReviewTextFor(pointLoss: Double?): String =
    when (moveReviewToneFor(pointLoss)) {
        MoveReviewTone.Good -> "good"
        MoveReviewTone.Inaccuracy -> "inaccuracy"
        MoveReviewTone.Mistake -> "mistake"
        MoveReviewTone.Unknown -> "unknown"
    }
