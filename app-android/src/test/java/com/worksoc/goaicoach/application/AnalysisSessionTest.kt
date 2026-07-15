package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.analysis.*
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.DifficultyProfile
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisSessionTest {
    @Test
    fun topMoveCandidateCountUsesLightweightFiveCandidatePolicy() {
        val empty = GameState.empty()
        val nearlyFull = empty.copy(
            stones = buildMap {
                for (row in 0 until BoardSize.Nine.value) {
                    for (column in 0 until BoardSize.Nine.value) {
                        val coordinate = BoardCoordinate(row, column)
                        if (coordinate != BoardCoordinate.fromLabel("E5", BoardSize.Nine)) {
                            put(coordinate, StoneColor.Black)
                        }
                    }
                }
            },
        )

        assertEquals(5, topMoveCandidateCountFor(empty, AnalysisPreset.Lite))
        assertEquals(5, topMoveCandidateCountFor(empty, AnalysisPreset.Deep))
        assertEquals(5, topMoveCandidateCountFor(nearlyFull, AnalysisPreset.Deep))
    }

    @Test
    fun topMovesLimitPromotesFastBeginnerB16WhileKeepingSelectedTimeLimit() {
        val profile = EngineProfile(
            difficulty = DifficultyProfile.Beginner,
            analysisLimit = AnalysisLimit(visits = 16, timeMillis = 250, candidateCount = 8),
        )

        val lite = topMovesAnalysisLimitFor(profile, AnalysisPreset.Lite, candidateCount = 1)
        val balanced = topMovesAnalysisLimitFor(profile, AnalysisPreset.Balanced, candidateCount = 1)

        assertEquals(32, lite.visits)
        assertEquals(250L, lite.timeMillis)
        assertEquals(1, lite.candidateCount)
        assertEquals(0, lite.refinePolicyMoves)

        assertEquals(32, balanced.visits)
        assertEquals(250L, balanced.timeMillis)
        assertEquals(1, balanced.candidateCount)
        assertEquals(0, balanced.refinePolicyMoves)
    }

    @Test
    fun topMovesLimitKeepsProfileTimeLimitForEveryVisitBudget() {
        val beginnerProfile = EngineProfile(
            difficulty = DifficultyProfile.Beginner,
            analysisLimit = AnalysisLimit(visits = 32, timeMillis = 4_000, candidateCount = 16),
        )
        val intermediateProfile = EngineProfile(
            difficulty = DifficultyProfile.Casual,
            analysisLimit = AnalysisLimit(visits = 64, timeMillis = 7_500, candidateCount = 20),
        )

        val beginner = topMovesAnalysisLimitFor(beginnerProfile, AnalysisPreset.Learning, candidateCount = 1)
        val intermediate = topMovesAnalysisLimitFor(intermediateProfile, AnalysisPreset.Balanced, candidateCount = 1)

        assertEquals(32, beginner.visits)
        assertEquals(4_000L, beginner.timeMillis)
        assertEquals(1, beginner.candidateCount)

        assertEquals(64, intermediate.visits)
        assertEquals(7_500L, intermediate.timeMillis)
        assertEquals(1, intermediate.candidateCount)
    }

    @Test
    fun deepTopMovesLimitUsesFullAnalysisVisitsWithoutExceedingSelectedTimeLimit() {
        val profile = EngineProfile(
            difficulty = DifficultyProfile.Beginner,
            analysisLimit = AnalysisLimit(visits = 16, timeMillis = 250, candidateCount = 8),
        )

        val limit = deepTopMovesAnalysisLimitFor(profile, candidateCount = 81)

        assertEquals(1_000, limit.visits)
        assertEquals(250L, limit.timeMillis)
        assertEquals(81, limit.candidateCount)
        assertEquals(AnalysisPreset.Deep.refinePolicyMoves, limit.refinePolicyMoves)
        assertEquals(250L, limit.minTimeMillis)
    }

    @Test
    fun deepTopMovesOffKeepsTimeUncapped() {
        val profile = EngineProfile(
            analysisLimit = AnalysisLimit(visits = 64, timeMillis = null, candidateCount = 20),
        )

        val limit = deepTopMovesAnalysisLimitFor(profile, candidateCount = 5)

        assertNull(limit.timeMillis)
        assertNull(limit.minTimeMillis)
    }

    @Test
    fun analysisCacheTracksHitsMissesAndEvictsLeastRecentlyUsedEntry() {
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
        val firstKey = analysisKeyFor(
            state = state,
            preset = AnalysisPreset.Lite,
            limit = AnalysisLimit(visits = 16, timeMillis = 250, candidateCount = 8),
            deep = false,
        )
        val secondKey = firstKey.copy(deep = true)
        val cache = AnalysisResultCache(maxEntries = 1, mode = AnalysisCacheMode.Enabled)
        val cached = CachedAnalysisResult(snapshot = snapshot, candidateText = "first")

        assertNull(cache.get(firstKey))
        cache.put(firstKey, cached)
        assertSame(cached, cache.get(firstKey))
        cache.put(secondKey, CachedAnalysisResult(snapshot = snapshot, candidateText = "second"))

        assertNull(cache.get(firstKey))
        assertTrue(cache.statsText().contains("entries=1"))
        assertTrue(cache.statsText().contains("hits=1"))
        assertTrue(cache.statsText().contains("misses=2"))
        assertNotEquals(firstKey, secondKey)
    }

    @Test
    fun analysisCacheIsDisabledByDefault() {
        val state = GameState.empty()
        val key = analysisKeyFor(
            state = state,
            preset = AnalysisPreset.Lite,
            limit = AnalysisLimit(visits = 16, timeMillis = 250, candidateCount = 8),
            deep = false,
        )
        val snapshot = MoveAnalysisSnapshot.empty(state)
        val cache = AnalysisResultCache(maxEntries = 1)

        cache.put(key, CachedAnalysisResult(snapshot = snapshot, candidateText = "ignored"))

        assertNull(cache.get(key))
        assertEquals("disabled, entries=0, hits=0, misses=0", cache.statsText())
    }

    @Test
    fun analysisHeadersIncludeCoverageAndBudgetContext() {
        val state = GameState.empty()
        val snapshot = MoveAnalysisSnapshot.empty(state)
        val profile = EngineProfile()
        val limit = AnalysisLimit(visits = 16, timeMillis = 250, candidateCount = 8)

        val text = "KataGo analysis complete."
            .withAnalysisCoverage(snapshot)
            .withTopMovesStrengthHeader(
                profile = profile,
                preset = AnalysisPreset.Lite,
                limit = limit,
                candidateCount = 8,
                deep = false,
            )

        assertTrue(text.contains("Top Moves request: Lite"))
        assertTrue(text.contains("Analysis coverage: legal 81"))
        assertTrue(text.contains("KataGo analysis complete."))
    }
}
