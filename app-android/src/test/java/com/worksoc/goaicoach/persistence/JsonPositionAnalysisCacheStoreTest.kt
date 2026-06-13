package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.PositionAnalysisCacheEntry
import com.worksoc.goaicoach.application.PositionAnalysisCacheKey
import com.worksoc.goaicoach.application.PositionAnalysisCacheOrigin
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonPositionAnalysisCacheStoreTest {
    @Test
    fun codecPreservesJsonAnalysisCacheEntry() {
        val limit = AnalysisLimit(
            visits = 32,
            timeMillis = 2_000L,
            candidateCount = 16,
            includePolicy = true,
            refinePolicyMoves = 0,
            minVisitsPerCandidate = 0,
            minTimeMillis = null,
        )
        val entry = PositionAnalysisCacheEntry(
            key = PositionAnalysisCacheKey(
                positionFingerprint = "size=9|next=Black",
                searchMode = EngineSearchMode.JsonPositionAnalysis,
                limit = limit,
            ),
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
                    ),
                ),
                summary = "json summary",
                rootVisits = 35,
                elapsedMillis = 3_067L,
            ),
            createdAtMillis = 1_780_000_000_000L,
            requestedRootVisits = 32,
            rootVisits = 35,
            origin = PositionAnalysisCacheOrigin.OperatorTrusted,
        )

        val decoded = JsonPositionAnalysisCacheCodec.decode(
            JsonPositionAnalysisCacheCodec.encode(listOf(entry)),
        )

        assertEquals(listOf(entry), decoded)
    }
}
