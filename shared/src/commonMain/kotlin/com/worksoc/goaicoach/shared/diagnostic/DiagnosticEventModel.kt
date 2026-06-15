package com.worksoc.goaicoach.shared.diagnostic

enum class DiagnosticSeverity(
    val label: String,
) {
    Info("info"),
    Warning("warning"),
    Critical("critical"),
}

data class DiagnosticEvent(
    val severity: DiagnosticSeverity,
    val code: String,
    val message: String,
    val context: Map<String, String> = emptyMap(),
) {
    init {
        require(code.isNotBlank()) { "code must not be blank" }
        require(message.isNotBlank()) { "message must not be blank" }
    }

    fun summary(): String =
        buildString {
            append("severity=${severity.label} code=${code.oneLineValue()} message=${message.oneLineValue()}")
            if (context.isNotEmpty()) {
                append(" context=")
                append(
                    context.entries
                        .sortedBy { entry -> entry.key }
                        .joinToString(",") { entry ->
                            "${entry.key.oneLineValue()}=${entry.value.oneLineValue()}"
                        },
                )
            }
        }
}

enum class DiagnosticEventExternalExportDecision {
    LocalOnly,
    EligibleForUserConsentExport,
}

data class DiagnosticEventExternalExportPlan(
    val decision: DiagnosticEventExternalExportDecision,
    val reason: String,
)

data class DiagnosticEventExternalExportPayload(
    val event: DiagnosticEvent,
    val debugReportText: String? = null,
)

sealed class DiagnosticEventExternalSinkPlan {
    data class Skip(
        val reason: String,
    ) : DiagnosticEventExternalSinkPlan()

    data class Send(
        val payload: DiagnosticEventExternalExportPayload,
    ) : DiagnosticEventExternalSinkPlan()
}

fun planDiagnosticEventExternalExport(event: DiagnosticEvent): DiagnosticEventExternalExportPlan =
    when (event.severity) {
        DiagnosticSeverity.Info ->
            DiagnosticEventExternalExportPlan(
                decision = DiagnosticEventExternalExportDecision.LocalOnly,
                reason = "Info diagnostic events are retained locally unless a debug report is manually shared.",
            )

        DiagnosticSeverity.Warning ->
            DiagnosticEventExternalExportPlan(
                decision = DiagnosticEventExternalExportDecision.EligibleForUserConsentExport,
                reason = "Warning diagnostic events can be exported with user consent for performance analysis.",
            )

        DiagnosticSeverity.Critical ->
            DiagnosticEventExternalExportPlan(
                decision = DiagnosticEventExternalExportDecision.EligibleForUserConsentExport,
                reason = "Critical diagnostic events can be exported with user consent for correctness analysis.",
            )
    }

fun buildDiagnosticEventExternalSinkPlan(
    event: DiagnosticEvent,
    userConsented: Boolean,
    debugReportText: String? = null,
): DiagnosticEventExternalSinkPlan {
    val export = planDiagnosticEventExternalExport(event)
    if (export.decision == DiagnosticEventExternalExportDecision.LocalOnly) {
        return DiagnosticEventExternalSinkPlan.Skip(export.reason)
    }
    if (!userConsented) {
        return DiagnosticEventExternalSinkPlan.Skip("User consent is required before exporting diagnostic events.")
    }
    return DiagnosticEventExternalSinkPlan.Send(
        DiagnosticEventExternalExportPayload(
            event = event,
            debugReportText = debugReportText,
        ),
    )
}

private fun String.oneLineValue(): String =
    replace('\n', ' ')
        .replace('\r', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(240)
