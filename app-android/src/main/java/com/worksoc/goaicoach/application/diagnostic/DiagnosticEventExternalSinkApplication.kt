package com.worksoc.goaicoach.application.diagnostic

import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEventExternalExportPayload
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEventExternalSinkPlan
import com.worksoc.goaicoach.shared.diagnostic.buildDiagnosticEventExternalSinkPlan

internal sealed class DiagnosticEventExternalSinkResult {
    data class Skipped(val reason: String) : DiagnosticEventExternalSinkResult()
    data class Sent(val payload: DiagnosticEventExternalExportPayload) : DiagnosticEventExternalSinkResult()
    data class Failed(
        val payload: DiagnosticEventExternalExportPayload,
        val error: Throwable,
    ) : DiagnosticEventExternalSinkResult()
}

internal fun runDiagnosticEventExternalSinkPlan(
    event: DiagnosticEvent,
    userConsented: Boolean,
    debugReportText: String,
    sink: DiagnosticEventExternalSinkPort,
): DiagnosticEventExternalSinkResult =
    when (
        val plan = buildDiagnosticEventExternalSinkPlan(
            event = event,
            userConsented = userConsented,
            debugReportText = debugReportText,
        )
    ) {
        is DiagnosticEventExternalSinkPlan.Skip ->
            DiagnosticEventExternalSinkResult.Skipped(plan.reason)

        is DiagnosticEventExternalSinkPlan.Send ->
            sink.send(plan.payload).fold(
                onSuccess = { DiagnosticEventExternalSinkResult.Sent(plan.payload) },
                onFailure = { error ->
                    DiagnosticEventExternalSinkResult.Failed(
                        payload = plan.payload,
                        error = error,
                    )
                },
            )
    }
