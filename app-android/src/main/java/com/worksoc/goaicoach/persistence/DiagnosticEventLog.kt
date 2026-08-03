package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import java.io.File
import org.json.JSONObject

internal class DiagnosticEventLog(
    file: File,
    maxBytes: Int = TrimmedAppendOnlyLog.DefaultMaxBytes,
    trimToBytes: Int = TrimmedAppendOnlyLog.DefaultTrimToBytes,
) : TrimmedAppendOnlyLog(
    file = file,
    maxBytes = maxBytes,
    trimToBytes = trimToBytes,
    trimMarker = DiagnosticLogTrimMarker,
    emptyMessage = "No diagnostic event log recorded.",
),
    DiagnosticEventLogPort {
    override fun append(
        event: DiagnosticEvent,
        nowMillis: Long,
    ) {
        appendAndTrim(DiagnosticEventLogCodec.encodeLine(event, nowMillis))
    }

    companion object {
        const val FileName: String = "diagnostic_events.jsonl"
        private const val DiagnosticLogTrimMarker = """{"trimmed":true,"message":"diagnostic log trimmed to recent events"}""" + "\n"
    }
}

internal object DiagnosticEventLogCodec {
    fun encodeLine(
        event: DiagnosticEvent,
        nowMillis: Long,
    ): String =
        JSONObject()
            .put("t", nowMillis)
            .put("severity", event.severity.label)
            .put("code", event.code)
            .put("message", event.message)
            .put(
                "context",
                JSONObject().also { contextJson ->
                    event.context.entries
                        .sortedBy { entry -> entry.key }
                        .forEach { entry ->
                            contextJson.put(entry.key, entry.value)
                        }
                },
            )
            .toString()
}
