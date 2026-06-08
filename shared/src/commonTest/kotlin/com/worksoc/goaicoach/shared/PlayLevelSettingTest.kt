package com.worksoc.goaicoach.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayLevelSettingTest {
    @Test
    fun fastBeginnerMapsToB16LiteBudget() {
        val setting = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 1)
        val profile = setting.toEngineProfile(EngineProfile())

        assertEquals(DifficultyProfile.Beginner, profile.difficulty)
        assertEquals(16, profile.analysisLimit.visits)
        assertEquals(250L, profile.analysisLimit.timeMillis)
        assertEquals(8, profile.analysisLimit.candidateCount)
        assertEquals(AnalysisPreset.Lite, setting.analysisPreset)
    }

    @Test
    fun beginnerMapsToB32LearningBudget() {
        val setting = PlayLevelSetting(PlayLevelGroup.Beginner, level = 4)
        val profile = setting.toEngineProfile(EngineProfile())

        assertEquals(DifficultyProfile.Beginner, profile.difficulty)
        assertEquals(32, profile.analysisLimit.visits)
        assertEquals(350L, profile.analysisLimit.timeMillis)
        assertEquals(16, profile.analysisLimit.candidateCount)
        assertEquals(AnalysisPreset.Learning, setting.analysisPreset)
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
    fun bestOnlyAlwaysChoosesTopIndex() {
        assertEquals(0..0, MoveSelectionPolicy.BestOnly.candidateIndexRange(candidateCount = 10))
    }
}
