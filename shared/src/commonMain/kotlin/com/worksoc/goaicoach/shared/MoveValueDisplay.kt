package com.worksoc.goaicoach.shared

import kotlin.math.abs
import kotlin.math.roundToInt

fun CandidateMove.pointLossLabel(): String? =
    pointLoss?.coerceAtLeast(0.0)?.toOneDecimalLabel()

fun CandidateMove.topMoveDeltaScoreLabel(): String? =
    pointLoss
        ?.coerceAtLeast(0.0)
        ?.let { pointLoss -> (-pointLoss).toOneDecimalLabel() }

fun Double.toOneDecimalLabel(): String =
    ((this * 10).roundToInt() / 10.0)
        .let { rounded -> if (abs(rounded) < 0.000001) 0.0 else rounded }
        .toString()
