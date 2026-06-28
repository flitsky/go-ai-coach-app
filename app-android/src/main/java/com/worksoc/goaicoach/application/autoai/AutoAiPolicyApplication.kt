package com.worksoc.goaicoach.application.autoai

import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.aiMoveAnalysisLimitWith
import com.worksoc.goaicoach.shared.aiMoveSearchMode

internal fun shouldRequestAiTurn(
    isGameEnded: Boolean,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    shouldShowResumePrompt: Boolean,
    playerSetup: PlayerSetup,
    gameState: GameState,
): Boolean =
    !isGameEnded &&
        isEngineReady &&
        !isEngineBusy &&
        !shouldShowResumePrompt &&
        playerSetup.seatFor(gameState.nextPlayer).isAi

internal fun shouldRequestTopMoveAnalysis(
    isGameEnded: Boolean,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    shouldShowResumePrompt: Boolean,
    playerSetup: PlayerSetup,
    targetState: GameState,
): Boolean =
    !isGameEnded &&
        isEngineReady &&
        !isEngineBusy &&
        !shouldShowResumePrompt &&
        playerSetup.seatFor(targetState.nextPlayer).isHuman

internal fun autoAiTurnDelayMillis(
    playerSetup: PlayerSetup,
    setting: AutoPlayDelaySetting,
): Long =
    if (playerSetup.isAutoPlay()) setting.millis else 0L

internal sealed class AutoAiTurnRequestPlan {
    data object Skip : AutoAiTurnRequestPlan()
    data class Schedule(
        val delayMillis: Long,
    ) : AutoAiTurnRequestPlan()
}

internal sealed class AutoAiTurnScheduleValidationPlan {
    data object Cancel : AutoAiTurnScheduleValidationPlan()
    data class Continue(
        val runPlan: AutoAiTurnRunPlan,
    ) : AutoAiTurnScheduleValidationPlan() {
        val context: AutoAiTurnExecutionContext
            get() = runPlan.context
    }
}

internal data class AutoAiTurnUiState(
    val isPending: Boolean = false,
) {
    fun markScheduled(): AutoAiTurnUiState =
        copy(isPending = true)

    fun clearPending(): AutoAiTurnUiState =
        copy(isPending = false)
}

internal fun AutoAiTurnUiState.applyAutoAiTurnRequestPlan(
    plan: AutoAiTurnRequestPlan,
): AutoAiTurnUiState =
    when (plan) {
        AutoAiTurnRequestPlan.Skip -> this
        is AutoAiTurnRequestPlan.Schedule -> markScheduled()
    }

internal fun AutoAiTurnUiState.applyAutoAiTurnScheduleValidationPlan(
    plan: AutoAiTurnScheduleValidationPlan,
): AutoAiTurnUiState =
    when (plan) {
        AutoAiTurnScheduleValidationPlan.Cancel -> clearPending()
        is AutoAiTurnScheduleValidationPlan.Continue -> this
    }

internal fun AutoAiTurnUiState.completeAutoAiTurnRun(): AutoAiTurnUiState =
    clearPending()

internal fun buildAutoAiTurnRequestPlan(
    isGameEnded: Boolean,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    isAutoAiTurnPending: Boolean,
    shouldShowResumePrompt: Boolean,
    playerSetup: PlayerSetup,
    gameState: GameState,
    autoPlayDelaySetting: AutoPlayDelaySetting,
): AutoAiTurnRequestPlan {
    if (isAutoAiTurnPending) {
        return AutoAiTurnRequestPlan.Skip
    }
    if (
        !shouldRequestAiTurn(
            isGameEnded = isGameEnded,
            isEngineReady = isEngineReady,
            isEngineBusy = isEngineBusy,
            shouldShowResumePrompt = shouldShowResumePrompt,
            playerSetup = playerSetup,
            gameState = gameState,
        )
    ) {
        return AutoAiTurnRequestPlan.Skip
    }

    return AutoAiTurnRequestPlan.Schedule(
        delayMillis = autoAiTurnDelayMillis(playerSetup, autoPlayDelaySetting),
    )
}

