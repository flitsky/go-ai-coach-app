package com.worksoc.goaicoach.presentation

import com.worksoc.goaicoach.application.analysis.translateScoreText
import com.worksoc.goaicoach.application.engine.operation.EngineActivityIndicator
import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.application.movereview.MoveReviewMarker
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationPrompt
import com.worksoc.goaicoach.application.prompt.decidePromptVisibility
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.MatchSeatSnapshot
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.turnStatusText
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.StoneColor

internal data class GameScreenState(
    val gameState: GameState,
    val matchMode: MatchMode,
    val playerSetup: PlayerSetup,
    val playerSetupUi: PlayerSetupUiState,
    val autoPlayDelaySetting: AutoPlayDelaySetting,
    val searchTimeSettings: SearchTimeSettings,
    val playLevel: PlayLevelSetting,
    val matchSeats: MatchSeatSnapshot,
    val uxOptions: KaTrainUxOptions,
    val engine: EngineUiState,
    val analysis: AnalysisUiState,
    val score: ScoreUiState,
    val turnStatusText: String,
    val turnTimeText: String,
    val actionButtons: List<GameActionButtonState>,
    val resumePrompt: ResumePromptState?,
    val cacheOptimizationPrompt: PositionAnalysisCacheOptimizationPrompt?,
    val isGameEnded: Boolean,
    val endgameLog: String,
    val finalScoreJudgement: FinalScoreJudgement?,
    val handicapCount: Int = 0,
) {
    val nextPlayer: StoneColor
        get() = gameState.nextPlayer
}

internal data class GameScreenStateInput(
    val gameState: GameState,
    val matchMode: MatchMode,
    val playerSetup: PlayerSetup,
    val autoPlayDelaySetting: AutoPlayDelaySetting,
    val searchTimeSettings: SearchTimeSettings,
    val playLevel: PlayLevelSetting,
    val matchSeats: MatchSeatSnapshot,
    val uxOptions: KaTrainUxOptions,
    val engineName: String,
    val engineDiagnostic: String,
    val engineProfile: EngineProfile,
    val isEngineReady: Boolean,
    val isEngineBusy: Boolean,
    val engineMessage: String,
    val analysisPreset: AnalysisPreset,
    val analysisCacheStats: String,
    val topMovesEnabled: Boolean,
    val candidateMoves: List<CandidateMove>,
    val candidateText: String,
    val reviewAnalysis: MoveAnalysisSnapshot,
    val reviewCandidateMoves: List<CandidateMove>,
    val sideAnalysisTexts: Map<StoneColor, String> = emptyMap(),
    val moveReviews: List<MoveReviewMarker>,
    val moveReviewText: String,
    val lastMoveText: String,
    val scoreText: String,
    val scoreEstimate: ScoreEstimate?,
    val scoreSnapshots: List<ScoreSnapshot>,
    val isScoreGraphExpanded: Boolean,
    val turnTimeText: String,
    val pendingSavedSession: SavedGameSnapshot?,
    val shouldShowResumePrompt: Boolean,
    val cacheOptimizationPrompt: PositionAnalysisCacheOptimizationPrompt?,
    val hasCompletedEngineStartup: Boolean,
    val isGameEnded: Boolean,
    val endgameLog: String,
    val finalScoreJudgement: FinalScoreJudgement? = null,
    val handicapCount: Int = 0,
    val isEngineBlockingBusy: Boolean = false,
    val engineActivityIndicator: EngineActivityIndicator? = null,
    val engineTurnWaitCompletionSeq: Int = 0,
)

