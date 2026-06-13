package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.match.TurnOutcome
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.StoneColor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAutomationApplicationTest {
    @Test
    fun aiTurnRunsOnlyForReadyIdleAiSeat() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Human),
        )

        assertTrue(
            shouldRequestAiTurn(
                isGameEnded = false,
                isEngineReady = true,
                isEngineBusy = false,
                shouldShowResumePrompt = false,
                playerSetup = setup,
                gameState = GameState.empty(),
            ),
        )
        assertFalse(
            shouldRequestAiTurn(
                isGameEnded = false,
                isEngineReady = true,
                isEngineBusy = true,
                shouldShowResumePrompt = false,
                playerSetup = setup,
                gameState = GameState.empty(),
            ),
        )
    }

    @Test
    fun topMoveAnalysisRunsOnlyForReadyIdleHumanSeatWithoutResumePrompt() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(controller = SeatController.Ai),
        )

        assertTrue(
            shouldRequestTopMoveAnalysis(
                isGameEnded = false,
                isEngineReady = true,
                isEngineBusy = false,
                shouldShowResumePrompt = false,
                playerSetup = setup,
                targetState = GameState.empty(),
            ),
        )
        assertFalse(
            shouldRequestTopMoveAnalysis(
                isGameEnded = false,
                isEngineReady = true,
                isEngineBusy = false,
                shouldShowResumePrompt = true,
                playerSetup = setup,
                targetState = GameState.empty(),
            ),
        )
    }

    @Test
    fun autoAiTurnDelayAppliesOnlyWhenBothSeatsAreAi() {
        val autoSetup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Ai),
        )
        val humanVsAiSetup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(controller = SeatController.Ai),
        )

        assertEquals(
            AutoPlayDelaySetting.Slow.millis,
            autoAiTurnDelayMillis(autoSetup, AutoPlayDelaySetting.Slow),
        )
        assertEquals(
            0L,
            autoAiTurnDelayMillis(humanVsAiSetup, AutoPlayDelaySetting.Slow),
        )
    }

    @Test
    fun autoAiTurnUiStateTracksPendingSchedule() {
        val scheduled = AutoAiTurnUiState().markScheduled()
        val cleared = scheduled.clearPending()

        assertTrue(scheduled.isPending)
        assertFalse(cleared.isPending)
    }

    @Test
    fun autoAiTurnRequestPlanSkipsWhenAlreadyPending() {
        val plan = buildAutoAiTurnRequestPlan(
            isGameEnded = false,
            isEngineReady = true,
            isEngineBusy = false,
            isAutoAiTurnPending = true,
            shouldShowResumePrompt = false,
            playerSetup = PlayerSetup(
                black = SidePlayerSetup(controller = SeatController.Ai),
                white = SidePlayerSetup(controller = SeatController.Ai),
            ),
            gameState = GameState.empty(),
            autoPlayDelaySetting = AutoPlayDelaySetting.Slow,
        )

        assertEquals(AutoAiTurnRequestPlan.Skip, plan)
    }

    @Test
    fun autoAiTurnRequestPlanSchedulesWithAutoPlayDelay() {
        val plan = buildAutoAiTurnRequestPlan(
            isGameEnded = false,
            isEngineReady = true,
            isEngineBusy = false,
            isAutoAiTurnPending = false,
            shouldShowResumePrompt = false,
            playerSetup = PlayerSetup(
                black = SidePlayerSetup(controller = SeatController.Ai),
                white = SidePlayerSetup(controller = SeatController.Ai),
            ),
            gameState = GameState.empty(),
            autoPlayDelaySetting = AutoPlayDelaySetting.Slow,
        )

        assertEquals(
            AutoAiTurnRequestPlan.Schedule(AutoPlayDelaySetting.Slow.millis),
            plan,
        )
    }

    @Test
    fun controllerStateBuildsAutoAiTurnRequestPlan() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Ai),
        )
        val controller = automationControllerState(
            playerSetup = setup,
            autoPlayDelaySetting = AutoPlayDelaySetting.Slow,
        )

        val plan = controller.toAutoAiTurnRequestPlan(
            isEngineReady = true,
            isEngineBusy = false,
        )

        assertEquals(
            AutoAiTurnRequestPlan.Schedule(AutoPlayDelaySetting.Slow.millis),
            plan,
        )
    }

    @Test
    fun controllerStateBuildsAutoAiTurnExecutionContext() {
        val whiteLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 7)
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = whiteLevel,
            ),
        )
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
        val reviewCandidate = CandidateMove(Move.Pass(StoneColor.White), pointLoss = 0.0)
        val controller = automationControllerState(
            gameState = state,
            playerSetup = setup,
            searchTimeSettings = SearchTimeSettings(b32Millis = 4_000L),
            reviewCandidateMoves = listOf(reviewCandidate),
        )

        val context = controller.toAutoAiTurnExecutionContext()

        assertEquals(state, context.turnState)
        assertEquals(StoneColor.White, context.aiPlayer)
        assertEquals(whiteLevel, context.playLevel)
        assertEquals(32, context.analysisLimit.visits)
        assertEquals(4_000L, context.analysisLimit.timeMillis)
        assertEquals(EngineSearchMode.JsonPositionAnalysis, context.searchMode)
        assertEquals(listOf(reviewCandidate), context.previousReviewCandidates)
    }

    @Test
    fun autoAiTurnExecutionContextUsesCurrentAiSeatLevelAndSearchTime() {
        val whiteLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 7)
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = whiteLevel,
            ),
        )
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
        val reviewCandidate = CandidateMove(Move.Pass(StoneColor.White), pointLoss = 0.0)

        val context = buildAutoAiTurnExecutionContext(
            gameState = state,
            playerSetup = setup,
            searchTimeSettings = SearchTimeSettings(b32Millis = 4_000L),
            reviewCandidateMoves = listOf(reviewCandidate),
        )

        assertEquals(state, context.turnState)
        assertEquals(StoneColor.White, context.aiPlayer)
        assertEquals(whiteLevel, context.playLevel)
        assertEquals(32, context.analysisLimit.visits)
        assertEquals(4_000L, context.analysisLimit.timeMillis)
        assertEquals(true, context.analysisLimit.includePolicy)
        assertEquals(EngineSearchMode.JsonPositionAnalysis, context.searchMode)
        assertFalse(context.isolateSearchCache)
        assertEquals(listOf(reviewCandidate), context.previousReviewCandidates)
    }

    @Test
    fun fastBeginnerAutoAiTurnExecutionContextUsesGtpBestOnly() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 2),
            ),
            white = SidePlayerSetup(controller = SeatController.Human),
        )

        val context = buildAutoAiTurnExecutionContext(
            gameState = GameState.empty(),
            playerSetup = setup,
            searchTimeSettings = SearchTimeSettings(b16Millis = 1_500L),
            reviewCandidateMoves = emptyList(),
        )

        assertEquals(EngineSearchMode.GtpStatefulFast, context.searchMode)
        assertEquals(16, context.analysisLimit.visits)
        assertEquals(1_500L, context.analysisLimit.timeMillis)
        assertEquals(1, context.analysisLimit.candidateCount)
        assertEquals(false, context.analysisLimit.includePolicy)
    }

    @Test
    fun autoAiTurnExecutionContextIsolatesSearchCacheOnlyForAiVsAi() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Ai),
        )

        val context = buildAutoAiTurnExecutionContext(
            gameState = GameState.empty(),
            playerSetup = setup,
            searchTimeSettings = SearchTimeSettings(),
            reviewCandidateMoves = emptyList(),
        )

        assertTrue(context.isolateSearchCache)
    }

    @Test
    fun autoAiTurnDisplayPlanUsesEngineEstimateWhenAvailable() {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
        val estimate = ScoreEstimate(
            status = EngineStatus.ready("estimate ready"),
            whiteScoreLead = 1.5,
            whiteWinRate = 0.6,
            summary = "estimate",
        )
        val result = autoAiTurnResult(state, estimate)

        val plan = buildAutoAiTurnDisplayPlan(
            result = result,
            previousSnapshots = emptyList(),
            previousReviewCandidates = emptyList(),
        )

        assertEquals(state, plan.gameState)
        assertEquals(result.playLevel.analysisPreset, plan.analysisPreset)
        assertEquals("candidate text", plan.candidateText)
        assertEquals(estimate, plan.scoreDisplay.scoreEstimate)
        assertEquals("estimate ready", plan.scoreDisplay.engineMessage)
        assertEquals(ScoreSnapshotSource.EngineEstimate, plan.scoreDisplay.scoreSnapshots.single().source)
        assertFalse(plan.shouldResolveEndgame)
        assertEquals(state, plan.nextAnalysisState)
    }

    @Test
    fun autoAiTurnDisplayPlanFallsBackToLocalSnapshotWhenEstimateIsMissing() {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
        val result = autoAiTurnResult(state, estimate = null)

        val plan = buildAutoAiTurnDisplayPlan(
            result = result,
            previousSnapshots = emptyList(),
            previousReviewCandidates = emptyList(),
        )

        assertNull(plan.scoreDisplay.scoreEstimate)
        assertEquals("Score estimate not current.", plan.scoreDisplay.scoreText)
        assertEquals("engine text", plan.scoreDisplay.engineMessage)
        assertEquals(ScoreSnapshotSource.LocalAreaEstimate, plan.scoreDisplay.scoreSnapshots.single().source)
    }

    @Test
    fun autoAiTurnDisplayPlanCarriesPrePassCandidatesForEndgameResolution() {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))
        val passCandidate = CandidateMove(Move.Pass(StoneColor.White), pointLoss = 0.0)
        val result = autoAiTurnResult(state, estimate = null)

        val plan = buildAutoAiTurnDisplayPlan(
            result = result,
            previousSnapshots = emptyList(),
            previousReviewCandidates = listOf(passCandidate),
        )

        assertTrue(plan.shouldResolveEndgame)
        assertNull(plan.nextAnalysisState)
        assertEquals(listOf(passCandidate), plan.endgamePrePassCandidates)
    }

    @Test
    fun autoAiTurnDisplayRunnerDelegatesToEngineSessionAndBuildsDisplayPlan() = runBlocking {
        val initialState = GameState.empty()
        val nextState = initialState.play(Move.Pass(StoneColor.Black))
        val playLevel = PlayLevelSetting()
        val client = FakeAutoAiEngineSessionClient(
            result = autoAiTurnResult(
                state = nextState,
                estimate = ScoreEstimate(
                    status = EngineStatus.ready("estimate ready"),
                    whiteScoreLead = 0.0,
                    whiteWinRate = 0.5,
                    summary = "estimate",
                ),
            ),
        )

        val display = client.runAutoAiTurnDisplayPlan(
            currentState = initialState,
            playLevel = playLevel,
            currentProfile = EngineProfile(),
            searchTimeSettings = SearchTimeSettings(),
            searchMode = EngineSearchMode.GtpStatefulFast,
            isolateSearchCache = true,
            previousSnapshots = emptyList(),
            previousReviewCandidates = emptyList(),
        )

        assertEquals(initialState, client.currentState)
        assertEquals(playLevel, client.playLevel)
        assertEquals(EngineSearchMode.GtpStatefulFast, client.searchMode)
        assertEquals(true, client.isolateSearchCache)
        assertEquals(nextState, display.gameState)
        assertEquals("candidate text", display.candidateText)
        assertEquals("estimate ready", display.scoreDisplay.engineMessage)
    }

    private fun autoAiTurnResult(
        state: GameState,
        estimate: ScoreEstimate?,
    ): AutoAiTurnResult =
        AutoAiTurnResult(
            turnOutcome = TurnOutcome(
                gameState = state,
                engineMessage = "engine text",
                candidateText = "candidate text",
                lastMoveText = "last move",
            ),
            scoreEstimate = estimate,
            profile = EngineProfile(),
            playLevel = PlayLevelSetting(),
        )
}

