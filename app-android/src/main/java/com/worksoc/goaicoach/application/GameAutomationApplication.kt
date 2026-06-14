package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.MatchReferee
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreTimeline
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

internal data class AutoAiTurnDisplayPlan(
    val playLevel: PlayLevelSetting,
    val profile: EngineProfile,
    val analysisPreset: AnalysisPreset,
    val gameState: GameState,
    val turnEngineMessage: String,
    val candidateText: String,
    val lastMoveText: String,
    val scoreDisplay: ScoreEstimateDisplayPlan,
    val shouldResolveEndgame: Boolean,
    val endgamePrePassCandidates: List<CandidateMove>,
    val nextAnalysisState: GameState?,
)

internal sealed class AutoAiTurnFollowUpPlan {
    data object None : AutoAiTurnFollowUpPlan()
    data class RequestTopMoveAnalysis(
        val targetState: GameState,
    ) : AutoAiTurnFollowUpPlan()
}

internal data class AutoAiTurnFollowUpRequest(
    val targetState: GameState,
    val automatic: Boolean,
    val deep: Boolean,
)

internal fun buildAutoAiTurnFollowUpPlan(display: AutoAiTurnDisplayPlan): AutoAiTurnFollowUpPlan =
    display.nextAnalysisState?.let { state ->
        AutoAiTurnFollowUpPlan.RequestTopMoveAnalysis(state)
    } ?: AutoAiTurnFollowUpPlan.None

internal fun AutoAiTurnFollowUpPlan.toAutoAiTurnFollowUpRequest(): AutoAiTurnFollowUpRequest? =
    when (this) {
        AutoAiTurnFollowUpPlan.None -> null
        is AutoAiTurnFollowUpPlan.RequestTopMoveAnalysis ->
            AutoAiTurnFollowUpRequest(
                targetState = targetState,
                automatic = true,
                deep = false,
            )
    }

internal sealed class AutoAiTurnEndgamePlan {
    data object None : AutoAiTurnEndgamePlan()
    data class Resolve(
        val state: GameState,
        val profile: EngineProfile,
        val prePassCandidates: List<CandidateMove>,
        val engineMessagePrefix: String,
        val successSource: String = "auto-ai-engine-dead-stone-cleanup",
        val failureSource: String = "auto-ai-engine-final-score-failed",
    ) : AutoAiTurnEndgamePlan()
}

internal fun buildAutoAiTurnEndgamePlan(display: AutoAiTurnDisplayPlan): AutoAiTurnEndgamePlan =
    if (display.shouldResolveEndgame) {
        AutoAiTurnEndgamePlan.Resolve(
            state = display.gameState,
            profile = display.profile,
            prePassCandidates = display.endgamePrePassCandidates,
            engineMessagePrefix = display.turnEngineMessage,
        )
    } else {
        AutoAiTurnEndgamePlan.None
    }

internal sealed class AutoAiTurnEndgameDisplayPlan {
    data class Resolved(
        val resolution: AiEndgameResolution,
        val display: FinalScoreDisplayPlan,
    ) : AutoAiTurnEndgameDisplayPlan()

    data class Failed(
        val error: Throwable,
        val display: EndgameFailureDisplayPlan,
    ) : AutoAiTurnEndgameDisplayPlan()
}

internal data class AutoAiTurnFailureDisplayPlan(
    val engineMessage: String,
    val candidateText: String,
)

internal fun buildAutoAiTurnFailureDisplayPlan(error: Throwable): AutoAiTurnFailureDisplayPlan =
    AutoAiTurnFailureDisplayPlan(
        engineMessage = error.message ?: "AI turn failed.",
        candidateText = "AI turn failed. Current board state was not changed.",
    )

internal data class AutoAiTurnRunPlan(
    val delayMillis: Long,
    val context: AutoAiTurnExecutionContext,
)

