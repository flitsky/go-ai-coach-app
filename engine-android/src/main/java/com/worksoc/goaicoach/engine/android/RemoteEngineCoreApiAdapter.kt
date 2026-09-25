package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.domain.describe
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.DeadStonesResult
import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi
import com.worksoc.goaicoach.shared.enginecontract.EngineMode
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.FinalScoreResult
import com.worksoc.goaicoach.shared.enginecontract.MoveResult
import com.worksoc.goaicoach.shared.enginecontract.OwnershipEstimate
import com.worksoc.goaicoach.shared.enginecontract.OwnershipPoint
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/**
 * 2계층(Middleware / Bridge) — [EngineCoreApi]의 원격 구현체.
 *
 * `ARCHITECTURE.md` 2계층 설명대로 "계약은 하나, 구현체는 여러 개(로컬/원격)"라는
 * 원칙을 따른다: [KataGoProcessEngineAdapter](로컬)와 완전히 동일한 [EngineCoreApi] 계약을
 * 만족하는 것이 목표다. 260804 정리로 로컬/원격 구현체가 같은 모듈(`engine-android`)에
 * 물리적으로 함께 있다 — app-android는 둘 중 무엇을 쓰든 `EngineCoreApi` 계약만 보면 된다.
 *
 * 로컬 어댑터는 GTP 프로세스가 서버 쪽에 대국 상태를 들고 있지만, 이 원격 어댑터가 말을
 * 거는 서버에는 아직 세션 개념이 없다 — `HttpRemotePositionAnalysisTransport`(현재
 * position-analysis 단위 read-only 스파이크)와 같은 상태 비저장(stateless) 패턴을 그대로
 * 따른다. 그래서 `initialize`/`configure`/`newGame`/`playMove`/`undoMove`/
 * `clearSearchCache`/`stop`처럼 상태만 바꾸는 호출은 네트워크를 타지 않고 이 어댑터 안에서
 * [GameState]를 직접 추적하며(앱이 어차피 정본 상태를 갖고 있다는 [EngineCoreApi]의 문서화된
 * 전제와 일치), `genMove`/`analyze`/`estimateScore`/`deadStones`/`scoreFinal`처럼 실제
 * 연산이 필요한 호출에서만 그 시점의 전체 국면을 원격으로 보낸다.
 */
