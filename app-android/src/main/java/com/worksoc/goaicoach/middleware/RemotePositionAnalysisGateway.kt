package com.worksoc.goaicoach.middleware

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.analysisFingerprint

/**
 * Read-only remote analysis spike.
 *
 * This gateway deliberately exposes only explicit position analysis. It does
 * not own a match, does not mutate local engine state, and does not implement
 * genmove/play/undo. That keeps remote rollout safe: the app can first compare
 * remote analysis quality/latency while local offline play remains unchanged.
 */
internal class RemotePositionAnalysisGateway(
    private val transport: RemotePositionAnalysisTransport,
) : PositionAnalysisGateway {
    override suspend fun analyze(request: PositionAnalysisRequest): PositionAnalysisResponse {
        val response = transport.analyze(
            RemotePositionAnalysisRequest(
                state = request.state,
                limit = request.limit,
                searchMode = request.searchMode,
                positionFingerprint = request.state.analysisFingerprint(),
            ),
        )
        return PositionAnalysisResponse(
            result = response.result,
            backend = PositionAnalysisBackend.Remote,
            diagnosticText = response.diagnosticText,
        )
    }
}

internal interface RemotePositionAnalysisTransport {
    suspend fun analyze(request: RemotePositionAnalysisRequest): RemotePositionAnalysisResponse
}

internal data class RemotePositionAnalysisRequest(
    val state: GameState,
    val limit: AnalysisLimit,
    val searchMode: EngineSearchMode,
    val positionFingerprint: String,
)

internal data class RemotePositionAnalysisResponse(
    val result: AnalysisResult,
    val diagnosticText: String? = null,
)
