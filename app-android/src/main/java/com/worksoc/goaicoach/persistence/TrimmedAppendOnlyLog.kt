package com.worksoc.goaicoach.persistence

import java.io.File
import java.io.IOException
import kotlin.math.min

/**
 * append-only, 크기 제한이 있는 로그 파일의 공통 구현 — [DiagnosticEventLog]와
 * [RuntimeEventLog]가 회전(rotation) 정책을 공유한다. 파일이 [maxBytes]를 넘으면 뒤쪽(최근
 * 기록)만 [trimToBytes]만큼 남기고 앞부분을 잘라내며 그 자리에 [trimMarker]를 남긴다. 실제 한
 * 줄을 어떤 포맷으로 쓸지(JSON이냐 평문이냐)는 하위 클래스가 [appendAndTrim]을 통해 결정한다.
 *
 * ⚠️ **매체 실패는 여기서 삼킨다**(refactor backlog #73, docs/ARCHITECTURE.md 「관측 포트」).
 * 두 하위 클래스는 관측 포트의 구현이고, 관측 포트의 쓰기는 **부르는 쪽의 흐름을 바꾸면 안 된다.**
 * 예전엔 저장 공간이 차는 등으로 쓰기가 실패하면 `IOException`이 부르는 쪽으로 그대로 올라갔고,
 * 엔진 오퍼레이션의 `markStarted`처럼 기록 **뒤에** 정리가 오는 경로에서는 busy 상태가 굳을 수 있었다.
 * 버린 줄은 조용히 사라지지 않게 세어 두고 [readText] 끝줄에 밝힌다.
 * 삼키는 것은 매체 실패(`IOException`·`SecurityException`)뿐이다 — 인코딩 버그 같은 프로그래밍
 * 오류는 테스트에서 드러나야 하므로 그대로 올라간다.
 */
internal abstract class TrimmedAppendOnlyLog(
    private val file: File,
    private val maxBytes: Int,
    private val trimToBytes: Int,
    private val trimMarker: String,
    private val emptyMessage: String,
) {
    private var droppedLineCount = 0

    @Synchronized
    protected fun appendAndTrim(line: String) {
        try {
            file.parentFile?.mkdirs()
            file.appendText("$line\n", Charsets.UTF_8)
            trimIfNeeded()
        } catch (mediumFailure: IOException) {
            droppedLineCount += 1
        } catch (mediumFailure: SecurityException) {
            droppedLineCount += 1
        }
    }

    @Synchronized
    fun readText(): String {
        val body = if (file.isFile) {
            file.readText(Charsets.UTF_8)
        } else {
            emptyMessage
        }
        return if (droppedLineCount == 0) {
            body
        } else {
            body.trimEnd('\n') + "\n" + DroppedLinesNotePrefix + droppedLineCount + "\n"
        }
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

        /** 매체 실패로 버린 줄이 있을 때 [readText] 끝줄의 머리말. 뒤에 버린 줄 수가 붙는다. */
        const val DroppedLinesNotePrefix: String = "⚠️ 저장 매체 오류로 기록하지 못한 줄: "
    }
}
