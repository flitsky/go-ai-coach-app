package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.JsonPositionAnalysisCacheMaxEntries
import com.worksoc.goaicoach.application.JsonPositionAnalysisCacheTtlMillis
import com.worksoc.goaicoach.application.PositionAnalysisCacheEntry
import com.worksoc.goaicoach.application.PositionAnalysisCacheKey
import com.worksoc.goaicoach.application.PositionAnalysisCacheStore
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.CandidateMoveSource
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.EngineState
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal class JsonPositionAnalysisCacheStore(context: Context) : PositionAnalysisCacheStore {
    private val file = File(context.applicationContext.filesDir, CacheFileName)

    override fun get(
        key: PositionAnalysisCacheKey,
        nowMillis: Long,
    ): PositionAnalysisCacheEntry? {
        val entries = loadEntries().filterFresh(nowMillis)
        saveEntries(entries)
        return entries.firstOrNull { entry ->
            entry.key == key &&
                entry.rootVisits >= key.limit.visits &&
                entry.requestedRootVisits == key.limit.visits
        }
    }

    override fun put(
        entry: PositionAnalysisCacheEntry,
        nowMillis: Long,
    ) {
        if (entry.key.searchMode != EngineSearchMode.JsonPositionAnalysis) {
            return
        }
        if (entry.rootVisits < entry.requestedRootVisits) {
            return
        }
        val entries = (loadEntries().filterFresh(nowMillis).filterNot { it.key == entry.key } + entry)
            .sortedByDescending { it.createdAtMillis }
            .take(JsonPositionAnalysisCacheMaxEntries)
        saveEntries(entries)
    }

    override fun statsText(nowMillis: Long): String {
        val entries = loadEntries().filterFresh(nowMillis)
        return "entries=${entries.size}, ttlDays=${JsonPositionAnalysisCacheTtlMillis / DayMillis}, max=$JsonPositionAnalysisCacheMaxEntries"
    }

    private fun loadEntries(): List<PositionAnalysisCacheEntry> =
        file
            .takeIf { it.isFile }
            ?.readText(Charsets.UTF_8)
            ?.let(JsonPositionAnalysisCacheCodec::decode)
            .orEmpty()

    private fun saveEntries(entries: List<PositionAnalysisCacheEntry>) {
        file.writeText(JsonPositionAnalysisCacheCodec.encode(entries), Charsets.UTF_8)
    }

    private fun List<PositionAnalysisCacheEntry>.filterFresh(nowMillis: Long): List<PositionAnalysisCacheEntry> =
        filterNot { entry -> entry.isExpired(nowMillis) }

    private companion object {
        const val CacheFileName = "json_position_analysis_cache.json"
        const val DayMillis = 24L * 60L * 60L * 1_000L
    }
}

internal object JsonPositionAnalysisCacheCodec {
    private const val SchemaVersion = 1

    fun encode(entries: List<PositionAnalysisCacheEntry>): String =
        JSONObject()
            .put("schema", SchemaVersion)
            .put(
                "entries",
                JSONArray().also { array ->
                    entries.forEach { entry ->
                        array.put(encodeEntry(entry))
                    }
                },
            )
            .toString(2)

    fun decode(raw: String): List<PositionAnalysisCacheEntry> =
        runCatching {
            val root = JSONObject(raw)
            if (root.optInt("schema", SchemaVersion) != SchemaVersion) {
                return@runCatching emptyList()
            }
            val entries = root.optJSONArray("entries") ?: JSONArray()
            List(entries.length()) { index ->
                decodeEntry(entries.getJSONObject(index))
            }
        }.getOrDefault(emptyList())

    private fun encodeEntry(entry: PositionAnalysisCacheEntry): JSONObject =
        JSONObject()
            .put("createdAtMillis", entry.createdAtMillis)
            .put("requestedRootVisits", entry.requestedRootVisits)
            .put("rootVisits", entry.rootVisits)
            .put("key", encodeKey(entry.key))
            .put("result", encodeResult(entry.result))

    private fun decodeEntry(json: JSONObject): PositionAnalysisCacheEntry =
        PositionAnalysisCacheEntry(
            key = decodeKey(json.getJSONObject("key")),
            result = decodeResult(json.getJSONObject("result")),
            createdAtMillis = json.getLong("createdAtMillis"),
            requestedRootVisits = json.getInt("requestedRootVisits"),
            rootVisits = json.getInt("rootVisits"),
        )

    private fun encodeKey(key: PositionAnalysisCacheKey): JSONObject =
        JSONObject()
            .put("positionFingerprint", key.positionFingerprint)
            .put("searchMode", key.searchMode.name)
            .put("limit", encodeLimit(key.limit))

    private fun decodeKey(json: JSONObject): PositionAnalysisCacheKey =
        PositionAnalysisCacheKey(
            positionFingerprint = json.getString("positionFingerprint"),
            searchMode = enumOrDefault(json.optString("searchMode"), EngineSearchMode.GtpStatefulFast),
            limit = decodeLimit(json.getJSONObject("limit")),
        )

