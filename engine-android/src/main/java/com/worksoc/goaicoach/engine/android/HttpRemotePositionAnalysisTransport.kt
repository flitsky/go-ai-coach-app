package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.CandidateMoveSource
import com.worksoc.goaicoach.shared.enginecontract.EngineState
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.RemotePositionAnalysisRequest
import com.worksoc.goaicoach.shared.enginecontract.RemotePositionAnalysisResponse
import com.worksoc.goaicoach.shared.enginecontract.RemotePositionAnalysisTransport
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

internal data class RemotePositionAnalysisHttpConfig(
    val endpointUrl: String,
    val enabled: Boolean = false,
    val connectTimeoutMillis: Int = 3_000,
    val readTimeoutMillis: Int = 10_000,
)

/**
 * Android/JVM-bound HTTP spike for read-only remote analysis.
 *
 * Keep this implementation outside the KMP-ready gateway contract
 * ([RemotePositionAnalysisTransport], `:shared`). It depends on `HttpURLConnection` and
 * `org.json`, so it is a transport detail that can later be replaced by Ktor, OkHttp, or a
 * server-to-server client. Physically living in `engine-android` (260804 정리) keeps every
 * `EngineCoreApi` implementation — local and remote — in one module.
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
            RemotePositionAnalysisJsonCodec.decodeResponse(request.state.boardSize, body)
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

/**
 * 2계층 — remote position-analysis 스파이크의 JSON 코덱. 여기 있는 상태/한도 인코딩과
 * 후보수/상태 디코딩 헬퍼는 [RemoteEngineCoreApiAdapter]가 `EngineCoreApi` 전체를 원격으로
 * 확장할 때도 동일한 국면 표현이 필요해 `internal`로 공개해 재사용한다 — 같은 국면을 두 번
 * 다르게 직렬화하면 로컬/원격 대등성이 오히려 깨진다.
 */
internal object RemotePositionAnalysisJsonCodec {
    fun encodeRequest(request: RemotePositionAnalysisRequest): JSONObject =
        JSONObject()
            .put("positionFingerprint", request.positionFingerprint)
            .put("searchMode", request.searchMode.name)
            .put("limit", encodeLimit(request.limit))
            .put("state", encodeState(request.state))

    /** [boardSize]는 요청에 실어 보낸 국면의 판이다 — 후보수 좌표를 이 크기로 읽는다([decodeMove]). */
    fun decodeResponse(
        boardSize: BoardSize,
        json: String,
    ): RemotePositionAnalysisResponse {
        val root = JSONObject(json)
        val result = root.optJSONObject("result") ?: root
        return RemotePositionAnalysisResponse(
            result = AnalysisResult(
                status = decodeStatus(result.optJSONObject("status")),
                candidates = decodeCandidates(result.optJSONArray("candidates"), boardSize),
                summary = result.optString("summary", "Remote position analysis complete."),
                rootVisits = result.optNullableInt("rootVisits"),
            ),
            diagnosticText = root.optNullableString("diagnosticText"),
        )
    }

    internal fun encodeLimit(limit: AnalysisLimit): JSONObject =
        JSONObject()
            .put("visits", limit.visits)
            .putNullable("timeMillis", limit.timeMillis)
            .put("candidateCount", limit.candidateCount)
            .put("includePolicy", limit.includePolicy)
            .put("refinePolicyMoves", limit.refinePolicyMoves)
            .put("minVisitsPerCandidate", limit.minVisitsPerCandidate)
            .putNullable("minTimeMillis", limit.minTimeMillis)

    internal fun encodeState(state: GameState): JSONObject =
        JSONObject()
            .put("boardSize", state.boardSize.value)
            // 접바둑 보정 방식(`Ruleset.handicapBonusRule`, #106)은 싣지 않는다 — 서버는 이름 룰의 기본값을 쓰고
            // (#65 실측), 로컬 엔진처럼 덮어쓰기를 보낼 자리가 와이어에 없다. 모든 룰셋이 그 기본값과 같다는
            // 전제는 `KataGoNamedRulesTest`가 지킨다. 다른 값을 쓰는 룰셋을 들이려면 와이어부터 넓혀라.
            .put("ruleset", state.ruleset.name)
            // ⚠️ 백로그 #19 — 예전에는 komi/handicapCount가 빠져 있었다. 원격 서버가
            // 이 값 없이는 덤/접바둑을 알 도리가 없어 항상 `DefaultKomi`(6.5)·맞바둑으로
            // 가정해 분석했다 — 이어하기 덤 유실(#1)과 같은 함정이 같은 이유로 남아 있던 것.
            // `scripts/run-katago-remote-analysis-server.py` 모듈 docstring이 이 gap을
            // 스스로 문서화해 두고 있었다("neither ... sends komi at all").
            .put("komi", state.komi)
            .put("handicapCount", state.handicapCount)
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

    internal fun encodeMove(
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

    internal fun decodeStatus(status: JSONObject?): EngineStatus {
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

    internal fun decodeCandidates(
        candidates: JSONArray?,
        boardSize: BoardSize,
    ): List<CandidateMove> {
        if (candidates == null) return emptyList()
        return buildList {
            for (index in 0 until candidates.length()) {
                val candidate = candidates.getJSONObject(index)
                add(
                    CandidateMove(
                        move = decodeMove(candidate, boardSize),
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

    /**
     * [boardSize]는 **권위 판 크기**다 — 요청에 실어 보낸 국면의 판(refactor backlog #100).
     * 좌표 표기는 판 크기 없이는 뜻이 없다: `C3`는 9x9에서 (6, 2), 13x13에서 (10, 2)다.
     *
     * ⚠️ 예전에는 페이로드의 `boardSize`를 읽고, 없으면 9로 가정했다. 13x13·19x19 응답이 그 값을
     * 빠뜨리면 좌표가 **조용히** 다른 점으로 읽히거나(`C3`) 판 밖이라 실패했다(`Q16`).
     * 페이로드의 `boardSize`는 이제 교차 검사로만 쓴다 — 없거나 null이면 [boardSize]로 읽고,
     * 있는데 다르면 디코드를 실패시킨다. 서버가 다른 판을 분석했다는 뜻이라, 어느 크기로 읽어도
     * 틀린 점이 된다.
     */
    internal fun decodeMove(
        candidate: JSONObject,
        boardSize: BoardSize,
    ): Move {
        requirePayloadBoardSizeAgrees(candidate, boardSize)
        val player = StoneColor.valueOf(candidate.optString("player", StoneColor.Black.name))
        return when (candidate.optString("type", "play")) {
            "pass" -> Move.Pass(player)
            "resign" -> Move.Resign(player)
            else -> Move.Play(
                player = player,
                coordinate = BoardCoordinate.fromLabel(candidate.getString("point"), boardSize),
            )
        }
    }

    private fun requirePayloadBoardSizeAgrees(
        move: JSONObject,
        boardSize: BoardSize,
    ) {
        if (move.isNull(PAYLOAD_BOARD_SIZE)) return
        val payloadBoardSize = move.getInt(PAYLOAD_BOARD_SIZE)
        require(payloadBoardSize == boardSize.value) {
            "Remote move payload boardSize $payloadBoardSize disagrees with the requested " +
                "${boardSize.value}x${boardSize.value} board"
        }
    }

    private const val PAYLOAD_BOARD_SIZE = "boardSize"
}
