package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.EngineBenchmarkMetric
import com.worksoc.goaicoach.application.EngineBenchmarkProfile
import com.worksoc.goaicoach.application.EngineBenchmarkRuleset
import com.worksoc.goaicoach.application.EngineBenchmarkSample
import com.worksoc.goaicoach.shared.Ruleset
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal class EngineBenchmarkStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, BenchmarkFileName)

    fun exists(): Boolean =
        file.isFile

    fun hasUsableProfile(
        samplesPerVisit: Int,
        timeCapMs: Long,
        measurementVersion: Int,
        visitsTargets: List<Int>,
    ): Boolean {
        val profile = load() ?: return false
        if (
            profile.samplesPerVisit != samplesPerVisit ||
            profile.timeCapMs != timeCapMs ||
            profile.measurementVersion != measurementVersion ||
            profile.benchmarkRuleset != EngineBenchmarkRuleset
        ) {
            return false
        }
        val metricByVisits = profile.metrics.associateBy { metric -> metric.visits }
        return visitsTargets.all { visits ->
            metricByVisits[visits]?.samples == samplesPerVisit
        }
    }

    fun save(profile: EngineBenchmarkProfile) {
        file.writeText(EngineBenchmarkCodec.encode(profile), Charsets.UTF_8)
    }

    fun load(): EngineBenchmarkProfile? =
        file
            .takeIf { it.isFile }
            ?.readText(Charsets.UTF_8)
            ?.let(EngineBenchmarkCodec::decode)

    fun loadText(): String =
        file
            .takeIf { it.isFile }
            ?.readText(Charsets.UTF_8)
            ?: "No engine benchmark file recorded."

    fun path(): String =
        file.absolutePath

    private companion object {
        const val BenchmarkFileName = "engine_benchmark_profile.json"
    }
}

internal object EngineBenchmarkCodec {
    private const val SchemaVersion = 1

    fun encode(profile: EngineBenchmarkProfile): String =
        JSONObject()
            .put("schema", SchemaVersion)
            .put("createdAtMillis", profile.createdAtMillis)
            .put("measurementVersion", profile.measurementVersion)
            .put("samplesPerVisit", profile.samplesPerVisit)
            .put("timeCapMs", profile.timeCapMs)
            .put("benchmarkPositionName", profile.benchmarkPositionName)
            .put("benchmarkRuleset", profile.benchmarkRuleset.name)
            .put(
                "benchmarkPositionMoves",
                JSONArray().also { moves ->
                    profile.benchmarkPositionMoves.forEach { move ->
                        moves.put(move)
                    }
                },
            )
            .put(
                "metrics",
                JSONArray().also { array ->
                    profile.metrics.forEach { metric ->
                        array.put(
                            JSONObject()
                                .put("visits", metric.visits)
                                .put("samples", metric.samples)
                                .put("minMs", metric.minMs)
                                .put("maxMs", metric.maxMs)
                                .put("avgMs", metric.avgMs)
                                .put("rootMinVisits", metric.rootMinVisits)
                                .put("rootMaxVisits", metric.rootMaxVisits)
                                .put("rootAvgVisits", metric.rootAvgVisits)
                                .put("fillOk", metric.fillOk)
                                .put("fillShort", metric.fillShort)
                                .put("fillUnknown", metric.fillUnknown)
                                .put(
                                    "sampleDetails",
                                    JSONArray().also { samples ->
                                        metric.sampleDetails.forEach { sample ->
                                            samples.put(
                                                JSONObject()
                                                    .put("sampleIndex", sample.sampleIndex)
                                                    .put("visits", sample.visits)
                                                    .put("elapsedMs", sample.elapsedMs)
                                                    .put("engineElapsedMs", sample.engineElapsedMs)
                                                    .put("rootVisits", sample.rootVisits)
                                                    .put("fillStatus", sample.fillStatus)
                                                    .put(
                                                        "positionMoves",
                                                        JSONArray().also { moves ->
                                                            sample.positionMoves.forEach { move ->
                                                                moves.put(move)
                                                            }
                                                        },
                                                    ),
                                            )
                                        }
                                    },
                                ),
                        )
                    }
                },
            )
            .toString(2)

    fun decode(raw: String): EngineBenchmarkProfile? =
        runCatching {
            val json = JSONObject(raw)
            if (json.optInt("schema", SchemaVersion) != SchemaVersion) {
                return@runCatching null
            }
            val metricsJson = json.optJSONArray("metrics") ?: JSONArray()
            EngineBenchmarkProfile(
                createdAtMillis = json.optLong("createdAtMillis", 0L),
                samplesPerVisit = json.optInt("samplesPerVisit", 0),
                timeCapMs = json.optLong("timeCapMs", 0L),
                measurementVersion = json.optInt("measurementVersion", 0),
                benchmarkPositionName = json.optString("benchmarkPositionName", ""),
                benchmarkPositionMoves = json.optJSONArray("benchmarkPositionMoves")?.let { movesJson ->
                    List(movesJson.length()) { index -> movesJson.getString(index) }
                }.orEmpty(),
                benchmarkRuleset = enumOrDefault(json.optString("benchmarkRuleset"), Ruleset.Japanese),
                metrics = List(metricsJson.length()) { index ->
                    val metric = metricsJson.getJSONObject(index)
                    EngineBenchmarkMetric(
                        visits = metric.getInt("visits"),
                        samples = metric.getInt("samples"),
                        minMs = metric.getDouble("minMs"),
                        maxMs = metric.getDouble("maxMs"),
                        avgMs = metric.getDouble("avgMs"),
                        rootMinVisits = metric.optNullableInt("rootMinVisits"),
                        rootMaxVisits = metric.optNullableInt("rootMaxVisits"),
                        rootAvgVisits = metric.optNullableDouble("rootAvgVisits"),
                        fillOk = metric.optInt("fillOk", 0),
                        fillShort = metric.optInt("fillShort", 0),
                        fillUnknown = metric.optInt("fillUnknown", 0),
                        sampleDetails = metric.optJSONArray("sampleDetails")?.let { samplesJson ->
                            List(samplesJson.length()) { sampleIndex ->
                                val sample = samplesJson.getJSONObject(sampleIndex)
                                EngineBenchmarkSample(
                                    sampleIndex = sample.getInt("sampleIndex"),
                                    visits = sample.getInt("visits"),
                                    elapsedMs = sample.getDouble("elapsedMs"),
                                    engineElapsedMs = sample.optNullableLong("engineElapsedMs"),
                                    rootVisits = sample.optNullableInt("rootVisits"),
                                    fillStatus = sample.optString("fillStatus", "UNKNOWN"),
                                    positionMoves = sample.optJSONArray("positionMoves")?.let { movesJson ->
                                        List(movesJson.length()) { moveIndex -> movesJson.getString(moveIndex) }
                                    }.orEmpty(),
                                )
                            }
                        }.orEmpty(),
                    )
                },
            )
        }.getOrNull()
}

private fun JSONObject.optNullableInt(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private fun JSONObject.optNullableLong(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name) else null
