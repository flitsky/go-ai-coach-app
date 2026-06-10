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
