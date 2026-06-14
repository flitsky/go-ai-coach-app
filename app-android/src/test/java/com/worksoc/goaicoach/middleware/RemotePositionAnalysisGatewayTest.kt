package com.worksoc.goaicoach.middleware

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.EngineState
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.analysisFingerprint
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

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

    @Test
    fun httpTransportIsDisabledByDefault() = runBlocking {
        val transport = HttpRemotePositionAnalysisTransport(
            config = RemotePositionAnalysisHttpConfig(endpointUrl = "http://example.test/analyze"),
        )

        try {
            transport.analyze(
                RemotePositionAnalysisRequest(
                    state = GameState.empty(),
                    limit = AnalysisLimit(visits = 16),
                    searchMode = EngineSearchMode.JsonPositionAnalysis,
                    positionFingerprint = "fingerprint",
                ),
            )
            fail("Disabled HTTP transport should reject remote calls.")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("disabled"))
        }
    }

    @Test
    fun httpTransportPostsPositionAndParsesAnalysisResponse() = runBlocking {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val connection = FakeHttpURLConnection(
            url = URL("http://example.test/analyze"),
            responseBody = """
                {
                  "diagnosticText": "remote elapsed=42ms",
                  "result": {
                    "status": {"state": "Ready", "message": "remote ready"},
                    "summary": "remote json complete",
                    "rootVisits": 32,
                    "candidates": [
                      {
                        "type": "play",
                        "player": "Black",
                        "point": "D4",
                        "boardSize": 9,
                        "winRate": 0.61,
                        "scoreLead": -0.5,
                        "pointLoss": 0.0,
                        "visits": 32,
                        "policyPrior": 0.25,
                        "engineOrder": 0,
                        "source": "EngineSearch",
                        "note": "remote best"
                      }
                    ]
                  }
                }
            """.trimIndent(),
        )
        val transport = HttpRemotePositionAnalysisTransport(
            config = RemotePositionAnalysisHttpConfig(
                endpointUrl = "http://example.test/analyze",
                enabled = true,
                connectTimeoutMillis = 111,
                readTimeoutMillis = 222,
            ),
            connectionFactory = object : RemotePositionAnalysisHttpConnectionFactory {
                override fun open(url: URL): HttpURLConnection {
                    assertEquals("http://example.test/analyze", url.toString())
                    return connection
                }
            },
        )

        val response = transport.analyze(
            RemotePositionAnalysisRequest(
                state = state,
                limit = AnalysisLimit(visits = 32, timeMillis = 2_000L, candidateCount = 10),
                searchMode = EngineSearchMode.JsonPositionAnalysis,
                positionFingerprint = state.analysisFingerprint(),
            ),
        )

        assertEquals("POST", connection.requestMethod)
        assertEquals(111, connection.connectTimeout)
        assertEquals(222, connection.readTimeout)
        assertEquals("application/json; charset=utf-8", connection.recordedRequestProperties["Content-Type"])
        val requestJson = JSONObject(connection.requestBodyString)
        assertEquals(state.analysisFingerprint(), requestJson.getString("positionFingerprint"))
        assertEquals("JsonPositionAnalysis", requestJson.getString("searchMode"))
        assertEquals(32, requestJson.getJSONObject("limit").getInt("visits"))
        assertEquals("E5", requestJson.getJSONObject("state").getJSONArray("stones").getJSONObject(0).getString("point"))

        assertEquals("remote elapsed=42ms", response.diagnosticText)
        assertEquals(EngineState.Ready, response.result.status.state)
        assertEquals("remote json complete", response.result.summary)
        assertEquals(32, response.result.rootVisits)
        assertEquals(1, response.result.candidates.size)
        assertEquals("D4", (response.result.candidates.single().move as Move.Play).coordinate.label(BoardSize.Nine))
        assertEquals(0.0, response.result.candidates.single().pointLoss ?: -1.0, 0.0001)
        assertNotNull(connection.requestBodyString)
        assertTrue(connection.disconnected)
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

private class FakeHttpURLConnection(
    url: URL,
    private val responseBody: String,
    private val statusCode: Int = 200,
) : HttpURLConnection(url) {
    private val output = ByteArrayOutputStream()
    val recordedRequestProperties = mutableMapOf<String, String>()
    var disconnected = false
        private set
    val requestBodyString: String
        get() = output.toString(Charsets.UTF_8.name())

    override fun setRequestProperty(
        key: String,
        value: String,
    ) {
        recordedRequestProperties[key] = value
    }

    override fun getOutputStream(): ByteArrayOutputStream = output

    override fun getInputStream(): InputStream =
        ByteArrayInputStream(responseBody.toByteArray(Charsets.UTF_8))

    override fun getResponseCode(): Int = statusCode

    override fun disconnect() {
        disconnected = true
    }

    override fun usingProxy(): Boolean = false

    override fun connect() = Unit
}
