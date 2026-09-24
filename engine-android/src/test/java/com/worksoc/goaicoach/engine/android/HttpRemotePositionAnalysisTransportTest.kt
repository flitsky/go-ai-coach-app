package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.enginecontract.EngineState
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.enginecontract.RemotePositionAnalysisRequest
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.domain.analysisFingerprint
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

class HttpRemotePositionAnalysisTransportTest {
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

    /**
     * Contract/regression test against `scripts/run-katago-remote-analysis-server.py`
     * (the macOS dev-time reference server, `REMOTE_ENGINE_AND_LAYERING.md`
     * Stage E-3). The response body below is a verbatim capture from that script
     * actually running against local KataGo (the app's own model), answering the
     * exact request this test sends — 13x13, max handicap (5), Japanese, komi 6.5,
     * White to move, 16 visits. Not a hand-written fixture. If the Python
     * server's JSON shape ever drifts from what this Kotlin codec expects, this
     * test is the tripwire; keep both sides in sync rather than editing this
     * fixture to make it pass.
     *
     * Re-captured 2026-09-24 (refactor backlog #65). The previous capture came from a
     * server that hard-coded `initialPlayer: "B"`: KataGo had analyzed *Black* to move
     * (`rootInfo.currentPlayer` B), and the server labeled Black's candidates (L8/L6,
     * about -64.6) as White's. These are White's own candidates (C3 and its symmetric
     * copies), with KataGo analyzing White to move.
     */
    @Test
    fun httpTransportParsesRealMacReferenceServerResponse() = runBlocking {
        val connection = FakeHttpURLConnection(
            url = URL("http://example.test/analyze"),
            responseBody = """
                {
                  "result": {
                    "status": {"state": "Ready", "message": "Remote position analysis complete."},
                    "candidates": [
                      {
                        "player": "White", "type": "play", "engineOrder": 0, "source": "EngineSearch",
                        "note": "KataGo JSON order 0", "point": "C3", "boardSize": 13,
                        "winRate": 7.073000000024088e-06, "scoreLead": -50.5504244, "pointLoss": 0.0,
                        "visits": 9, "policyPrior": 0.129473075
                      },
                      {
                        "player": "White", "type": "play", "engineOrder": 1, "source": "EngineSearch",
                        "note": "KataGo JSON order 1", "point": "C11", "boardSize": 13,
                        "winRate": 7.073000000024088e-06, "scoreLead": -50.5504244, "pointLoss": 0.0,
                        "visits": 9, "policyPrior": 0.129473075
                      },
                      {
                        "player": "White", "type": "play", "engineOrder": 2, "source": "EngineSearch",
                        "note": "KataGo JSON order 2", "point": "L3", "boardSize": 13,
                        "winRate": 7.073000000024088e-06, "scoreLead": -50.5504244, "pointLoss": 0.0,
                        "visits": 9, "policyPrior": 0.129473075
                      },
                      {
                        "player": "White", "type": "play", "engineOrder": 3, "source": "EngineSearch",
                        "note": "KataGo JSON order 3", "point": "L11", "boardSize": 13,
                        "winRate": 7.073000000024088e-06, "scoreLead": -50.5504244, "pointLoss": 0.0,
                        "visits": 9, "policyPrior": 0.129473075
                      },
                      {
                        "player": "White", "type": "play", "engineOrder": 4, "source": "EngineSearch",
                        "note": "KataGo JSON order 4", "point": "F7", "boardSize": 13,
                        "winRate": 5.331999999969028e-06, "scoreLead": -51.2534216, "pointLoss": 0.33506189999999947,
                        "visits": 4, "policyPrior": 0.0238203648
                      }
                    ],
                    "summary": "Remote (macOS reference server) analysis in 749ms, 5 candidate(s), rootVisits=17.",
                    "rootVisits": 17
                  },
                  "diagnosticText": "positionFingerprint=size=13|rules=Japanese|next=White|capturedB=0|capturedW=0|ko=none|koFor=none|stones=D10:B,K10:B,G7:B,D4:B,K4:B,|moves="
                }
            """.trimIndent(),
        )
        val transport = HttpRemotePositionAnalysisTransport(
            config = RemotePositionAnalysisHttpConfig(endpointUrl = "http://example.test/analyze", enabled = true),
            connectionFactory = object : RemotePositionAnalysisHttpConnectionFactory {
                override fun open(url: URL): HttpURLConnection = connection
            },
        )

        // 13x13, White to move, max handicap(5) already placed for Black — the
        // exact request this fixture was captured against (the request body this
        // test sends was dumped and POSTed to the server as-is).
        val handicapState = GameState.withHandicap(
            boardSize = BoardSize.Thirteen,
            ruleset = com.worksoc.goaicoach.shared.domain.Ruleset.Japanese,
            handicapCount = BoardSize.Thirteen.maxHandicapCount,
        )
        val response = transport.analyze(
            RemotePositionAnalysisRequest(
                state = handicapState,
                limit = AnalysisLimit(visits = 16, timeMillis = 5_000L, candidateCount = 5),
                searchMode = EngineSearchMode.JsonPositionAnalysis,
                positionFingerprint = handicapState.analysisFingerprint(),
            ),
        )

        assertEquals(EngineState.Ready, response.result.status.state)
        assertEquals(17, response.result.rootVisits)
        assertEquals(5, response.result.candidates.size)
        val best = response.result.candidates.first()
        assertEquals(StoneColor.White, best.move.player)
        assertEquals("C3", (best.move as Move.Play).coordinate.label(BoardSize.Thirteen))
        assertEquals(0, best.engineOrder)
        assertEquals(9, best.visits)
        // White is far behind against five handicap stones at komi 6.5 — about -51 for
        // White at 16 visits. The expected sign and magnitude, not a parsing artifact;
        // -40 leaves room for search noise if this fixture is ever re-captured.
        assertTrue((best.scoreLead ?: 0.0) < -40.0)
        assertTrue((best.winRate ?: 1.0) < 0.01)
        assertEquals(0.335, response.result.candidates.last().pointLoss ?: -1.0, 0.001)
    }
}

