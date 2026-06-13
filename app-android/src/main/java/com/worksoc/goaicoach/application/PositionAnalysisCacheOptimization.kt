package com.worksoc.goaicoach.application

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

internal data class PositionAnalysisCacheOptimizationPrompt(
    val gameFingerprint: String,
    val moveCount: Int,
    val targetCount: Int,
) {
    val title: String = "이번 판 분석 최적화"
    val message: String =
        "이번 판의 주요 국면을 분석 캐시에 저장해도 될까요?\n" +
            "다음 플레이에서 같은 흐름이 나오면 더 쾌적하게 응수할 수 있습니다.\n\n" +
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

internal fun buildPositionAnalysisCacheOptimizationPlan(
    finalState: GameState,
    playerSetup: PlayerSetup,
    searchTimeSettings: SearchTimeSettings,
    maxTargets: Int = JsonPositionAnalysisCacheMaxEntries,
): PositionAnalysisCacheOptimizationPlan {
    val levels = playerSetup
        .seats()
        .mapNotNull { seat -> seat.aiCharacter?.playLevel }
        .filter { level -> level.aiMoveSearchMode() == EngineSearchMode.JsonPositionAnalysis }
        .distinctBy { level -> level.group to level.safeLevel }

    if (levels.isEmpty() || finalState.moves.isEmpty()) {
        return PositionAnalysisCacheOptimizationPlan(
            gameFingerprint = finalState.analysisFingerprint(),
            finalMoveCount = finalState.moves.size,
            targets = emptyList(),
        )
    }

    val perLevelTargetCount = (maxTargets / levels.size).coerceAtLeast(1)
    val targets = levels.flatMap { level ->
        val cacheLimit = level.aiMoveAnalysisLimitWith(searchTimeSettings)
            .forcedJsonPositionAnalysis()
        val executionLimit = cacheLimit.copy(timeMillis = null)
        selectOptimizationStates(
            finalState = finalState,
            maxStates = perLevelTargetCount,
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
): PositionAnalysisCacheOptimizationPrompt? {
    if (
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

private fun selectOptimizationStates(
    finalState: GameState,
    maxStates: Int,
): List<GameState> {
    if (maxStates <= 0) {
        return emptyList()
    }

    val states = buildList {
        var state = GameState.empty(
            boardSize = finalState.boardSize,
            ruleset = finalState.ruleset,
        )
        add(state)
        finalState.moves.forEach { move ->
            state = state.play(move)
            if (!state.hasConsecutivePasses()) {
                add(state)
            }
        }
    }.distinctBy { state -> state.analysisFingerprint() }

    if (states.size <= maxStates) {
        return states
    }
    if (maxStates == 1) {
        return listOf(states.last())
    }

    val lastIndex = states.lastIndex
    return (0 until maxStates)
        .map { index -> states[(index * lastIndex) / (maxStates - 1)] }
        .distinctBy { state -> state.analysisFingerprint() }
}
