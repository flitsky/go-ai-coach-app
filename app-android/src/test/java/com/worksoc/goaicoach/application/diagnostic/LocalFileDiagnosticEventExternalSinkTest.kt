package com.worksoc.goaicoach.application.diagnostic

import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEventExternalExportPayload
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticSeverity
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * app-android의 파일 싱크([LocalFileDiagnosticEventExternalSink])가 내보내기 페이로드를 JSON 한 줄로 붙인다.
 *
 * `DiagnosticEventApplicationTest`에 있던 테스트다 — 그 파일이 `:shared`의 commonTest로 옮겨 갈 때
 * (refactor backlog #97) 이 하나는 app-android의 `internal` 싱크와 JVM 파일 API를 불러서 따라갈 수 없어 남았다.
 */
class LocalFileDiagnosticEventExternalSinkTest {
    @Test
    fun localFileDiagnosticExternalSinkAppendsJsonLine() {
        val file = createTempDirectory("go-coach-diagnostic-export")
            .toFile()
            .resolve("diagnostic-export.jsonl")
        val event = DiagnosticEvent(
            severity = DiagnosticSeverity.Critical,
            code = "score.final_disagreement",
            message = "score mismatch",
            context = mapOf("engineFinalScore" to "W+2", "localScore" to "B+10"),
        )
        val sink = LocalFileDiagnosticEventExternalSink(
            file = file,
            currentTimeMillis = { 12_345L },
        )

        val result = sink.send(
            DiagnosticEventExternalExportPayload(
                event = event,
                debugReportText = "debug\nreport",
            ),
        )

        assertTrue(result.isSuccess)
        val text = file.readText()
        assertTrue(text.contains("\"t\":12345"))
        assertTrue(text.contains("\"severity\":\"critical\""))
        assertTrue(text.contains("\"code\":\"score.final_disagreement\""))
        assertTrue(text.contains("\"localScore\":\"B+10\""))
        assertTrue(text.contains("\"debugReportText\":\"debug\\nreport\""))
    }
}
