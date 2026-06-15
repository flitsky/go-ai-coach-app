package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.analysis.*
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.endgame.*
import com.worksoc.goaicoach.application.engine.*
import com.worksoc.goaicoach.application.session.*

import com.worksoc.goaicoach.application.autoai.*

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
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
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.analysisFingerprint
import com.worksoc.goaicoach.shared.engine.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.engine.EngineOperationKind
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionAnalysisCacheOptimizationTest {
    @Test
    fun planSkipsFastBeginnerOnlySetupBecauseItDoesNotUseJsonCache() {
        val plan = buildPositionAnalysisCacheOptimizationPlan(
            finalState = shortFinishedGame(),
            playerSetup = PlayerSetup(
                black = SidePlayerSetup(
                    controller = SeatController.Ai,
                    playLevel = PlayLevelSetting(PlayLevelGroup.FastBeginner, 3),
                ),
                white = SidePlayerSetup(controller = SeatController.Human),
            ),
            searchTimeSettings = SearchTimeSettings(),
        )

        assertTrue(plan.isEmpty)
    }

    @Test
    fun planBuildsJsonTargetsForLearningLevelAndKeepsGameplayCacheLimit() {
        val settings = SearchTimeSettings(b32Millis = 2_000L)
        val plan = buildPositionAnalysisCacheOptimizationPlan(
            finalState = shortFinishedGame(),
            playerSetup = PlayerSetup(
                black = SidePlayerSetup(controller = SeatController.Human),
                white = SidePlayerSetup(
                    controller = SeatController.Ai,
                    playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, 7),
                ),
            ),
            searchTimeSettings = settings,
            maxTargets = 3,
        )

        assertFalse(plan.isEmpty)
        assertEquals(3, plan.targets.size)
        plan.targets.forEach { target ->
            assertEquals(32, target.cacheLimit.visits)
            assertEquals(2_000L, target.cacheLimit.timeMillis)
            assertNull(target.executionLimit.timeMillis)
        }
    }

    @Test
    fun planPrioritizesFirstTenOpeningMovesBeforeLaterMoves() {
        val plan = buildPositionAnalysisCacheOptimizationPlan(
            finalState = openingGame(moveCount = 20),
            playerSetup = beginnerAiSetup(),
            searchTimeSettings = SearchTimeSettings(),
            maxTargets = 10,
        )

        assertEquals((1..10).toList(), plan.targets.map { target -> target.moveNumber })
    }

    @Test
    fun planExtendsToMoveElevenOnlyAfterFirstTenAreComplete() {
        val plan = buildPositionAnalysisCacheOptimizationPlan(
            finalState = openingGame(moveCount = 20),
            playerSetup = beginnerAiSetup(),
            searchTimeSettings = SearchTimeSettings(),
            maxTargets = 5,
            qualityFor = { state, limit ->
                if (state.moves.size <= 10) {
                    PositionAnalysisCacheQuality.from(
                        rootVisits = limit.visits,
                        requestedRootVisits = limit.visits,
                    )
                } else {
                    null
                }
            },
        )

        assertEquals((11..15).toList(), plan.targets.map { target -> target.moveNumber })
    }

    @Test
    fun promptIsDisabledByDefaultForMobilePlayExperience() {
        val plan = buildPositionAnalysisCacheOptimizationPlan(
            finalState = shortFinishedGame(),
            playerSetup = beginnerAiSetup(),
            searchTimeSettings = SearchTimeSettings(),
            maxTargets = 2,
        )

        assertNull(
            buildPositionAnalysisCacheOptimizationPrompt(
                isGameEnded = true,
                isEngineReady = true,
                isEngineBusy = false,
                isOptimizationRunning = false,
                dismissedGameFingerprint = null,
                plan = plan,
            ),
        )
    }

    @Test
    fun promptLogicCanBeEnabledForExplicitExperimentBuilds() {
        val plan = buildPositionAnalysisCacheOptimizationPlan(
            finalState = shortFinishedGame(),
            playerSetup = PlayerSetup(
                black = SidePlayerSetup(controller = SeatController.Human),
                white = SidePlayerSetup(
                    controller = SeatController.Ai,
                    playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, 7),
                ),
            ),
            searchTimeSettings = SearchTimeSettings(),
            maxTargets = 2,
        )

        val prompt = buildPositionAnalysisCacheOptimizationPrompt(
            isGameEnded = true,
            isEngineReady = true,
            isEngineBusy = false,
            isOptimizationRunning = false,
            dismissedGameFingerprint = null,
            plan = plan,
            isPromptEnabled = true,
        )

        assertEquals(2, prompt?.targetCount)
        assertNull(
            buildPositionAnalysisCacheOptimizationPrompt(
                isGameEnded = true,
                isEngineReady = true,
                isEngineBusy = false,
                isOptimizationRunning = false,
                dismissedGameFingerprint = plan.gameFingerprint,
                plan = plan,
                isPromptEnabled = true,
            ),
        )
    }

    @Test
    fun uiStateTracksPromptDismissAcceptAndRunningFlag() {
        val plan = buildPositionAnalysisCacheOptimizationPlan(
            finalState = shortFinishedGame(),
            playerSetup = beginnerAiSetup(),
            searchTimeSettings = SearchTimeSettings(),
            maxTargets = 2,
        )
        val prompt = PositionAnalysisCacheOptimizationPrompt(
            gameFingerprint = plan.gameFingerprint,
            moveCount = plan.finalMoveCount,
            targetCount = plan.targets.size,
        )

        val prompted = PositionAnalysisCacheOptimizationUiState().withPrompt(prompt)
        val dismissed = prompted.dismiss(currentGameFingerprint = "fallback")
        val accepted = prompted.accept(plan)
        val running = accepted.startRunning()
        val finished = running.finishRunning()

        assertEquals(prompt, prompted.prompt)
        assertNull(dismissed.prompt)
        assertEquals(plan.gameFingerprint, dismissed.dismissedGameFingerprint)
        assertNull(accepted.prompt)
        assertEquals(plan.gameFingerprint, accepted.dismissedGameFingerprint)
        assertTrue(running.isRunning)
        assertFalse(finished.isRunning)
    }

    @Test
    fun cacheOptimizationEffectRunnerDelegatesPlanToEngineClient() = runBlocking {
        val finalState = GameState.empty()
        val plan = PositionAnalysisCacheOptimizationPlan(
            gameFingerprint = "game",
            finalState = finalState,
            finalMoveCount = 2,
            targets = emptyList(),
        )
        val expected = PositionAnalysisCacheOptimizationResult(
            requestedTargets = 0,
            analyzedTargets = 0,
            reusableTargets = 0,
            completeTargets = 0,
            summaries = listOf("done"),
        )
        val client = FakeCacheOptimizationEngineSessionClient(expected)

        val actual = client.runPositionAnalysisCacheOptimizationEffect(
            GameSessionEffect.RunPositionCacheOptimization(plan),
        )

        assertEquals(plan, client.optimizedPlan)
        assertEquals(expected, actual)
    }

    @Test
    fun cacheOptimizationWorkflowResultWrapsSuccessAndFailure() = runBlocking {
        val finalState = GameState.empty()
        val plan = PositionAnalysisCacheOptimizationPlan(
            gameFingerprint = "game",
            finalState = finalState,
            finalMoveCount = 2,
            targets = emptyList(),
        )
        val expected = PositionAnalysisCacheOptimizationResult(
            requestedTargets = 0,
            analyzedTargets = 0,
            reusableTargets = 0,
            completeTargets = 0,
            summaries = listOf("done"),
        )

        val success = FakeCacheOptimizationEngineSessionClient(expected)
            .runPositionAnalysisCacheOptimizationWorkflowResult(
                GameSessionEffect.RunPositionCacheOptimization(plan),
            )
        val failure = FakeCacheOptimizationEngineSessionClient(
            result = expected,
            failure = IllegalStateException("optimization failed"),
        ).runPositionAnalysisCacheOptimizationWorkflowResult(
            GameSessionEffect.RunPositionCacheOptimization(plan),
        )

        assertTrue(success is PositionAnalysisCacheOptimizationWorkflowResult.Success)
        assertEquals(
            expected,
            (success as PositionAnalysisCacheOptimizationWorkflowResult.Success).result,
        )
        assertTrue(failure is PositionAnalysisCacheOptimizationWorkflowResult.Failure)
        assertEquals(
            "optimization failed",
            (failure as PositionAnalysisCacheOptimizationWorkflowResult.Failure).error.message,
        )
    }

    @Test
    fun cacheOptimizationApplicationAcceptsPromptRunsEngineAndUpdatesDisplay() {
        val finalState = shortFinishedGame()
        val plan = oneTargetOptimizationPlan(finalState)
        val expected = PositionAnalysisCacheOptimizationResult(
            requestedTargets = 1,
            analyzedTargets = 1,
            reusableTargets = 1,
            completeTargets = 1,
            summaries = listOf("m1 Beginner complete"),
        )
        val client = FakeCacheOptimizationEngineSessionClient(expected)
        var uiState = PositionAnalysisCacheOptimizationUiState().withPrompt(
            PositionAnalysisCacheOptimizationPrompt(
                gameFingerprint = plan.gameFingerprint,
                moveCount = plan.finalMoveCount,
                targetCount = plan.targets.size,
            ),
        )
        var launchedOperation: EngineOperationRequest? = null
        var engineMessage = ""
        var candidateText = ""

        runPositionAnalysisCacheOptimizationApplication(
            PositionAnalysisCacheOptimizationRunRequest(
                plan = plan,
                uiState = uiState,
                isEngineBusy = false,
                sessionGeneration = 12L,
                engineClient = client,
                diagnosticEventLog = NoopDiagnosticEventLog,
                currentUiStateProvider = { uiState },
                applyUiState = { state -> uiState = state },
                applyEngineMessage = { message -> engineMessage = message },
                applyCandidateText = { message -> candidateText = message },
                launchEngineOperation = { operation, block ->
                    launchedOperation = operation
                    runBlocking { block() }
                },
                runEngineWork = { block -> block() },
            ),
        )

        assertEquals(plan, client.optimizedPlan)
        assertEquals(EngineOperationKind.PositionCacheOptimization, launchedOperation?.kind)
        assertEquals(EngineFallbackPolicy.CachedAnalysis, launchedOperation?.fallbackPolicy)
        assertEquals(12L, launchedOperation?.sessionGeneration)
        assertFalse(uiState.isRunning)
        assertNull(uiState.prompt)
        assertEquals(plan.gameFingerprint, uiState.dismissedGameFingerprint)
        assertTrue(engineMessage.contains("Post-game cache optimization complete"))
        assertEquals(engineMessage, candidateText)
    }

    @Test
    fun cacheOptimizationApplicationAcceptsButDoesNotRunWhenEngineBusy() {
        val finalState = shortFinishedGame()
        val plan = oneTargetOptimizationPlan(finalState)
        val client = FakeCacheOptimizationEngineSessionClient(
            PositionAnalysisCacheOptimizationResult(
                requestedTargets = 1,
                analyzedTargets = 1,
                reusableTargets = 1,
                completeTargets = 1,
                summaries = emptyList(),
            ),
        )
        var uiState = PositionAnalysisCacheOptimizationUiState().withPrompt(
            PositionAnalysisCacheOptimizationPrompt(
                gameFingerprint = plan.gameFingerprint,
                moveCount = plan.finalMoveCount,
                targetCount = plan.targets.size,
            ),
        )
        var launched = false

        runPositionAnalysisCacheOptimizationApplication(
            PositionAnalysisCacheOptimizationRunRequest(
                plan = plan,
                uiState = uiState,
                isEngineBusy = true,
                sessionGeneration = 1L,
                engineClient = client,
                diagnosticEventLog = NoopDiagnosticEventLog,
                currentUiStateProvider = { uiState },
                applyUiState = { state -> uiState = state },
                applyEngineMessage = {},
                applyCandidateText = {},
                launchEngineOperation = { _, _ -> launched = true },
                runEngineWork = { block -> block() },
            ),
        )

        assertFalse(launched)
        assertNull(client.optimizedPlan)
        assertNull(uiState.prompt)
        assertEquals(plan.gameFingerprint, uiState.dismissedGameFingerprint)
        assertFalse(uiState.isRunning)
    }

    @Test
    fun cacheOptimizationApplicationFinishesRunningAndShowsFailureMessage() {
        val finalState = shortFinishedGame()
        val plan = oneTargetOptimizationPlan(finalState)
        val client = FakeCacheOptimizationEngineSessionClient(
            result = PositionAnalysisCacheOptimizationResult(
                requestedTargets = 1,
                analyzedTargets = 0,
                reusableTargets = 0,
                completeTargets = 0,
                summaries = emptyList(),
            ),
            failure = IllegalStateException("optimization failed"),
        )
        var uiState = PositionAnalysisCacheOptimizationUiState()
        var engineMessage = ""

        runPositionAnalysisCacheOptimizationApplication(
            PositionAnalysisCacheOptimizationRunRequest(
                plan = plan,
                uiState = uiState,
                isEngineBusy = false,
                sessionGeneration = 1L,
                engineClient = client,
                diagnosticEventLog = NoopDiagnosticEventLog,
                currentUiStateProvider = { uiState },
                applyUiState = { state -> uiState = state },
                applyEngineMessage = { message -> engineMessage = message },
                applyCandidateText = {},
                launchEngineOperation = { _, block -> runBlocking { block() } },
                runEngineWork = { block -> block() },
            ),
        )

        assertFalse(uiState.isRunning)
        assertEquals("optimization failed", engineMessage)
    }

    private fun shortFinishedGame(): GameState =
        GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("C5", BoardSize.Nine)))
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))

    private fun oneTargetOptimizationPlan(finalState: GameState): PositionAnalysisCacheOptimizationPlan =
        PositionAnalysisCacheOptimizationPlan(
            gameFingerprint = finalState.analysisFingerprint(),
            finalState = finalState,
            finalMoveCount = finalState.moves.size,
            targets = listOf(
                PositionAnalysisCacheOptimizationTarget(
                    state = finalState,
                    moveNumber = finalState.moves.size,
                    levelLabel = "Beginner",
                    cacheLimit = AnalysisLimit(visits = 32, timeMillis = 2_000L, candidateCount = 10),
                    executionLimit = AnalysisLimit(visits = 32, timeMillis = null, candidateCount = 10),
                ),
            ),
        )

    private fun beginnerAiSetup(): PlayerSetup =
        PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, 7),
            ),
        )

    private fun openingGame(moveCount: Int): GameState {
        val coordinates = listOf(
            "A9", "C9", "E9", "G9", "J9",
            "B8", "D8", "F8", "H8",
            "A7", "C7", "E7", "G7", "J7",
            "B6", "D6", "F6", "H6",
            "A5", "C5", "E5", "G5", "J5",
            "B4", "D4",
        )
        return coordinates.take(moveCount).fold(GameState.empty()) { state, label ->
            state.play(
                Move.Play(
                    player = state.nextPlayer,
                    coordinate = BoardCoordinate.fromLabel(label, BoardSize.Nine),
                ),
            )
        }
    }
}

private class FakeCacheOptimizationEngineSessionClient(
    private val result: PositionAnalysisCacheOptimizationResult,
    private val failure: Throwable? = null,
) : EngineSessionClient {
    var optimizedPlan: PositionAnalysisCacheOptimizationPlan? = null
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
    ): PositionAnalysisCacheOptimizationResult {
        optimizedPlan = plan
        failure?.let { throw it }
        return result
    }

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
    ): AutoAiTurnResult =
        error("not used")

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
