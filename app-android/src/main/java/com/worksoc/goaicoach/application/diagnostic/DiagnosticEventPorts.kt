package com.worksoc.goaicoach.application.diagnostic

import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEventExternalExportPayload

internal interface DiagnosticEventLogPort {
    fun append(
        event: DiagnosticEvent,
        nowMillis: Long = System.currentTimeMillis(),
    )

    fun readText(): String
    fun clear()
}

internal interface DiagnosticEventExternalSinkPort {
    fun send(payload: DiagnosticEventExternalExportPayload): Result<Unit>
}

internal object NoopDiagnosticEventExternalSink : DiagnosticEventExternalSinkPort {
    override fun send(payload: DiagnosticEventExternalExportPayload): Result<Unit> =
        Result.success(Unit)
}

internal class RecordingDiagnosticEventExternalSink(
    private val payloadBuffer: MutableList<DiagnosticEventExternalExportPayload> = mutableListOf(),
) : DiagnosticEventExternalSinkPort {
    var failure: Throwable? = null

    val payloads: List<DiagnosticEventExternalExportPayload>
        get() = payloadBuffer.toList()

    override fun send(payload: DiagnosticEventExternalExportPayload): Result<Unit> {
        failure?.let { return Result.failure(it) }
        payloadBuffer += payload
        return Result.success(Unit)
    }

    fun clear() {
        payloadBuffer.clear()
        failure = null
    }
}