internal fun buildGameScreenStateInput(
    controller: GameSessionControllerState,
    uxOptions: KaTrainUxOptions,
    engineName: String,
    engineDiagnostic: String,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    isEngineBlockingBusy: Boolean,
    engineActivityIndicator: EngineActivityIndicator? = null,
    engineTurnWaitCompletionSeq: Int = 0,
    analysisCacheStats: String,
    isScoreGraphExpanded: Boolean,
    turnTimeText: String,
    hasCompletedEngineStartup: Boolean,
): GameScreenStateInput =
    GameScreenStateInput(
        gameState = controller.gameState,
        matchMode = controller.matchMode,
        playerSetup = controller.playerSetup,
        autoPlayDelaySetting = controller.settings.autoPlayDelaySetting,
        searchTimeSettings = controller.settings.searchTimeSettings,
        playLevel = controller.core.runtimeState.playLevel,
        matchSeats = controller.playerSetup.seatSnapshot(
            nextPlayer = controller.gameState.nextPlayer,
            isEngineReady = isEngineReady,
            isEngineBlockingBusy = isEngineBlockingBusy,
        ),
        uxOptions = uxOptions,
        engineName = engineName,
        engineDiagnostic = engineDiagnostic,
        engineProfile = controller.core.runtimeState.engineProfile,
        isEngineReady = isEngineReady,
        isEngineBusy = isEngineBusy,
        engineMessage = controller.engineMessage,
        analysisPreset = controller.core.runtimeState.analysisPreset,
        analysisCacheStats = analysisCacheStats,
        topMovesEnabled = controller.settings.topMovesEnabled,
        candidateMoves = controller.core.analysisState.candidateMoves,
        candidateText = controller.core.analysisState.candidateText,
        reviewAnalysis = controller.core.analysisState.reviewAnalysis,
        reviewCandidateMoves = controller.core.analysisState.reviewCandidateMoves,
        sideAnalysisTexts = controller.core.analysisState.sideAnalysisTexts,
        moveReviews = controller.core.moveReviewState.moveReviews,
        moveReviewText = controller.core.moveReviewState.moveReviewText,
        lastMoveText = controller.core.moveReviewState.lastMoveText,
        scoreText = controller.core.scoreState.scoreText,
        scoreEstimate = controller.core.scoreState.scoreEstimate,
        scoreSnapshots = controller.core.scoreState.scoreSnapshots,
        isScoreGraphExpanded = isScoreGraphExpanded,
        turnTimeText = turnTimeText,
        pendingSavedSession = controller.savedSession.pendingSavedSession,
        shouldShowResumePrompt = controller.savedSession.shouldShowResumePrompt,
        cacheOptimizationPrompt = controller.positionCacheOptimization.prompt,
        hasCompletedEngineStartup = hasCompletedEngineStartup,
        isGameEnded = controller.isGameEnded,
        endgameLog = controller.core.scoreState.endgameLog,
        finalScoreJudgement = controller.core.scoreState.finalScoreJudgement,
        handicapCount = controller.settings.handicapCount,
        isEngineBlockingBusy = isEngineBlockingBusy,
        engineActivityIndicator = engineActivityIndicator,
        engineTurnWaitCompletionSeq = engineTurnWaitCompletionSeq,
    )

internal fun buildGameScreenState(input: GameScreenStateInput): GameScreenState {
    val promptVisibility = decidePromptVisibility(
        hasCompletedEngineStartup = input.hasCompletedEngineStartup,
        isEngineBusy = input.isEngineBusy,
        hasPendingSavedSession = input.pendingSavedSession != null,
        shouldShowResumePrompt = input.shouldShowResumePrompt,
        hasCacheOptimizationPrompt = input.cacheOptimizationPrompt != null,
    )
    return GameScreenState(
        gameState = input.gameState,
        matchMode = input.matchMode,
        playerSetup = input.playerSetup,
        playerSetupUi = buildPlayerSetupUiState(
            setup = input.playerSetup,
            autoPlayDelaySetting = input.autoPlayDelaySetting,
            engineName = input.engineName,
        ),
        autoPlayDelaySetting = input.autoPlayDelaySetting,
        searchTimeSettings = input.searchTimeSettings,
        playLevel = input.playLevel,
        matchSeats = input.matchSeats,
        uxOptions = input.uxOptions,
        engine = EngineUiState(
            name = input.engineName,
            diagnostic = input.engineDiagnostic,
            profile = input.engineProfile,
            isReady = input.isEngineReady,
            isBusy = input.isEngineBusy,
            isBlockingBusy = input.isEngineBlockingBusy,
            activityIndicator = input.engineActivityIndicator,
            engineTurnWaitCompletionSeq = input.engineTurnWaitCompletionSeq,
            message = input.engineMessage,
        ),
        analysis = AnalysisUiState(
            preset = input.analysisPreset,
            cacheStats = input.analysisCacheStats,
            topMovesEnabled = input.topMovesEnabled,
            candidateMoves = input.candidateMoves,
            candidateText = input.candidateText,
            reviewAnalysis = input.reviewAnalysis,
            reviewCandidateMoves = input.reviewCandidateMoves,
            sideAnalysisTexts = input.sideAnalysisTexts,
            moveReviews = input.moveReviews,
            moveReviewText = input.moveReviewText,
            lastMoveText = input.lastMoveText,
        ),
        score = ScoreUiState(
            text = translateScoreText(input.scoreText),
            estimate = input.scoreEstimate,
            snapshots = input.scoreSnapshots,
            isGraphExpanded = input.isScoreGraphExpanded,
        ),
        turnStatusText = input.matchSeats.turnStatusText(input.isEngineBlockingBusy),
        turnTimeText = input.turnTimeText,
        actionButtons = buildGameActionButtonStates(input),
        resumePrompt = input.pendingSavedSession
            ?.takeIf { promptVisibility.showResumePrompt }
            ?.let(::ResumePromptState),
        cacheOptimizationPrompt = input.cacheOptimizationPrompt
            ?.takeIf { promptVisibility.showCacheOptimizationPrompt },
        isGameEnded = input.isGameEnded,
        endgameLog = input.endgameLog,
        finalScoreJudgement = input.finalScoreJudgement,
        handicapCount = input.handicapCount,
    )
}

