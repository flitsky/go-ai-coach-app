package com.worksoc.goaicoach.application.contract

import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisPreset
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode

data class AnalysisCacheKey(
    val positionFingerprint: String,
    val preset: AnalysisPreset,
    val limit: AnalysisLimit,
    val deep: Boolean,
    // Analysis results from the JSON process and the stateful GTP process are
    // not interchangeable. Keep their short-lived UI cache entries separate.
    val searchMode: EngineSearchMode = EngineSearchMode.GtpStatefulFast,
)
