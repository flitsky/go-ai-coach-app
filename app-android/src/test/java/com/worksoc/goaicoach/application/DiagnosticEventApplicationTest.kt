package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DiagnosticEventApplicationTest {
    @Test
    fun visitFillDiagnosticIsEmptyWhenRootVisitsMeetRequest() {
        val event = engineVisitFillDiagnosticEvent(
            requestedVisits = 32,
            rootVisits = 32,
            searchMode = "JsonPositionAnalysis",
            positionFingerprint = "fp",
        )

        assertNull(event)
    }

    @Test
    fun visitFillDiagnosticWarnsWhenRootVisitsAreShort() {
        val event = requireNotNull(
            engineVisitFillDiagnosticEvent(
                requestedVisits = 32,
                rootVisits = 12,
                searchMode = "JsonPositionAnalysis",
                positionFingerprint = "fp",
            ),
        )

        assertEquals(DiagnosticSeverity.Warning, event.severity)
        assertEquals("engine.visit_fill_short", event.code)
        assertEquals("12", event.context["rootVisits"])
        assertEquals("32", event.context["requestedVisits"])
    }

    @Test
    fun visitFillDiagnosticWarnsWhenRootVisitsAreUnknown() {
        val event = requireNotNull(
            engineVisitFillDiagnosticEvent(
                requestedVisits = 16,
                rootVisits = null,
                searchMode = "GtpStatefulFast",
                positionFingerprint = "fp",
            ),
        )

        assertEquals(DiagnosticSeverity.Warning, event.severity)
        assertEquals("engine.visit_fill_unknown", event.code)
        assertEquals("16", event.context["requestedVisits"])
    }

    @Test
    fun summaryNormalizesMultiLineValues() {
        val summary = DiagnosticEvent(
            severity = DiagnosticSeverity.Critical,
            code = "score.final_disagreement",
            message = "Final\nscore\rproblem",
            context = mapOf("localScore" to "B+10\n", "engineFinalScore" to "W+2"),
        ).summary()

        assertTrue(summary.contains("severity=critical"))
        assertTrue(summary.contains("message=Final score problem"))
        assertTrue(summary.contains("engineFinalScore=W+2,localScore=B+10"))
    }

    @Test
    fun slowOperationDiagnosticWarnsOnlyAboveThreshold() {
        assertNull(
            engineOperationSlowDiagnosticEvent(
                operation = "position_analysis",
                elapsedMillis = 900L,
                thresholdMillis = 1_000L,
            ),
        )

        val event = requireNotNull(
            engineOperationSlowDiagnosticEvent(
                operation = "position_analysis",
                elapsedMillis = 1_500L,
                thresholdMillis = 1_000L,
                positionFingerprint = "fp",
            ),
        )

        assertEquals(DiagnosticSeverity.Warning, event.severity)
        assertEquals("engine.operation.slow", event.code)
        assertEquals("position_analysis", event.context["operation"])
        assertEquals("1500", event.context["elapsedMillis"])
        assertEquals("1000", event.context["thresholdMillis"])
        assertEquals("fp", event.context["positionFingerprint"])
    }

    @Test
    fun timeoutDiagnosticIsCritical() {
        val event = engineOperationTimeoutDiagnosticEvent(
            operation = "final_score",
            timeoutMillis = 5_000L,
            positionFingerprint = "fp",
        )

        assertEquals(DiagnosticSeverity.Critical, event.severity)
        assertEquals("engine.operation.timeout", event.code)
        assertEquals("final_score", event.context["operation"])
        assertEquals("5000", event.context["timeoutMillis"])
        assertEquals("fp", event.context["positionFingerprint"])
    }

    @Test
    fun observedEngineOperationRecordsSlowEventWithOperationMetadata() = runBlocking {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val request = engineOperationRequest(
            kind = EngineOperationKind.PositionAnalysis,
            state = state,
            sessionGeneration = 2,
            timeoutPolicy = EngineTimeoutPolicy(timeoutMillis = 1_000L, label = "json:32v"),
            fallbackPolicy = EngineFallbackPolicy.CachedAnalysis,
            backendId = "local-engine",
        )
        val log = RecordingDiagnosticEventLogForDiagnostics()
        var nowMillis = 10_000L

        val result = runObservedEngineOperation(
            request = request,
            diagnosticEventLog = log,
            currentTimeMillis = { nowMillis },
        ) {
            nowMillis = 11_501L
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(1, log.events.size)
        val event = log.events.single()
        assertEquals("engine.operation.slow", event.code)
        assertEquals("position_analysis", event.context["operation"])
        assertEquals(request.operationId, event.context["operationId"])
        assertEquals("2", event.context["sessionGeneration"])
        assertEquals("1501", event.context["elapsedMillis"])
        assertEquals("1000", event.context["thresholdMillis"])
        assertEquals("cached-analysis", event.context["fallbackPolicy"])
    }

    @Test
    fun observedEngineOperationRecordsTimeoutEvent() = runBlocking {
        val request = engineOperationRequest(
            kind = EngineOperationKind.ScoreEstimate,
            state = GameState.empty(),
            sessionGeneration = 1,
            timeoutPolicy = EngineTimeoutPolicy(timeoutMillis = 10L),
            fallbackPolicy = EngineFallbackPolicy.LocalRules,
        )
        val log = RecordingDiagnosticEventLogForDiagnostics()

        try {
            withTimeout(10L) {
                runObservedEngineOperation(
                    request = request,
                    diagnosticEventLog = log,
                ) {
                    delay(1_000L)
                }
            }
            fail("Timeout should be rethrown after diagnostic logging.")
        } catch (_: TimeoutCancellationException) {
            // Expected.
        }

        assertEquals(1, log.events.size)
        val event = log.events.single()
        assertEquals("engine.operation.timeout", event.code)
        assertEquals("score_estimate", event.context["operation"])
        assertEquals("10", event.context["timeoutMillis"])
        assertEquals("local-rules", event.context["fallbackPolicy"])
    }

    @Test
    fun discardedOperationDiagnosticCapturesCurrentPosition() {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val event = engineOperationDiscardedDiagnosticEvent(
            discard = EngineOperationResultGuard.Discard("position result is stale"),
            currentState = state,
        )

        assertEquals(DiagnosticSeverity.Info, event.severity)
        assertEquals("engine.operation.discarded", event.code)
        assertEquals("position result is stale", event.context["reason"])
        assertEquals("1", event.context["currentMoveCount"])
        assertTrue(event.context["positionFingerprint"].orEmpty().isNotBlank())
    }
}

private class RecordingDiagnosticEventLogForDiagnostics : DiagnosticEventLogPort {
    val events = mutableListOf<DiagnosticEvent>()

    override fun append(
        event: DiagnosticEvent,
        nowMillis: Long,
    ) {
        events += event
    }

    override fun readText(): String =
        events.joinToString("\n") { event -> event.summary() }

    override fun clear() {
        events.clear()
    }
}
