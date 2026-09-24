package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineState
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 계약 테스트(Stage D-2) — [RemoteEngineCoreApiAdapter]가 [KataGoProcessEngineAdapter]
 * (로컬)와 동일한 [com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi] 계약을 만족하는지 검증한다:
 * 상태만 바꾸는 호출은 네트워크를 타지 않는지, 실제 연산 호출은 그 시점의 전체 국면을 원격으로
 * 보내는지, 타임아웃 시 로컬의 강제 프로세스 재시작과 동등하게 연결을 강제로 끊는지.
 */
class RemoteEngineCoreApiAdapterTest {
    @Test
    fun statefulBookkeepingCallsNeverTouchTransport() = runBlocking {
        val adapter = RemoteEngineCoreApiAdapter(RejectingTransport())

        adapter.initialize(EngineProfile())
        adapter.configure(EngineProfile())
        adapter.newGame(BoardSize.Nine, Ruleset.Japanese)
        adapter.playMove(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        adapter.clearSearchCache()
        adapter.undoMove()
        adapter.stop()
        // RejectingTransport.execute()가 한 번이라도 호출되면 즉시 예외를 던지므로, 여기까지
        // 도달했다는 것 자체가 위 호출들이 네트워크를 타지 않았다는 증거다.
        Unit
    }

    @Test
    fun genMoveSendsTrackedStateAndAdvancesLocalHistoryForNextCall() = runBlocking {
        val recorded = mutableListOf<RemoteEngineOperationRequest>()
        val move = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine))
        val transport = RecordingTransport(recorded) {
            RemoteEngineOperationResponse(
                status = EngineStatus.ready("remote genMove ready"),
                summary = "remote genMove complete",
                move = move,
            )
        }
        val adapter = RemoteEngineCoreApiAdapter(transport)
        adapter.newGame(BoardSize.Nine, Ruleset.Japanese)

        val result = adapter.genMove(StoneColor.Black)

        assertEquals(RemoteEngineOperation.GenMove, recorded.single().operation)
        assertEquals(StoneColor.Black, recorded.single().player)
        assertTrue(recorded.single().state.moves.isEmpty())
        assertEquals(move, result.move)
        assertEquals("remote genMove complete", result.summary)