private fun automationControllerState(
    gameState: GameState = GameState.empty(),
    playerSetup: PlayerSetup = PlayerSetup(
        black = SidePlayerSetup(controller = SeatController.Ai),
        white = SidePlayerSetup(controller = SeatController.Human),
    ),
    autoPlayDelaySetting: AutoPlayDelaySetting = AutoPlayDelaySetting.Default,
    searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
    reviewCandidateMoves: List<CandidateMove> = emptyList(),
): GameSessionControllerState =
    GameSessionControllerState(
        core = GameSessionCoreState(
            gameState = gameState,
            isGameEnded = false,
            analysisState = GameSessionAnalysisState.empty(gameState)
                .copy(reviewCandidateMoves = reviewCandidateMoves),
            scoreState = GameSessionScoreState.reset(
                scoreText = "score",
                scoreSnapshots = listOf(localScoreSnapshot(gameState)),
                endgameLog = "endgame",
            ),
            runtimeState = GameSessionRuntimeState(
                playLevel = PlayLevelSetting(),
                engineProfile = EngineProfile(),
                analysisPreset = AnalysisPreset.Lite,
            ),
            moveReviewState = GameSessionMoveReviewState.reset(
                moveReviewText = "review",
                lastMoveText = "none",
            ),
            engineMessage = "engine",
        ),
        settings = GameSessionSettingsState(
            playerSetup = playerSetup,
            autoPlayDelaySetting = autoPlayDelaySetting,
            searchTimeSettings = searchTimeSettings,
            topMovesEnabled = false,
        ),
        benchmark = EngineBenchmarkUiState.initial(
            benchmarkText = "benchmark",
            profile = null,
        ),
        savedSession = SavedSessionUiState(),
        autoAiTurn = AutoAiTurnUiState(),
        positionCacheOptimization = PositionAnalysisCacheOptimizationUiState(),
    )

