package com.worksoc.goaicoach.application.topmoves

import com.worksoc.goaicoach.application.analysis.CachedAnalysisResult
import com.worksoc.goaicoach.application.contract.AnalysisCacheKey
import com.worksoc.goaicoach.application.contract.GameSessionEffect
import com.worksoc.goaicoach.application.contract.TopMoveAnalysisPlan
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.runEngineIo
import com.worksoc.goaicoach.application.session.GameSessionAnalysisState
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.GameSessionSettingsState
import com.worksoc.goaicoach.application.session.TopMoveAnalysisFailureDisplayPlan
import com.worksoc.goaicoach.application.session.TopMoveAnalysisUpdate
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.enginecontract.AnalysisPreset
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.policy.EngineOperationRequest
import com.worksoc.goaicoach.shared.policy.EngineOperationResultGuard
import com.worksoc.goaicoach.shared.policy.MoveAnalysisSnapshot

// Top Moves is a lightweight, in-session suggestion feature. It must retain
// the GTP engine tree and avoid the separate JSON analysis process.
val TopMovesSearchMode = EngineSearchMode.GtpStatefulFast

/**
 * A follow-up request can be produced while the preceding engine operation is
 * still completing. Keep only the newest automatic request and run it once
 * the engine lifecycle returns to idle.
 */
data class DeferredTopMoveAnalysisRequest(
    val targetState: GameState,
    val deep: Boolean,
)

class TopMoveAnalysisDeferral {
    private var pending: DeferredTopMoveAnalysisRequest? = null

    fun defer(
        targetState: GameState,
        deep: Boolean,
    ) {
        pending = DeferredTopMoveAnalysisRequest(targetState, deep)
    }

    fun takeWhenIdle(isEngineBusy: Boolean): DeferredTopMoveAnalysisRequest? {
        if (isEngineBusy) return null
        return pending.also { pending = null }
    }

    fun clear() {
        pending = null
    }
}

data class TopMoveAnalysisOperationToken(
    val operation: EngineOperationRequest,
    val analysisKey: AnalysisCacheKey,
)

sealed class TopMoveAnalysisCompletionPlan {
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

sealed class TopMoveAnalysisWorkflowResult {
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
    /** "착수 평가"(구독자 전용, 대국 중 마지막 수 색 링) 토글. 켜져 있으면 `topMovesEnabled`가
     * 꺼져 있어도 매 턴 자동 탐색이 돈다 — 그 표시가 이 탐색 결과를 그대로 쓰기 때문이다. */
    val showMoveReviewEnabled: Boolean = false,
    val analysisCacheEnabled: Boolean,
    val cachedResultFor: (AnalysisCacheKey) -> CachedAnalysisResult?,
    val currentState: () -> GameState,
    val currentAnalysisKey: () -> AnalysisCacheKey?,
    val currentSessionGeneration: () -> Long,
    val launchEngineOperation: (EngineOperationRequest, suspend () -> Unit) -> Unit,
    val runEngineWork: suspend (suspend () -> TopMoveAnalysisCompletionApplyPlan) -> TopMoveAnalysisCompletionApplyPlan =
        { block -> runEngineIo { block() } },
    val applyLaunchUpdate: (TopMoveAnalysisLaunchStateUpdate) -> Unit,
    val applyTopMoveAnalysisUpdate: (TopMoveAnalysisUpdate, AnalysisCacheKey) -> Unit,
    val putUndoRestoreCache: (AnalysisCacheKey, CachedAnalysisResult) -> Unit,
    val putAnalysisCache: (AnalysisCacheKey, CachedAnalysisResult) -> Unit,
    val applyFailureDisplay: (TopMoveAnalysisFailureDisplayPlan) -> Unit,
    val appendEngineOperationDiscardLog: (EngineOperationResultGuard.Discard) -> Unit,
)

internal data class TopMoveAnalysisCompletionApplyRunRequest(
    val applyPlan: TopMoveAnalysisCompletionApplyPlan,
    val applyTopMoveAnalysisUpdate: (TopMoveAnalysisUpdate, AnalysisCacheKey) -> Unit,
    val putUndoRestoreCache: (AnalysisCacheKey, CachedAnalysisResult) -> Unit,
    val putAnalysisCache: (AnalysisCacheKey, CachedAnalysisResult) -> Unit,
    val applyFailureDisplay: (TopMoveAnalysisFailureDisplayPlan) -> Unit,
    val appendEngineOperationDiscardLog: (EngineOperationResultGuard.Discard) -> Unit,
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

data class ShowTopMovesStateUpdate(
    val settingsState: GameSessionSettingsState,
    val analysisState: GameSessionAnalysisState,
    val engineMessage: String?,
)

data class ShowTopMovesAnalysisRequest(
    val targetState: GameState,
    val deep: Boolean,
)

data class ShowTopMovesApplicationPlan(
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

data class SearchTimeTopMovesResetRunRequest(
    val analysisState: GameSessionAnalysisState,
    val state: GameState,
    val applyAnalysisState: (GameSessionAnalysisState) -> Unit,
)

sealed class TopMoveAnalysisLaunchPlan {
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

/**
 * ⚠️ **타입은 public, 만들기와 [effect]는 `internal`이다**(refactor backlog #97). public인 [TopMovesController]가
 * `applyLaunchUpdate`로 이 값을 app-android에 건네고 배선이 [analysisState]·[engineMessage]를 읽는다.
 * [effect]는 `internal`인 `GameSessionEffect`라 `:shared` 안에서만 보인다.
 */
@ConsistentCopyVisibility
data class TopMoveAnalysisLaunchStateUpdate internal constructor(
    val analysisState: GameSessionAnalysisState,
    val engineMessage: String? = null,
    internal val effect: GameSessionEffect.RunTopMoveAnalysis? = null,
)

data class TopMoveAnalysisLaunchRequest(
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