internal class RemoteEngineCoreApiAdapter(
    private val transport: RemoteEngineOperationTransport,
) : EngineCoreApi {
    private var profile: EngineProfile = EngineProfile(mode = EngineMode.RemoteServer)
    private var state: GameState = GameState.empty()
    private val history = mutableListOf<GameState>()

    // KataGoProcessEngineAdapter의 commandMutex/analysisQueryMutex와 같은 목적 — 이 인스턴스에
    // 대한 호출은 직렬화돼야 상태 비저장 원격 호출 사이에서도 로컬 프로세스와 동등한 "한 번에
    // 하나의 오퍼레이션"이라는 신뢰도를 유지한다. forceReset()은 로컬과 동일하게 이 락을 절대
    // 얻지 않는다(아래 forceReset 주석 참고).
    private val mutex = Mutex()

    override suspend fun initialize(profile: EngineProfile): EngineStatus =
        mutex.withLock {
            this.profile = profile.copy(mode = EngineMode.RemoteServer)
            EngineStatus.ready("Remote engine session ready: ${this.profile.describeForRemote()}")
        }

    override suspend fun configure(profile: EngineProfile): EngineStatus =
        mutex.withLock {
            this.profile = profile.copy(mode = EngineMode.RemoteServer)
            EngineStatus.ready("Remote engine configured: ${this.profile.describeForRemote()}")
        }

    override suspend fun newGame(
        boardSize: BoardSize,
        ruleset: Ruleset,
        handicapCount: Int,
        komi: Double,
    ): EngineStatus =
        mutex.withLock {
            state = GameState.withHandicap(boardSize, ruleset, handicapCount, komi)
            history.clear()
            EngineStatus.ready("Remote engine new ${boardSize.value}x${boardSize.value} ${ruleset.scoringLabel} game")
        }

    /**
     * 정적 국면도 **네트워크를 타지 않는다** — 이 어댑터의 다른 상태 전용 호출과 같은 규칙이다.
     * 상태 비저장 서버에는 "지금 판을 이렇게 세팅해 둬라"라고 말할 자리가 없고, 다음 연산
     * 호출(`genMove`/`analyze`/...)이 어차피 그 시점의 전체 국면을 실어 보낸다.
     *
     * ⚠️ 그렇다고 **아무것도 안 해도 된다는 뜻은 아니다.** 예전에는 [EngineCoreApi]의 기본
     * 구현에 기대 이 호출을 통째로 빠뜨렸고, 그 결과 동기화 직후의 `genMove`가 **빈 판을
     * 원격으로 보내** 이미 돌이 놓인 자리를 후보로 받아 왔다(refactor backlog #20).
     */
    override suspend fun syncStaticPosition(state: GameState): EngineStatus =
        mutex.withLock {
            this.state = state
            // 정적 국면에는 되돌릴 수순이 없다 — 이전 대국의 이력을 남겨 두면 undoMove가
            // 방금 동기화한 판을 엉뚱한 국면으로 되돌린다.
            history.clear()
            EngineStatus.ready(
                "Remote engine static position synced: ${state.stones.size} stone(s), ${state.nextPlayer.label} to play",
            )
        }

    override suspend fun playMove(move: Move): EngineStatus =
        mutex.withLock {
            history += state
            state = state.play(move)
            EngineStatus.ready("Remote engine accepted ${move.describe(state.boardSize)}")
        }

    override suspend fun genMove(player: StoneColor): MoveResult =
        mutex.withLock {
            val response = executeRemote(RemoteEngineOperation.GenMove, player)
            val move = response.move
                ?: return@withLock MoveResult(
                    status = EngineStatus.error("Remote engine did not return a move for genMove"),
                    move = Move.Pass(player),
                    summary = response.summary,
                )
            history += state
            state = state.play(move)
            MoveResult(
                status = response.status,
                move = move,
                summary = response.summary,
            )
        }

    override suspend fun undoMove(): EngineStatus =
        mutex.withLock {
            val previous = history.removeLastOrNull()
            if (previous == null) {
                EngineStatus.error("Remote engine has no move to undo")
            } else {
                state = previous
                EngineStatus.ready("Remote engine undid one move")
            }
        }

    override suspend fun clearSearchCache(): EngineStatus =
        // 상태 비저장 서버라 보존할 원격 검색 트리 자체가 없다 — 로컬과 동일한 시그니처를
        // 만족시키기 위한 no-op.
        EngineStatus.ready("Remote engine search cache unchanged.")

    override suspend fun analyze(limit: AnalysisLimit): AnalysisResult =
        mutex.withLock {
            val response = executeRemote(RemoteEngineOperation.Analyze, state.nextPlayer, limit)
            AnalysisResult(
                status = response.status,
                candidates = response.candidates,
                summary = response.summary,
                rootVisits = response.rootVisits,
            )
        }

    override suspend fun estimateScore(limit: AnalysisLimit): ScoreEstimate =
        mutex.withLock {
            val response = executeRemote(RemoteEngineOperation.EstimateScore, state.nextPlayer, limit)
            ScoreEstimate(
                status = response.status,
                whiteWinRate = response.whiteWinRate,
                whiteScoreLead = response.whiteScoreLead,
                ownership = response.ownership,
                summary = response.summary,
            )
        }

    override suspend fun deadStones(): DeadStonesResult =
        mutex.withLock {
            val response = executeRemote(RemoteEngineOperation.DeadStones, state.nextPlayer)
            DeadStonesResult(
                status = response.status,
                coordinates = response.deadStoneCoordinates,
                summary = response.summary,
            )
        }

    override suspend fun scoreFinal(): FinalScoreResult =
        mutex.withLock {
            val response = executeRemote(RemoteEngineOperation.ScoreFinal, state.nextPlayer)
            FinalScoreResult(
                status = response.status,
                rawScore = response.rawScore.orEmpty(),
                winner = response.winner,
                margin = response.margin,
                blackArea = response.blackArea,
                whiteAreaWithKomi = response.whiteAreaWithKomi,
                komi = response.komi,
                summary = response.summary,
            )
        }

    override suspend fun stop(): EngineStatus =
        mutex.withLock {
            transport.abandonInFlightRequest()
            EngineStatus.stopped("Remote engine session stopped")
        }

    // KataGoProcessEngineAdapter.forceReset()과 동일한 이유로 mutex를 얻지 않는다: 어떤 호출이
    // 정말로 멈춰서 락을 쥔 채라면, 여기서 락을 기다리는 순간 "지금 당장 풀어달라"는 이 함수의
    // 존재 이유 자체가 무너진다. transport.abandonInFlightRequest()가 현재 열려 있는 HTTP
    // 연결을 다른 스레드에서 강제로 끊어(disconnect) 블로킹 읽기를 풀어준다 — 로컬의
    // process.destroy()와 같은 역할.
    override fun forceReset() {
        transport.abandonInFlightRequest()
    }

    private suspend fun executeRemote(
        operation: RemoteEngineOperation,
        player: StoneColor,
        limit: AnalysisLimit = profile.analysisLimit,
    ): RemoteEngineOperationResponse =
        transport.execute(
            RemoteEngineOperationRequest(
                operation = operation,
                state = state,
                limit = limit,
                player = player,
            ),
        )

    private fun EngineProfile.describeForRemote(): String =
        "${difficulty.label}, visits=${analysisLimit.visits}, time=${analysisLimit.timeMillis ?: "none"}ms"
}

