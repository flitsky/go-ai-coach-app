package com.worksoc.goaicoach.middleware

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.DeadStonesResult
import com.worksoc.goaicoach.shared.EngineCoreApi
import com.worksoc.goaicoach.shared.EngineMode
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveResult
import com.worksoc.goaicoach.shared.OwnershipEstimate
import com.worksoc.goaicoach.shared.OwnershipPoint
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.describe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 2계층(Middleware / Bridge) — [EngineCoreApi]의 원격 구현체.
 *
 * `docs/ARCHITECTURE.md` 2계층 설명대로 "계약은 하나, 구현체는 여러 개(로컬/원격)"라는
 * 원칙을 따른다: [com.worksoc.goaicoach.engine.android.KataGoProcessEngineAdapter](로컬)와
 * 완전히 동일한 [EngineCoreApi] 계약을 만족하는 것이 목표다.
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

internal data class RemoteEngineHttpConfig(
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

        return try {
            withTimeout(config.connectTimeoutMillis.toLong() + config.readTimeoutMillis.toLong()) {
                runInterruptible(Dispatchers.IO) {
                    executeBlocking(request)
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            abandonInFlightRequest()
            throw timeout
        }
    }

    override fun abandonInFlightRequest() {
        activeConnection?.let { connection -> runCatching { connection.disconnect() } }
    }

    private fun executeBlocking(request: RemoteEngineOperationRequest): RemoteEngineOperationResponse {
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
