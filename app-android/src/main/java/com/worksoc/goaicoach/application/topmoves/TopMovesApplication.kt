package com.worksoc.goaicoach.application.topmoves

import com.worksoc.goaicoach.application.analysis.AnalysisCacheKey
import com.worksoc.goaicoach.application.analysis.CachedAnalysisResult
import com.worksoc.goaicoach.application.analysis.analysisKeyFor
import com.worksoc.goaicoach.application.analysis.deepTopMovesAnalysisLimitFor
import com.worksoc.goaicoach.application.analysis.topMoveCandidateCountFor
import com.worksoc.goaicoach.application.analysis.topMovesAnalysisLimitFor
import com.worksoc.goaicoach.application.autoai.shouldRequestTopMoveAnalysis
import com.worksoc.goaicoach.application.session.GameSessionAnalysisState
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.GameSessionEffect
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot

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
    request: TopMoveAnalysisLaunchRequest,
    cachedResultFor: (AnalysisCacheKey) -> CachedAnalysisResult?,
): TopMoveAnalysisLaunchPlan =
    buildTopMoveAnalysisLaunchPlan(
        targetState = request.targetState,
        engineProfile = request.engineProfile,
        analysisPreset = request.analysisPreset,
        deep = request.deep,
        automatic = request.automatic,
        topMovesEnabled = request.topMovesEnabled,
        currentCandidateMoves = request.currentCandidateMoves,
        reviewAnalysis = request.reviewAnalysis,
        lastAnalysisKey = request.lastAnalysisKey,
        cachedResultFor = cachedResultFor,
    )

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
        request = TopMoveAnalysisLaunchRequest(
            targetState = targetState,
            engineProfile = core.runtimeState.engineProfile,
            analysisPreset = core.runtimeState.analysisPreset,
            deep = deep,
            automatic = automatic,
            topMovesEnabled = settings.topMovesEnabled,
            currentCandidateMoves = core.analysisState.candidateMoves,
            reviewAnalysis = core.analysisState.reviewAnalysis,
            lastAnalysisKey = core.analysisState.lastAnalysisKey,
        ),
        cachedResultFor = cachedResultFor,
    )

internal fun runTopMoveAnalysisApplication(request: TopMoveAnalysisRunRequest) {
    if (request.automatic && request.pendingPostUndoEngineSync) {
        return
    }
    if (
        !shouldRequestTopMoveAnalysis(
            isGameEnded = request.isGameEnded,
            isEngineReady = request.isEngineReady,
            isEngineBusy = request.isEngineBusy,
            shouldShowResumePrompt = request.shouldShowResumePrompt,
            playerSetup = request.playerSetup,
            targetState = request.targetState,
        )
    ) {
        return
    }

    val launchPlan = request.controllerState.toTopMoveAnalysisLaunchPlan(
        targetState = request.targetState,
        deep = request.deep,
        automatic = request.automatic,
        cachedResultFor = request.cachedResultFor,
    )
    val launchUpdate = request.controllerState.core.analysisState
        .applyTopMoveAnalysisLaunchPlan(launchPlan)
        ?: return
    request.applyLaunchUpdate(launchUpdate)
    val effect = launchUpdate.effect ?: return
    val operationToken = topMoveAnalysisOperationToken(
        targetState = request.targetState,
        plan = effect.plan,
        sessionGeneration = request.currentSessionGeneration(),
    )

    request.launchEngineOperation(operationToken.operation) {
        val applyPlan = request.runEngineWork {
            request.engineClient.runTopMoveAnalysisEffectApplyPlan(
                request = TopMoveAnalysisEffectLaunchRequest(
                    effect = effect,
                    context = TopMoveAnalysisExecutionContext(
                        targetState = request.targetState,
                        engineProfile = request.controllerState.core.runtimeState.engineProfile,
                        analysisPreset = request.controllerState.core.runtimeState.analysisPreset,
                        topMovesEnabled = request.controllerState.settings.topMovesEnabled,
                        cacheEnabled = request.analysisCacheEnabled,
                    ),
                    token = operationToken,
                    currentState = request.currentState(),
                    currentAnalysisKey = request.currentAnalysisKey(),
                    currentSessionGeneration = request.currentSessionGeneration(),
                    targetState = request.targetState,
                    topMovesEnabled = request.controllerState.settings.topMovesEnabled,
                ),
            )
        }
        request.applyCompletion(applyPlan)
    }
}