internal data class EngineUiState(
    val name: String,
    val diagnostic: String,
    val profile: EngineProfile,
    val isReady: Boolean,
    val isBusy: Boolean,
    val isBlockingBusy: Boolean,
    val activityIndicator: EngineActivityIndicator?,
    val engineTurnWaitCompletionSeq: Int,
    val message: String,
)

internal data class AnalysisUiState(
    val preset: AnalysisPreset,
    val cacheStats: String,
    val topMovesEnabled: Boolean,
    val candidateMoves: List<CandidateMove>,
    val candidateText: String,
    val reviewAnalysis: MoveAnalysisSnapshot,
    val reviewCandidateMoves: List<CandidateMove>,
    val sideAnalysisTexts: Map<StoneColor, String> = emptyMap(),
    val moveReviews: List<MoveReviewMarker>,
    val moveReviewText: String,
    val lastMoveText: String,
)

internal data class ScoreUiState(
    val text: String,
    val estimate: ScoreEstimate?,
    val snapshots: List<ScoreSnapshot>,
    val isGraphExpanded: Boolean,
)

internal data class ResumePromptState(
    val snapshot: SavedGameSnapshot,
)

internal enum class GameActionButtonRole {
    Pass,
    Undo,
    TopMoves,
    Eval,
}

internal data class GameActionButtonState(
    val role: GameActionButtonRole,
    val label: String,
    val event: GameUiEvent,
    val enabled: Boolean,
    val isFilled: Boolean,
)

