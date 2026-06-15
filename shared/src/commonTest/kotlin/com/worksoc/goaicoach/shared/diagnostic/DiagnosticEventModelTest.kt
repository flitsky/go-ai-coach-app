package com.worksoc.goaicoach.shared.diagnostic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DiagnosticEventModelTest {
    @Test
    fun summaryNormalizesContextInStableOrder() {
        val event = DiagnosticEvent(
            severity = DiagnosticSeverity.Warning,
            code = "engine.operation.slow",
            message = "Engine\noperation  slow",
            context = mapOf(
                "z" to "late\nresult",
                "a" to "gtp fast",
            ),
        )

        assertEquals(
            "severity=warning code=engine.operation.slow message=Engine operation slow context=a=gtp fast,z=late result",
            event.summary(),
        )
    }

    @Test
    fun eventRequiresNonBlankCodeAndMessage() {
        assertFailsWith<IllegalArgumentException> {
            DiagnosticEvent(
                severity = DiagnosticSeverity.Info,
                code = " ",
                message = "message",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DiagnosticEvent(
                severity = DiagnosticSeverity.Info,
                code = "code",
                message = " ",
            )
        }
    }

    @Test
    fun exportPolicyKeepsInfoLocalAndAllowsWarningCriticalOnlyWithConsent() {
        val info = DiagnosticEvent(
            severity = DiagnosticSeverity.Info,
            code = "runtime.note",
            message = "local note",
        )
        val warning = DiagnosticEvent(
            severity = DiagnosticSeverity.Warning,
            code = "engine.operation.short_fill",
            message = "root visits did not fill",
        )
        val critical = DiagnosticEvent(
            severity = DiagnosticSeverity.Critical,
            code = "endgame.score.disagreement",
            message = "assistant judge disagreed with final score",
        )

        assertEquals(
            DiagnosticEventExternalExportDecision.LocalOnly,
            planDiagnosticEventExternalExport(info).decision,
        )
        assertEquals(
            DiagnosticEventExternalExportDecision.EligibleForUserConsentExport,
            planDiagnosticEventExternalExport(warning).decision,
        )
        assertEquals(
            DiagnosticEventExternalExportDecision.EligibleForUserConsentExport,
            planDiagnosticEventExternalExport(critical).decision,
        )

        assertTrue(buildDiagnosticEventExternalSinkPlan(info, userConsented = true) is DiagnosticEventExternalSinkPlan.Skip)
        assertTrue(buildDiagnosticEventExternalSinkPlan(warning, userConsented = false) is DiagnosticEventExternalSinkPlan.Skip)

        val send = buildDiagnosticEventExternalSinkPlan(
            event = critical,
            userConsented = true,
            debugReportText = "debug report",
        )
        assertTrue(send is DiagnosticEventExternalSinkPlan.Send)
        assertEquals(critical, send.payload.event)
        assertEquals("debug report", send.payload.debugReportText)
    }
}
