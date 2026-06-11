package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot

internal data class TopMoveAnalysisPlan(
    val candidateCount: Int,
    val analysisLimit: AnalysisLimit,
    val analysisKey: AnalysisCacheKey,
)

internal data class TopMoveAnalysisUpdate(
    val snapshot: MoveAnalysisSnapshot,
    val reviewCandidateMoves: List<CandidateMove>,
    val candidateMoves: List<CandidateMove>,
    val candidateText: String,
    val engineMessage: String,
    val cachedResult: CachedAnalysisResult?,
)

internal sealed class ShowTopMovesPlan {
    data class ShowCached(
        val candidateMoves: List<CandidateMove>,
        val engineMessage: String,
    ) : ShowTopMovesPlan()

    data class RequestAnalysis(
        val deep: Boolean,
        val candidateMoves: List<CandidateMove>,
        val engineMessage: String? = null,
    ) : ShowTopMovesPlan()
}

internal fun buildTopMoveAnalysisPlan(
    targetState: GameState,
    engineProfile: EngineProfile,
    analysisPreset: AnalysisPreset,
    deep: Boolean,
): TopMoveAnalysisPlan {
    val candidateCount = topMoveCandidateCountFor(targetState, analysisPreset)
    val analysisLimit = if (deep) {
        deepTopMovesAnalysisLimitFor(engineProfile, candidateCount)
    } else {
        topMovesAnalysisLimitFor(engineProfile, analysisPreset, candidateCount)
    }
    return TopMoveAnalysisPlan(
        candidateCount = candidateCount,
        analysisLimit = analysisLimit,
        analysisKey = analysisKeyFor(targetState, analysisPreset, analysisLimit, deep),
    )
}

internal fun buildCachedTopMoveAnalysisUpdate(
    targetState: GameState,
    cacheKey: AnalysisCacheKey,
    cached: CachedAnalysisResult,
    topMovesEnabled: Boolean,
): TopMoveAnalysisUpdate {
    val snapshot = cached.snapshot
    return TopMoveAnalysisUpdate(
        snapshot = snapshot,
        reviewCandidateMoves = snapshot.candidatesForReview(),
        candidateMoves = if (topMovesEnabled) snapshot.candidatesForDisplay() else emptyList(),
        candidateText = "Analysis cache hit: ${cacheKey.preset.label}.\n${cached.candidateText}",
        engineMessage = if (topMovesEnabled) {
            "Top Moves cache hit for ${targetState.nextPlayer.label}: ${snapshot.scoredPlayCount}/${snapshot.legalPlayCount} legal spot(s) scored."
        } else {
            "Move review analysis cache hit for ${targetState.nextPlayer.label}: ${snapshot.scoredPlayCount}/${snapshot.legalPlayCount} legal spot(s) scored."
        },
        cachedResult = null,
    )
}

internal fun buildCompletedTopMoveAnalysisUpdate(
    targetState: GameState,
    result: AnalysisResult,
    rawCandidateText: String,
    engineProfile: EngineProfile,
    analysisPreset: AnalysisPreset,
    plan: TopMoveAnalysisPlan,
    deep: Boolean,
    topMovesEnabled: Boolean,
    cacheEnabled: Boolean = true,
): TopMoveAnalysisUpdate {
    val snapshot = MoveAnalysisSnapshot.from(targetState, result.candidates)
    val analysisText = rawCandidateText
        .withAnalysisCoverage(snapshot)
        .withTopMovesStrengthHeader(
            profile = engineProfile,
            preset = analysisPreset,
            limit = plan.analysisLimit,
            candidateCount = plan.candidateCount,
            deep = deep,
        )
    val cacheText = if (cacheEnabled) {
        "Analysis cache miss: stored ${analysisPreset.label} result."
    } else {
        "Analysis cache disabled: fresh ${analysisPreset.label} result."
    }
    return TopMoveAnalysisUpdate(
        snapshot = snapshot,
        reviewCandidateMoves = snapshot.candidatesForReview(),
        candidateMoves = if (topMovesEnabled) snapshot.candidatesForDisplay() else emptyList(),
        candidateText = "$cacheText\n$analysisText",
        engineMessage = if (topMovesEnabled) {
            result.status.message
        } else {
            "Move review analysis ready for ${targetState.nextPlayer.label}: ${snapshot.scoredPlayCount}/${snapshot.legalPlayCount} legal spot(s) scored."
        },
        cachedResult = if (cacheEnabled) {
            CachedAnalysisResult(
                snapshot = snapshot,
                candidateText = analysisText,
            )
        } else {
            null
        },
    )
}

internal fun planShowTopMoves(
    reviewAnalysis: MoveAnalysisSnapshot,
    lastAnalysisKey: AnalysisCacheKey?,
    currentPlan: TopMoveAnalysisPlan,
    analysisPreset: AnalysisPreset,
    isEngineBusy: Boolean,
): ShowTopMovesPlan {
    if (reviewAnalysis.hasEngineCandidates && lastAnalysisKey == currentPlan.analysisKey) {
        val candidateMoves = reviewAnalysis.candidatesForDisplay()
        return ShowTopMovesPlan.ShowCached(
            candidateMoves = candidateMoves,
            engineMessage = "Showing ${candidateMoves.scoredCandidateCount()} scored best move from current ${analysisPreset.label} analysis.",
        )
    }

    return ShowTopMovesPlan.RequestAnalysis(
        deep = false,
        candidateMoves = emptyList(),
    )
}

private fun List<CandidateMove>.scoredCandidateCount(): Int =
    count { it.pointLoss != null }