/**
 * 백로그 #19 — `encodeState`가 `komi`/`handicapCount`를 실어 보내는지 직접 확인한다.
 * 이 값이 빠지면 원격 서버는 덤/접바둑을 알 길이 없어 `DefaultKomi`(6.5)·맞바둑을 그냥
 * 가정한다(`scripts/run-katago-remote-analysis-server.py` 모듈 docstring이 이 gap을
 * 스스로 문서화해 두고 있었다) — 이어하기 덤 유실(#1)과 같은 함정이 같은 이유로 남는다.
 */
class RemotePositionAnalysisJsonCodecStateTest {
    @Test
    fun encodeStateCarriesNonDefaultKomi() {
        val state = GameState.empty(boardSize = BoardSize.Nine, komi = 0.5)

        val encoded = RemotePositionAnalysisJsonCodec.encodeState(state)

        assertEquals(0.5, encoded.getDouble("komi"), 0.0001)
    }

    @Test
    fun encodeStateCarriesAnotherNonDefaultKomi() {
        val state = GameState.empty(boardSize = BoardSize.Nine, komi = 7.5)

        val encoded = RemotePositionAnalysisJsonCodec.encodeState(state)

        assertEquals(7.5, encoded.getDouble("komi"), 0.0001)
    }

    @Test
    fun encodeStateCarriesHandicapCount() {
        val state = GameState.withHandicap(
            boardSize = BoardSize.Nine,
            ruleset = com.worksoc.goaicoach.shared.domain.Ruleset.Japanese,
            handicapCount = 2,
        )

        val encoded = RemotePositionAnalysisJsonCodec.encodeState(state)

        assertEquals(2, encoded.getInt("handicapCount"))
        // 접바둑 komi도 왕복해야 한다 — handicapCount만 살아남고 komi가 죽으면 반쪽짜리다.
        assertEquals(state.komi, encoded.getDouble("komi"), 0.0001)
    }

    @Test
    fun encodeStateDefaultsHandicapCountToZeroForANonHandicapGame() {
        val state = GameState.empty(boardSize = BoardSize.Nine)

        val encoded = RemotePositionAnalysisJsonCodec.encodeState(state)

        assertEquals(0, encoded.getInt("handicapCount"))
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