/**
 * 원격 엔진 후보 1개를 가리키는 설정 — app-android가 여러 후보를 조립해 (3계층)
 * `RemoteEngineSessionClient`에 넘긴다. [KataGoProcessConfig]와 같은 이유로 public이다:
 * app-android가 알아야 하는 값(엔드포인트, 타임아웃)이지 engine-android 내부 구현이 아니다.
 */
data class RemoteEngineHttpConfig(
    val endpointUrl: String,
    val enabled: Boolean = false,
    val connectTimeoutMillis: Int = 3_000,
    val readTimeoutMillis: Int = 30_000,
)

internal enum class RemoteEngineOperation(val wireName: String) {
    GenMove("genMove"),
    Analyze("analyze"),
    EstimateScore("estimateScore"),
    DeadStones("deadStones"),
    ScoreFinal("scoreFinal"),
}

internal data class RemoteEngineOperationRequest(
    val operation: RemoteEngineOperation,
    val state: GameState,
    val limit: AnalysisLimit,
    val player: StoneColor,
)

internal data class RemoteEngineOperationResponse(
    val status: EngineStatus,
    val summary: String,
    val move: Move? = null,
    val candidates: List<CandidateMove> = emptyList(),
    val rootVisits: Int? = null,
    val whiteWinRate: Double? = null,
    val whiteScoreLead: Double? = null,
    val ownership: OwnershipEstimate? = null,
    val deadStoneCoordinates: List<BoardCoordinate> = emptyList(),
    val rawScore: String? = null,
    val winner: StoneColor? = null,
    val margin: Double? = null,
    val blackArea: Double? = null,
    val whiteAreaWithKomi: Double? = null,
    val komi: Double? = null,
)

internal interface RemoteEngineOperationTransport {
    suspend fun execute(request: RemoteEngineOperationRequest): RemoteEngineOperationResponse

    /**
     * 지금 진행 중인 원격 호출이 있다면 응답을 기다리지 않고 강제로 끊는다. 논블로킹이어야
     * 하며, 진행 중인 호출이 없어도 안전하게 no-op이어야 한다.
     */
    fun abandonInFlightRequest()
}

/**
 * Android/JVM-bound HTTP 구현체. `HttpRemotePositionAnalysisTransport`와 같은 스타일이지만,
 * [forceReset]이 실제로 블로킹 읽기를 풀 수 있도록 현재 연결을 추적하고, 타임아웃 시 로컬
 * 프로세스 재시작과 동등한 "강제 폐기"를 수행한다는 점이 다르다.
 */
