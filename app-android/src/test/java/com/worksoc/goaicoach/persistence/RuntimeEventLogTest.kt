package com.worksoc.goaicoach.persistence

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeEventLogTest {
    @Test
    fun appendStoresOneLineEvents() {
        val file = tempLogFile()
        val log = RuntimeEventLog(file, maxBytes = 1024, trimToBytes = 768)

        log.append("event\nwith\rspaces", nowMillis = 1234L)

        assertTrue(log.readText().contains("t=1234 event with spaces"))
    }

    @Test
    fun appendTrimsToRecentEventsWhenFileExceedsLimit() {
        val file = tempLogFile()
        val log = RuntimeEventLog(file, maxBytes = 240, trimToBytes = 160)

        repeat(30) { index ->
            log.append("event_$index payload=${"x".repeat(30)}", nowMillis = index.toLong())
        }

        val text = log.readText()
        assertTrue(file.length() <= 240)
        assertTrue(text.contains("runtime log trimmed"))
        assertTrue(text.contains("event_29"))
        assertFalse(text.contains("event_0"))
    }

    private fun tempLogFile(): File =
        createTempDirectory("go-coach-runtime-log").toFile().resolve(RuntimeEventLog.FileName)
}
