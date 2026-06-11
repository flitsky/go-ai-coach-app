package com.worksoc.goaicoach.persistence

import java.io.File
import kotlin.math.min

internal class RuntimeEventLog(
    private val file: File,
    private val maxBytes: Int = DefaultMaxBytes,
    private val trimToBytes: Int = DefaultTrimToBytes,
) {
    @Synchronized
    fun append(
        event: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        file.parentFile?.mkdirs()
        file.appendText("t=$nowMillis ${event.oneLine()}\n", Charsets.UTF_8)
        trimIfNeeded()
    }

    @Synchronized
    fun readText(): String =
        if (file.isFile) {
            file.readText(Charsets.UTF_8)
        } else {
            "No runtime event log recorded."
        }

    @Synchronized
    fun clear() {
        if (file.isFile) {
            file.delete()
        }
    }

    private fun trimIfNeeded() {
        if (!file.isFile || file.length() <= maxBytes) {
            return
        }

        val bytes = file.readBytes()
        val marker = RuntimeLogTrimMarker.toByteArray(Charsets.UTF_8)
        val keepLength = min((trimToBytes - marker.size).coerceAtLeast(0), bytes.size)
        file.outputStream().use { output ->
            output.write(marker)
            output.write(bytes, bytes.size - keepLength, keepLength)
        }
    }

    private fun String.oneLine(): String =
        replace('\n', ' ')
            .replace('\r', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

    companion object {
        const val FileName: String = "runtime_event_log.txt"
        const val DefaultMaxBytes: Int = 1_048_576
        private const val DefaultTrimToBytes: Int = 921_600
        private const val RuntimeLogTrimMarker = "... runtime log trimmed to recent events ...\n"
    }
}