        // genMove가 로컬 이력을 갱신했으므로, 다음 호출(analyze)에는 방금 둔 수가 반영된
        // 국면이 실려 나가야 한다 — KataGoProcessEngineAdapter가 매 호출마다 playedMoves를
        // 갱신해 다음 컨텍스트에 반영하는 것과 동일한 계약.
        adapter.analyze(AnalysisLimit(visits = 16))
        assertEquals(1, recorded[1].state.moves.size)
        assertEquals(move, recorded[1].state.moves.single())
    }

    @Test
    fun genMoveWithoutRemoteMoveReturnsErrorStatusWithoutAdvancingHistory() = runBlocking {
        val recorded = mutableListOf<RemoteEngineOperationRequest>()
        val transport = RecordingTransport(recorded) {
            RemoteEngineOperationResponse(status = EngineStatus.error("no move"), summary = "no move")
        }
        val adapter = RemoteEngineCoreApiAdapter(transport)
        adapter.newGame(BoardSize.Nine, Ruleset.Japanese)

        val result = adapter.genMove(StoneColor.Black)

        assertEquals(EngineState.Error, result.status.state)

        adapter.analyze(AnalysisLimit(visits = 16))
        assertTrue(recorded[1].state.moves.isEmpty())
    }

    @Test
    fun undoMoveWithoutHistoryReturnsErrorStatus() = runBlocking {
        val adapter = RemoteEngineCoreApiAdapter(RejectingTransport())

        val status = adapter.undoMove()

        assertEquals(EngineState.Error, status.state)
    }

    @Test
    fun undoMoveRestoresStateSentToNextRemoteCall() = runBlocking {
        val recorded = mutableListOf<RemoteEngineOperationRequest>()
        val transport = RecordingTransport(recorded) {
            RemoteEngineOperationResponse(status = EngineStatus.ready("ready"), summary = "ready")
        }
        val adapter = RemoteEngineCoreApiAdapter(transport)
        adapter.newGame(BoardSize.Nine, Ruleset.Japanese)
        adapter.playMove(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))

        adapter.undoMove()
        adapter.analyze(AnalysisLimit(visits = 16))

        assertTrue(recorded.single().state.moves.isEmpty())
    }

    @Test
    fun forceResetDelegatesToTransportWithoutSuspending() {
        var abandoned = false
        val transport = object : RemoteEngineOperationTransport {
            override suspend fun execute(request: RemoteEngineOperationRequest): RemoteEngineOperationResponse =
                error("not used in this test")

            override fun abandonInFlightRequest() {
                abandoned = true
            }
        }
        val adapter = RemoteEngineCoreApiAdapter(transport)

        adapter.forceReset()

        assertTrue(abandoned)
    }

    @Test
    fun httpTransportIsDisabledByDefault() = runBlocking {
        val transport = HttpRemoteEngineOperationTransport(
            config = RemoteEngineHttpConfig(endpointUrl = "http://example.test/engine"),
        )

        try {
            transport.execute(sampleRequest())
            fail("Disabled HTTP transport should reject remote engine calls.")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("disabled"))
        }
    }

    @Test
    fun httpTransportPostsOperationAndParsesGenMoveResponse() = runBlocking {
        val connection = FakeEngineHttpURLConnection(
            url = URL("http://example.test/engine"),
            responseBody = """
                {
                  "result": {
                    "status": {"state": "Ready", "message": "remote ready"},
                    "summary": "remote genMove complete",
                    "move": {"type": "play", "player": "Black", "point": "D4", "boardSize": 9}
                  }
                }
            """.trimIndent(),
        )
        val transport = HttpRemoteEngineOperationTransport(
            config = RemoteEngineHttpConfig(
                endpointUrl = "http://example.test/engine",
                enabled = true,
            ),
            connectionFactory = object : RemotePositionAnalysisHttpConnectionFactory {
                override fun open(url: URL): HttpURLConnection = connection
            },
        )

        val response = transport.execute(sampleRequest(operation = RemoteEngineOperation.GenMove))

        val requestJson = JSONObject(connection.requestBodyString)
        assertEquals("genMove", requestJson.getString("operation"))
        assertEquals("Black", requestJson.getString("player"))
        assertEquals(EngineState.Ready, response.status.state)
        assertEquals(
            Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("D4", BoardSize.Nine)),
            response.move,
        )
        assertTrue(connection.disconnected)
    }

    @Test
    fun httpTransportParsesEstimateScoreDeadStonesAndScoreFinalResponses() = runBlocking {
        val estimateConnection = FakeEngineHttpURLConnection(
            url = URL("http://example.test/engine"),
            responseBody = """
                {"result": {"summary": "estimate", "whiteWinRate": 0.42, "whiteScoreLead": -1.5,
                  "ownership": {"blackLikelyPoints": 40, "whiteLikelyPoints": 38, "neutralOrUnclearPoints": 3,
                    "threshold": 0.3, "points": [{"point": "A1", "value": 0.9}]}}}
            """.trimIndent(),
        )
        val estimateResponse = HttpRemoteEngineOperationTransport(
            config = RemoteEngineHttpConfig(endpointUrl = "http://example.test/engine", enabled = true),
            connectionFactory = object : RemotePositionAnalysisHttpConnectionFactory {
                override fun open(url: URL): HttpURLConnection = estimateConnection
            },
        ).execute(sampleRequest(operation = RemoteEngineOperation.EstimateScore))
        assertEquals(0.42, estimateResponse.whiteWinRate ?: 0.0, 0.0001)
        assertEquals(40, estimateResponse.ownership?.blackLikelyPoints)
        assertEquals(1, estimateResponse.ownership?.points?.size)

        val deadStonesConnection = FakeEngineHttpURLConnection(
            url = URL("http://example.test/engine"),
            responseBody = """{"result": {"summary": "dead", "coordinates": ["A1", "B2"]}}""",
        )
        val deadStonesResponse = HttpRemoteEngineOperationTransport(
            config = RemoteEngineHttpConfig(endpointUrl = "http://example.test/engine", enabled = true),
            connectionFactory = object : RemotePositionAnalysisHttpConnectionFactory {
                override fun open(url: URL): HttpURLConnection = deadStonesConnection
            },
        ).execute(sampleRequest(operation = RemoteEngineOperation.DeadStones))
        assertEquals(2, deadStonesResponse.deadStoneCoordinates.size)

        val scoreFinalConnection = FakeEngineHttpURLConnection(
            url = URL("http://example.test/engine"),
            responseBody = """
                {"result": {"summary": "final", "rawScore": "B+3.5", "winner": "Black", "margin": 3.5,
                  "blackArea": 42.0, "whiteAreaWithKomi": 38.5, "komi": 6.5}}
            """.trimIndent(),
        )
        val scoreFinalResponse = HttpRemoteEngineOperationTransport(
            config = RemoteEngineHttpConfig(endpointUrl = "http://example.test/engine", enabled = true),
            connectionFactory = object : RemotePositionAnalysisHttpConnectionFactory {
                override fun open(url: URL): HttpURLConnection = scoreFinalConnection
            },
        ).execute(sampleRequest(operation = RemoteEngineOperation.ScoreFinal))
        assertEquals("B+3.5", scoreFinalResponse.rawScore)
        assertEquals(StoneColor.Black, scoreFinalResponse.winner)
        assertEquals(3.5, scoreFinalResponse.margin ?: 0.0, 0.0001)
    }

    /**
     * 타임아웃 계약 — **시계가 아니라 호출 기록으로** 단언한다(refactor backlog #75).
     *
     * 예전 판본은 두 가지를 벽시계에 기대고 있었고, 그래서 게이트에서 간헐적으로 빨개졌다:
     * ⓐ 20ms 예산이 **디스패치 전부터** 흘러서, 머신이 붐비면 호출이 스레드 풀 큐에 앉은 채
     *   타임아웃이 났다 — 연결을 열어 보지도 못했으니 끊을 것도 없어 `disconnected`가 false였다.
     * ⓑ 가짜 연결이 `Thread.sleep`으로 막혀 있어 **인터럽트만으로도 풀렸다.** 그래서 마지막에
     *   남는 `disconnect()`가 "바깥에서 강제로 끊었다"의 증거가 아니라 막힌 스레드가 스스로
     *   빠져나오며 부른 `finally`의 흔적이어도 초록이었다.
     *
     * 지금은 예산이 **요청이 실제로 나간 뒤부터** 흐르고(ⓐ), 가짜 연결은 실제 소켓처럼
     * 인터럽트에 반응하지 않아 **`disconnect()`만이 그 읽기를 푼다**(ⓑ). 단언도 "끊겼는가"가
     * 아니라 **누가 끊었는가** 다 — 막힌 스레드 자신이 아니라 바깥 스레드여야 한다.
     *
     * `timeout`은 그물이다: 프로덕션이 "취소부터 걸고 완료를 기다리는" 옛 구조로 돌아가면
     * 막힌 읽기가 영원히 안 풀리는데, 그걸 무한 대기가 아니라 **빨강**으로 받기 위한 것이다.
     */
    @Test(timeout = 30_000L)
    fun httpTransportAbandonsConnectionOnTimeoutLikeLocalForcedProcessRestart() = runBlocking {
        val wedgedConnection = WedgedFakeHttpURLConnection(URL("http://example.test/engine"))
        val transport = HttpRemoteEngineOperationTransport(
            config = RemoteEngineHttpConfig(
                endpointUrl = "http://example.test/engine",
                enabled = true,
                connectTimeoutMillis = 10,
                readTimeoutMillis = 10,
            ),
            connectionFactory = object : RemotePositionAnalysisHttpConnectionFactory {
                override fun open(url: URL): HttpURLConnection = wedgedConnection
            },
        )

        try {
            transport.execute(sampleRequest())
            fail("A remote call stuck past the configured timeout must be cancelled.")
        } catch (timeout: TimeoutCancellationException) {
            // expected — mirrors KataGoProcessEngineAdapter.sendCommand's TimeoutCancellationException.
        }

        val wedgedThread = wedgedConnection.requestIssuingThread
        assertNotNull(
            "The timeout budget must be spent on a request that actually went out — if the call never " +
                "left the dispatcher queue, this test would be measuring thread scheduling, not the contract.",
            wedgedThread,
        )
        assertTrue(
            "A timed-out call must forcibly disconnect, just like the local adapter destroys its process " +
                "on timeout instead of leaving a wedged stream behind.",
            wedgedConnection.disconnected,
        )
        assertNotEquals(
            "The disconnect must come from OUTSIDE the wedged call, the way forceReset()/the timeout path " +
                "destroys the local process from another thread. A disconnect performed by the wedged " +
                "thread itself, on its way out, proves nothing — a real socket read never unwinds on its own.",
            wedgedThread,
            wedgedConnection.firstDisconnectThread,
        )
    }

    /**
     * Contract/regression test against `scripts/run-katago-remote-analysis-server.py`'s
     * `/engine` endpoint (`REMOTE_ENGINE_AND_LAYERING.md` Stage E-3) — the
     * one `createRemoteEngineSessionClient` actually talks to. Both response bodies
     * below are verbatim captures from that script running against local KataGo on
     * an empty 9x9 board, Black to move — not hand-written fixtures. If the Python
     * server's JSON shape ever drifts from what this Kotlin codec expects, this
     * test is the tripwire.
     */
    @Test
    fun httpTransportParsesRealMacReferenceServerEngineResponses() = runBlocking {
        val genMoveConnection = FakeEngineHttpURLConnection(
            url = URL("http://example.test/engine"),
            responseBody = """
                {
                  "result": {
                    "status": {"state": "Ready", "message": "Remote engine genMove complete."},
                    "summary": "Remote (macOS reference server) genMove selected E5.",
                    "move": {"player": "Black", "type": "play", "point": "E5", "boardSize": 9}
                  }
                }
            """.trimIndent(),
        )
        val genMoveTransport = HttpRemoteEngineOperationTransport(
            config = RemoteEngineHttpConfig(endpointUrl = "http://example.test/engine", enabled = true),
            connectionFactory = object : RemotePositionAnalysisHttpConnectionFactory {
                override fun open(url: URL): HttpURLConnection = genMoveConnection
            },
        )
        val genMoveResponse = genMoveTransport.execute(sampleRequest(operation = RemoteEngineOperation.GenMove))
        assertEquals(EngineState.Ready, genMoveResponse.status.state)
        assertEquals(
            Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)),
            genMoveResponse.move,
        )

        val estimateConnection = FakeEngineHttpURLConnection(
            url = URL("http://example.test/engine"),
            responseBody = """
                {
                  "result": {
                    "status": {"state": "Ready", "message": "Remote engine estimateScore complete."},
                    "summary": "Remote (macOS reference server) score estimate (ownership heatmap not implemented).",
                    "whiteWinRate": 0.662439694,
                    "whiteScoreLead": 0.252659785
                  }
                }
            """.trimIndent(),
        )
        val estimateTransport = HttpRemoteEngineOperationTransport(
            config = RemoteEngineHttpConfig(endpointUrl = "http://example.test/engine", enabled = true),
            connectionFactory = object : RemotePositionAnalysisHttpConnectionFactory {
                override fun open(url: URL): HttpURLConnection = estimateConnection
            },
        )
        val estimateResponse = estimateTransport.execute(sampleRequest(operation = RemoteEngineOperation.EstimateScore))
        assertEquals(0.662439694, estimateResponse.whiteWinRate ?: -1.0, 0.0001)
        assertEquals(0.252659785, estimateResponse.whiteScoreLead ?: -1.0, 0.0001)
        // Documented scope cut (module docstring): no ownership heatmap yet.
        assertEquals(null, estimateResponse.ownership)

        // deadStones: the reference server returns a clean "not implemented"
        // error rather than a guessed answer — confirm that parses as an error
        // status rather than throwing, so the app's existing local-fallback
        // endgame path (docs/engine/ENGINE_API_CALL_POLICY.md "종국 GTP 호출 정책") can
        // catch it the same way it already catches local timeouts.
        val deadStonesConnection = FakeEngineHttpURLConnection(
            url = URL("http://example.test/engine"),
            responseBody = """
                {
                  "result": {
                    "status": {"state": "Error", "message": "Remote engine 'deadStones' is not implemented by this dev server."},
                    "summary": "deadStones not implemented — app should fall back to local endgame judging."
                  }
                }
            """.trimIndent(),
        )
        val deadStonesTransport = HttpRemoteEngineOperationTransport(
            config = RemoteEngineHttpConfig(endpointUrl = "http://example.test/engine", enabled = true),
            connectionFactory = object : RemotePositionAnalysisHttpConnectionFactory {
                override fun open(url: URL): HttpURLConnection = deadStonesConnection
            },
        )
        val deadStonesResponse = deadStonesTransport.execute(sampleRequest(operation = RemoteEngineOperation.DeadStones))
        assertEquals(EngineState.Error, deadStonesResponse.status.state)
    }

    private fun sampleRequest(
        operation: RemoteEngineOperation = RemoteEngineOperation.Analyze,
    ): RemoteEngineOperationRequest =
        RemoteEngineOperationRequest(
            operation = operation,
            state = GameState.empty(),
            limit = AnalysisLimit(visits = 16),
            player = StoneColor.Black,
        )
}

