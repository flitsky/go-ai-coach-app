package com.worksoc.goaicoach.application.analysis

import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.session.GameSessionEffect
import com.worksoc.goaicoach.shared.engine.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.engine.EngineOperationKind
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.diagnostic.runObservedEngineOperation
import com.worksoc.goaicoach.shared.engine.engineOperationRequest
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.aiMoveAnalysisLimitWith
import com.worksoc.goaicoach.shared.aiMoveSearchMode
import com.worksoc.goaicoach.shared.analysisFingerprint
import com.worksoc.goaicoach.shared.forcedJsonPositionAnalysis

internal const val JsonPositionAnalysisCacheOptimizationBatchMaxTargets: Int = 10
internal const val JsonPositionAnalysisCacheOpeningInitialMoveCount: Int = 10
internal const val JsonPositionAnalysisCacheOpeningMaxMoveCount: Int = 20
internal const val PostGamePositionAnalysisCacheOptimizationPromptEnabled: Boolean = false

internal data class PositionAnalysisCacheOptimizationPrompt(
    val gameFingerprint: String,
    val moveCount: Int,
    val targetCount: Int,
) {
    val title: String = "이번 판 분석 최적화"
    val message: String =
        "이번 판의 주요 국면을 분석 캐시에 저장해도 될까요?\n" +
            "다음 플레이에서 같은 흐름이 나오면 더 쾌적하게 응수할 수 있습니다.\n\n" +
            "우선 초반 ${JsonPositionAnalysisCacheOpeningInitialMoveCount}수를 확보하고, 안정화되면 ${JsonPositionAnalysisCacheOpeningMaxMoveCount}수까지 확장합니다.\n" +
            "대상: ${moveCount}수 대국 중 최대 ${targetCount}개 JSON 분석"
}

internal data class PositionAnalysisCacheOptimizationTarget(
    val state: GameState,
    val moveNumber: Int,
    val levelLabel: String,
    val cacheLimit: AnalysisLimit,
    val executionLimit: AnalysisLimit,
)

internal data class PositionAnalysisCacheOptimizationPlan(
    val gameFingerprint: String,
    val finalState: GameState,
    val finalMoveCount: Int,
    val targets: List<PositionAnalysisCacheOptimizationTarget>,
) {
    val isEmpty: Boolean = targets.isEmpty()
}

internal data class PositionAnalysisCacheOptimizationResult(
    val requestedTargets: Int,
    val analyzedTargets: Int,
    val reusableTargets: Int,
    val completeTargets: Int,
    val summaries: List<String>,
) {
    fun messageText(): String =
        buildString {
            append("Post-game cache optimization complete: ")
            append("$analyzedTargets/$requestedTargets analyzed, ")
            append("$reusableTargets reusable, $completeTargets complete.")
            if (summaries.isNotEmpty()) {
                append("\n")
                append(summaries.take(5).joinToString("\n"))
            }
        }
}

internal sealed class PositionAnalysisCacheOptimizationWorkflowResult {
    data class Success(
        val result: PositionAnalysisCacheOptimizationResult,
    ) : PositionAnalysisCacheOptimizationWorkflowResult()

    data class Failure(
        val error: Throwable,
    ) : PositionAnalysisCacheOptimizationWorkflowResult()
}

internal suspend fun EngineSessionClient.runPositionAnalysisCacheOptimizationEffect(
    effect: GameSessionEffect.RunPositionCacheOptimization,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): PositionAnalysisCacheOptimizationResult =
    runObservedEngineOperation(
        request = operationRequest ?: engineOperationRequest(
            kind = EngineOperationKind.PositionCacheOptimization,
            state = effect.plan.finalState,
            sessionGeneration = 0L,
            timeoutPolicy = EngineTimeoutPolicy(label = "position-cache-optimization"),
            fallbackPolicy = EngineFallbackPolicy.CachedAnalysis,
        ),
        diagnosticEventLog = diagnosticEventLog,
    ) {
        optimizePositionAnalysisCache(effect.plan)
    }

internal suspend fun EngineSessionClient.runPositionAnalysisCacheOptimizationWorkflowResult(
    effect: GameSessionEffect.RunPositionCacheOptimization,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): PositionAnalysisCacheOptimizationWorkflowResult =
    runCatching {
        runPositionAnalysisCacheOptimizationEffect(
            effect = effect,
            operationRequest = operationRequest,
            diagnosticEventLog = diagnosticEventLog,
        )
    }.fold(
        onSuccess = { result -> PositionAnalysisCacheOptimizationWorkflowResult.Success(result) },
        onFailure = { error -> PositionAnalysisCacheOptimizationWorkflowResult.Failure(error) },
    )

internal data class PositionAnalysisCacheOptimizationUiState(
    val prompt: PositionAnalysisCacheOptimizationPrompt? = null,
    val dismissedGameFingerprint: String? = null,
    val isRunning: Boolean = false,
) {
    fun withPrompt(prompt: PositionAnalysisCacheOptimizationPrompt?): PositionAnalysisCacheOptimizationUiState =
        copy(prompt = prompt)

    fun clearPrompt(): PositionAnalysisCacheOptimizationUiState =
        copy(prompt = null)

    fun dismiss(currentGameFingerprint: String): PositionAnalysisCacheOptimizationUiState =
        copy(
            prompt = null,
            dismissedGameFingerprint = prompt?.gameFingerprint ?: currentGameFingerprint,
        )

    fun accept(plan: PositionAnalysisCacheOptimizationPlan): PositionAnalysisCacheOptimizationUiState =
        copy(
            prompt = null,
            dismissedGameFingerprint = plan.gameFingerprint,
        )

    fun startRunning(): PositionAnalysisCacheOptimizationUiState =
        copy(isRunning = true)

    fun finishRunning(): PositionAnalysisCacheOptimizationUiState =
        copy(isRunning = false)
}

