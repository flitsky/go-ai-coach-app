package com.worksoc.goaicoach.application.diagnostic

import com.worksoc.goaicoach.application.EngineOperationRequest
import com.worksoc.goaicoach.application.EngineOperationResultGuard
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.analysisFingerprint
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticSeverity

internal fun engineVisitFillDiagnosticEvent(
    requestedVisits: Int,
    rootVisits: Int?,
    searchMode: String,
    positionFingerprint: String,
): DiagnosticEvent? {
    require(requestedVisits > 0) { "requestedVisits must be positive" }

    if (rootVisits == null) {
        return DiagnosticEvent(
            severity = DiagnosticSeverity.Warning,
            code = "engine.visit_fill_unknown",
            message = "Engine analysis did not report root visits.",
            context = mapOf(
                "requestedVisits" to requestedVisits.toString(),
                "searchMode" to searchMode,
                "positionFingerprint" to positionFingerprint,
            ),
        )
    }

    if (rootVisits >= requestedVisits) {
        return null
    }

    return DiagnosticEvent(
        severity = DiagnosticSeverity.Warning,
        code = "engine.visit_fill_short",
        message = "Engine analysis finished before requested root visits were filled.",
        context = mapOf(
            "requestedVisits" to requestedVisits.toString(),
            "rootVisits" to rootVisits.toString(),
            "searchMode" to searchMode,
            "positionFingerprint" to positionFingerprint,
        ),
    )
}

internal fun scoreDisagreementDiagnosticEvent(
    engineFinalScore: String,
    localScore: String,
    source: String,
): DiagnosticEvent =
    DiagnosticEvent(
        severity = DiagnosticSeverity.Critical,
        code = "score.final_disagreement",
        message = "Final score sources disagree.",
        context = mapOf(
            "engineFinalScore" to engineFinalScore,
            "localScore" to localScore,
            "source" to source,
        ),
    )

internal fun engineOperationSlowDiagnosticEvent(
    operation: String,
    elapsedMillis: Long,
    thresholdMillis: Long,
    positionFingerprint: String? = null,
): DiagnosticEvent? {
    require(operation.isNotBlank()) { "operation must not be blank" }
    require(elapsedMillis >= 0L) { "elapsedMillis must not be negative" }
    require(thresholdMillis > 0L) { "thresholdMillis must be positive" }

    if (elapsedMillis <= thresholdMillis) {
        return null
    }

    return DiagnosticEvent(
        severity = DiagnosticSeverity.Warning,
        code = "engine.operation.slow",
        message = "Engine operation exceeded the expected latency threshold.",
        context = buildMap {
            put("operation", operation)
            put("elapsedMillis", elapsedMillis.toString())
            put("thresholdMillis", thresholdMillis.toString())
            positionFingerprint?.let { put("positionFingerprint", it) }
        },
    )
}

internal fun engineOperationSlowDiagnosticEvent(
    request: EngineOperationRequest,
    elapsedMillis: Long,
    thresholdMillis: Long,
): DiagnosticEvent? {
    require(elapsedMillis >= 0L) { "elapsedMillis must not be negative" }
    require(thresholdMillis > 0L) { "thresholdMillis must be positive" }

    if (elapsedMillis <= thresholdMillis) {
        return null
    }

    return DiagnosticEvent(
        severity = DiagnosticSeverity.Warning,
        code = "engine.operation.slow",
        message = "Engine operation exceeded the expected latency threshold.",
        context = request.operationContext() + mapOf(
            "elapsedMillis" to elapsedMillis.toString(),
            "thresholdMillis" to thresholdMillis.toString(),
        ),
    )
}

internal fun engineOperationTimeoutDiagnosticEvent(
    operation: String,
    timeoutMillis: Long,
    positionFingerprint: String? = null,
): DiagnosticEvent {
    require(operation.isNotBlank()) { "operation must not be blank" }
    require(timeoutMillis > 0L) { "timeoutMillis must be positive" }

    return DiagnosticEvent(
        severity = DiagnosticSeverity.Critical,
        code = "engine.operation.timeout",
        message = "Engine operation timed out.",
        context = buildMap {
            put("operation", operation)
            put("timeoutMillis", timeoutMillis.toString())
            positionFingerprint?.let { put("positionFingerprint", it) }
        },
    )
}

internal fun engineOperationTimeoutDiagnosticEvent(
    request: EngineOperationRequest,
): DiagnosticEvent? {
    val timeoutMillis = request.timeoutPolicy.timeoutMillis ?: return null
    return DiagnosticEvent(
        severity = DiagnosticSeverity.Critical,
        code = "engine.operation.timeout",
        message = "Engine operation timed out.",
        context = request.operationContext() + mapOf(
            "timeoutMillis" to timeoutMillis.toString(),
        ),
    )
}

internal fun engineOperationDiscardedDiagnosticEvent(
    discard: EngineOperationResultGuard.Discard,
    currentState: GameState,
): DiagnosticEvent =
    DiagnosticEvent(
        severity = DiagnosticSeverity.Info,
        code = "engine.operation.discarded",
        message = "Late engine operation result was discarded.",
        context = buildMap {
            put("reason", discard.reason)
            discard.operation?.let { put("operation", it) }
            discard.operationId?.let { put("operationId", it) }
            discard.sessionGeneration?.let { put("sessionGeneration", it.toString()) }
            put("currentMoveCount", currentState.moves.size.toString())
            put("positionFingerprint", currentState.analysisFingerprint())
        },
    )

private fun EngineOperationRequest.operationContext(): Map<String, String> =
    mapOf(
        "operation" to kind.code,
        "operationId" to operationId,
        "sessionGeneration" to sessionGeneration.toString(),
        "positionFingerprint" to boardFingerprint,
        "moveCount" to moveCount.toString(),
        "backendId" to backendId,
        "timeoutPolicy" to timeoutPolicy.label,
        "fallbackPolicy" to fallbackPolicy.label,
    )
