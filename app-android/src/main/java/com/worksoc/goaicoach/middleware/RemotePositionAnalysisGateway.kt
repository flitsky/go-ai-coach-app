package com.worksoc.goaicoach.middleware

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.CandidateMoveSource
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.EngineState
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.analysisFingerprint
import com.worksoc.goaicoach.shared.StoneColor
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

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

internal data class RemotePositionAnalysisHttpConfig(
    val endpointUrl: String,
    val enabled: Boolean = false,
    val connectTimeoutMillis: Int = 3_000,
    val readTimeoutMillis: Int = 10_000,
)

/**
 * Feature-flagged HTTP spike for read-only remote analysis.
 *
 * This transport is intentionally disabled by default. Production code should
 * only construct it behind an explicit setting/feature flag and must keep a
 * local/offline fallback path.
 */
internal class HttpRemotePositionAnalysisTransport(
    private val config: RemotePositionAnalysisHttpConfig,
    private val connectionFactory: RemotePositionAnalysisHttpConnectionFactory =
        DefaultRemotePositionAnalysisHttpConnectionFactory,
) : RemotePositionAnalysisTransport {
    override suspend fun analyze(request: RemotePositionAnalysisRequest): RemotePositionAnalysisResponse {
        check(config.enabled) { "Remote position analysis HTTP transport is disabled." }
        require(config.endpointUrl.isNotBlank()) { "endpointUrl must not be blank when remote analysis is enabled." }

        val connection = connectionFactory.open(URL(config.endpointUrl))
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = config.connectTimeoutMillis
            connection.readTimeout = config.readTimeoutMillis
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")

            val requestBody = RemotePositionAnalysisJsonCodec
                .encodeRequest(request)
                .toString()
                .toByteArray(Charsets.UTF_8)
            connection.outputStream.use { output -> output.write(requestBody) }

            val statusCode = connection.responseCode
            val body = if (statusCode in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                throw IOException("Remote analysis HTTP $statusCode: ${errorBody.orEmpty()}")
            }
            RemotePositionAnalysisJsonCodec.decodeResponse(body)
        } finally {
            connection.disconnect()
        }
    }
}

internal interface RemotePositionAnalysisHttpConnectionFactory {
    fun open(url: URL): HttpURLConnection
}

internal object DefaultRemotePositionAnalysisHttpConnectionFactory : RemotePositionAnalysisHttpConnectionFactory {
    override fun open(url: URL): HttpURLConnection =
        url.openConnection() as HttpURLConnection
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

internal object RemotePositionAnalysisJsonCodec {
    fun encodeRequest(request: RemotePositionAnalysisRequest): JSONObject =
        JSONObject()
            .put("positionFingerprint", request.positionFingerprint)
            .put("searchMode", request.searchMode.name)
            .put("limit", encodeLimit(request.limit))
            .put("state", encodeState(request.state))

    fun decodeResponse(json: String): RemotePositionAnalysisResponse {
        val root = JSONObject(json)
        val result = root.optJSONObject("result") ?: root
        return RemotePositionAnalysisResponse(
            result = AnalysisResult(
                status = decodeStatus(result.optJSONObject("status")),
                candidates = decodeCandidates(result.optJSONArray("candidates")),
                summary = result.optString("summary", "Remote position analysis complete."),
                rootVisits = result.optNullableInt("rootVisits"),
            ),
            diagnosticText = root.optNullableString("diagnosticText"),
        )
    }

    private fun encodeLimit(limit: AnalysisLimit): JSONObject =
        JSONObject()
            .put("visits", limit.visits)
            .putNullable("timeMillis", limit.timeMillis)
            .put("candidateCount", limit.candidateCount)
            .put("includePolicy", limit.includePolicy)
            .put("refinePolicyMoves", limit.refinePolicyMoves)
            .put("minVisitsPerCandidate", limit.minVisitsPerCandidate)
            .putNullable("minTimeMillis", limit.minTimeMillis)

