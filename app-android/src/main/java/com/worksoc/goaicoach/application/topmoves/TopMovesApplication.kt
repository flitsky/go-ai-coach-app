package com.worksoc.goaicoach.application.topmoves

import com.worksoc.goaicoach.application.session.*

import com.worksoc.goaicoach.application.analysis.AnalysisCacheKey
import com.worksoc.goaicoach.application.analysis.CachedAnalysisResult
import com.worksoc.goaicoach.shared.engine.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.engine.EngineOperationKind
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
import com.worksoc.goaicoach.application.session.GameSessionAnalysisState
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.GameSessionEffect
import com.worksoc.goaicoach.application.analysis.analysisKeyFor
import com.worksoc.goaicoach.application.analysis.deepTopMovesAnalysisLimitFor
import com.worksoc.goaicoach.shared.engine.engineOperationRequest
import com.worksoc.goaicoach.application.engine.operation.evaluateEngineOperationResultGuard
import com.worksoc.goaicoach.application.analysis.cacheQualityFor
import com.worksoc.goaicoach.application.analysis.topMoveCandidateCountFor
import com.worksoc.goaicoach.application.analysis.topMovesAnalysisLimitFor
import com.worksoc.goaicoach.application.analysis.toCandidateText
import com.worksoc.goaicoach.application.analysis.withTopMovesStrengthHeader
import com.worksoc.goaicoach.application.analysis.withAnalysisCoverage
import com.worksoc.goaicoach.application.autoai.AutoAiTurnRunPlan
import com.worksoc.goaicoach.application.autoai.shouldRequestTopMoveAnalysis
import com.worksoc.goaicoach.application.engine.runEngineIo
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.match.PlayerSetup

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

internal sealed class TopMoveAnalysisCompletionPlan {
    data class ApplySuccess(
        val update: TopMoveAnalysisUpdate,
        val analysisKey: AnalysisCacheKey,
    ) : TopMoveAnalysisCompletionPlan()

    data class ApplyFailure(
        val display: TopMoveAnalysisFailureDisplayPlan,
    ) : TopMoveAnalysisCompletionPlan()

    data class Discard(
        val discard: EngineOperationResultGuard.Discard,
    ) : TopMoveAnalysisCompletionPlan()
}

internal sealed class TopMoveAnalysisCompletionApplyPlan {
    data class ApplySuccess(
        val update: TopMoveAnalysisUpdate,
        val analysisKey: AnalysisCacheKey,
    ) : TopMoveAnalysisCompletionApplyPlan()

    data class ApplyFailure(
        val display: TopMoveAnalysisFailureDisplayPlan,
    ) : TopMoveAnalysisCompletionApplyPlan()

    data class Discard(
        val discard: EngineOperationResultGuard.Discard,
    ) : TopMoveAnalysisCompletionApplyPlan()
}

internal sealed class TopMoveAnalysisWorkflowResult {
    data class Success(
        val update: TopMoveAnalysisUpdate,
    ) : TopMoveAnalysisWorkflowResult()

    data class Failure(
        val error: Throwable,
    ) : TopMoveAnalysisWorkflowResult()
}

internal data class TopMoveAnalysisExecutionContext(
    val targetState: GameState,
    val engineProfile: EngineProfile,
    val analysisPreset: AnalysisPreset,
    val topMovesEnabled: Boolean,
    val cacheEnabled: Boolean,
)

internal data class TopMoveAnalysisEffectLaunchRequest(
    val effect: GameSessionEffect.RunTopMoveAnalysis,
    val context: TopMoveAnalysisExecutionContext,
    val token: TopMoveAnalysisOperationToken,
    val currentState: GameState,
    val currentAnalysisKey: AnalysisCacheKey?,
    val currentSessionGeneration: Long,
    val targetState: GameState,
    val topMovesEnabled: Boolean,
)

