package com.worksoc.goaicoach.middleware

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.analysisFingerprint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePositionAnalysisGatewayTest {
    @Test
    fun remoteGatewayForwardsExplicitPositionAnalysisRequest() = runBlocking {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val limit = AnalysisLimit(visits = 32, timeMillis = 2_000L, candidateCount = 10)
        val result = AnalysisResult(
            status = EngineStatus.ready("remote ready"),
            candidates = emptyList(),
            summary = "remote summary",
            rootVisits = 32,
        )
        val transport = RecordingRemotePositionAnalysisTransport(
            response = RemotePositionAnalysisResponse(
                result = result,
                diagnosticText = "remote elapsed=1200ms",
            ),
        )
        val gateway = RemotePositionAnalysisGateway(transport)

        val response = gateway.analyze(
            PositionAnalysisRequest(
                state = state,
                limit = limit,
                searchMode = EngineSearchMode.JsonPositionAnalysis,
            ),
        )

        assertEquals(PositionAnalysisBackend.Remote, response.backend)
        assertEquals(result, response.result)
        assertEquals("remote elapsed=1200ms", response.diagnosticText)
        assertEquals(state, transport.request?.state)
        assertEquals(limit, transport.request?.limit)
        assertEquals(EngineSearchMode.JsonPositionAnalysis, transport.request?.searchMode)
        assertEquals(state.analysisFingerprint(), transport.request?.positionFingerprint)
        assertTrue(transport.request?.positionFingerprint.orEmpty().isNotBlank())
    }
}

private class RecordingRemotePositionAnalysisTransport(
    private val response: RemotePositionAnalysisResponse,
) : RemotePositionAnalysisTransport {
    var request: RemotePositionAnalysisRequest? = null
        private set

    override suspend fun analyze(request: RemotePositionAnalysisRequest): RemotePositionAnalysisResponse {
        this.request = request
        return response
    }
}
