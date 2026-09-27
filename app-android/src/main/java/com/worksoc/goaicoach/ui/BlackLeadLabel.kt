package com.worksoc.goaicoach.ui

import kotlin.math.abs
import kotlin.math.roundToInt

/** 흑 우세 기준 점수차 하나를 `B +1.5` / `W +0.9` / `0.0`으로. 언어와 무관한 표기라 번역이 없다. */
internal fun blackLeadLabel(blackLead: Double): String {
    val rounded = ((abs(blackLead) * 10).roundToInt() / 10.0).toString()
    return when {
        blackLead > 0.0 -> "B +$rounded"
        blackLead < 0.0 -> "W +$rounded"
        else -> "0.0"
    }
}
