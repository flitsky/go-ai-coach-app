package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.engineVisitFillDiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticSeverity
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.analysisFingerprint
import com.worksoc.goaicoach.shared.enginecontract.AnalysisFallbackRecord
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode

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

    /**
     * 분석이 폴백 경로로 내려간 사실을 한 줄 남긴다(refactor backlog #16ⓐ).
     *
     * ⚠️ **지금까지 이 사건은 완전 무음이었다.** 2계층에서 `runCatching`이 전부 삼키고 조용히
     * 다른 경로로 내려갔으므로, 매 수마다 일어나고 있어도 앱에도 로그에도 흔적이 없었다.
     * 2계층은 사실만 [AnalysisFallbackRecord]로 싣고, 진단 이벤트로 바꾸는 것은 여기서 한다 —
     * 엔진 구현체가 진단 로그 포트를 알 필요는 없다.
     */
    fun recordAnalysisFallback(
        state: GameState,
        fallback: AnalysisFallbackRecord?,
    ) {
        if (fallback == null) return
        diagnosticEventLog.append(
            DiagnosticEvent(
                severity = DiagnosticSeverity.Warning,
                code = "engine.analysis.fallback",
                message = "Engine analysis fell back to another search path.",
                context = mapOf(
                    "from" to fallback.fromPath,
                    "to" to fallback.toPath,
                    "reason" to fallback.reason,
                    "positionFingerprint" to state.analysisFingerprint(),
                ),
            ),
        )
    }
}
