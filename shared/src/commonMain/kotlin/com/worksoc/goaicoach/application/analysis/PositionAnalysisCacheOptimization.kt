package com.worksoc.goaicoach.application.analysis

import com.worksoc.goaicoach.application.contract.PositionAnalysisCacheOptimizationPlan
import com.worksoc.goaicoach.application.contract.PositionAnalysisCacheOptimizationTarget
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.analysisFingerprint
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.shared.policy.aiMoveAnalysisLimitWith
import com.worksoc.goaicoach.shared.policy.aiMoveSearchMode
import com.worksoc.goaicoach.shared.policy.forcedJsonPositionAnalysis

internal const val JsonPositionAnalysisCacheOptimizationBatchMaxTargets: Int = 10
const val JsonPositionAnalysisCacheOpeningInitialMoveCount: Int = 10
const val JsonPositionAnalysisCacheOpeningMaxMoveCount: Int = 20
const val PostGamePositionAnalysisCacheOptimizationPromptEnabled: Boolean = false

data class PositionAnalysisCacheOptimizationPrompt(
    val gameFingerprint: String,
    val moveCount: Int,
    val targetCount: Int,
)

data class PositionAnalysisCacheOptimizationResult(
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

data class PositionAnalysisCacheOptimizationUiState(
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

fun buildPositionAnalysisCacheOptimizationPrompt(
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
        // The move history of a handicap game starts with White. Replay from
        // the same initial handicap position rather than an empty Black-to-play
        // board, otherwise the first White move violates the game rules.
        var state = GameState.withHandicap(
            boardSize = finalState.boardSize,
            ruleset = finalState.ruleset,
            handicapCount = finalState.handicapCount,
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
