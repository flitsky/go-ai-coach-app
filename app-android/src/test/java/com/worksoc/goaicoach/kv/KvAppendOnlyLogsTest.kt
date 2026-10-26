package com.worksoc.goaicoach.kv

import com.worksoc.goaicoach.persistence.DiagnosticEventLog
import com.worksoc.goaicoach.persistence.DiagnosticEventLogCodec
import com.worksoc.goaicoach.persistence.RuntimeEventLog
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticSeverity
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * iOS의 진단 로그 · 실행 기록(백로그 #294 — `KvAppendOnlyLogs.kt`)이 안드로이드 파일 로그와 **같은 파일 · 같은 줄**을 쓰는지.
 * 진단 리포트를 읽는 쪽(개발자 메뉴 · 리포트)은 하나라, 두 플랫폼의 로그가 같은 모양이어야 같은 눈으로 읽힌다.
 * ⚠️ JSON 줄은 키 순서를 빼고 견준다 — JVM 시험의 org.json(Maven)은 해시 순서라 기기의 안드로이드 org.json과 다르다([assertSameJson]).
 */
class KvAppendOnlyLogsTest {
    private val event = DiagnosticEvent(
        severity = DiagnosticSeverity.Warning,
        code = "engine.visit_fill_short",
        message = "short \"visits\" / 한글",
        context = mapOf("rootVisits" to "12", "requestedVisits" to "32"),
    )

    @Test
    fun theDiagnosticLineIsTheAndroidLine() {
        assertSameJson(DiagnosticEventLogCodec.encodeLine(event, 1234L), KvDiagnosticEventLogCodec.encodeLine(event, 1234L))
        assertEquals(DiagnosticEventLog.FileName, KvDiagnosticEventLog.FileName)
        assertEquals(RuntimeEventLog.FileName, KvRuntimeEventLog.FileName)
    }

    @Test
    fun bothLogsWriteTheSameFiles() {
        val androidDir = createTempDirectory("android-logs").toFile()
        val kvDir = createTempDirectory("kv-logs").toFile()
        val android = DiagnosticEventLog(androidDir.resolve(DiagnosticEventLog.FileName))
        val kv = KvDiagnosticEventLog(JvmAppFileStore(kvDir))
        val androidRuntime = RuntimeEventLog(androidDir.resolve(RuntimeEventLog.FileName))
        val kvRuntime = KvRuntimeEventLog(JvmAppFileStore(kvDir))
        listOf(1L, 2L).forEach { t ->
            android.append(event, t)
            kv.append(event, t)
            androidRuntime.append("turn\n  ended   gen=3", t)
            kvRuntime.append("turn\n  ended   gen=3", t)
        }
        val androidLines = android.readText().lines().filter(String::isNotBlank)
        val kvLines = kv.readText().lines().filter(String::isNotBlank)
        assertEquals(androidLines.size, kvLines.size)
        androidLines.zip(kvLines).forEach { (expected, actual) -> assertSameJson(expected, actual) }
        assertEquals(androidRuntime.readText(), kvRuntime.readText())
        kv.clear()
        assertTrue(kv.readText().contains("No diagnostic event log"))
    }

    @Test
    fun theLogIsTrimmedToRecentEventsUnderTheMarker() {
        val dir = createTempDirectory("kv-trim").toFile()
        val log = object : KvTrimmedAppendOnlyLog(
            files = JvmAppFileStore(dir),
            path = "trim.txt",
            trimMarker = "... trimmed ...\n",
            emptyMessage = "empty",
            maxBytes = 300,
            trimToBytes = 220,
        ) {
            fun add(line: String) = appendAndTrim(line)
        }
        repeat(30) { index -> log.add("event_$index ${"x".repeat(40)}") }
        val text = log.readText()
        assertTrue(dir.resolve("trim.txt").length() <= 300)
        assertTrue(text.startsWith("... trimmed ..."))
        assertTrue(text.contains("event_29"))
        assertFalse(text.contains("event_0 "))
    }
}
