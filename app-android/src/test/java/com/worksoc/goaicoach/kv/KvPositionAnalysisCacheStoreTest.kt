package com.worksoc.goaicoach.kv

import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheEntry
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheKey
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOrigin
import com.worksoc.goaicoach.persistence.JsonPositionAnalysisCacheCodec as AndroidCodec
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * iOS의 분석 캐시(백로그 #294 — `PositionAnalysisCacheStoreCodecs.kt`)가 안드로이드와 **같은 JSON**을 쓰고 읽는지, 저장소가 같은 규칙으로 도는지.
 * ⚠️ 키 순서는 빼고 견준다 — JVM 시험의 org.json(Maven)은 해시 순서라 기기의 안드로이드 org.json과 다르다([assertSameJson]).
 */
class KvPositionAnalysisCacheStoreTest {
    private val limit = AnalysisLimit(
        visits = 32,
        timeMillis = 2_000L,
        candidateCount = 16,
        includePolicy = true,
        refinePolicyMoves = 0,
        minVisitsPerCandidate = 0,
        minTimeMillis = null,
    )

    private fun entry(fingerprint: String, createdAt: Long = 1_780_000_000_000L) = PositionAnalysisCacheEntry(
        key = PositionAnalysisCacheKey(positionFingerprint = fingerprint, searchMode = EngineSearchMode.JsonPositionAnalysis, limit = limit),
        result = AnalysisResult(
            status = EngineStatus.ready("ready"),
            candidates = listOf(
                CandidateMove(
                    move = Move.Play(StoneColor.Black, BoardCoordinate(row = 4, column = 4)),
                    winRate = 0.55,
                    scoreLead = 1.25,
                    pointLoss = 0.0,
                    visits = 12,
                    engineOrder = 0,
                    note = null,
                ),
                CandidateMove(move = Move.Pass(StoneColor.White), winRate = null, visits = null),
            ),
            summary = "json summary / 한글",
            rootVisits = 35,
            elapsedMillis = 3_067L,
        ),
        createdAtMillis = createdAt,
        requestedRootVisits = 32,
        rootVisits = 35,
        origin = PositionAnalysisCacheOrigin.OperatorTrusted,
    )

    @Test
    fun theJsonIsTheAndroidJsonBothWays() {
        val entries = listOf(entry("size=9|next=Black"), entry("size=13|next=White"))
        assertSameJson(AndroidCodec.encode(entries), JsonPositionAnalysisCacheCodec.encode(entries))
        assertEquals(entries, JsonPositionAnalysisCacheCodec.decode(AndroidCodec.encode(entries)))
        assertEquals(entries, AndroidCodec.decode(JsonPositionAnalysisCacheCodec.encode(entries)))
    }

    @Test
    fun theStoreKeepsWhatItIsGivenUntilItExpires() {
        val store = KvPositionAnalysisCacheStore(JvmAppFileStore(createTempDirectory("kv-cache").toFile()))
        val now = 1_780_000_000_000L
        val stored = entry("size=9|next=Black", createdAt = now)
        store.put(stored, now)
        assertEquals(stored, store.peek(stored.key, now))
        assertNull(store.peek(entry("other").key, now))
        // 아주 먼 뒤에는 만료로 사라진다(규칙은 :shared의 TTL 하나).
        assertNull(store.peek(stored.key, now + 1_000L * 24L * 60L * 60L * 1_000L))
    }
}