private class FakeAutoAiEngineSessionClient(
    private val result: AutoAiTurnResult,
) : EngineSessionClient {
    var currentState: GameState? = null
        private set
    var playLevel: PlayLevelSetting? = null
        private set
    var isolateSearchCache: Boolean? = null
        private set
    var searchMode: EngineSearchMode? = null
        private set

    override val capabilities: EngineSessionCapabilities =
        EngineSessionCapabilities(supportsDeviceBenchmark = false)

    override fun positionAnalysisCacheStatsText(nowMillis: Long): String =
        "disabled"

    override fun positionAnalysisCacheQualityFor(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode,
        nowMillis: Long,
    ): PositionAnalysisCacheQuality? = null

    override suspend fun startSession(
        profile: EngineProfile,
        state: GameState,
    ): EngineStartupResult =
        error("not used")

    override suspend fun startNewGame(
        profile: EngineProfile,
        boardSize: BoardSize,
        ruleset: Ruleset,
    ): EngineStartupResult =
        error("not used")

    override suspend fun analyzePosition(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode,
    ): AnalysisResult =
        error("not used")

    override suspend fun optimizePositionAnalysisCache(
        plan: PositionAnalysisCacheOptimizationPlan,
    ): PositionAnalysisCacheOptimizationResult =
        error("not used")

    override suspend fun syncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate =
        error("not used")

    override suspend fun configureSyncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate =
        error("not used")

    override suspend fun runAutoAiTurn(
        currentState: GameState,
        playLevel: PlayLevelSetting,
        currentProfile: EngineProfile,
        searchTimeSettings: SearchTimeSettings,
        searchMode: EngineSearchMode,
        isolateSearchCache: Boolean,
    ): AutoAiTurnResult {
        this.currentState = currentState
        this.playLevel = playLevel
        this.searchMode = searchMode
        this.isolateSearchCache = isolateSearchCache
        return result
    }

    override suspend fun syncAfterHumanMove(
        afterMove: GameState,
        profile: EngineProfile,
        move: Move,
        previousReviewCandidates: List<CandidateMove>,
    ): LocalEngineMoveResult =
        error("not used")

    override suspend fun estimateScoreForState(
        state: GameState,
        profile: EngineProfile,
        syncFirst: Boolean,
    ): ScoreEstimate =
        error("not used")

    override suspend fun resolveEndgameForState(
        state: GameState,
        profile: EngineProfile,
        prePassCandidates: List<CandidateMove>,
    ): AiEndgameResolution =
        error("not used")

    override suspend fun undoMove(): EngineStatus =
        error("not used")

    override suspend fun runStartupBenchmark(
        restoreState: GameState,
        nowMillis: Long,
        onProgress: suspend (EngineBenchmarkProgress) -> Unit,
    ): EngineBenchmarkProfile =
        error("not used")
}
