package com.worksoc.goaicoach.persistence

import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
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
 * 실패는 조용히 사라지지 않게 세어 두고 [readText] 끝줄에 밝힌다. ⚠️ **인스턴스가 아니라 파일 경로 단위로
 * 센다**(정규화한 경로 문자열 — 심볼릭 링크는 풀지 않으므로 같은 파일은 같은 방식으로 만들어야 한다) — 진단 로그 화면처럼 같은 파일을 새 인스턴스로 열어 읽는 곳이 있어서, 인스턴스 필드로 세면 쓰는 쪽
 * 인스턴스만 알고 화면에는 영영 안 뜬다(#73 4차 검수가 재현했다). 세는 범위는 **프로세스 수명**이다 —
 * 매체가 실패한 상태에서는 그 매체에 남길 수 없다. 센 것은 "기록이 실패한 횟수"다: 줄은 써졌는데 뒤이은
 * 자르기가 실패한 경우도 한 번으로 센다.
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
    private val mediumKey: String = file.absoluteFile.normalize().path

    @Synchronized
    protected fun appendAndTrim(line: String) {
        try {
            file.parentFile?.mkdirs()
            file.appendText("$line\n", Charsets.UTF_8)
            trimIfNeeded()
        } catch (mediumFailure: IOException) {
            recordMediumFailure()
        } catch (mediumFailure: SecurityException) {
            recordMediumFailure()
        }
    }

    @Synchronized
    fun readText(): String {
        val body = if (file.isFile) {
            file.readText(Charsets.UTF_8)
        } else {
            emptyMessage
        }
        val failures = mediumFailureCounts[mediumKey] ?: 0
        return if (failures == 0) {
            body
        } else {
            body.trimEnd('\n') + "\n" + MediumFailureNotePrefix + failures + "\n"
        }
    }

    // merge는 키 단위로 원자적이다 — 다른 인스턴스의 clear(remove)와 겹쳐도 센 것이 맵 밖으로 새지 않는다.
    private fun recordMediumFailure() {
        mediumFailureCounts.merge(mediumKey, 1, Int::plus)
    }

    @Synchronized
    fun clear() {
        if (file.isFile) {
            file.delete()
        }
        mediumFailureCounts.remove(mediumKey)
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

        /** 매체 실패가 있었을 때 [readText] 끝줄의 머리말. 뒤에 이 실행에서 기록이 실패한 횟수가 붙는다. */
        const val MediumFailureNotePrefix: String = "⚠️ 이 실행에서 저장 매체 오류로 기록이 실패한 횟수: "

        /** 정규화한 파일 경로 → 이 프로세스에서 기록이 실패한 횟수. 같은 파일을 여는 모든 인스턴스가 함께 본다. */
        private val mediumFailureCounts = ConcurrentHashMap<String, Int>()
    }
}