internal data class TopMoveAnalysisRunRequest(
    val engineClient: EngineSessionClient,
    val controllerState: GameSessionControllerState,
    val targetState: GameState,
    val deep: Boolean,
    val automatic: Boolean,
    val pendingPostUndoEngineSync: Boolean,
    val isGameEnded: Boolean,
    val isEngineReady: Boolean,
    val isEngineBusy: Boolean,
    val shouldShowResumePrompt: Boolean,
    val playerSetup: PlayerSetup,
    val analysisCacheEnabled: Boolean,
    val cachedResultFor: (AnalysisCacheKey) -> CachedAnalysisResult?,
    val currentState: () -> GameState,
    val currentAnalysisKey: () -> AnalysisCacheKey?,
    val currentSessionGeneration: () -> Long,
    val launchEngineOperation: (EngineOperationRequest, suspend () -> Unit) -> Unit,
    val runEngineWork: suspend (suspend () -> TopMoveAnalysisCompletionApplyPlan) -> TopMoveAnalysisCompletionApplyPlan =
        { block -> runEngineIo { block() } },
    val applyLaunchUpdate: (TopMoveAnalysisLaunchStateUpdate) -> Unit,
    val applyCompletion: (TopMoveAnalysisCompletionApplyPlan) -> Unit,
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

internal data class ShowTopMovesStateUpdate(
    val settingsState: GameSessionSettingsState,
    val analysisState: GameSessionAnalysisState,
    val engineMessage: String?,
)

internal data class ShowTopMovesAnalysisRequest(
    val targetState: GameState,
    val deep: Boolean,
)

internal data class ShowTopMovesApplicationPlan(
    val update: ShowTopMovesStateUpdate,
    val analysisRequest: ShowTopMovesAnalysisRequest? = null,
)

internal data class ShowTopMovesRunRequest(
    val controllerState: GameSessionControllerState,
    val isGameEnded: Boolean,
    val isEngineReady: Boolean,
    val isEngineBusy: Boolean,
    val shouldShowResumePrompt: Boolean,
    val playerSetup: PlayerSetup,
    val applyUpdate: (ShowTopMovesStateUpdate) -> Unit,
    val requestAnalysis: (ShowTopMovesAnalysisRequest) -> Unit,
)

internal data class HideTopMovesRunRequest(
    val controllerState: GameSessionControllerState,
    val applyUpdate: (ShowTopMovesStateUpdate) -> Unit,
)

internal data class SearchTimeTopMovesResetRunRequest(
    val analysisState: GameSessionAnalysisState,
    val state: GameState,
    val applyAnalysisState: (GameSessionAnalysisState) -> Unit,
)

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

internal data class TopMoveAnalysisLaunchRequest(
    val targetState: GameState,
    val engineProfile: EngineProfile,
    val analysisPreset: AnalysisPreset,
    val deep: Boolean,
    val automatic: Boolean,
    val topMovesEnabled: Boolean,
    val currentCandidateMoves: List<CandidateMove>,
    val reviewAnalysis: MoveAnalysisSnapshot,
    val lastAnalysisKey: AnalysisCacheKey?,
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

internal fun buildTopMoveAnalysisSuccessCompletionPlan(
    token: TopMoveAnalysisOperationToken,
    currentState: GameState,
    currentAnalysisKey: AnalysisCacheKey?,
    currentSessionGeneration: Long,
    update: TopMoveAnalysisUpdate,
): TopMoveAnalysisCompletionPlan =
    when (
        val guard = evaluateTopMoveAnalysisResultGuard(
            token = token,
            currentState = currentState,
            currentAnalysisKey = currentAnalysisKey,
            currentSessionGeneration = currentSessionGeneration,
        )
    ) {
        EngineOperationResultGuard.Apply ->
            TopMoveAnalysisCompletionPlan.ApplySuccess(
                update = update,
                analysisKey = token.analysisKey,
            )

        is EngineOperationResultGuard.Discard ->
            TopMoveAnalysisCompletionPlan.Discard(guard)
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

internal fun buildTopMoveAnalysisFailureCompletionPlan(
    token: TopMoveAnalysisOperationToken,
    currentState: GameState,
    currentAnalysisKey: AnalysisCacheKey?,
    currentSessionGeneration: Long,
    targetState: GameState,
    error: Throwable,
    topMovesEnabled: Boolean,
): TopMoveAnalysisCompletionPlan =
    when (
        val guard = evaluateTopMoveAnalysisResultGuard(
            token = token,
            currentState = currentState,
            currentAnalysisKey = currentAnalysisKey,
            currentSessionGeneration = currentSessionGeneration,
        )
    ) {
        EngineOperationResultGuard.Apply ->
            TopMoveAnalysisCompletionPlan.ApplyFailure(
                buildTopMoveAnalysisFailureDisplayPlan(
                    targetState = targetState,
                    error = error,
                    topMovesEnabled = topMovesEnabled,
                ),
            )

        is EngineOperationResultGuard.Discard ->
            TopMoveAnalysisCompletionPlan.Discard(guard)
    }

internal fun buildTopMoveAnalysisCompletionPlan(
    result: TopMoveAnalysisWorkflowResult,
    token: TopMoveAnalysisOperationToken,
    currentState: GameState,
    currentAnalysisKey: AnalysisCacheKey?,
    currentSessionGeneration: Long,
    targetState: GameState,
    topMovesEnabled: Boolean,
): TopMoveAnalysisCompletionPlan =
    when (result) {
        is TopMoveAnalysisWorkflowResult.Success ->
            buildTopMoveAnalysisSuccessCompletionPlan(
                token = token,
                currentState = currentState,
                currentAnalysisKey = currentAnalysisKey,
                currentSessionGeneration = currentSessionGeneration,
                update = result.update,
            )

        is TopMoveAnalysisWorkflowResult.Failure ->
            buildTopMoveAnalysisFailureCompletionPlan(
                token = token,
                currentState = currentState,
                currentAnalysisKey = currentAnalysisKey,
                currentSessionGeneration = currentSessionGeneration,
                targetState = targetState,
                error = result.error,
                topMovesEnabled = topMovesEnabled,
            )
    }

internal fun TopMoveAnalysisCompletionPlan.toApplyPlan(): TopMoveAnalysisCompletionApplyPlan =
    when (this) {
        is TopMoveAnalysisCompletionPlan.ApplySuccess ->
            TopMoveAnalysisCompletionApplyPlan.ApplySuccess(
                update = update,
                analysisKey = analysisKey,
            )

        is TopMoveAnalysisCompletionPlan.ApplyFailure ->
            TopMoveAnalysisCompletionApplyPlan.ApplyFailure(display)

        is TopMoveAnalysisCompletionPlan.Discard ->
            TopMoveAnalysisCompletionApplyPlan.Discard(discard)
    }

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

internal fun GameSessionControllerState.toShowTopMovesApplicationPlan(
    isGameEnded: Boolean,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    shouldShowResumePrompt: Boolean,
    playerSetup: PlayerSetup,
): ShowTopMovesApplicationPlan {
    if (
        !shouldRequestTopMoveAnalysis(
            isGameEnded = isGameEnded,
            isEngineReady = isEngineReady,
            isEngineBusy = isEngineBusy,
            shouldShowResumePrompt = shouldShowResumePrompt,
            playerSetup = playerSetup,
            targetState = gameState,
        )
    ) {
        return ShowTopMovesApplicationPlan(
            update = ShowTopMovesStateUpdate(
                settingsState = settings.hideTopMoves(),
                analysisState = core.analysisState.clearTopMoveSpots(),
                engineMessage = "Top Moves is available only on human turns.",
            ),
        )
    }

    return when (val plan = toShowTopMovesPlan(isEngineBusy = isEngineBusy)) {
        is ShowTopMovesPlan.ShowCached ->
            ShowTopMovesApplicationPlan(
                update = ShowTopMovesStateUpdate(
                    settingsState = settings.showTopMoves(),
                    analysisState = core.analysisState.copy(candidateMoves = plan.candidateMoves),
                    engineMessage = plan.engineMessage,
                ),
            )

        is ShowTopMovesPlan.RequestAnalysis ->
            ShowTopMovesApplicationPlan(
                update = ShowTopMovesStateUpdate(
                    settingsState = settings.showTopMoves(),
                    analysisState = core.analysisState.copy(candidateMoves = plan.candidateMoves),
                    engineMessage = plan.engineMessage,
                ),
                analysisRequest = ShowTopMovesAnalysisRequest(
                    targetState = gameState,
                    deep = plan.deep,
                ),
            )
    }
}

internal fun runShowTopMovesApplication(request: ShowTopMovesRunRequest) {
    val plan = request.controllerState.toShowTopMovesApplicationPlan(
        isGameEnded = request.isGameEnded,
        isEngineReady = request.isEngineReady,
        isEngineBusy = request.isEngineBusy,
        shouldShowResumePrompt = request.shouldShowResumePrompt,
        playerSetup = request.playerSetup,
    )
    request.applyUpdate(plan.update)
    plan.analysisRequest?.let(request.requestAnalysis)
}

internal fun GameSessionControllerState.toHideTopMovesStateUpdate(): ShowTopMovesStateUpdate =
    ShowTopMovesStateUpdate(
        settingsState = settings.hideTopMoves(),
        analysisState = core.analysisState.clearTopMoveSpots(),
        engineMessage = "Top Moves hidden. Background move review keeps using fast best-1 analysis.",
    )

internal fun runHideTopMovesApplication(request: HideTopMovesRunRequest) {
    request.applyUpdate(request.controllerState.toHideTopMovesStateUpdate())
}

internal fun GameSessionAnalysisState.toSearchTimeTopMovesResetState(state: GameState): GameSessionAnalysisState =
    clearTopMoveSpots("Search time changed. Analysis cache will rebuild with the new time cap.")
        .clearReviewAnalysis(state)
        .copy(lastAnalysisKey = null)

internal fun runSearchTimeTopMovesResetApplication(request: SearchTimeTopMovesResetRunRequest) {
    request.applyAnalysisState(
        request.analysisState.toSearchTimeTopMovesResetState(request.state),
    )
}

private fun List<CandidateMove>.scoredCandidateCount(): Int =
    count { it.pointLoss != null }