internal fun buildPositionAnalysisCacheOptimizationPlan(
    finalState: GameState,
    playerSetup: PlayerSetup,
    searchTimeSettings: SearchTimeSettings,
    maxTargets: Int = JsonPositionAnalysisCacheOptimizationBatchMaxTargets,
    qualityFor: (GameState, AnalysisLimit) -> PositionAnalysisCacheQuality? = { _, _ -> null },
): PositionAnalysisCacheOptimizationPlan {
    val levels = playerSetup
        .seats()
        .mapNotNull { seat -> seat.aiCharacter?.playLevel }
        .filter { level -> level.aiMoveSearchMode() == EngineSearchMode.JsonPositionAnalysis }
        .distinctBy { level -> level.group to level.safeLevel }

    if (levels.isEmpty() || finalState.moves.isEmpty()) {
        return PositionAnalysisCacheOptimizationPlan(
            gameFingerprint = finalState.analysisFingerprint(),
            finalState = finalState,
            finalMoveCount = finalState.moves.size,
            targets = emptyList(),
        )
    }

    val perLevelTargetCount = (maxTargets / levels.size).coerceAtLeast(1)
    val targets = levels.flatMap { level ->
        val cacheLimit = level.aiMoveAnalysisLimitWith(searchTimeSettings)
            .forcedJsonPositionAnalysis()
        val executionLimit = cacheLimit.copy(timeMillis = null)
        selectProgressiveOpeningOptimizationStates(
            finalState = finalState,
            maxStates = perLevelTargetCount,
            cacheLimit = cacheLimit,
            qualityFor = qualityFor,
        ).map { state ->
            PositionAnalysisCacheOptimizationTarget(
                state = state,
                moveNumber = state.moves.size,
                levelLabel = level.displayLabel,
                cacheLimit = cacheLimit,
                executionLimit = executionLimit,
            )
        }
    }.take(maxTargets)

    return PositionAnalysisCacheOptimizationPlan(
        gameFingerprint = finalState.analysisFingerprint(),
        finalState = finalState,
        finalMoveCount = finalState.moves.size,
        targets = targets,
    )
}

internal fun buildPositionAnalysisCacheOptimizationPrompt(
    isGameEnded: Boolean,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    isOptimizationRunning: Boolean,
    dismissedGameFingerprint: String?,
    plan: PositionAnalysisCacheOptimizationPlan,
    isPromptEnabled: Boolean = PostGamePositionAnalysisCacheOptimizationPromptEnabled,
): PositionAnalysisCacheOptimizationPrompt? {
    if (
        !isPromptEnabled ||
        !isGameEnded ||
        !isEngineReady ||
        isEngineBusy ||
        isOptimizationRunning ||
        plan.isEmpty ||
        dismissedGameFingerprint == plan.gameFingerprint
    ) {
        return null
    }
    return PositionAnalysisCacheOptimizationPrompt(
        gameFingerprint = plan.gameFingerprint,
        moveCount = plan.finalMoveCount,
        targetCount = plan.targets.size,
    )
}

internal fun refreshPositionAnalysisCacheOptimizationPrompt(
    currentState: PositionAnalysisCacheOptimizationUiState,
    isGameEnded: Boolean,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    plan: PositionAnalysisCacheOptimizationPlan,
    isPromptEnabled: Boolean = PostGamePositionAnalysisCacheOptimizationPromptEnabled,
): PositionAnalysisCacheOptimizationUiState {
    if (!isPromptEnabled) {
        return currentState.clearPrompt()
    }
    return currentState.withPrompt(
        buildPositionAnalysisCacheOptimizationPrompt(
            isGameEnded = isGameEnded,
            isEngineReady = isEngineReady,
            isEngineBusy = isEngineBusy,
            isOptimizationRunning = currentState.isRunning,
            dismissedGameFingerprint = currentState.dismissedGameFingerprint,
            plan = plan,
            isPromptEnabled = isPromptEnabled,
        ),
    )
}

private fun selectProgressiveOpeningOptimizationStates(
    finalState: GameState,
    maxStates: Int,
    cacheLimit: AnalysisLimit,
    qualityFor: (GameState, AnalysisLimit) -> PositionAnalysisCacheQuality?,
): List<GameState> {
    if (maxStates <= 0) {
        return emptyList()
    }

    val states = buildOpeningStates(finalState)
    val initialBand = states
        .filter { state -> state.moves.size <= JsonPositionAnalysisCacheOpeningInitialMoveCount }
        .filterNot { state -> qualityFor(state, cacheLimit)?.isComplete == true }
    if (initialBand.isNotEmpty()) {
        return initialBand.take(maxStates)
    }

    return states
        .filter { state -> state.moves.size > JsonPositionAnalysisCacheOpeningInitialMoveCount }
        .filterNot { state -> qualityFor(state, cacheLimit)?.isComplete == true }
        .take(maxStates)
}

private fun buildOpeningStates(finalState: GameState): List<GameState> =
    buildList {
        var state = GameState.empty(
            boardSize = finalState.boardSize,
            ruleset = finalState.ruleset,
        )
        finalState.moves.forEach { move ->
            state = state.play(move)
            if (
                state.moves.size in 1..JsonPositionAnalysisCacheOpeningMaxMoveCount &&
                !state.hasConsecutivePasses()
            ) {
                add(state)
            }
        }
    }.distinctBy { state -> state.analysisFingerprint() }
