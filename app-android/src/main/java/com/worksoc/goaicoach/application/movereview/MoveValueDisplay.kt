package com.worksoc.goaicoach.application.movereview

import com.worksoc.goaicoach.shared.CandidateMove
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun CandidateMove.pointLossLabel(): String? =
    pointLoss?.coerceAtLeast(0.0)?.toOneDecimalLabel()

internal fun CandidateMove.topMoveDeltaScoreLabel(): String? =
    pointLoss
        ?.coerceAtLeast(0.0)
        ?.let { pointLoss -> (-pointLoss).toOneDecimalLabel() }

internal fun Double.toOneDecimalLabel(): String =
    ((this * 10).roundToInt() / 10.0)
        .let { rounded -> if (abs(rounded) < 0.000001) 0.0 else rounded }
        .toString()
