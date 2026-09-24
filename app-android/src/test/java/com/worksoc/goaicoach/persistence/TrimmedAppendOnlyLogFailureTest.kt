package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticSeverity
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 관측(로그·진단) 포트의 쓰기는 **부르는 쪽의 흐름을 바꾸면 안 된다**(docs/ARCHITECTURE.md 「관측 포트」,
 * refactor backlog #73). 저장 공간이 차는 등 매체가 실패해도 예외가 새어 나가면, 엔진 오퍼레이션의
 * `markStarted`처럼 기록 **뒤에** 정리가 오는 경로에서 busy 상태가 굳는다. 그래서 매체 실패는 구현이
 * 삼키고, 버린 줄 수는 [TrimmedAppendOnlyLog.readText] 끝줄에 밝힌다 — 조용히 사라지지 않게.
 */
class TrimmedAppendOnlyLogFailureTest {
    private val roots = mutableListOf<File>()

    @After
    fun deleteTempRoots() {
        roots.forEach { it.deleteRecursively() }
    }

    @Test
    fun runtimeLogAppendDoesNotThrowWhenTheMediumFails() {
        val log = RuntimeEventLog(unwritableLogFile(RuntimeEventLog.FileName), maxBytes = 1024, trimToBytes = 768)

        log.append("event_a", nowMillis = 1L)
        log.append("event_b", nowMillis = 2L)

        val text = log.readText()
        assertTrue(text, text.contains("${TrimmedAppendOnlyLog.DroppedLinesNotePrefix}2"))
    }

    @Test
    fun diagnosticLogAppendDoesNotThrowWhenTheMediumFails() {
        val log = DiagnosticEventLog(unwritableLogFile(DiagnosticEventLog.FileName), maxBytes = 1024, trimToBytes = 768)

        log.append(
            DiagnosticEvent(
                severity = DiagnosticSeverity.Warning,
                code = "test.medium_gone",
                message = "medium is gone",
            ),
            nowMillis = 1L,
        )

        val text = log.readText()
        assertTrue(text, text.contains("${TrimmedAppendOnlyLog.DroppedLinesNotePrefix}1"))
    }

    @Test
    fun healthyLogReportsNoDroppedLines() {
        val root = createTempDirectory("go-coach-log-ok").toFile().also(roots::add)
        val log = RuntimeEventLog(root.resolve(RuntimeEventLog.FileName), maxBytes = 1024, trimToBytes = 768)

        log.append("event_a", nowMillis = 1L)

        assertTrue(!log.readText().contains(TrimmedAppendOnlyLog.DroppedLinesNotePrefix))
    }

    /** 부모 경로 자리에 **일반 파일**을 두어 디렉터리를 만들 수 없게 한다 — 쓰기가 IOException으로 실패한다. */
    private fun unwritableLogFile(name: String): File {
        val root = createTempDirectory("go-coach-log-fail").toFile().also(roots::add)
        val blocker = root.resolve("logs").apply { writeText("not a directory") }
        return blocker.resolve(name)
    }
}
