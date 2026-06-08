package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.CandidateMove
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun CandidateMove.pointLossLabel(): String? =
    pointLoss?.toOneDecimalLabel()

internal fun CandidateMove.pointDeltaLabel(): String? =
    pointLoss?.let { (-it).toSignedOneDecimalLabel() }

internal fun Double.toOneDecimalLabel(): String =
    ((this * 10).roundToInt() / 10.0).toString()

internal fun Double.toSignedOneDecimalLabel(): String {
    val rounded = (this * 10).roundToInt() / 10.0
    val normalized = if (abs(rounded) < 0.05) 0.0 else rounded
    return if (normalized > 0.0) "+$normalized" else normalized.toString()
}