internal fun GameSessionControllerState.toAutoAiTurnRequestPlan(
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
): AutoAiTurnRequestPlan =
    buildAutoAiTurnRequestPlan(
        isGameEnded = isGameEnded,
        isEngineReady = isEngineReady,
        isEngineBusy = isEngineBusy,
        isAutoAiTurnPending = isAutoAiTurnPending,
        shouldShowResumePrompt = shouldShowResumePrompt,
        playerSetup = playerSetup,
        gameState = gameState,
        autoPlayDelaySetting = settings.autoPlayDelaySetting,
    )

internal fun buildAutoAiTurnScheduleValidationPlan(
    isGameEnded: Boolean,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    shouldShowResumePrompt: Boolean,
    playerSetup: PlayerSetup,
    gameState: GameState,
    searchTimeSettings: SearchTimeSettings,
    reviewCandidateMoves: List<CandidateMove>,
    scheduledDelayMillis: Long = 0L,
): AutoAiTurnScheduleValidationPlan {
    if (
        !shouldRequestAiTurn(
            isGameEnded = isGameEnded,
            isEngineReady = isEngineReady,
            isEngineBusy = isEngineBusy,
            shouldShowResumePrompt = shouldShowResumePrompt,
            playerSetup = playerSetup,
            gameState = gameState,
        )
    ) {
        return AutoAiTurnScheduleValidationPlan.Cancel
    }

    return AutoAiTurnScheduleValidationPlan.Continue(
        runPlan = AutoAiTurnRunPlan(
            delayMillis = scheduledDelayMillis,
            context = buildAutoAiTurnExecutionContext(
                gameState = gameState,
                playerSetup = playerSetup,
                searchTimeSettings = searchTimeSettings,
                reviewCandidateMoves = reviewCandidateMoves,
            ),
        ),
    )
}

internal fun GameSessionControllerState.toAutoAiTurnScheduleValidationPlan(
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    scheduledDelayMillis: Long = 0L,
): AutoAiTurnScheduleValidationPlan =
    buildAutoAiTurnScheduleValidationPlan(
        isGameEnded = isGameEnded,
        isEngineReady = isEngineReady,
        isEngineBusy = isEngineBusy,
        shouldShowResumePrompt = shouldShowResumePrompt,
        playerSetup = playerSetup,
        gameState = gameState,
        searchTimeSettings = settings.searchTimeSettings,
        reviewCandidateMoves = core.analysisState.reviewCandidateMoves,
        scheduledDelayMillis = scheduledDelayMillis,
    )

internal data class AutoAiTurnRunPlan(
    val delayMillis: Long,
    val context: AutoAiTurnExecutionContext,
)

internal data class AutoAiTurnExecutionContext(
    val turnState: GameState,
    val aiPlayer: StoneColor,
    val playLevel: PlayLevelSetting,
    val analysisLimit: AnalysisLimit,
    val searchMode: EngineSearchMode,
    val isolateSearchCache: Boolean,
    val previousReviewCandidates: List<CandidateMove>,
)

internal fun buildAutoAiTurnExecutionContext(
    gameState: GameState,
    playerSetup: PlayerSetup,
    searchTimeSettings: SearchTimeSettings,
    reviewCandidateMoves: List<CandidateMove>,
    searchMode: EngineSearchMode? = null,
): AutoAiTurnExecutionContext {
    val aiPlayer = gameState.nextPlayer
    val playLevel = playerSetup.seatFor(aiPlayer)
        .aiCharacter
        ?.playLevel
        ?: PlayLevelSetting()
    val resolvedSearchMode = searchMode ?: playLevel.aiMoveSearchMode()
    return AutoAiTurnExecutionContext(
        turnState = gameState,
        aiPlayer = aiPlayer,
        playLevel = playLevel,
        analysisLimit = playLevel.aiMoveAnalysisLimitWith(searchTimeSettings),
        searchMode = resolvedSearchMode,
        isolateSearchCache = playerSetup.isAutoPlay(),
        previousReviewCandidates = reviewCandidateMoves,
    )
}

internal fun GameSessionControllerState.toAutoAiTurnExecutionContext(
    searchMode: EngineSearchMode? = null,
): AutoAiTurnExecutionContext =
    buildAutoAiTurnExecutionContext(
        gameState = gameState,
        playerSetup = playerSetup,
        searchTimeSettings = settings.searchTimeSettings,
        reviewCandidateMoves = core.analysisState.reviewCandidateMoves,
        searchMode = searchMode,
    )
