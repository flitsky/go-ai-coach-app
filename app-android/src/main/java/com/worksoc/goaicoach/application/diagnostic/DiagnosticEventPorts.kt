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
