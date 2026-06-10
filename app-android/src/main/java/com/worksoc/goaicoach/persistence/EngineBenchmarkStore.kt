package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.EngineBenchmarkMetric
import com.worksoc.goaicoach.application.EngineBenchmarkProfile
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
        visitsTargets: List<Int>,
    ): Boolean {
        val profile = load() ?: return false
        if (profile.samplesPerVisit != samplesPerVisit || profile.timeCapMs != timeCapMs) {
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
            .put("samplesPerVisit", profile.samplesPerVisit)
            .put("timeCapMs", profile.timeCapMs)
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
                                .put("avgMs", metric.avgMs),
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
                metrics = List(metricsJson.length()) { index ->
                    val metric = metricsJson.getJSONObject(index)
                    EngineBenchmarkMetric(
                        visits = metric.getInt("visits"),
                        samples = metric.getInt("samples"),
                        minMs = metric.getDouble("minMs"),
                        maxMs = metric.getDouble("maxMs"),
                        avgMs = metric.getDouble("avgMs"),
                    )
                },
            )
        }.getOrNull()
}
