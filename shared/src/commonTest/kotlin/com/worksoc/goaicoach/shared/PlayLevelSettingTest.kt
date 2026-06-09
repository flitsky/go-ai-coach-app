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
        assertEquals(500L, profile.analysisLimit.timeMillis)
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
        assertEquals(500L, levelSeven.analysisLimit.timeMillis)
        assertEquals(16, levelSeven.analysisLimit.candidateCount)
        assertEquals(MoveSelectionPolicy.PercentileRange(70, 100, "탐색 후보 최하위 30%"), levelOne.selectionPolicy)
        assertEquals(MoveSelectionPolicy.PercentileRange(30, 60, "탐색 후보 상위 30~60%"), levelFour.selectionPolicy)
        assertEquals(MoveSelectionPolicy.BestOnly, levelSeven.selectionPolicy)
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
    fun fastBeginnerStageTwoAvoidsTopCandidateWhenPossible() {
        val policy = PlayLevelSetting(
            group = PlayLevelGroup.FastBeginner,
            level = 2,
        ).selectionPolicy

        assertEquals("최적수 제외 상위 후보", policy.description)
        assertEquals(0..0, policy.candidateIndexRange(candidateCount = 1))
        assertEquals(1..1, policy.candidateIndexRange(candidateCount = 2))
        assertEquals(1..2, policy.candidateIndexRange(candidateCount = 5))
        assertEquals(1..5, policy.candidateIndexRange(candidateCount = 10))
    }

    @Test
    fun bestOnlyAlwaysChoosesTopIndex() {
        assertEquals(0..0, MoveSelectionPolicy.BestOnly.candidateIndexRange(candidateCount = 10))
    }
}