internal data class AutoAiTurnRunExecutionContext(
    val currentProfile: EngineProfile,
    val searchTimeSettings: SearchTimeSettings,
    val previousSnapshots: List<ScoreSnapshot>,
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

internal fun buildAutoAiTurnDisplayPlan(
    result: AutoAiTurnResult,
    previousSnapshots: List<ScoreSnapshot>,
    previousReviewCandidates: List<CandidateMove>,
): AutoAiTurnDisplayPlan {
    val outcome = result.turnOutcome
    val nextState = outcome.gameState
    val shouldResolveEndgame = MatchReferee.shouldResolveEndgame(nextState)
    val scoreDisplay = result.scoreEstimate?.let { estimate ->
        buildEngineEstimateDisplayPlan(
            state = nextState,
            estimate = estimate,
            previousSnapshots = previousSnapshots,
        )
    } ?: ScoreEstimateDisplayPlan(
        scoreText = "Score estimate not current.",
        scoreEstimate = null,
        scoreSnapshots = ScoreTimeline.record(
            previousSnapshots,
            localScoreSnapshot(nextState),
        ),
        engineMessage = outcome.engineMessage,
    )

    return AutoAiTurnDisplayPlan(
        playLevel = result.playLevel,
        profile = result.profile,
        analysisPreset = result.playLevel.analysisPreset,
        gameState = nextState,
        turnEngineMessage = outcome.engineMessage,
        candidateText = outcome.candidateText,
        lastMoveText = outcome.lastMoveText,
        scoreDisplay = scoreDisplay,
        shouldResolveEndgame = shouldResolveEndgame,
        endgamePrePassCandidates = if (nextState.moves.lastOrNull() is Move.Pass) {
            previousReviewCandidates
        } else {
            emptyList()
        },
        nextAnalysisState = nextState.takeUnless { shouldResolveEndgame },
    )
}

internal suspend fun EngineSessionClient.runAutoAiTurnDisplayPlan(
    currentState: GameState,
    playLevel: PlayLevelSetting,
    currentProfile: EngineProfile,
    searchTimeSettings: SearchTimeSettings,
    searchMode: EngineSearchMode,
    isolateSearchCache: Boolean,
    previousSnapshots: List<ScoreSnapshot>,
    previousReviewCandidates: List<CandidateMove>,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): AutoAiTurnDisplayPlan {
    val analysisLimit = playLevel.aiMoveAnalysisLimitWith(searchTimeSettings)
    val result = runObservedEngineOperation(
        request = operationRequest ?: engineOperationRequest(
            kind = EngineOperationKind.AutoAiTurn,
            state = currentState,
            sessionGeneration = 0L,
            timeoutPolicy = EngineTimeoutPolicy(
                timeoutMillis = analysisLimit.timeMillis,
                label = "${searchMode.name}:${analysisLimit.visits}v",
            ),
            fallbackPolicy = EngineFallbackPolicy.None,
        ),
        diagnosticEventLog = diagnosticEventLog,
    ) {
        runAutoAiTurn(
            currentState = currentState,
            playLevel = playLevel,
            currentProfile = currentProfile,
            searchTimeSettings = searchTimeSettings,
            searchMode = searchMode,
            isolateSearchCache = isolateSearchCache,
        )
    }
    return buildAutoAiTurnDisplayPlan(
        result = result,
        previousSnapshots = previousSnapshots,
        previousReviewCandidates = previousReviewCandidates,
    )
}

internal suspend fun EngineSessionClient.runAutoAiTurnEffect(
    effect: GameSessionEffect.RunAutoAiTurn,
    executionContext: AutoAiTurnRunExecutionContext,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): AutoAiTurnDisplayPlan {
    val turnContext = effect.plan.context
    return runAutoAiTurnDisplayPlan(
        currentState = turnContext.turnState,
        playLevel = turnContext.playLevel,
        currentProfile = executionContext.currentProfile,
        searchTimeSettings = executionContext.searchTimeSettings,
        searchMode = turnContext.searchMode,
        isolateSearchCache = turnContext.isolateSearchCache,
        previousSnapshots = executionContext.previousSnapshots,
        previousReviewCandidates = turnContext.previousReviewCandidates,
        operationRequest = operationRequest,
        diagnosticEventLog = diagnosticEventLog,
    )
}

internal suspend fun EngineSessionClient.runAutoAiEndgameDisplayPlan(
    plan: AutoAiTurnEndgamePlan.Resolve,
    previousSnapshots: List<ScoreSnapshot>,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): AutoAiTurnEndgameDisplayPlan =
    runCatching {
        val resolution = runObservedEngineOperation(
            request = operationRequest ?: autoAiEndgameOperationToken(plan).operation,
            diagnosticEventLog = diagnosticEventLog,
        ) {
            resolveEndgameForState(
                state = plan.state,
                profile = plan.profile,
                prePassCandidates = plan.prePassCandidates,
            )
        }
        AutoAiTurnEndgameDisplayPlan.Resolved(
            resolution = resolution,
            display = buildResolvedEndgameDisplayPlan(
                source = plan.successSource,
                originalState = plan.state,
                resolution = resolution,
                previousSnapshots = previousSnapshots,
                engineMessagePrefix = plan.engineMessagePrefix,
            ),
        )
    }.getOrElse { error ->
        AutoAiTurnEndgameDisplayPlan.Failed(
            error = error,
            display = buildEndgameFailureDisplayPlan(
                source = plan.failureSource,
                state = plan.state,
                errorMessage = error.message ?: "Unknown error",
                engineMessagePrefix = plan.engineMessagePrefix,
            ),
        )
    }

internal suspend fun EngineSessionClient.runAutoAiEndgameEffect(
    effect: GameSessionEffect.ResolveAutoAiEndgame,
    previousSnapshots: List<ScoreSnapshot>,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): AutoAiTurnEndgameDisplayPlan =
    runAutoAiEndgameDisplayPlan(
        plan = effect.plan,
        previousSnapshots = previousSnapshots,
        operationRequest = operationRequest,
        diagnosticEventLog = diagnosticEventLog,
    )
