package com.worksoc.goaicoach.persistence

import java.io.File
import kotlin.math.min

/**
 * append-only, 크기 제한이 있는 로그 파일의 공통 구현 — [DiagnosticEventLog]와
 * [RuntimeEventLog]가 회전(rotation) 정책을 공유한다. 파일이 [maxBytes]를 넘으면 뒤쪽(최근
 * 기록)만 [trimToBytes]만큼 남기고 앞부분을 잘라내며 그 자리에 [trimMarker]를 남긴다. 실제 한
 * 줄을 어떤 포맷으로 쓸지(JSON이냐 평문이냐)는 하위 클래스가 [appendAndTrim]을 통해 결정한다.
 */
internal abstract class TrimmedAppendOnlyLog(
    private val file: File,
    private val maxBytes: Int,
    private val trimToBytes: Int,
    private val trimMarker: String,
    private val emptyMessage: String,
) {
    @Synchronized
    protected fun appendAndTrim(line: String) {
        file.parentFile?.mkdirs()
        file.appendText("$line\n", Charsets.UTF_8)
        trimIfNeeded()
    }

    @Synchronized
    fun readText(): String =
        if (file.isFile) {
            file.readText(Charsets.UTF_8)
        } else {
            emptyMessage
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
        val marker = trimMarker.toByteArray(Charsets.UTF_8)
        val keepLength = min((trimToBytes - marker.size).coerceAtLeast(0), bytes.size)
        file.outputStream().use { output ->
            output.write(marker)
            output.write(bytes, bytes.size - keepLength, keepLength)
        }
    }

    companion object {
        const val DefaultMaxBytes: Int = 1_048_576
        const val DefaultTrimToBytes: Int = 921_600
    }
}
