package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import java.io.File

internal class RuntimeEventLog(
    file: File,
    maxBytes: Int = TrimmedAppendOnlyLog.DefaultMaxBytes,
    trimToBytes: Int = TrimmedAppendOnlyLog.DefaultTrimToBytes,
) : TrimmedAppendOnlyLog(
    file = file,
    maxBytes = maxBytes,
    trimToBytes = trimToBytes,
    trimMarker = RuntimeLogTrimMarker,
    emptyMessage = "No runtime event log recorded.",
),
    RuntimeEventLogPort {
    override fun append(
        event: String,
        nowMillis: Long,
    ) {
        appendAndTrim("t=$nowMillis ${event.oneLine()}")
    }

    fun append(event: String) {
        append(event, System.currentTimeMillis())
    }

    private fun String.oneLine(): String =
        replace('\n', ' ')
            .replace('\r', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

    companion object {
        const val FileName: String = "runtime_event_log.txt"
        private const val RuntimeLogTrimMarker = "... runtime log trimmed to recent events ...\n"
    }
}
