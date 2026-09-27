package com.worksoc.goaicoach.application.topmoves

import com.worksoc.goaicoach.application.analysis.CachedAnalysisResult
import com.worksoc.goaicoach.application.contract.AnalysisCacheKey
import com.worksoc.goaicoach.application.engine.EngineAnalysisClient
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.TopMoveAnalysisFailureDisplayPlan
import com.worksoc.goaicoach.application.session.TopMoveAnalysisUpdate
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.policy.EngineOperationRequest
import com.worksoc.goaicoach.shared.policy.EngineOperationResultGuard

class TopMovesController(
    private val engineClient: EngineAnalysisClient,
    private val currentControllerState: () -> GameSessionControllerState,
    private val isGameEnded: () -> Boolean,
    private val isEngineReady: () -> Boolean,
    private val isEngineBusy: () -> Boolean,
    private val shouldShowResumePrompt: () -> Boolean,
    private val currentPlayerSetup: () -> PlayerSetup,
    private val showMoveReviewEnabled: () -> Boolean,
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
    private val deferredAutomaticAnalysis: TopMoveAnalysisDeferral,
) {
    fun requestAnalysis(targetState: GameState, automatic: Boolean, deep: Boolean = false) {
        if (automatic && isEngineBusy()) {
            deferredAutomaticAnalysis.defer(
                targetState = targetState,
                deep = deep,
            )
            return
        }
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
                showMoveReviewEnabled = showMoveReviewEnabled(),
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
                deferAfterBusy = ::deferAfterBusy,
            ),
        )
    }

    /**
     * 엔진이 다른 오퍼레이션을 하고 있어 분석이 기다리지 않고 포기했다(refactor backlog #15). 실패로 보이지 않고, 자동
     * 요청이 바쁠 때 늘 가던 길([deferredAutomaticAnalysis])로 미룬다 — 엔진이 한가해지면 [resumeDeferredAnalysisIfIdle]이
     * 다시 건다. 손으로 켠 요청도 같다(다시 걸 때는 자동 요청으로 간다 — 켜 둔 토글이 그 조건을 채운다).
     */
    private fun deferAfterBusy(deferral: TopMoveAnalysisCompletionApplyPlan.Defer) {
        deferredAutomaticAnalysis.defer(
            targetState = deferral.targetState,
            deep = deferral.deep,
        )
        applyLaunchUpdate(
            TopMoveAnalysisLaunchStateUpdate(
                analysisState = currentControllerState().core.analysisState.releaseDeferredTopMoveAnalysisKey(),
            ),
        )
    }

    fun resumeDeferredAnalysisIfIdle(): Boolean {
        val request = deferredAutomaticAnalysis.takeWhenIdle(isEngineBusy()) ?: return false
        requestAnalysis(
            targetState = request.targetState,
            automatic = true,
            deep = request.deep,
        )
        return true
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
        deferredAutomaticAnalysis.clear()
        runHideTopMovesApplication(
            HideTopMovesRunRequest(
                controllerState = currentControllerState(),
                applyUpdate = applyShowTopMovesStateUpdate,
            ),
        )
    }
}
