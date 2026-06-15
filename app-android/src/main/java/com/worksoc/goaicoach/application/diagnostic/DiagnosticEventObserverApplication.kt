package com.worksoc.goaicoach.application.diagnostic

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.EngineOperationRequest
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import kotlinx.coroutines.TimeoutCancellationException

internal suspend fun <T> runObservedEngineOperation(
    request: EngineOperationRequest,
    diagnosticEventLog: DiagnosticEventLogPort,
    slowThresholdMillis: Long = request.timeoutPolicy.timeoutMillis ?: 5_000L,
    currentTimeMillis: () -> Long = System::currentTimeMillis,
    block: suspend () -> T,
): T {
    require(slowThresholdMillis > 0L) { "slowThresholdMillis must be positive" }

    val startedAtMillis = currentTimeMillis()
    var timeoutRecorded = false
    try {
        return block()
    } catch (error: TimeoutCancellationException) {
        engineOperationTimeoutDiagnosticEvent(request)?.let { event ->
            diagnosticEventLog.append(event)
            timeoutRecorded = true
        }
        throw error
    } finally {
        val elapsedMillis = (currentTimeMillis() - startedAtMillis).coerceAtLeast(0L)
        if (!timeoutRecorded) {
            engineOperationSlowDiagnosticEvent(
                request = request,
                elapsedMillis = elapsedMillis,
                thresholdMillis = slowThresholdMillis,
            )?.let { event -> diagnosticEventLog.append(event) }
        }
    }
}

internal object NoopDiagnosticEventLog : DiagnosticEventLogPort {
    override fun append(
        event: DiagnosticEvent,
        nowMillis: Long,
    ) = Unit

    override fun readText(): String =
        "Diagnostic event log disabled."

    override fun clear() = Unit
}