private class RejectingTransport : RemoteEngineOperationTransport {
    override suspend fun execute(request: RemoteEngineOperationRequest): RemoteEngineOperationResponse =
        error("Unexpected remote call for a state-only operation: ${request.operation}")

    override fun abandonInFlightRequest() = Unit
}

private class RecordingTransport(
    private val recorded: MutableList<RemoteEngineOperationRequest>,
    private val respond: (RemoteEngineOperationRequest) -> RemoteEngineOperationResponse,
) : RemoteEngineOperationTransport {
    override suspend fun execute(request: RemoteEngineOperationRequest): RemoteEngineOperationResponse {
        recorded += request
        return respond(request)
    }

    override fun abandonInFlightRequest() = Unit
}

private class FakeEngineHttpURLConnection(
    url: URL,
    private val responseBody: String,
    private val statusCode: Int = 200,
) : HttpURLConnection(url) {
    private val output = ByteArrayOutputStream()
    var disconnected = false
        private set
    val requestBodyString: String
        get() = output.toString(Charsets.UTF_8.name())

    override fun setRequestProperty(
        key: String,
        value: String,
    ) = Unit

    override fun getOutputStream(): ByteArrayOutputStream = output

    override fun getInputStream(): InputStream = ByteArrayInputStream(responseBody.toByteArray(Charsets.UTF_8))

    override fun getResponseCode(): Int = statusCode

    override fun disconnect() {
        disconnected = true
    }

    override fun usingProxy(): Boolean = false

    override fun connect() = Unit
}

