package com.worksoc.goaicoach.application

internal enum class DiagnosticSeverity(
    val label: String,
) {
    Info("info"),
    Warning("warning"),
    Critical("critical"),
}

internal data class DiagnosticEvent(
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

internal fun engineVisitFillDiagnosticEvent(
    requestedVisits: Int,
    rootVisits: Int?,
    searchMode: String,
    positionFingerprint: String,
): DiagnosticEvent? {
    require(requestedVisits > 0) { "requestedVisits must be positive" }

    if (rootVisits == null) {
        return DiagnosticEvent(
            severity = DiagnosticSeverity.Warning,
            code = "engine.visit_fill_unknown",
            message = "Engine analysis did not report root visits.",
            context = mapOf(
                "requestedVisits" to requestedVisits.toString(),
                "searchMode" to searchMode,
                "positionFingerprint" to positionFingerprint,
            ),
        )
    }

    if (rootVisits >= requestedVisits) {
        return null
    }

    return DiagnosticEvent(
        severity = DiagnosticSeverity.Warning,
        code = "engine.visit_fill_short",
        message = "Engine analysis finished before requested root visits were filled.",
        context = mapOf(
            "requestedVisits" to requestedVisits.toString(),
            "rootVisits" to rootVisits.toString(),
            "searchMode" to searchMode,
            "positionFingerprint" to positionFingerprint,
        ),
    )
}

internal fun scoreDisagreementDiagnosticEvent(
    engineFinalScore: String,
    localScore: String,
    source: String,
): DiagnosticEvent =
    DiagnosticEvent(
        severity = DiagnosticSeverity.Critical,
        code = "score.final_disagreement",
        message = "Final score sources disagree.",
        context = mapOf(
            "engineFinalScore" to engineFinalScore,
            "localScore" to localScore,
            "source" to source,
        ),
    )

internal object NoopDiagnosticEventLog : DiagnosticEventLogPort {
    override fun append(
        event: DiagnosticEvent,
        nowMillis: Long,
    ) = Unit

    override fun readText(): String =
        "Diagnostic event log disabled."

    override fun clear() = Unit
}

private fun String.oneLineValue(): String =
    replace('\n', ' ')
        .replace('\r', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(240)
