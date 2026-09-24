package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

internal object KataGoJsonAnalysisQueryFactory {
    /**
     * 쿼리 id의 앞자리를 만드는 카운터.
     *
     * ⚠️ **예전에는 어댑터 인스턴스의 평범한 `Int` 필드였다(refactor backlog #16ⓑ).** 그 증가는
     * 원자적이지 않았고, 하필 id를 만드는 자리가 `analysisQueryMutex` **바깥**이라 동시에 들어온
     * 두 분석이 같은 id를 달 수 있었다. 응답은 id로 짝을 찾으므로(`sendAnalysisQueryBlocking`),
     * 같은 id 둘은 **한쪽의 응답을 다른 쪽이 받아 가는** 형태로 조용히 틀린다.
     *
     * 시계나 난수를 쓰지 않는다 — 이 파일은 `engine-android/src/main`(Android/JVM)이라
     * [AtomicLong]이 그대로 있고, 프로세스 안에서 단조 증가하는 값이 시각보다 강하다
     * (같은 밀리초에 두 쿼리가 나가도 갈린다). 오브젝트에 두었으므로 어댑터를 새로 만들어도
     * 이어진다 — 옛 어댑터의 응답이 아직 떠 있는 동안 새 어댑터가 1번부터 다시 시작하지 않는다.
     */
    private val querySequence = AtomicLong(0L)

    /**
     * 충돌하지 않으면서 **로그에서 어느 국면의 쿼리였는지 읽히는** id.
     *
     * 유일성은 [querySequence] 하나가 책임진다. 뒤에 붙는 판 크기·수순 번호·정련 후보는
     * 사람이 읽기 위한 것이다 — `go-ai-coach-analysis-41-9x9-m17-rE5`를 보면 9줄 판의 17수째,
     * E5를 넣어 보는 policy-refine 쿼리였음이 바로 읽힌다. 예전 id(`...-analysis-41`)로는
     * 로그에 어떤 국면이 걸렸는지 되짚을 방법이 아예 없었다.
     */
    fun nextQueryId(
        boardSize: BoardSize,
        playedMoves: List<Move>,
        refineMove: Move.Play? = null,
    ): String {
        val turn = playedMoves.size + if (refineMove == null) 0 else 1
        val refineTag = refineMove?.let { move -> "-r${move.toGtpVertex(boardSize)}" }.orEmpty()
        return "go-ai-coach-analysis-${querySequence.incrementAndGet()}" +
            "-${boardSize.value}x${boardSize.value}-m$turn$refineTag"
    }

    fun build(
        id: String,
        boardSize: BoardSize,
        ruleset: Ruleset,
        playedMoves: List<Move>,
        limit: AnalysisLimit,
        refineMove: Move.Play? = null,
        includePolicyOverride: Boolean? = null,
        komi: Double = DefaultKomi,
        initialStones: List<Pair<StoneColor, BoardCoordinate>> = emptyList(),
        initialPlayer: StoneColor = StoneColor.Black,
    ): JSONObject {
        val overrideSettings = JSONObject()
        limit.timeMillis?.let { overrideSettings.put("maxTime", it / 1_000.0) }
        val queryMoves = if (refineMove == null) {
            playedMoves
        } else {
            playedMoves + refineMove
        }

        return JSONObject()
            .put("id", id)
            .put("rules", ruleset.katagoName)
            .put("komi", komi)
            .put("boardXSize", boardSize.value)
            .put("boardYSize", boardSize.value)
            .put("initialPlayer", initialPlayer.toGtpColor())
            .put("initialStones", initialStones.toJsonInitialStones(boardSize))
            .put("moves", queryMoves.toJsonMoves(boardSize))
            .put("analyzeTurns", JSONArray().put(queryMoves.size))
            .put("maxVisits", limit.visits)
            .put("includeOwnership", false)
            .put("includeMovesOwnership", false)
            .put("includePolicy", includePolicyOverride ?: (refineMove == null && limit.includePolicy))
            .put("overrideSettings", overrideSettings)
            .put("priority", 0)
    }

    private fun List<Pair<StoneColor, BoardCoordinate>>.toJsonInitialStones(boardSize: BoardSize): JSONArray =
        JSONArray().also { stones ->
            forEach { (color, coord) ->
                stones.put(
                    JSONArray()
                        .put(color.toGtpColor())
                        .put(coord.label(boardSize)),
                )
            }
        }

    private fun List<Move>.toJsonMoves(boardSize: BoardSize): JSONArray =
        JSONArray().also { moves ->
            forEach { move ->
                moves.put(
                    JSONArray()
                        .put(move.player.toGtpColor())
                        .put(move.toGtpVertex(boardSize)),
                )
            }
        }
}
