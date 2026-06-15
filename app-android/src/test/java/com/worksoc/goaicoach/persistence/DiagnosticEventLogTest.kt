package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticSeverity
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticEventLogTest {
    @Test
    fun appendStoresJsonLines() {
        val file = tempLogFile()
        val log = DiagnosticEventLog(file, maxBytes = 1024, trimToBytes = 768)

        log.append(
            DiagnosticEvent(
                severity = DiagnosticSeverity.Warning,
                code = "engine.visit_fill_short",
                message = "short visits",
                context = mapOf("requestedVisits" to "32", "rootVisits" to "12"),
            ),
            nowMillis = 1234L,
        )

        val text = log.readText()
        assertTrue(text.contains(""""t":1234"""))
        assertTrue(text.contains(""""severity":"warning""""))
        assertTrue(text.contains(""""code":"engine.visit_fill_short""""))
        assertTrue(text.contains(""""rootVisits":"12""""))
    }

    @Test
    fun appendTrimsToRecentEventsWhenFileExceedsLimit() {
        val file = tempLogFile()
        val log = DiagnosticEventLog(file, maxBytes = 300, trimToBytes = 220)

        repeat(30) { index ->
            log.append(
                DiagnosticEvent(
                    severity = DiagnosticSeverity.Warning,
                    code = "event_$index",
                    message = "payload ${"x".repeat(40)}",
                ),
                nowMillis = index.toLong(),
            )
        }

        val text = log.readText()
        assertTrue(file.length() <= 300)
        assertTrue(text.contains("diagnostic log trimmed"))
        assertTrue(text.contains("event_29"))
        assertFalse(text.contains("event_0"))
    }

    @Test
    fun clearDeletesLogFile() {
        val file = tempLogFile()
        val log = DiagnosticEventLog(file)
        log.append(
            DiagnosticEvent(
                severity = DiagnosticSeverity.Info,
                code = "test.event",
                message = "test",
            ),
            nowMillis = 1L,
        )

        log.clear()

        assertFalse(file.exists())
        assertTrue(log.readText().contains("No diagnostic event log"))
    }

    private fun tempLogFile(): File =
        createTempDirectory("go-coach-diagnostic-log").toFile().resolve(DiagnosticEventLog.FileName)
}
