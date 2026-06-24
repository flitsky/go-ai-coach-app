package com.worksoc.goaicoach.shared

enum class TurnAnalysisPurpose {
    AiMoveSelection,
    HumanMoveReview,
    TopMovesDisplay,
    ScoreGraph,
}

fun PlayLevelSetting.turnAnalysisLimitFor(
    purpose: TurnAnalysisPurpose,
    candidateCount: Int? = null,
): AnalysisLimit =
    when (purpose) {
        TurnAnalysisPurpose.AiMoveSelection ->
            analysisLimit.fastCandidateAnalysis(candidateCount ?: analysisLimit.candidateCount)
        TurnAnalysisPurpose.HumanMoveReview,
        TurnAnalysisPurpose.TopMovesDisplay ->
            analysisLimit.fastCandidateAnalysis(candidateCount ?: 1)
        TurnAnalysisPurpose.ScoreGraph ->
            analysisLimit.copy(candidateCount = candidateCount ?: 1)
    }

fun EngineProfile.turnAnalysisLimitFor(
    purpose: TurnAnalysisPurpose,
    candidateCount: Int? = null,
): AnalysisLimit =
    when (purpose) {
        TurnAnalysisPurpose.AiMoveSelection,
        TurnAnalysisPurpose.HumanMoveReview,
        TurnAnalysisPurpose.TopMovesDisplay ->
            analysisLimit.fastCandidateAnalysis(candidateCount ?: analysisLimit.candidateCount)
        TurnAnalysisPurpose.ScoreGraph ->
            analysisLimit.copy(candidateCount = candidateCount ?: 1)
    }

fun AnalysisLimit.fastCandidateAnalysis(candidateCount: Int = this.candidateCount): AnalysisLimit =
    copy(
        candidateCount = candidateCount,
        includePolicy = false,
        refinePolicyMoves = 0,
        minVisitsPerCandidate = 0,
        minTimeMillis = null,
    )

fun PlayLevelSetting.aiMoveSearchMode(): EngineSearchMode =
    if (group == PlayLevelGroup.FastBeginner) {
        EngineSearchMode.GtpStatefulFast
    } else {
        EngineSearchMode.JsonPositionAnalysis
    }

fun PlayLevelSetting.aiMoveAnalysisLimitWith(
    searchTimeSettings: SearchTimeSettings,
): AnalysisLimit {
    val base = analysisLimitWith(searchTimeSettings)
    return when (aiMoveSearchMode()) {
        EngineSearchMode.GtpStatefulFast -> {
            val count = if (selectionPolicy is MoveSelectionPolicy.BestOnly) 1 else base.candidateCount
            base.fastCandidateAnalysis(candidateCount = count)
        }
        EngineSearchMode.JsonPositionAnalysis ->
            base.copy(
                includePolicy = true,
                refinePolicyMoves = 0,
                minVisitsPerCandidate = 0,
                minTimeMillis = null,
            )
    }
}

fun AnalysisLimit.forcedJsonPositionAnalysis(): AnalysisLimit =
    if (includePolicy || refinePolicyMoves > 0) {
        this
    } else {
        copy(
            includePolicy = true,
            refinePolicyMoves = 0,
            minVisitsPerCandidate = 0,
            minTimeMillis = null,
        )
    }
