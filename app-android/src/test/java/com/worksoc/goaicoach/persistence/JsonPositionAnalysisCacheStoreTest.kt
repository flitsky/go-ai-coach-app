package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheEntry
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheKey
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOrigin
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
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