/**
 * 진짜로 **막힌** 연결 — 실제 소켓 읽기처럼 `Thread.interrupt()`로는 풀리지 않고 오직
 * `disconnect()`만이 이 읽기를 푼다. 그래서 "바깥에서 강제로 끊었다"와 "취소 신호에 반응해
 * 스스로 빠져나왔다"가 여기서는 **구별된다** — 예전 `Thread.sleep` 판본에서는 구별되지 않았다.
 */
private class WedgedFakeHttpURLConnection(url: URL) : HttpURLConnection(url) {
    private val output = ByteArrayOutputStream()
    private val released = CountDownLatch(1)
    private val firstDisconnect = AtomicReference<Thread?>(null)

    /** 요청을 실제로 내보낸 스레드 — 즉 이 아래에서 막히게 될 스레드. */
    @Volatile
    var requestIssuingThread: Thread? = null
        private set

    val firstDisconnectThread: Thread?
        get() = firstDisconnect.get()

    val disconnected: Boolean
        get() = firstDisconnect.get() != null

    override fun setRequestProperty(
        key: String,
        value: String,
    ) = Unit

    override fun getOutputStream(): ByteArrayOutputStream {
        requestIssuingThread = Thread.currentThread()
        return output
    }

    override fun getResponseCode(): Int {
        // 상한은 "아무도 안 끊으면 영원히 매달린다"를 막는 그물일 뿐, 통과 조건이 아니다:
        // 상한에 걸려 스스로 풀려나면 첫 disconnect가 이 스레드 자신이 되고, 그러면 테스트는
        // 초록이 아니라 **빨강**이 된다.
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(WEDGE_CAP_SECONDS)
        while (!disconnected && System.nanoTime() < deadlineNanos) {
            try {
                released.await(25L, TimeUnit.MILLISECONDS)
            } catch (interrupt: InterruptedException) {
                // 소켓 읽기는 인터럽트로 풀리지 않는다 — 여기서도 풀리지 않는다.
                continue
            }
        }
        throw IOException("Wedged response read: only disconnect() releases it.")
    }

    override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun disconnect() {
        firstDisconnect.compareAndSet(null, Thread.currentThread())
        released.countDown()
    }

    override fun usingProxy(): Boolean = false

    override fun connect() = Unit

    private companion object {
        const val WEDGE_CAP_SECONDS = 10L
    }
}
