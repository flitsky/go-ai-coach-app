package com.worksoc.goaicoach.application.topmoves

import com.worksoc.goaicoach.application.analysis.AnalysisCacheKey
import com.worksoc.goaicoach.application.autoai.shouldRequestTopMoveAnalysis
import com.worksoc.goaicoach.application.session.GameSessionAnalysisState
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.match.PlayerSetup

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
