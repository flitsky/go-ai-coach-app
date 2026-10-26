package com.worksoc.goaicoach.kv

// 안드로이드 `persistence/EngineBenchmarkStore.kt`의 공용 사본(백로그 #294 — P2) — 저장소 클래스를 빼고 코덱만, import만 바꿨다(org.json → kv.json). 고칠 때는 둘을 함께 고친다(`KvStoreParityTest`).

import com.worksoc.goaicoach.application.debugreport.DebugReportMirrorPort
import com.worksoc.goaicoach.application.engine.EngineBenchmarkMetric
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProfile
import com.worksoc.goaicoach.application.engine.EngineBenchmarkSample
import com.worksoc.goaicoach.application.engine.EngineBenchmarkStorePort
import com.worksoc.goaicoach.kv.json.JSONArray
import com.worksoc.goaicoach.kv.json.JSONObject
import com.worksoc.goaicoach.shared.domain.Ruleset

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

/** 엔진 측정값 저장소의 공용 구현(P3) — 안드로이드와 같은 파일 이름(`engine_benchmark_profile.json`), 같은 코덱. */
internal class KvEngineBenchmarkStore(private val files: AppFileStore) : EngineBenchmarkStorePort {
    override fun exists(): Boolean = files.exists(BenchmarkFileName)

    override fun save(profile: EngineBenchmarkProfile) {
        files.writeAtomically(BenchmarkFileName, EngineBenchmarkCodec.encode(profile))
    }

    override fun load(): EngineBenchmarkProfile? = files.readText(BenchmarkFileName)?.let(EngineBenchmarkCodec::decode)

    fun loadText(): String = files.readText(BenchmarkFileName) ?: "No engine benchmark file recorded."

    private companion object {
        const val BenchmarkFileName = "engine_benchmark_profile.json"
    }
}

/** 마지막 진단 리포트의 사본(안드로이드 `DebugReportMirrorStore`와 같은 파일 이름). */
internal class KvDebugReportMirrorStore(private val files: AppFileStore) : DebugReportMirrorPort {
    override fun save(report: String) {
        files.writeAtomically("last_debug_report.txt", report)
    }
}
