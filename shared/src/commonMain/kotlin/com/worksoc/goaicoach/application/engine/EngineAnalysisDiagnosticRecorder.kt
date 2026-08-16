package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.engineVisitFillDiagnosticEvent
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.analysisFingerprint

internal class EngineAnalysisDiagnosticRecorder(
    private val diagnosticEventLog: DiagnosticEventLogPort,
) {
    fun recordVisitFill(
        state: GameState,
        requestedVisits: Int,
        rootVisits: Int?,
        searchMode: EngineSearchMode,
    ) {
        val event = engineVisitFillDiagnosticEvent(
            requestedVisits = requestedVisits,
            rootVisits = rootVisits,
            searchMode = searchMode.name,
            positionFingerprint = state.analysisFingerprint(),
        ) ?: return
        diagnosticEventLog.append(event)
    }
}
