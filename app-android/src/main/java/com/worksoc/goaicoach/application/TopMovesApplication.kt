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

internal data class TopMoveAnalysisOperationToken(
    val operation: EngineOperationRequest,
    val analysisKey: AnalysisCacheKey,
)

internal data class TopMoveAnalysisUpdate(
    val snapshot: MoveAnalysisSnapshot,
    val reviewCandidateMoves: List<CandidateMove>,
    val candidateMoves: List<CandidateMove>,
    val candidateText: String,
    val engineMessage: String,
    val cachedResult: CachedAnalysisResult?,
    val undoRestoreResult: CachedAnalysisResult? = null,
)

internal data class TopMoveAnalysisExecutionContext(
    val targetState: GameState,
    val engineProfile: EngineProfile,
    val analysisPreset: AnalysisPreset,
    val topMovesEnabled: Boolean,
    val cacheEnabled: Boolean,
)

internal data class TopMoveAnalysisFailureDisplayPlan(
    val targetState: GameState,
    val engineMessage: String,
    val clearDisplayedTopMoves: Boolean,
    val candidateText: String? = null,
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

internal sealed class TopMoveAnalysisLaunchPlan {
    data object Skip : TopMoveAnalysisLaunchPlan()

    data class RestoreCurrentSnapshot(
        val candidateMoves: List<CandidateMove>,
    ) : TopMoveAnalysisLaunchPlan()

    data class UseCached(
        val analysisKey: AnalysisCacheKey,
        val update: TopMoveAnalysisUpdate,
    ) : TopMoveAnalysisLaunchPlan()

    data class RunEngine(
        val plan: TopMoveAnalysisPlan,
        val deep: Boolean,
        val automatic: Boolean,
    ) : TopMoveAnalysisLaunchPlan()
}

internal data class TopMoveAnalysisLaunchStateUpdate(
    val analysisState: GameSessionAnalysisState,
    val engineMessage: String? = null,
    val effect: GameSessionEffect.RunTopMoveAnalysis? = null,
)

internal fun topMoveAnalysisOperationToken(
    targetState: GameState,
    plan: TopMoveAnalysisPlan,
    sessionGeneration: Long = 0L,
): TopMoveAnalysisOperationToken =
    TopMoveAnalysisOperationToken(
        operation = engineOperationRequest(
            kind = EngineOperationKind.TopMoves,
            state = targetState,
            sessionGeneration = sessionGeneration,
            timeoutPolicy = EngineTimeoutPolicy(
                timeoutMillis = plan.analysisLimit.timeMillis,
                label = "${plan.analysisKey.preset.label}:${plan.analysisLimit.visits}v",
            ),
            fallbackPolicy = EngineFallbackPolicy.CachedAnalysis,
        ),
        analysisKey = plan.analysisKey,
    )

internal fun evaluateTopMoveAnalysisResultGuard(
    token: TopMoveAnalysisOperationToken,
    currentState: GameState,
    currentAnalysisKey: AnalysisCacheKey?,
    currentSessionGeneration: Long = 0L,
): EngineOperationResultGuard {
    val positionGuard = evaluateEngineOperationResultGuard(
        request = token.operation,
        currentState = currentState,
        currentSessionGeneration = currentSessionGeneration,
    )
    if (positionGuard is EngineOperationResultGuard.Discard) {
        return positionGuard
    }
    return if (currentAnalysisKey == token.analysisKey) {
        EngineOperationResultGuard.Apply
    } else {
        EngineOperationResultGuard.Discard(
            reason = "top_moves_analysis result is stale: analysis key changed before result arrived.",
            operation = token.operation.kind.code,
            operationId = token.operation.operationId,
            sessionGeneration = token.operation.sessionGeneration,
        )
    }
}

internal fun buildTopMoveAnalysisFailureDisplayPlan(
    targetState: GameState,
    error: Throwable,
    topMovesEnabled: Boolean,
): TopMoveAnalysisFailureDisplayPlan =
    TopMoveAnalysisFailureDisplayPlan(
        targetState = targetState,
        engineMessage = error.message ?: "Top Moves analysis failed.",
        clearDisplayedTopMoves = topMovesEnabled,
        candidateText = "Top Moves analysis failed.".takeIf { topMovesEnabled },
    )

internal fun GameSessionAnalysisState.applyTopMoveAnalysisLaunchPlan(
    launchPlan: TopMoveAnalysisLaunchPlan,
): TopMoveAnalysisLaunchStateUpdate? =
    when (launchPlan) {
        TopMoveAnalysisLaunchPlan.Skip -> null
        is TopMoveAnalysisLaunchPlan.RestoreCurrentSnapshot ->
            TopMoveAnalysisLaunchStateUpdate(
                analysisState = copy(candidateMoves = launchPlan.candidateMoves),
            )
        is TopMoveAnalysisLaunchPlan.UseCached ->
            TopMoveAnalysisLaunchStateUpdate(
                analysisState = applyTopMoveAnalysisUpdate(
                    update = launchPlan.update,
                    analysisKey = launchPlan.analysisKey,
                ),
                engineMessage = launchPlan.update.engineMessage,
            )
        is TopMoveAnalysisLaunchPlan.RunEngine ->
            TopMoveAnalysisLaunchStateUpdate(
                analysisState = copy(lastAnalysisKey = launchPlan.plan.analysisKey),
                effect = GameSessionEffect.RunTopMoveAnalysis(
                    plan = launchPlan.plan,
                    deep = launchPlan.deep,
                    automatic = launchPlan.automatic,
                ),
            )
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

internal fun buildTopMoveAnalysisLaunchPlan(
    targetState: GameState,
    engineProfile: EngineProfile,
    analysisPreset: AnalysisPreset,
    deep: Boolean,
    automatic: Boolean,
    topMovesEnabled: Boolean,
    currentCandidateMoves: List<CandidateMove>,
    reviewAnalysis: MoveAnalysisSnapshot,
    lastAnalysisKey: AnalysisCacheKey?,
    cachedResultFor: (AnalysisCacheKey) -> CachedAnalysisResult?,
): TopMoveAnalysisLaunchPlan {
    val plan = buildTopMoveAnalysisPlan(
        targetState = targetState,
        engineProfile = engineProfile,
        analysisPreset = analysisPreset,
        deep = deep,
    )

    if (automatic && plan.analysisKey == lastAnalysisKey) {
        return if (topMovesEnabled && currentCandidateMoves.isEmpty() && reviewAnalysis.scoredPlayCount > 0) {
            TopMoveAnalysisLaunchPlan.RestoreCurrentSnapshot(reviewAnalysis.candidatesForDisplay())
        } else {
            TopMoveAnalysisLaunchPlan.Skip
        }
    }

    val cached = cachedResultFor(plan.analysisKey)
    if (cached != null) {
        return TopMoveAnalysisLaunchPlan.UseCached(
            analysisKey = plan.analysisKey,
            update = buildCachedTopMoveAnalysisUpdate(
                targetState = targetState,
                cacheKey = plan.analysisKey,
                cached = cached,
                topMovesEnabled = topMovesEnabled,
            ),
        )
    }

    return TopMoveAnalysisLaunchPlan.RunEngine(
        plan = plan,
        deep = deep,
        automatic = automatic,
    )
}

internal fun GameSessionControllerState.toTopMoveAnalysisLaunchPlan(
    targetState: GameState,
    deep: Boolean,
    automatic: Boolean,
    cachedResultFor: (AnalysisCacheKey) -> CachedAnalysisResult?,
): TopMoveAnalysisLaunchPlan =
    buildTopMoveAnalysisLaunchPlan(
        targetState = targetState,
        engineProfile = core.runtimeState.engineProfile,
        analysisPreset = core.runtimeState.analysisPreset,
        deep = deep,
        automatic = automatic,
        topMovesEnabled = settings.topMovesEnabled,
        currentCandidateMoves = core.analysisState.candidateMoves,
        reviewAnalysis = core.analysisState.reviewAnalysis,
        lastAnalysisKey = core.analysisState.lastAnalysisKey,
        cachedResultFor = cachedResultFor,
    )

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

internal fun GameSessionControllerState.toShowTopMovesPlan(
    isEngineBusy: Boolean,
): ShowTopMovesPlan =
    planShowTopMoves(
        reviewAnalysis = core.analysisState.reviewAnalysis,
        lastAnalysisKey = core.analysisState.lastAnalysisKey,
        currentPlan = buildTopMoveAnalysisPlan(
            targetState = gameState,
            engineProfile = core.runtimeState.engineProfile,
            analysisPreset = core.runtimeState.analysisPreset,
            deep = false,
        ),
        analysisPreset = core.runtimeState.analysisPreset,
        isEngineBusy = isEngineBusy,
    )

private fun List<CandidateMove>.scoredCandidateCount(): Int =
    count { it.pointLoss != null }