    private fun encodeLimit(limit: AnalysisLimit): JSONObject =
        JSONObject()
            .put("visits", limit.visits)
            .put("timeMillis", limit.timeMillis)
            .put("candidateCount", limit.candidateCount)
            .put("includePolicy", limit.includePolicy)
            .put("refinePolicyMoves", limit.refinePolicyMoves)
            .put("minVisitsPerCandidate", limit.minVisitsPerCandidate)
            .put("minTimeMillis", limit.minTimeMillis)

    private fun decodeLimit(json: JSONObject): AnalysisLimit =
        AnalysisLimit(
            visits = json.getInt("visits"),
            timeMillis = json.optNullableLong("timeMillis"),
            candidateCount = json.getInt("candidateCount"),
            includePolicy = json.optBoolean("includePolicy", false),
            refinePolicyMoves = json.optInt("refinePolicyMoves", 0),
            minVisitsPerCandidate = json.optInt("minVisitsPerCandidate", 0),
            minTimeMillis = json.optNullableLong("minTimeMillis"),
        )

    private fun encodeResult(result: AnalysisResult): JSONObject =
        JSONObject()
            .put("status", encodeStatus(result.status))
            .put("summary", result.summary)
            .put("rootVisits", result.rootVisits)
            .put("elapsedMillis", result.elapsedMillis)
            .put(
                "candidates",
                JSONArray().also { array ->
                    result.candidates.forEach { candidate ->
                        array.put(encodeCandidate(candidate))
                    }
                },
            )

    private fun decodeResult(json: JSONObject): AnalysisResult =
        AnalysisResult(
            status = decodeStatus(json.getJSONObject("status")),
            candidates = json.optJSONArray("candidates")?.let { candidates ->
                List(candidates.length()) { index ->
                    decodeCandidate(candidates.getJSONObject(index))
                }
            }.orEmpty(),
            summary = json.optString("summary"),
            rootVisits = json.optNullableInt("rootVisits"),
            elapsedMillis = json.optNullableLong("elapsedMillis"),
        )

    private fun encodeStatus(status: EngineStatus): JSONObject =
        JSONObject()
            .put("state", status.state.name)
            .put("message", status.message)

    private fun decodeStatus(json: JSONObject): EngineStatus =
        EngineStatus(
            state = enumOrDefault(json.optString("state"), EngineState.Ready),
            message = json.optString("message"),
        )

    private fun encodeCandidate(candidate: CandidateMove): JSONObject =
        JSONObject()
            .put("move", encodeMove(candidate.move))
            .put("winRate", candidate.winRate)
            .put("scoreLead", candidate.scoreLead)
            .put("pointLoss", candidate.pointLoss)
            .put("visits", candidate.visits)
            .put("policyPrior", candidate.policyPrior)
            .put("engineOrder", candidate.engineOrder)
            .put("source", candidate.source.name)
            .put("note", candidate.note)

    private fun decodeCandidate(json: JSONObject): CandidateMove =
        CandidateMove(
            move = decodeMove(json.getJSONObject("move")),
            winRate = json.optNullableDouble("winRate"),
            scoreLead = json.optNullableDouble("scoreLead"),
            pointLoss = json.optNullableDouble("pointLoss"),
            visits = json.optNullableInt("visits"),
            policyPrior = json.optNullableDouble("policyPrior"),
            engineOrder = json.optNullableInt("engineOrder"),
            source = enumOrDefault(json.optString("source"), CandidateMoveSource.Unknown),
            note = json.optNullableString("note"),
        )

    private fun encodeMove(move: Move): JSONObject =
        JSONObject()
            .put("type", move.typeName())
            .put("player", move.player.name)
            .also { json ->
                if (move is Move.Play) {
                    json.put("row", move.coordinate.row)
                    json.put("column", move.coordinate.column)
                }
            }

    private fun decodeMove(json: JSONObject): Move {
        val player = enumOrDefault(json.optString("player"), StoneColor.Black)
        return when (json.getString("type")) {
            "play" -> Move.Play(
                player = player,
                coordinate = BoardCoordinate(
                    row = json.getInt("row"),
                    column = json.getInt("column"),
                ),
            )
            "pass" -> Move.Pass(player)
            "resign" -> Move.Resign(player)
            else -> error("Unknown cached move type: ${json.getString("type")}")
        }
    }

    private fun Move.typeName(): String =
        when (this) {
            is Move.Play -> "play"
            is Move.Pass -> "pass"
            is Move.Resign -> "resign"
        }
}

private fun JSONObject.optNullableInt(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private fun JSONObject.optNullableLong(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name) else null

private fun JSONObject.optNullableString(name: String): String? =
    if (has(name) && !isNull(name)) optString(name) else null
