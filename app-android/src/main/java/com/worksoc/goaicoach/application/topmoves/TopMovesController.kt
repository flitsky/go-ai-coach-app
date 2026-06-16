package com.worksoc.goaicoach.application.topmoves

import com.worksoc.goaicoach.application.analysis.AnalysisCacheKey
import com.worksoc.goaicoach.application.analysis.CachedAnalysisResult
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest

internal class TopMovesController(
    private val engineClient: EngineSessionClient,
    private val currentControllerState: () -> GameSessionControllerState,
    private val isGameEnded: () -> Boolean,
    private val isEngineReady: () -> Boolean,
    private val isEngineBusy: () -> Boolean,
    private val shouldShowResumePrompt: () -> Boolean,
    private val currentPlayerSetup: () -> PlayerSetup,
    private val pendingPostUndoEngineSync: () -> Boolean,
    private val analysisCacheEnabled: () -> Boolean,
    private val cachedResultFor: (AnalysisCacheKey) -> CachedAnalysisResult?,
    private val currentGameState: () -> GameState,
    private val currentAnalysisKey: () -> AnalysisCacheKey?,
    private val currentSessionGeneration: () -> Long,
    private val launchEngineOperation: (EngineOperationRequest, suspend () -> Unit) -> Unit,
    private val applyLaunchUpdate: (TopMoveAnalysisLaunchStateUpdate) -> Unit,
    private val applyTopMoveAnalysisUpdate: (TopMoveAnalysisUpdate, AnalysisCacheKey) -> Unit,
    private val putUndoRestoreCache: (AnalysisCacheKey, CachedAnalysisResult) -> Unit,
    private val putAnalysisCache: (AnalysisCacheKey, CachedAnalysisResult) -> Unit,
    private val applyFailureDisplay: (TopMoveAnalysisFailureDisplayPlan) -> Unit,
    private val appendEngineOperationDiscardLog: (EngineOperationResultGuard.Discard) -> Unit,
    private val applyShowTopMovesStateUpdate: (ShowTopMovesStateUpdate) -> Unit,
) {
    fun requestAnalysis(targetState: GameState, automatic: Boolean, deep: Boolean = false) {
        runTopMoveAnalysisApplication(
            TopMoveAnalysisRunRequest(
                engineClient = engineClient,
                controllerState = currentControllerState(),
                targetState = targetState,
                deep = deep,
                automatic = automatic,
                pendingPostUndoEngineSync = pendingPostUndoEngineSync(),
                isGameEnded = isGameEnded(),
                isEngineReady = isEngineReady(),
                isEngineBusy = isEngineBusy(),
                shouldShowResumePrompt = shouldShowResumePrompt(),
                playerSetup = currentPlayerSetup(),
                analysisCacheEnabled = analysisCacheEnabled(),
                cachedResultFor = cachedResultFor,
                currentState = currentGameState,
                currentAnalysisKey = currentAnalysisKey,
                currentSessionGeneration = currentSessionGeneration,
                launchEngineOperation = launchEngineOperation,
                applyLaunchUpdate = applyLaunchUpdate,
                applyTopMoveAnalysisUpdate = applyTopMoveAnalysisUpdate,
                putUndoRestoreCache = putUndoRestoreCache,
                putAnalysisCache = putAnalysisCache,
                applyFailureDisplay = applyFailureDisplay,
                appendEngineOperationDiscardLog = appendEngineOperationDiscardLog,
            ),
        )
    }

    fun showForCurrentState() {
        runShowTopMovesApplication(
            ShowTopMovesRunRequest(
                controllerState = currentControllerState(),
                isGameEnded = isGameEnded(),
                isEngineReady = isEngineReady(),
                isEngineBusy = isEngineBusy(),
                shouldShowResumePrompt = shouldShowResumePrompt(),
                playerSetup = currentPlayerSetup(),
                applyUpdate = applyShowTopMovesStateUpdate,
                requestAnalysis = { analysisRequest ->
                    requestAnalysis(
                        targetState = analysisRequest.targetState,
                        automatic = false,
                        deep = analysisRequest.deep,
                    )
                },
            ),
        )
    }

    fun hide() {
        runHideTopMovesApplication(
            HideTopMovesRunRequest(
                controllerState = currentControllerState(),
                applyUpdate = applyShowTopMovesStateUpdate,
            ),
        )
    }
}
