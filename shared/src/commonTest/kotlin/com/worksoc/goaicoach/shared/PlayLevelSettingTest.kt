package com.worksoc.goaicoach.shared

import kotlin.random.Random
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
        assertEquals(3_000L, profile.analysisLimit.timeMillis)
        assertEquals(8, profile.analysisLimit.candidateCount)
        assertEquals(AnalysisPreset.Lite, setting.analysisPreset)
    }

    @Test
    fun beginnerMapsToB32LearningBudget() {
        val setting = PlayLevelSetting(PlayLevelGroup.Beginner, level = 4)
        val profile = setting.toEngineProfile(EngineProfile())

        assertEquals(DifficultyProfile.Beginner, profile.difficulty)
        assertEquals(32, profile.analysisLimit.visits)
        assertEquals(3_000L, profile.analysisLimit.timeMillis)
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
        assertEquals(3_000L, limit.timeMillis)
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
    fun searchTimeSettingsApplyOneLimitToEveryAiStrength() {
        val settings = SearchTimeSettings(SearchTimeLimit.WithinFiveSeconds)

        val fast = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 1)
        val beginner = PlayLevelSetting(PlayLevelGroup.Beginner, level = 1)
        val intermediate = PlayLevelSetting(PlayLevelGroup.Intermediate, level = 1)
        val advanced = PlayLevelSetting(PlayLevelGroup.Advanced, level = 1)

        assertEquals(5_000L, fast.analysisLimitWith(settings).timeMillis)
        assertEquals(5_000L, beginner.analysisLimitWith(settings).timeMillis)
        assertEquals(5_000L, intermediate.analysisLimitWith(settings).timeMillis)
        assertEquals(5_000L, advanced.analysisLimitWith(settings).timeMillis)
    }

    @Test
    fun searchTimeSettingsOffRemovesTimeCapsForEveryAiStrength() {
        val settings = SearchTimeSettings(SearchTimeLimit.Off)
        val fast = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 1)
        val beginner = PlayLevelSetting(PlayLevelGroup.Beginner, level = 1)
        val intermediate = PlayLevelSetting(PlayLevelGroup.Intermediate, level = 1)
        val advanced = PlayLevelSetting(PlayLevelGroup.Advanced, level = 1)

        assertNull(fast.analysisLimitWith(settings).timeMillis)
        assertNull(beginner.analysisLimitWith(settings).timeMillis)
        assertNull(intermediate.analysisLimitWith(settings).timeMillis)
        assertNull(advanced.analysisLimitWith(settings).timeMillis)
        assertEquals("Time limit OFF", settings.summaryText())
    }

    @Test
    fun supportedSearchTimeLimitsRoundLegacyAndMeasuredValuesUpSafely() {
        assertEquals(SearchTimeLimit.WithinOneSecond, SearchTimeLimit.ceilingFor(1_000L))
        assertEquals(SearchTimeLimit.WithinThreeSeconds, SearchTimeLimit.ceilingFor(1_001L))
        assertEquals(SearchTimeLimit.WithinFiveSeconds, SearchTimeLimit.ceilingFor(3_001L))
        assertEquals(SearchTimeLimit.WithinTenSeconds, SearchTimeLimit.ceilingFor(5_001L))
        assertEquals(SearchTimeLimit.WithinTenSeconds, SearchTimeLimit.ceilingFor(20_000L))
    }

    @Test
    fun stageIsClampedToGroupRange() {
        val setting = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 10)

        assertEquals(5, setting.safeLevel)
        assertEquals("빠른 초급 · 초고수", setting.displayLabel)
    }

    @Test
    fun tierLabelDropsTheGroupNameForTheCharacterPicker() {
        assertEquals(
            listOf("초보", "하수", "중수", "고수", "초고수"),
            (1..PlayLevelGroup.FastBeginner.maxLevel).map { level ->
                PlayLevelSetting(PlayLevelGroup.FastBeginner, level).tierLabel
            },
        )
        // 캐릭터화 대상이 아닌 그룹은 기존 "N단계" 표기를 그대로 유지한다.
        assertEquals("4단계", PlayLevelSetting(PlayLevelGroup.Beginner, level = 4).tierLabel)
        assertEquals("초급 4단계", PlayLevelSetting(PlayLevelGroup.Beginner, level = 4).displayLabel)
    }

    @Test
    fun percentileSelectionUsesSortedCandidateIndexes() {
        val policy = MoveSelectionPolicy.PercentileRange(50, 100, "하위 50%")

        assertEquals(5..9, policy.candidateIndexRange(candidateCount = 10))
        assertEquals(1..1, policy.candidateIndexRange(candidateCount = 2))
    }

    @Test
    fun fastBeginnerTopStageAlwaysUsesGtpBestOnlyForFastPlay() {
        // 5단계(초고수)는 여전히 BestOnly + 후보 1개 요청임을 검증(과거 3단계와 동일 동작)
        val policy = PlayLevelSetting(
            group = PlayLevelGroup.FastBeginner,
            level = 5,
        ).selectionPolicy
        val limit = PlayLevelSetting(
            group = PlayLevelGroup.FastBeginner,
            level = 5,
        ).aiMoveAnalysisLimitWith(SearchTimeSettings())

        assertEquals(MoveSelectionPolicy.BestOnly, policy)
        assertEquals(EngineSearchMode.GtpStatefulFast, PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 5).aiMoveSearchMode())
        assertEquals(16, limit.visits)
        assertEquals(1, limit.candidateCount)
        assertEquals(false, limit.includePolicy)
        assertEquals(0..0, policy.candidateIndexRange(candidateCount = 1))
        assertEquals(0..0, policy.candidateIndexRange(candidateCount = 10))
    }

    @Test
    fun fastBeginnerLevelsOneToFourUseBucketedTierSelectionWithSharedCandidateBudget() {
        val expected = mapOf(
            1 to Triple(100, 0, 0),
            2 to Triple(60, 40, 0),
            3 to Triple(20, 60, 20),
            4 to Triple(10, 30, 60),
        )
        for ((level, percents) in expected) {
            val (worst, mid, best) = percents
            val setting = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = level)
            val policy = setting.selectionPolicy as MoveSelectionPolicy.BucketedTierSelection

            assertEquals(worst, policy.worstPercent, "level $level worstPercent")
            assertEquals(mid, policy.midPercent, "level $level midPercent")
            assertEquals(best, policy.bestPercent, "level $level bestPercent")
            // 후보 요청 자체는 오늘과 동일한 GTP fast candidateCount=8을 그대로 쓴다 —
            // 엔진 부하를 늘리지 않는다는 게 이 재설계의 핵심 전제.
            assertEquals(8, setting.aiMoveAnalysisLimitWith(SearchTimeSettings()).candidateCount)
            assertEquals(EngineSearchMode.GtpStatefulFast, setting.aiMoveSearchMode())
        }
    }

    @Test
    fun bestOnlyAlwaysChoosesTopIndex() {
        assertEquals(0..0, MoveSelectionPolicy.BestOnly.candidateIndexRange(candidateCount = 10))
    }

    @Test
    fun candidateBucketRangeMatchesClassificationTable() {
        assertEquals(0..0, candidateBucketRange(1, CandidateBucket.Best))
        assertEquals(null, candidateBucketRange(1, CandidateBucket.Mid))
        assertEquals(null, candidateBucketRange(1, CandidateBucket.Worst))

        assertEquals(0..0, candidateBucketRange(2, CandidateBucket.Best))
        assertEquals(1..1, candidateBucketRange(2, CandidateBucket.Mid))
        assertEquals(null, candidateBucketRange(2, CandidateBucket.Worst))

        assertEquals(0..0, candidateBucketRange(3, CandidateBucket.Best))
        assertEquals(1..1, candidateBucketRange(3, CandidateBucket.Mid))
        assertEquals(2..2, candidateBucketRange(3, CandidateBucket.Worst))

        assertEquals(0..0, candidateBucketRange(4, CandidateBucket.Best))
        assertEquals(1..2, candidateBucketRange(4, CandidateBucket.Mid))
        assertEquals(3..3, candidateBucketRange(4, CandidateBucket.Worst))

        assertEquals(0..0, candidateBucketRange(8, CandidateBucket.Best))
        assertEquals(1..6, candidateBucketRange(8, CandidateBucket.Mid))
        assertEquals(7..7, candidateBucketRange(8, CandidateBucket.Worst))
    }

    @Test
    fun targetBucketAlwaysStartsWithWorstOnFirstOwnMoveForAnyPositiveWorstPercent() {
        val tiers = listOf(
            MoveSelectionPolicy.BucketedTierSelection(100, 0, 0, "초보", "초보"),
            MoveSelectionPolicy.BucketedTierSelection(60, 40, 0, "하수", "하수"),
            MoveSelectionPolicy.BucketedTierSelection(20, 60, 20, "중수", "중수"),
            MoveSelectionPolicy.BucketedTierSelection(10, 30, 60, "고수", "고수"),
        )
        for (tier in tiers) {
            assertEquals(
                CandidateBucket.Worst,
                tier.targetBucket(ownMoveIndex = 0, random = Random(1)),
                "${tier.tierName} must target worst on the very first move",
            )
        }
    }

    @Test
    fun targetBucketConvergesToNominalWorstRatioOverManyMoves() {
        val tier = MoveSelectionPolicy.BucketedTierSelection(10, 30, 60, "고수", "고수")
        val totalMoves = 500
        val worstCount = (0 until totalMoves).count { index ->
            tier.targetBucket(ownMoveIndex = index, random = Random(index)) == CandidateBucket.Worst
        }
        // ceil-경계 통과 방식은 최대 1회 오차로 ceil(worstPercent/100 * totalMoves)에 수렴한다.
        val expected = kotlin.math.ceil(tier.worstPercent * totalMoves / 100.0).toInt()
        assertEquals(expected, worstCount)
    }

    @Test
    fun resolveIndexRangeFallsBackWhenTargetBucketIsEmptyThisTurn() {
        // 고수 1수차는 worst를 목표로 하지만, 후보가 2개뿐이면 worst 버킷이 없다 —
        // mid로 폴백해야 한다(정책상 best로 건너뛰지 않는다).
        val tier = MoveSelectionPolicy.BucketedTierSelection(10, 30, 60, "고수", "고수")
        val range = tier.resolveIndexRange(candidateCount = 2, ownMoveIndex = 0, random = Random(1))
        assertEquals(1..1, range)
    }
}
