package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.application.DiagnosticEventLogPort
import java.io.File
import kotlin.math.min
import org.json.JSONObject

internal class DiagnosticEventLog(
    private val file: File,
    private val maxBytes: Int = DefaultMaxBytes,
    private val trimToBytes: Int = DefaultTrimToBytes,
) : DiagnosticEventLogPort {
    @Synchronized
    override fun append(
        event: DiagnosticEvent,
        nowMillis: Long,
    ) {
        file.parentFile?.mkdirs()
        file.appendText("${DiagnosticEventLogCodec.encodeLine(event, nowMillis)}\n", Charsets.UTF_8)
        trimIfNeeded()
    }

    @Synchronized
    override fun readText(): String =
        if (file.isFile) {
            file.readText(Charsets.UTF_8)
        } else {
            "No diagnostic event log recorded."
        }

    @Synchronized
    override fun clear() {
        if (file.isFile) {
            file.delete()
        }
    }

    private fun trimIfNeeded() {
        if (!file.isFile || file.length() <= maxBytes) {
            return
        }

        val bytes = file.readBytes()
        val marker = DiagnosticLogTrimMarker.toByteArray(Charsets.UTF_8)
        val keepLength = min((trimToBytes - marker.size).coerceAtLeast(0), bytes.size)
        file.outputStream().use { output ->
            output.write(marker)
            output.write(bytes, bytes.size - keepLength, keepLength)
        }
    }

    companion object {
        const val FileName: String = "diagnostic_events.jsonl"
        const val DefaultMaxBytes: Int = 1_048_576
        private const val DefaultTrimToBytes: Int = 921_600
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
