package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.autoai.AutoAiTurnController
import com.worksoc.goaicoach.application.autoai.AutoAiTurnUiState
import com.worksoc.goaicoach.application.analysis.AnalysisResultCache
import com.worksoc.goaicoach.application.analysis.PositionCacheOptimizationController
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationUiState
import com.worksoc.goaicoach.application.analysis.UndoAnalysisRestoreCache
import com.worksoc.goaicoach.application.debugreport.ClipboardPort
import com.worksoc.goaicoach.application.debugreport.DebugReportController
import com.worksoc.goaicoach.application.debugreport.DebugReportMirrorPort
import com.worksoc.goaicoach.application.debugreport.UserNoticePort
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineBenchmarkController
import com.worksoc.goaicoach.application.engine.EngineBenchmarkStorePort
import com.worksoc.goaicoach.application.engine.EngineBenchmarkUiState
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.operation.EngineOperationLifecycleController
import com.worksoc.goaicoach.application.humanmove.HumanMoveController
import com.worksoc.goaicoach.application.preferences.UserPreferencesStorePort
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.savedgame.SavedGameStorePort
import com.worksoc.goaicoach.application.savedgame.SavedSessionController
import com.worksoc.goaicoach.application.savedgame.SavedSessionUiState
import com.worksoc.goaicoach.application.score.FinalScoreDisplayPlan
import com.worksoc.goaicoach.application.score.ScoreEstimateController
import com.worksoc.goaicoach.application.score.ScoringRuleController
import com.worksoc.goaicoach.application.session.GameSessionAnalysisState
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.GameSessionCoreState
import com.worksoc.goaicoach.application.session.GameSessionDisplayStateApplier
import com.worksoc.goaicoach.application.session.GameSessionMoveReviewState
import com.worksoc.goaicoach.application.session.GameSessionRuntimeState
import com.worksoc.goaicoach.application.session.GameSessionScoreState
import com.worksoc.goaicoach.application.session.GameSessionSettingsState
import com.worksoc.goaicoach.application.session.GameSessionTurnTimeState
import com.worksoc.goaicoach.application.session.GameSettingsController
import com.worksoc.goaicoach.application.startgame.NewGameController
import com.worksoc.goaicoach.application.topmoves.TopMoveAnalysisDeferral
import com.worksoc.goaicoach.application.topmoves.TopMovesController
import com.worksoc.goaicoach.application.undo.UndoController
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
import kotlinx.coroutines.CoroutineScope

internal data class GoCoachControllers(
    val topMovesController: TopMovesController,
    val undoController: UndoController,
    val autoAiTurnController: AutoAiTurnController,
    val humanMoveController: HumanMoveController,
    val newGameController: NewGameController,
    val savedSessionController: SavedSessionController,
    val cacheOptController: PositionCacheOptimizationController,
    val scoreEstimateController: ScoreEstimateController,
    val scoringRuleController: ScoringRuleController,
    val settingsController: GameSettingsController,
    val debugReportController: DebugReportController,
    val benchmarkController: EngineBenchmarkController,
)

internal interface GoCoachAppWiringContext {
    val scope: CoroutineScope
    val engineClient: EngineSessionClient
    val diagnosticEventLog: DiagnosticEventLogPort
    val runtimeEventLog: RuntimeEventLogPort
    val sessionStore: SavedGameStorePort
    val preferencesStore: UserPreferencesStorePort
    val benchmarkStore: EngineBenchmarkStorePort
    val debugReportMirror: DebugReportMirrorPort
    val clipboardPort: ClipboardPort
    val userNoticePort: UserNoticePort
    val lifecycleController: EngineOperationLifecycleController
    val displayStateApplier: GameSessionDisplayStateApplier
    val defaultPlayLevel: PlayLevelSetting
    val analysisCache: AnalysisResultCache
    val undoAnalysisRestoreCache: UndoAnalysisRestoreCache
    val deferredTopMoveAnalysis: TopMoveAnalysisDeferral

    // Snapshot
    fun sessionSnapshot(): GameSessionControllerState

