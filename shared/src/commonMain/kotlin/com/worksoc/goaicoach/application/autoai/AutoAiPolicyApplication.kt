package com.worksoc.goaicoach.application.autoai

import com.worksoc.goaicoach.application.contract.AutoAiTurnExecutionContext
import com.worksoc.goaicoach.application.contract.AutoAiTurnRunPlan
import com.worksoc.goaicoach.application.session.AutoAiTurnUiState
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.shared.policy.aiMoveAnalysisLimitWith
import com.worksoc.goaicoach.shared.policy.aiMoveSearchMode

fun shouldRequestAiTurn(
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
    topMovesEnabled: Boolean = true,
): Boolean =
    !isGameEnded &&
        isEngineReady &&
        !isEngineBusy &&
        !shouldShowResumePrompt &&
        playerSetup.seatFor(targetState.nextPlayer).isHuman &&
        topMovesEnabled

fun autoAiTurnDelayMillis(
    playerSetup: PlayerSetup,
    setting: AutoPlayDelaySetting,
): Long =
    if (playerSetup.isAutoPlay()) setting.millis else 0L

sealed class AutoAiTurnRequestPlan {
    data object Skip : AutoAiTurnRequestPlan()
    data class Schedule(
        val delayMillis: Long,
    ) : AutoAiTurnRequestPlan()
}

sealed class AutoAiTurnScheduleValidationPlan {
    data object Cancel : AutoAiTurnScheduleValidationPlan()
    data class Continue(
        val runPlan: AutoAiTurnRunPlan,
    ) : AutoAiTurnScheduleValidationPlan() {
        val context: AutoAiTurnExecutionContext
            get() = runPlan.context
    }
}

fun AutoAiTurnUiState.applyAutoAiTurnRequestPlan(
    plan: AutoAiTurnRequestPlan,
): AutoAiTurnUiState =
    when (plan) {
        AutoAiTurnRequestPlan.Skip -> this
        is AutoAiTurnRequestPlan.Schedule -> markScheduled()
    }

fun AutoAiTurnUiState.applyAutoAiTurnScheduleValidationPlan(
    plan: AutoAiTurnScheduleValidationPlan,
): AutoAiTurnUiState =
    when (plan) {
        AutoAiTurnScheduleValidationPlan.Cancel -> clearPending()
        is AutoAiTurnScheduleValidationPlan.Continue -> this
    }

fun AutoAiTurnUiState.completeAutoAiTurnRun(): AutoAiTurnUiState =
    clearPending()

fun buildAutoAiTurnRequestPlan(
    isGameEnded: Boolean,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    isAutoAiTurnPending: Boolean,
    shouldShowResumePrompt: Boolean,
    playerSetup: PlayerSetup,
    gameState: GameState,
    autoPlayDelaySetting: AutoPlayDelaySetting,
    /**
     * 이 국면의 탐색이 시간 초과로 끝나 사용자의 선택을 기다리는 중이면 건너뛴다(refactor backlog #74).
     * ⚠️ 이것이 없으면 busy가 풀리는 순간 트리거 효과가 **같은 예산으로 조용히 다시** 탐색한다 — 사용자가 고르기도
     * 전에. 막는 대신 선택 팝업이 반드시 떠야 한다(`GamePlaySection`의 「엔진 응답 지연」) — 안 뜨면 AI가 멈춘다.
     */
    isAwaitingTimeoutChoice: Boolean = false,
    /**
     * AI가 기권을 제안해 사용자의 답을 기다리는 중이면 건너뛴다(백로그 #213·#221) — 사용자가 고르기 전에 AI가 두어 버리면
     * 「기권 받기」가 뜻을 잃는다. 막는 대신 제안 팝업이 반드시 떠야 한다(`AiResignationOfferHost`) — 안 뜨면 AI가 멈춘다.
     */
    isAwaitingResignationChoice: Boolean = false,
): AutoAiTurnRequestPlan {
    if (isAutoAiTurnPending || isAwaitingTimeoutChoice || isAwaitingResignationChoice) {
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

fun GameSessionControllerState.toAutoAiTurnRequestPlan(
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
        isAwaitingTimeoutChoice = isAwaitingAutoAiTurnTimeoutChoice,
        isAwaitingResignationChoice = isAwaitingAiResignationChoice,
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

fun GameSessionControllerState.toAutoAiTurnScheduleValidationPlan(
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

fun buildAutoAiTurnExecutionContext(
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

fun GameSessionControllerState.toAutoAiTurnExecutionContext(
    searchMode: EngineSearchMode? = null,
): AutoAiTurnExecutionContext =
    buildAutoAiTurnExecutionContext(
        gameState = gameState,
        playerSetup = playerSetup,
        searchTimeSettings = settings.searchTimeSettings,
        reviewCandidateMoves = core.analysisState.reviewCandidateMoves,
        searchMode = searchMode,
    )
