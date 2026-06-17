package com.worksoc.goaicoach.application.diagnostic

import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEventExternalExportPayload
import java.io.File

internal class LocalFileDiagnosticEventExternalSink(
    private val file: File,
    private val currentTimeMillis: () -> Long = { System.currentTimeMillis() },
) : DiagnosticEventExternalSinkPort {
    override fun send(payload: DiagnosticEventExternalExportPayload): Result<Unit> =
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText(
                text = payload.toJsonLine(t = currentTimeMillis()) + "\n",
                charset = Charsets.UTF_8,
            )
        }
}

private fun DiagnosticEventExternalExportPayload.toJsonLine(t: Long): String =
    buildString {
        append("{")
        append("\"t\":$t")
        append(",\"severity\":${event.severity.label.jsonString()}")
        append(",\"code\":${event.code.jsonString()}")
        append(",\"message\":${event.message.jsonString()}")
        append(",\"context\":")
        append(
            event.context.entries
                .sortedBy { it.key }
                .joinToString(prefix = "{", postfix = "}") { entry ->
                    "${entry.key.jsonString()}:${entry.value.jsonString()}"
                },
        )
        append(",\"debugReportText\":")
        append(debugReportText?.jsonString() ?: "null")
        append("}")
    }

private fun String.jsonString(): String =
    buildString {
        append('"')
        for (character in this@jsonString) {
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }
