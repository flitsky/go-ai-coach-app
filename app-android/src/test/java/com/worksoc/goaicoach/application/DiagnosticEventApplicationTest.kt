package com.worksoc.goaicoach.application

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticEventApplicationTest {
    @Test
    fun visitFillDiagnosticIsEmptyWhenRootVisitsMeetRequest() {
        val event = engineVisitFillDiagnosticEvent(
            requestedVisits = 32,
            rootVisits = 32,
            searchMode = "JsonPositionAnalysis",
            positionFingerprint = "fp",
        )

        assertNull(event)
    }

    @Test
    fun visitFillDiagnosticWarnsWhenRootVisitsAreShort() {
        val event = requireNotNull(
            engineVisitFillDiagnosticEvent(
                requestedVisits = 32,
                rootVisits = 12,
                searchMode = "JsonPositionAnalysis",
                positionFingerprint = "fp",
            ),
        )

        assertEquals(DiagnosticSeverity.Warning, event.severity)
        assertEquals("engine.visit_fill_short", event.code)
        assertEquals("12", event.context["rootVisits"])
        assertEquals("32", event.context["requestedVisits"])
    }

    @Test
    fun visitFillDiagnosticWarnsWhenRootVisitsAreUnknown() {
        val event = requireNotNull(
            engineVisitFillDiagnosticEvent(
                requestedVisits = 16,
                rootVisits = null,
                searchMode = "GtpStatefulFast",
                positionFingerprint = "fp",
            ),
        )

        assertEquals(DiagnosticSeverity.Warning, event.severity)
        assertEquals("engine.visit_fill_unknown", event.code)
        assertEquals("16", event.context["requestedVisits"])
    }

    @Test
    fun summaryNormalizesMultiLineValues() {
        val summary = DiagnosticEvent(
            severity = DiagnosticSeverity.Critical,
            code = "score.final_disagreement",
            message = "Final\nscore\rproblem",
            context = mapOf("localScore" to "B+10\n", "engineFinalScore" to "W+2"),
        ).summary()

        assertTrue(summary.contains("severity=critical"))
        assertTrue(summary.contains("message=Final score problem"))
        assertTrue(summary.contains("engineFinalScore=W+2,localScore=B+10"))
    }
}
