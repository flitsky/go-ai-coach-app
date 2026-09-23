package com.worksoc.goaicoach.testsupport

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent

/**
 * [RuntimeEventLogPort] 페이크 — append 된 것을 시각까지 그대로 쌓는다.
 *
 * ## 왜 [entries]와 [events]가 둘 다 있는가
 * 통합 전 이 페이크는 **두 갈래로 갈라져 있었다.**
 * - 네 파일(`AutoAiEndgameRunnerTest`·`AutoAiScheduledTurnRunnerTest`·`AutoAiCompletionApplierTest`·
 *   `StartEngineBackedGameRunnerTest`)은 `nowMillis`를 **버리고** 문자열만 쌓았다.
 * - `RuntimeEventApplicationTest`만 `(이벤트, 시각)` 쌍으로 쌓아 **시각까지 단언**했다.
 *
 * 포트의 `append(event, nowMillis)`는 시각을 계약에 포함한다. 버리는 쪽은 **정보를 잃는 쪽**이므로
 * 쌓기는 쌍으로 하고([entries]), 시각을 보지 않는 테스트에는 문자열 뷰([events])를 준다.
 * 둘 중 하나로 조용히 합쳤다면 시각 단언 셋이 사라졌을 것이다.
 */
open class RecordingRuntimeEventLog : RuntimeEventLogPort {
    val entries: MutableList<Pair<String, Long>> = mutableListOf()

    /** 시각을 보지 않는 테스트용 뷰. */
    val events: List<String> get() = entries.map { it.first }

    override fun append(
        event: String,
        nowMillis: Long,
    ) {
        entries += event to nowMillis
    }

    override fun readText(): String = events.joinToString("\n")

    override fun clear() {
        entries.clear()
    }
}

/**
 * 읽기만 하는 쪽(디버그 리포트 조립 등)을 위한 [RuntimeEventLogPort] 페이크 —
 * `append`는 버리고 [readText]가 미리 정한 문자열을 돌려준다.
 */
class CannedRuntimeEventLog(
    private val text: String = "",
) : RuntimeEventLogPort {
    override fun append(
        event: String,
        nowMillis: Long,
    ) = Unit

    override fun readText(): String = text

    override fun clear() = Unit
}

/**
 * [DiagnosticEventLogPort] 페이크 — append 된 것을 시각까지 그대로 쌓는다.
 *
 * ## 통합하며 드러난 두 갈래
 * - `EngineSessionTest`·`EndgameResolverTest`: `DiagnosticEvent`만 쌓고 `nowMillis`를 버렸다.
 * - `RuntimeEventApplicationTest`: `(이벤트, 시각)` 쌍으로 쌓고 시각을 단언했다.
 *
 * [RecordingRuntimeEventLog]와 같은 이유로 **쌍이 정본**이고 [events]는 뷰다.
 *
 * ## [readText]가 `""`가 아닌 이유
 * `EndgameResolverTest`의 페이크만 [readText]가 `""`였다. 프로덕션 구현은 쌓인 로그를 돌려주므로
 * `""`는 계약을 지키지 않는 축약이었고, 그 테스트는 [readText]를 단언하지 않아 무해했을 뿐이다.
 * 여기서는 프로덕션 쪽 — 쌓인 것의 요약을 이어 붙인다 — 으로 맞춘다.
 */
open class RecordingDiagnosticEventLog : DiagnosticEventLogPort {
    val entries: MutableList<Pair<DiagnosticEvent, Long>> = mutableListOf()

    /** 시각을 보지 않는 테스트용 뷰. */
    val events: List<DiagnosticEvent> get() = entries.map { it.first }

    override fun append(
        event: DiagnosticEvent,
        nowMillis: Long,
    ) {
        entries += event to nowMillis
    }

    override fun readText(): String = events.joinToString("\n") { event -> event.summary() }

    override fun clear() {
        entries.clear()
    }
}

/**
 * 읽기만 하는 쪽을 위한 [DiagnosticEventLogPort] 페이크.
 *
 * ⚠️ 아무것도 읽지 않는 순수 no-op이 필요하면 **프로덕션의
 * `com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog`를 그대로 써라.**
 * 테스트가 그것을 두 번 재구현하고 있었다.
 */
class CannedDiagnosticEventLog(
    private val text: String = "",
) : DiagnosticEventLogPort {
    override fun append(
        event: DiagnosticEvent,
        nowMillis: Long,
    ) = Unit

    override fun readText(): String = text

    override fun clear() = Unit
}