    // State getters
    fun gameState(): GameState
    fun playerSetup(): PlayerSetup
    fun analysisState(): GameSessionAnalysisState
    fun scoreState(): GameSessionScoreState
    fun moveReviewState(): GameSessionMoveReviewState
    fun runtimeState(): GameSessionRuntimeState
    fun settingsState(): GameSessionSettingsState
    fun autoAiTurnUiState(): AutoAiTurnUiState
    fun positionCacheOptimizationState(): PositionAnalysisCacheOptimizationUiState
    fun benchmarkUiState(): EngineBenchmarkUiState
    fun savedSessionUiState(): SavedSessionUiState
    fun turnTimeState(): GameSessionTurnTimeState
    fun undoEngineInterventionQuietUntil(): Long
    fun isPendingUndoSync(): Boolean
    fun isEngineReady(): Boolean
    fun isEngineBusy(): Boolean
    fun isEngineBlockingBusy(): Boolean
    fun isGameEnded(): Boolean
    fun shouldShowResumePrompt(): Boolean
    fun matchMode(): MatchMode
    fun topMovesEnabled(): Boolean
    fun currentRuntimeLogContext(): RuntimeLogContext
    fun engineName(): String
    fun engineDiagnostic(): String

    // State setters
    fun setGameState(value: GameState)
    fun setEngineMessage(value: String)
    fun setAnalysisState(value: GameSessionAnalysisState)
    fun setScoreState(value: GameSessionScoreState)
    fun setMoveReviewState(value: GameSessionMoveReviewState)
    fun setRuntimeState(value: GameSessionRuntimeState)
    fun setSettingsState(value: GameSessionSettingsState)
    fun setAutoAiTurnUiState(value: AutoAiTurnUiState)
    fun setPositionCacheOptimizationState(value: PositionAnalysisCacheOptimizationUiState)
    fun setBenchmarkUiState(value: EngineBenchmarkUiState)
    fun setSavedSessionUiState(value: SavedSessionUiState)
    fun setTurnTimeState(value: GameSessionTurnTimeState)
    fun setUndoEngineInterventionQuietUntil(value: Long)
    fun setPendingUndoSync(value: Boolean)
    fun setEngineReady(value: Boolean)
    fun setIsGameEnded(value: Boolean)

    // Side-effects / Actions
    fun applyCoreSessionState(next: GameSessionCoreState)
    fun activateEndgameJudgementReview()
    fun clearUndoEngineInterventionQuietWindow()
    fun engineProfileTimeoutPolicy(profile: EngineProfile): EngineTimeoutPolicy
    fun applyFinalScoreWithJudgement(final: FinalScoreDisplayPlan)
}

/**
 * 12개 컨트롤러를 의존 순서대로 조립한다. `topMovesController`(추천수)는 착수/대국시작/
 * 채점규칙 변경 등 여러 컨트롤러가 후속 분석 트리거로 참조하고, `undoController`는
 * `settingsController`가 참조한다 — 그래서 이 순서가 임의가 아니라 실제 생성 순서 제약이다.
 * 각 그룹의 세부 배선은 도메인별 파일(`TurnFlowControllerWiring.kt`,
 * `GameLifecycleControllerWiring.kt`, `ScoringControllerWiring.kt`,
 * `SettingsAndDiagnosticsControllerWiring.kt`)에 있다.
 */
internal fun wireGoCoachControllers(
    context: GoCoachAppWiringContext
): GoCoachControllers {
    val topMovesController = wireTopMovesController(context)
    val undoController = wireUndoController(context, topMovesController)
    val autoAiTurnController = wireAutoAiTurnController(context, topMovesController)
    val humanMoveController = wireHumanMoveController(context, topMovesController)
    val newGameController = wireNewGameController(context, topMovesController)
    val savedSessionController = wireSavedSessionController(context, topMovesController)
    val cacheOptController = wireCacheOptController(context)
    val scoreEstimateController = wireScoreEstimateController(context)
    val scoringRuleController = wireScoringRuleController(context, topMovesController)
    val settingsController = wireSettingsController(context, undoController)
    val debugReportController = wireDebugReportController(context)
    val benchmarkController = wireBenchmarkController(context)

    return GoCoachControllers(
        topMovesController = topMovesController,
        undoController = undoController,
        autoAiTurnController = autoAiTurnController,
        humanMoveController = humanMoveController,
        newGameController = newGameController,
        savedSessionController = savedSessionController,
        cacheOptController = cacheOptController,
        scoreEstimateController = scoreEstimateController,
        scoringRuleController = scoringRuleController,
        settingsController = settingsController,
        debugReportController = debugReportController,
        benchmarkController = benchmarkController,
    )
}
