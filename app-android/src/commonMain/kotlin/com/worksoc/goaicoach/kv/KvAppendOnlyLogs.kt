package com.worksoc.goaicoach.kv

import com.worksoc.goaicoach.application.concurrency.sharedLock
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.time.currentEpochMillis
import com.worksoc.goaicoach.kv.json.JSONObject
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent

/**
 * 안드로이드 `persistence/TrimmedAppendOnlyLog`의 [AppFileStore] 판(백로그 #294 — iOS의 진단 로그 · 실행 기록). 같은 파일 이름 · 같은 줄 형식 ·
 * 같은 상한(1MiB를 넘으면 머리에 표시 줄을 두고 뒤쪽 900KiB만 남긴다). 매체 실패 횟수는 이 인스턴스가 센다 — 앱이 파일마다 하나씩만 만든다.
 */
internal abstract class KvTrimmedAppendOnlyLog(
    private val files: AppFileStore,
    private val path: String,
    private val trimMarker: String,
    private val emptyMessage: String,
    private val maxBytes: Int = DefaultMaxBytes,
    private val trimToBytes: Int = DefaultTrimToBytes,
) {
    private val lock = sharedLock()
    private var mediumFailures = 0

    protected fun appendAndTrim(line: String) = lock.withLock {
        if (!files.appendText(path, "$line\n")) {
            mediumFailures++
            return@withLock
        }
        trimIfNeeded()
    }

    fun readText(): String = lock.withLock {
        val body = files.readText(path) ?: emptyMessage
        if (mediumFailures == 0) body else body.trimEnd('\n') + "\n" + MediumFailureNotePrefix + mediumFailures + "\n"
    }

    fun clear() = lock.withLock {
        files.delete(path)
        mediumFailures = 0
    }

    private fun trimIfNeeded() {
        if (files.length(path) <= maxBytes) return
        val bytes = files.readText(path)?.encodeToByteArray() ?: return
        val marker = trimMarker.encodeToByteArray()
        val keepLength = minOf((trimToBytes - marker.size).coerceAtLeast(0), bytes.size)
        // 바이트로 자르므로 첫 글자가 반쯤 잘릴 수 있다 — 안드로이드도 같다(읽을 때 대체 문자로 보인다).
        val kept = bytes.copyOfRange(bytes.size - keepLength, bytes.size).decodeToString()
        if (!files.writeAtomically(path, trimMarker + kept)) mediumFailures++
    }

    companion object {
        const val DefaultMaxBytes: Int = 1_048_576
        const val DefaultTrimToBytes: Int = 921_600

        /** 안드로이드와 같은 머리말 — 뒤에 이 실행에서 기록이 실패한 횟수가 붙는다. */
        const val MediumFailureNotePrefix: String = "⚠️ 이 실행에서 저장 매체 오류로 기록이 실패한 횟수: "
    }
}

/** 안드로이드 `DiagnosticEventLog`의 짝 — 같은 파일 이름(`diagnostic_events.jsonl`) · 같은 JSON 줄([KvDiagnosticEventLogCodec]). */
internal class KvDiagnosticEventLog(files: AppFileStore) :
    KvTrimmedAppendOnlyLog(
        files = files,
        path = FileName,
        trimMarker = """{"trimmed":true,"message":"diagnostic log trimmed to recent events"}""" + "\n",
        emptyMessage = "No diagnostic event log recorded.",
    ),
    DiagnosticEventLogPort {
    override fun append(event: DiagnosticEvent, nowMillis: Long) {
        appendAndTrim(KvDiagnosticEventLogCodec.encodeLine(event, nowMillis))
    }

    companion object {
        const val FileName: String = "diagnostic_events.jsonl"
    }
}

/** 안드로이드 `DiagnosticEventLogCodec`을 그대로 옮긴 것(org.json 대신 같은 동작의 [JSONObject]). */
internal object KvDiagnosticEventLogCodec {
    fun encodeLine(event: DiagnosticEvent, nowMillis: Long): String =
        JSONObject()
            .put("t", nowMillis)
            .put("severity", event.severity.label)
            .put("code", event.code)
            .put("message", event.message)
            .put(
                "context",
                JSONObject().also { contextJson ->
                    event.context.entries
                        .sortedBy { entry -> entry.key }
                        .forEach { entry -> contextJson.put(entry.key, entry.value) }
                },
            )
            .toString()
}

/** 안드로이드 `RuntimeEventLog`의 짝 — 같은 파일 이름(`runtime_event_log.txt`) · 같은 한 줄 형식(`t=<ms> <사건>`). */
internal class KvRuntimeEventLog(files: AppFileStore) :
    KvTrimmedAppendOnlyLog(
        files = files,
        path = FileName,
        trimMarker = "... runtime log trimmed to recent events ...\n",
        emptyMessage = "No runtime event log recorded.",
    ),
    RuntimeEventLogPort {
    override fun append(event: String, nowMillis: Long) {
        appendAndTrim("t=$nowMillis ${event.oneLine()}")
    }

    fun append(event: String) = append(event, currentEpochMillis())

    private fun String.oneLine(): String =
        replace('\n', ' ')
            .replace('\r', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

    companion object {
        const val FileName: String = "runtime_event_log.txt"
    }
}