internal class HttpRemoteEngineOperationTransport(
    private val config: RemoteEngineHttpConfig,
    private val connectionFactory: RemotePositionAnalysisHttpConnectionFactory =
        DefaultRemotePositionAnalysisHttpConnectionFactory,
) : RemoteEngineOperationTransport {
    @Volatile
    private var activeConnection: HttpURLConnection? = null

    override suspend fun execute(request: RemoteEngineOperationRequest): RemoteEngineOperationResponse {
        check(config.enabled) { "Remote engine HTTP transport is disabled." }
        require(config.endpointUrl.isNotBlank()) { "endpointUrl must not be blank when remote engine is enabled." }

        val responseBudgetMillis = config.connectTimeoutMillis.toLong() + config.readTimeoutMillis.toLong()
        return coroutineScope {
            val awaitingResponse = CompletableDeferred<Unit>()
            val call = async(Dispatchers.IO) {
                try {
                    runInterruptible { executeBlocking(request) { awaitingResponse.complete(Unit) } }
                } catch (failure: Throwable) {
                    // 이미 취소된 뒤 — 즉 타임아웃이 연결을 강제로 끊은 뒤 — 막혔던 읽기가
                    // 풀리며 나는 예외는 **결과가 아니다.** 그대로 흘리면 이 예외가 호출자에게
                    // 타임아웃 대신 IO 오류로 보이고, 로컬 어댑터와의 대등성이 깨진다.
                    coroutineContext.ensureActive()
                    throw failure
                }
            }
            // 호출이 끝났는데 아무도 신호를 안 준 경우(요청을 내보내기도 전에 실패한 경우)에도
            // 아래 await가 영원히 매달리지 않게 한다.
            call.invokeOnCompletion { awaitingResponse.complete(Unit) }
            try {
                // ⚠️ connect/read 타임아웃 예산은 **요청이 실제로 나간 뒤부터** 잰다.
                // 이 값들은 "네트워크가 느리다"를 재는 값이지 "Dispatchers.IO가 붐빈다"를 재는
                // 값이 아니다. 예전에는 withTimeout이 디스패치 전부터 돌아서, 풀이 붐비면
                // **연결을 열어 보지도 못한 채** 타임아웃이 났다(그러면 아래 강제 폐기도
                // 끊을 대상이 없어 아무 일도 하지 않는다 — refactor backlog #75).
                withTimeout(DISPATCH_STARVATION_GUARD_MILLIS) { awaitingResponse.await() }
                withTimeout(responseBudgetMillis) { call.await() }
            } catch (timeout: TimeoutCancellationException) {
                // ⚠️ 여기서 **취소의 완료를 기다리면 안 된다.** 진짜로 막힌 소켓 읽기는
                // `Thread.interrupt()`에 반응하지 않는다 — 옛 구조
                // (`withTimeout { runInterruptible { … } }`)는 취소를 걸고 그 완료를 기다렸기
                // 때문에, 정말 막힌 연결에서는 강제 폐기에 **닿지도 못한 채** 멈춘다.
                // cancel()은 기다리지 않으니 표시만 먼저 해 두고, 실제로 읽기를 푸는 것은
                // 그 다음의 강제 폐기다 — 로컬 어댑터의 process.destroy()와 같은 자리다.
                call.cancel(timeout)
                abandonInFlightRequest()
                throw timeout
            }
        }
    }

    override fun abandonInFlightRequest() {
        activeConnection?.let { connection -> runCatching { connection.disconnect() } }
    }

    private fun executeBlocking(
        request: RemoteEngineOperationRequest,
        onAwaitingResponse: () -> Unit,
    ): RemoteEngineOperationResponse {
        val connection = connectionFactory.open(URL(config.endpointUrl))
        activeConnection = connection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = config.connectTimeoutMillis
            connection.readTimeout = config.readTimeoutMillis
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")

            val requestBody = RemoteEngineOperationJsonCodec
                .encodeRequest(request)
                .toString()
                .toByteArray(Charsets.UTF_8)
            connection.outputStream.use { output -> output.write(requestBody) }
            // 요청은 나갔다. 여기서부터가 "응답 대기"이고, 타임아웃 예산은 이 지점부터 흐른다.
            onAwaitingResponse()

            val statusCode = connection.responseCode
            val body = if (statusCode in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                throw IOException(
                    "Remote engine HTTP $statusCode (${request.operation.wireName}): ${errorBody.orEmpty()}",
                )
            }
            RemoteEngineOperationJsonCodec.decodeResponse(request.operation, request.state.boardSize, body)
        } finally {
            connection.disconnect()
            activeConnection = null
        }
    }

    private companion object {
        /**
         * 요청이 스레드 풀 큐에 앉아 있는 단계에만 걸리는 **안전장치**다 — connect/read 예산과는
         * 별개이고, 이 값에 기대 동작하는 정상 경로는 없다. Dispatchers.IO가 영구히 굶어도
         * `execute`가 영원히 매달리지는 않게 하는 용도라 넉넉하게 잡는다.
         */
        const val DISPATCH_STARVATION_GUARD_MILLIS = 60_000L
    }
}

