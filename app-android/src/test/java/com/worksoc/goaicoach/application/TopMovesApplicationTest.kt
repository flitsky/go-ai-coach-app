package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopMovesApplicationTest {
    @Test
    fun buildTopMoveAnalysisPlanCreatesBoundedLimitAndCacheKey() {
        val state = GameState.empty()
        val profile = EngineProfile()

        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = profile,
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )

        assertEquals(1, plan.candidateCount)
        assertEquals(1, plan.analysisLimit.candidateCount)
        assertEquals(AnalysisPreset.Lite, plan.analysisKey.preset)
        assertEquals(false, plan.analysisKey.deep)
    }

    @Test
    fun completedAnalysisUpdateBuildsSnapshotAndCachePayload() {
        val state = GameState.empty()
        val coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val result = AnalysisResult(
            status = EngineStatus.ready("analysis complete"),
            candidates = listOf(
                CandidateMove(
                    move = Move.Play(StoneColor.Black, coordinate),
                    pointLoss = 0.0,
                ),
            ),
            summary = "raw",
        )
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )

        val update = buildCompletedTopMoveAnalysisUpdate(
            targetState = state,
            result = result,
            rawCandidateText = "raw candidate text",
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            plan = plan,
            deep = false,
            topMovesEnabled = true,
        )

        assertEquals(1, update.snapshot.scoredPlayCount)
        assertEquals(coordinate, (update.candidateMoves.single().move as Move.Play).coordinate)
        assertTrue(update.candidateText.contains("Analysis cache miss"))
        assertEquals("analysis complete", update.engineMessage)
        assertNotNull(update.cachedResult)
    }

    @Test
    fun completedAnalysisUpdateDoesNotStorePayloadWhenCacheDisabled() {
        val state = GameState.empty()
        val result = AnalysisResult(
            status = EngineStatus.ready("analysis complete"),
            candidates = emptyList(),
            summary = "raw",
        )
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )

        val update = buildCompletedTopMoveAnalysisUpdate(
            targetState = state,
            result = result,
            rawCandidateText = "raw candidate text",
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            plan = plan,
            deep = false,
            topMovesEnabled = false,
            cacheEnabled = false,
        )

        assertTrue(update.candidateText.contains("Analysis cache disabled"))
        assertNull(update.cachedResult)
    }


    @Test
    fun cachedAnalysisUpdateKeepsDisplayMovesOnlyWhenTopMovesEnabled() {
        val state = GameState.empty()
        val snapshot = MoveAnalysisSnapshot.from(
            state = state,
            candidates = listOf(
                CandidateMove(
                    move = Move.Play(
                        player = StoneColor.Black,
                        coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
                    ),
                    pointLoss = 0.0,
                ),
            ),
        )
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )
        val cached = CachedAnalysisResult(snapshot = snapshot, candidateText = "cached text")

        val hidden = buildCachedTopMoveAnalysisUpdate(
            targetState = state,
            cacheKey = plan.analysisKey,
            cached = cached,
            topMovesEnabled = false,
        )
        val shown = buildCachedTopMoveAnalysisUpdate(
            targetState = state,
            cacheKey = plan.analysisKey,
            cached = cached,
            topMovesEnabled = true,
        )

        assertEquals(0, hidden.candidateMoves.size)
        assertEquals(1, shown.candidateMoves.size)
        assertTrue(hidden.engineMessage.startsWith("Move review analysis cache hit"))
        assertTrue(shown.engineMessage.startsWith("Top Moves cache hit"))
    }

    @Test
    fun planShowTopMovesUsesCachedBestMoveWithoutDeepFallback() {
        val state = GameState.empty()
        val snapshot = MoveAnalysisSnapshot.from(
            state = state,
            candidates = listOf(
                CandidateMove(
                    move = Move.Play(
                        player = StoneColor.Black,
                        coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
                    ),
                    pointLoss = 0.0,
                ),
            ),
        )
        val currentPlan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Deep,
            deep = false,
        )

        val showPlan = planShowTopMoves(
            reviewAnalysis = snapshot,
            lastAnalysisKey = currentPlan.analysisKey,
            currentPlan = currentPlan,
            analysisPreset = AnalysisPreset.Deep,
            isEngineBusy = false,
        )

        assertTrue(showPlan is ShowTopMovesPlan.ShowCached)
        assertEquals(1, (showPlan as ShowTopMovesPlan.ShowCached).candidateMoves.size)
    }
}
