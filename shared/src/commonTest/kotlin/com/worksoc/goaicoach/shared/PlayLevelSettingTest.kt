package com.worksoc.goaicoach.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayLevelSettingTest {
    @Test
    fun fastBeginnerMapsToB16LiteBudget() {
        val setting = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 1)
        val profile = setting.toEngineProfile(EngineProfile())

        assertEquals(DifficultyProfile.Beginner, profile.difficulty)
        assertEquals(16, profile.analysisLimit.visits)
        assertEquals(1_000L, profile.analysisLimit.timeMillis)
        assertEquals(8, profile.analysisLimit.candidateCount)
        assertEquals(AnalysisPreset.Lite, setting.analysisPreset)
    }

    @Test
    fun beginnerMapsToB32LearningBudget() {
        val setting = PlayLevelSetting(PlayLevelGroup.Beginner, level = 4)
        val profile = setting.toEngineProfile(EngineProfile())

        assertEquals(DifficultyProfile.Beginner, profile.difficulty)
        assertEquals(32, profile.analysisLimit.visits)
        assertEquals(2_000L, profile.analysisLimit.timeMillis)
        assertEquals(16, profile.analysisLimit.candidateCount)
        assertEquals(AnalysisPreset.Learning, setting.analysisPreset)
    }

    @Test
    fun beginnerLevelsShareSameB32RequestAndDifferOnlyBySelectionPolicy() {
        val levelOne = PlayLevelSetting(PlayLevelGroup.Beginner, level = 1)
        val levelFour = PlayLevelSetting(PlayLevelGroup.Beginner, level = 4)
        val levelSeven = PlayLevelSetting(PlayLevelGroup.Beginner, level = 7)

        assertEquals(levelOne.analysisLimit, levelFour.analysisLimit)
        assertEquals(levelOne.analysisLimit, levelSeven.analysisLimit)
        assertEquals(32, levelSeven.analysisLimit.visits)
        assertEquals(2_000L, levelSeven.analysisLimit.timeMillis)
        assertEquals(16, levelSeven.analysisLimit.candidateCount)
        assertEquals(MoveSelectionPolicy.PercentileRange(70, 100, "탐색 후보 최하위 30%"), levelOne.selectionPolicy)
        assertEquals(MoveSelectionPolicy.PercentileRange(30, 60, "탐색 후보 상위 30~60%"), levelFour.selectionPolicy)
        assertEquals(MoveSelectionPolicy.BestOnly, levelSeven.selectionPolicy)
    }

    @Test
    fun turnAnalysisPolicyKeepsAiSelectionBudgetFastAndLevelSpecific() {
        val levelSeven = PlayLevelSetting(PlayLevelGroup.Beginner, level = 7)

        val limit = levelSeven.aiMoveAnalysisLimitWith(SearchTimeSettings())

        assertEquals(32, limit.visits)
        assertEquals(2_000L, limit.timeMillis)
        assertEquals(16, limit.candidateCount)
        assertEquals(true, limit.includePolicy)
        assertEquals(0, limit.refinePolicyMoves)
        assertEquals(0, limit.minVisitsPerCandidate)
        assertEquals(null, limit.minTimeMillis)
        assertEquals(EngineSearchMode.JsonPositionAnalysis, levelSeven.aiMoveSearchMode())
        assertEquals(MoveSelectionPolicy.BestOnly, levelSeven.selectionPolicy)
    }

    @Test
    fun humanReviewAndTopMovesUseFastBestOneBudget() {
        val level = PlayLevelSetting(PlayLevelGroup.Intermediate, level = 5)

        val review = level.turnAnalysisLimitFor(TurnAnalysisPurpose.HumanMoveReview)
        val display = level.turnAnalysisLimitFor(TurnAnalysisPurpose.TopMovesDisplay)

        assertEquals(64, review.visits)
        assertEquals(3_000L, review.timeMillis)
        assertEquals(1, review.candidateCount)
        assertEquals(false, review.includePolicy)
        assertEquals(0, review.refinePolicyMoves)
        assertEquals(review, display)
    }

    @Test
    fun searchTimeSettingsOverrideTrackedVisitTypesOnly() {
        val settings = SearchTimeSettings(
            b16Millis = 1_500L,
            b32Millis = 4_000L,
            b64Millis = 7_500L,
        )

        val fast = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 1)
        val beginner = PlayLevelSetting(PlayLevelGroup.Beginner, level = 1)
        val intermediate = PlayLevelSetting(PlayLevelGroup.Intermediate, level = 1)
        val advanced = PlayLevelSetting(PlayLevelGroup.Advanced, level = 1)

        assertEquals(1_500L, fast.analysisLimitWith(settings).timeMillis)
        assertEquals(4_000L, beginner.analysisLimitWith(settings).timeMillis)
        assertEquals(7_500L, intermediate.analysisLimitWith(settings).timeMillis)
        assertEquals(1_000L, advanced.analysisLimitWith(settings).timeMillis)
    }

    @Test
    fun searchTimeSettingsCanDisableTimeCapsForUncappedVisits() {
        val settings = SearchTimeSettings(
            b16Millis = 1_500L,
            b32Millis = 4_000L,
            b64Millis = 7_500L,
            timeCapEnabled = false,
        )
        val fast = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 1)
        val beginner = PlayLevelSetting(PlayLevelGroup.Beginner, level = 1)
        val intermediate = PlayLevelSetting(PlayLevelGroup.Intermediate, level = 1)
        val advanced = PlayLevelSetting(PlayLevelGroup.Advanced, level = 1)

        assertNull(fast.analysisLimitWith(settings).timeMillis)
        assertNull(beginner.analysisLimitWith(settings).timeMillis)
        assertNull(intermediate.analysisLimitWith(settings).timeMillis)
        assertNull(advanced.analysisLimitWith(settings).timeMillis)
        assertEquals("Time cap OFF", settings.summaryText())
    }

    @Test
    fun stageIsClampedToGroupRange() {
        val setting = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 10)

        assertEquals(3, setting.safeLevel)
        assertEquals("빠른 초급 3단계", setting.displayLabel)
    }

    @Test
    fun percentileSelectionUsesSortedCandidateIndexes() {
        val policy = MoveSelectionPolicy.PercentileRange(50, 100, "하위 50%")

        assertEquals(5..9, policy.candidateIndexRange(candidateCount = 10))
        assertEquals(1..1, policy.candidateIndexRange(candidateCount = 2))
    }

    @Test
    fun fastBeginnerAlwaysUsesGtpBestOnlyForFastPlay() {
        // level = 3은 여전히 BestOnly 임을 검증
        val policy = PlayLevelSetting(
            group = PlayLevelGroup.FastBeginner,
            level = 3,
        ).selectionPolicy
        val limit = PlayLevelSetting(
            group = PlayLevelGroup.FastBeginner,
            level = 3,
        ).aiMoveAnalysisLimitWith(SearchTimeSettings())

        assertEquals(MoveSelectionPolicy.BestOnly, policy)
        assertEquals(EngineSearchMode.GtpStatefulFast, PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 3).aiMoveSearchMode())
        assertEquals(16, limit.visits)
        assertEquals(1, limit.candidateCount)
        assertEquals(false, limit.includePolicy)
        assertEquals(0..0, policy.candidateIndexRange(candidateCount = 1))
        assertEquals(0..0, policy.candidateIndexRange(candidateCount = 10))

        // level = 1, 2는 각각 하위권/중위권 정책 및 candidateCount = 8 임을 검증
        val level1 = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 1)
        val level2 = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 2)

        assertEquals(MoveSelectionPolicy.PercentileRange(70, 100, "탐색 후보 최하위 30%"), level1.selectionPolicy)
        assertEquals(8, level1.aiMoveAnalysisLimitWith(SearchTimeSettings()).candidateCount)

        assertEquals(MoveSelectionPolicy.PercentileRange(40, 70, "탐색 후보 중위 40~70%"), level2.selectionPolicy)
        assertEquals(8, level2.aiMoveAnalysisLimitWith(SearchTimeSettings()).candidateCount)
    }

    @Test
    fun bestOnlyAlwaysChoosesTopIndex() {
        assertEquals(0..0, MoveSelectionPolicy.BestOnly.candidateIndexRange(candidateCount = 10))
    }
}
