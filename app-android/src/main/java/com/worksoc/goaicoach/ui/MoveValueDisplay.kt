package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.CandidateMove
import kotlin.math.roundToInt

internal fun CandidateMove.pointLossLabel(): String? =
    pointLoss?.toOneDecimalLabel()

internal fun Double.toOneDecimalLabel(): String =
    ((this * 10).roundToInt() / 10.0).toString()