internal fun buildGameActionButtonStates(input: GameScreenStateInput): List<GameActionButtonState> {
    val canPlayOnBoard = !input.isGameEnded &&
        input.matchSeats.current.canAcceptBoardInput

    /**
     * 코칭 버튼(형세 판단·추천 수)이 지금 눌릴 수 있는가.
     *
     * **두 버튼이 같은 조건을 쓰는 것이 핵심이다**(2026-08-30). 예전에는 추천 수만
     * `!isGameEnded && isEngineReady`였고 형세는 `isEngineReady || LocalTwoPlayer`라 갈라져 있었다.
     * 그래서 대국이 끝났거나 새 대국을 준비하는 동안 **형세 버튼만 눌렸고, 누르면 1회권이 실제로
     * 차감됐다**(실기 확인: 9 → 8 → 7). 확인 팝업 없이 바로 쓰는 설계라(`featureGated`) 사용자는
     * 아무것도 못 본 채 표만 잃는다.
     *
     * `isEngineBusy`까지 보는 이유: AI가 생각하는 중에는 분석을 요청해 봐야 지금 국면의 답이
     * 아니다. `isEngineReady`는 "엔진이 떴는가"일 뿐 "지금 한가한가"가 아니다.
     *
     * ⚠️ **`isEngineBlockingBusy`가 아니라 `isEngineBusy`를 본다.** 느슨하게 잡으면 안 된다 —
     * 요청을 실제로 받아 주는 쪽(`buildScoreEstimateRequestPlan`, `shouldRequestTopMoveAnalysis`)이
     * **`isEngineBusy`에서 거절**하기 때문이다. 버튼이 그보다 넓게 열려 있으면 그 틈에서 표만
     * 나가고 아무것도 안 나온다. 규칙은 하나다: **버튼은 요청이 받아들여질 때만 눌린다.**
     *
     * 그래서 배경 분석이 도는 짧은 창에서도 잠긴다. 이는 의도된 후퇴다 — 잠깐 못 누르는 불편이
     * 표를 잃는 것보다 낫다.
     *
     * 사람끼리 두는 대국([MatchMode.LocalTwoPlayer])은 엔진 없이도 형세를 볼 수 있어야 하므로
     * 그 예외는 남긴다 — 다만 대국이 끝난 뒤에는 마찬가지로 막는다. 종국 후에는 형세가 이미
     * 무료로 그려지므로(`GamePlaySection`) 그때 눌러 봐야 표만 닳는다.
     *
     * ⚠️ **아직 한 수도 두지 않은 판도 막는다**(백로그 #43, 2026-08-30 사용자 제보). 빈 판의
     * 형세는 덤만 반영된 자명한 값이고 추천 수는 정석 첫수라, 판단할 것이 없는데 표만 나갔다.
     * 이 조건이 접바둑까지 함께 덮는다 — 배석 돌은 [GameState.moves]에 들어가지 않으므로
     * 수순 0수이고, 정해진 배석 그대로라 역시 볼 것이 없다. **되돌리려면 이 한 항만 빼면 된다.**
     */
    val coachingGateOpen = !input.isGameEnded &&
        !input.isEngineBusy &&
        input.gameState.moves.isNotEmpty()

    /**
     * 형세 판단은 사람 차례가 아니어도 요청할 수 있다 — `buildScoreEstimateRequestPlan`이 좌석을
     * 보지 않기 때문이다. 사람끼리 두는 대국은 엔진 없이도 국소 계가로 답을 주므로 그 예외를 남긴다.
     */
    val canRequestEval = coachingGateOpen &&
        (input.isEngineReady || input.matchMode == MatchMode.LocalTwoPlayer)

    /**
     * 추천 수는 **사람 차례에만** 요청할 수 있다 — `shouldRequestTopMoveAnalysis`가 좌석까지 본다
     * (`playerSetup.seatFor(nextPlayer).isHuman`). 그 조건을 여기서 빠뜨리면 AI 차례에 버튼이
     * 살아 있고, 눌러도 요청이 거절되며 표만 나간다.
     */
    val canRequestTopMoves = coachingGateOpen &&
        input.isEngineReady &&
        input.matchSeats.current.isHuman

    /**
     * **이미 켜 둔 토글은 언제나 끌 수 있어야 한다.** 게이트가 닫혔다고 켜진 표시까지 잠그면
     * 사용자가 그것을 끄지 못한 채 갇힌다(`buildGameScreenStateKeepsTopMovesButtonActiveWhileBusyWhenToggleIsOn`이
     * 고정한 기존 결정). 끄는 탭은 표를 쓰지도 않는다(`GamePlaySection.featureGated`).
     */
    fun coachingButtonEnabled(isFilled: Boolean, canRequest: Boolean): Boolean = isFilled || canRequest

    return listOf(
        GameActionButtonState(
            role = GameActionButtonRole.Pass,
            label = "Pass",
            event = GameUiEvent.Pass,
            enabled = canPlayOnBoard,
            isFilled = true,
        ),
        GameActionButtonState(
            role = GameActionButtonRole.Undo,
            label = "Undo",
            event = GameUiEvent.UndoLastTurn,
            // Always enabled regardless of engine-busy state -- undoLastTurn()
            // (application/undo/UndoController.kt) applies locally immediately
            // and safely, no matter what the engine is doing.
            enabled = input.gameState.moves.isNotEmpty() && input.matchMode != MatchMode.AiVsAi,
            isFilled = false,
        ),
        GameActionButtonState(
            role = GameActionButtonRole.TopMoves,
            label = "Best",
            event = GameUiEvent.ToggleTopMoves,
            enabled = coachingButtonEnabled(input.topMovesEnabled, canRequestTopMoves),
            isFilled = input.topMovesEnabled,
        ),
        GameActionButtonState(
            role = GameActionButtonRole.Eval,
            label = "Eval",
            event = GameUiEvent.ToggleEvalWithGradient,
            enabled = coachingButtonEnabled(input.uxOptions.showOwnershipOverlay, canRequestEval),
            isFilled = input.uxOptions.showOwnershipOverlay,
        ),
    )
}
