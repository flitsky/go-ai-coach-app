package com.worksoc.goaicoach.application.topmoves

import com.worksoc.goaicoach.application.analysis.AnalysisCacheKey
import com.worksoc.goaicoach.application.analysis.CachedAnalysisResult
import com.worksoc.goaicoach.application.analysis.cacheQualityFor
import com.worksoc.goaicoach.application.analysis.toCandidateText
import com.worksoc.goaicoach.application.analysis.withTopMovesStrengthHeader
import com.worksoc.goaicoach.application.analysis.withAnalysisCoverage
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.session.GameSessionEffect
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot

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
        undoRestoreResult = null,
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
    val quality = result.cacheQualityFor(plan.analysisLimit)
    val restorableResult = CachedAnalysisResult(
        snapshot = snapshot,
        candidateText = analysisText,
        quality = quality,
    )
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
            restorableResult
        } else {
            null
        },
        undoRestoreResult = restorableResult.takeIf { it.canRestoreAfterUndo },
    )
}

internal suspend fun EngineSessionClient.runTopMoveAnalysis(
    targetState: GameState,
    engineProfile: EngineProfile,
    analysisPreset: AnalysisPreset,
    plan: TopMoveAnalysisPlan,
    deep: Boolean,
    topMovesEnabled: Boolean,
    cacheEnabled: Boolean,
): TopMoveAnalysisUpdate {
    val result = analyzePosition(
        state = targetState,
        limit = plan.analysisLimit,
        searchMode = plan.searchMode,
    )
    return buildCompletedTopMoveAnalysisUpdate(
        targetState = targetState,
        result = result,
        rawCandidateText = result.toCandidateText(targetState.boardSize),
        engineProfile = engineProfile,
        analysisPreset = analysisPreset,
        plan = plan,
        deep = deep,
        topMovesEnabled = topMovesEnabled,
        cacheEnabled = cacheEnabled,
    )
}

internal suspend fun EngineSessionClient.runTopMoveAnalysisEffect(
    effect: GameSessionEffect.RunTopMoveAnalysis,
    context: TopMoveAnalysisExecutionContext,
): TopMoveAnalysisUpdate =
    runTopMoveAnalysis(
        targetState = context.targetState,
        engineProfile = context.engineProfile,
        analysisPreset = context.analysisPreset,
        plan = effect.plan,
        deep = effect.deep,
        topMovesEnabled = context.topMovesEnabled,
        cacheEnabled = context.cacheEnabled,
    )

internal suspend fun EngineSessionClient.runTopMoveAnalysisWorkflowResult(
    effect: GameSessionEffect.RunTopMoveAnalysis,
    context: TopMoveAnalysisExecutionContext,
): TopMoveAnalysisWorkflowResult =
    runCatching {
        runTopMoveAnalysisEffect(
            effect = effect,
            context = context,
        )
    }.fold(
        onSuccess = { update -> TopMoveAnalysisWorkflowResult.Success(update) },
        onFailure = { error -> TopMoveAnalysisWorkflowResult.Failure(error) },
    )

internal suspend fun EngineSessionClient.runTopMoveAnalysisEffectApplyPlan(
    request: TopMoveAnalysisEffectLaunchRequest,
): TopMoveAnalysisCompletionApplyPlan =
    buildTopMoveAnalysisCompletionPlan(
        result = runTopMoveAnalysisWorkflowResult(
            effect = request.effect,
            context = request.context,
        ),
        token = request.token,
        currentState = request.currentState,
        currentAnalysisKey = request.currentAnalysisKey,
        currentSessionGeneration = request.currentSessionGeneration,
        targetState = request.targetState,
        topMovesEnabled = request.topMovesEnabled,
    ).toApplyPlan()