    private fun encodeState(state: GameState): JSONObject =
        JSONObject()
            .put("boardSize", state.boardSize.value)
            .put("ruleset", state.ruleset.name)
            .put("nextPlayer", state.nextPlayer.name)
            .put("capturedByBlack", state.capturedByBlack)
            .put("capturedByWhite", state.capturedByWhite)
            .putNullable("koPoint", state.koPoint?.label(state.boardSize))
            .putNullable("koForbiddenFor", state.koForbiddenFor?.name)
            .put(
                "stones",
                JSONArray().also { stones ->
                    state.stones.entries
                        .sortedWith(compareBy({ it.key.row }, { it.key.column }))
                        .forEach { (coordinate, color) ->
                            stones.put(
                                JSONObject()
                                    .put("point", coordinate.label(state.boardSize))
                                    .put("color", color.name),
                            )
                        }
                },
            )
            .put(
                "moves",
                JSONArray().also { moves ->
                    state.moves.forEach { move -> moves.put(encodeMove(move, state.boardSize)) }
                },
            )

    private fun encodeMove(
        move: Move,
        boardSize: BoardSize,
    ): JSONObject {
        val base = JSONObject().put("player", move.player.name)
        return when (move) {
            is Move.Play -> base
                .put("type", "play")
                .put("point", move.coordinate.label(boardSize))

            is Move.Pass -> base.put("type", "pass")
            is Move.Resign -> base.put("type", "resign")
        }
    }

    private fun decodeStatus(status: JSONObject?): EngineStatus {
        if (status == null) {
            return EngineStatus.ready("Remote position analysis complete.")
        }
        val stateName = status.optString("state", EngineState.Ready.name)
        val state = runCatching { EngineState.valueOf(stateName) }.getOrDefault(EngineState.Ready)
        return EngineStatus(
            state = state,
            message = status.optString("message", "Remote position analysis complete."),
        )
    }

    private fun decodeCandidates(candidates: JSONArray?): List<CandidateMove> {
        if (candidates == null) return emptyList()
        return buildList {
            for (index in 0 until candidates.length()) {
                val candidate = candidates.getJSONObject(index)
                add(
                    CandidateMove(
                        move = decodeMove(candidate),
                        winRate = candidate.optNullableDouble("winRate"),
                        scoreLead = candidate.optNullableDouble("scoreLead"),
                        pointLoss = candidate.optNullableDouble("pointLoss"),
                        visits = candidate.optNullableInt("visits"),
                        policyPrior = candidate.optNullableDouble("policyPrior"),
                        engineOrder = candidate.optNullableInt("engineOrder"),
                        source = candidate.optNullableString("source")
                            ?.let { runCatching { CandidateMoveSource.valueOf(it) }.getOrNull() }
                            ?: CandidateMoveSource.Unknown,
                        note = candidate.optNullableString("note"),
                    ),
                )
            }
        }
    }

    private fun decodeMove(candidate: JSONObject): Move {
        val player = StoneColor.valueOf(candidate.optString("player", StoneColor.Black.name))
        return when (candidate.optString("type", "play")) {
            "pass" -> Move.Pass(player)
            "resign" -> Move.Resign(player)
            else -> {
                val boardSize = BoardSize(candidate.optInt("boardSize", BoardSize.Nine.value))
                Move.Play(
                    player = player,
                    coordinate = BoardCoordinate.fromLabel(candidate.getString("point"), boardSize),
                )
            }
        }
    }

    private fun JSONObject.putNullable(
        name: String,
        value: Any?,
    ): JSONObject =
        put(name, value ?: JSONObject.NULL)

    private fun JSONObject.optNullableString(name: String): String? =
        if (isNull(name)) null else optString(name)

    private fun JSONObject.optNullableInt(name: String): Int? =
        if (isNull(name) || !has(name)) null else optInt(name)

    private fun JSONObject.optNullableDouble(name: String): Double? =
        if (isNull(name) || !has(name)) null else optDouble(name)
}