internal object RemoteEngineOperationJsonCodec {
    fun encodeRequest(request: RemoteEngineOperationRequest): JSONObject =
        JSONObject()
            .put("operation", request.operation.wireName)
            .put("player", request.player.name)
            .put("limit", RemotePositionAnalysisJsonCodec.encodeLimit(request.limit))
            .put("state", RemotePositionAnalysisJsonCodec.encodeState(request.state))

    fun decodeResponse(
        operation: RemoteEngineOperation,
        boardSize: BoardSize,
        json: String,
    ): RemoteEngineOperationResponse {
        val root = JSONObject(json)
        val result = root.optJSONObject("result") ?: root
        val status = RemotePositionAnalysisJsonCodec.decodeStatus(result.optJSONObject("status"))
        val summary = result.optString("summary", "Remote engine ${operation.wireName} complete.")

        return when (operation) {
            RemoteEngineOperation.GenMove -> RemoteEngineOperationResponse(
                status = status,
                summary = summary,
                move = result.optJSONObject("move")?.let(RemotePositionAnalysisJsonCodec::decodeMove),
            )

            RemoteEngineOperation.Analyze -> RemoteEngineOperationResponse(
                status = status,
                summary = summary,
                candidates = RemotePositionAnalysisJsonCodec.decodeCandidates(result.optJSONArray("candidates")),
                rootVisits = result.optNullableInt("rootVisits"),
            )

            RemoteEngineOperation.EstimateScore -> RemoteEngineOperationResponse(
                status = status,
                summary = summary,
                whiteWinRate = result.optNullableDouble("whiteWinRate"),
                whiteScoreLead = result.optNullableDouble("whiteScoreLead"),
                ownership = result.optJSONObject("ownership")?.let { ownership -> decodeOwnership(ownership, boardSize) },
            )

            RemoteEngineOperation.DeadStones -> RemoteEngineOperationResponse(
                status = status,
                summary = summary,
                deadStoneCoordinates = decodeCoordinates(result.optJSONArray("coordinates"), boardSize),
            )

            RemoteEngineOperation.ScoreFinal -> RemoteEngineOperationResponse(
                status = status,
                summary = summary,
                rawScore = result.optString("rawScore", ""),
                winner = result.optNullableString("winner")
                    ?.let { name -> runCatching { StoneColor.valueOf(name) }.getOrNull() },
                margin = result.optNullableDouble("margin"),
                blackArea = result.optNullableDouble("blackArea"),
                whiteAreaWithKomi = result.optNullableDouble("whiteAreaWithKomi"),
                komi = result.optNullableDouble("komi"),
            )
        }
    }

    private fun decodeOwnership(
        json: JSONObject,
        boardSize: BoardSize,
    ): OwnershipEstimate =
        OwnershipEstimate(
            blackLikelyPoints = json.optInt("blackLikelyPoints", 0),
            whiteLikelyPoints = json.optInt("whiteLikelyPoints", 0),
            neutralOrUnclearPoints = json.optInt("neutralOrUnclearPoints", 0),
            threshold = json.optDouble("threshold", 0.0),
            points = decodeOwnershipPoints(json.optJSONArray("points"), boardSize),
        )

    private fun decodeOwnershipPoints(
        points: JSONArray?,
        boardSize: BoardSize,
    ): List<OwnershipPoint> {
        if (points == null) return emptyList()
        return buildList {
            for (index in 0 until points.length()) {
                val point = points.getJSONObject(index)
                add(
                    OwnershipPoint(
                        coordinate = BoardCoordinate.fromLabel(point.getString("point"), boardSize),
                        value = point.optDouble("value", 0.0),
                    ),
                )
            }
        }
    }

    private fun decodeCoordinates(
        coordinates: JSONArray?,
        boardSize: BoardSize,
    ): List<BoardCoordinate> {
        if (coordinates == null) return emptyList()
        return buildList {
            for (index in 0 until coordinates.length()) {
                add(BoardCoordinate.fromLabel(coordinates.getString(index), boardSize))
            }
        }
    }
}
