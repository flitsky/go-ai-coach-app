package com.worksoc.goaicoach.middleware

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.GameState

/**
 * Middleware boundary for read-only position analysis.
 *
 * Implementations may call a local process engine, reuse cache, or call a
 * remote analysis service. The contract intentionally uses only shared DTOs so
 * it can move to a KMP middleware module without pulling Android/UI concerns.
 */
internal interface PositionAnalysisGateway {
    suspend fun analyze(request: PositionAnalysisRequest): PositionAnalysisResponse
}

internal data class PositionAnalysisRequest(
    val state: GameState,
    val limit: AnalysisLimit,
    val searchMode: EngineSearchMode,
)

internal data class PositionAnalysisResponse(
    val result: AnalysisResult,
    val backend: PositionAnalysisBackend,
    val diagnosticText: String? = null,
)

internal enum class PositionAnalysisBackend(
    val label: String,
) {
    LocalEngine("local-engine"),
    LocalCache("local-cache"),
    TrustedCache("trusted-cache"),
    Remote("remote"),
}
