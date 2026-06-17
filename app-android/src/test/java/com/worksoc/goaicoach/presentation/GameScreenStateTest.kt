package com.worksoc.goaicoach.presentation

import com.worksoc.goaicoach.application.session.*

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.application.autoai.AutoAiTurnUiState
import com.worksoc.goaicoach.application.engine.EngineBenchmarkUiState
import com.worksoc.goaicoach.application.session.GameSessionAnalysisState
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.GameSessionCoreState
import com.worksoc.goaicoach.application.session.GameSessionMoveReviewState
import com.worksoc.goaicoach.application.session.GameSessionRuntimeState
import com.worksoc.goaicoach.application.session.GameSessionScoreState
import com.worksoc.goaicoach.application.session.GameSessionSettingsState
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationUiState
import com.worksoc.goaicoach.application.savedgame.SavedSessionUiState
import com.worksoc.goaicoach.application.engine.localScoreSnapshot
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameScreenStateTest {
    @Test
    fun buildGameScreenStateExposesCurrentTurnAndDefaultUxOptions() {
        val gameState = GameState.empty(nextPlayer = StoneColor.White)
        val screenState = buildGameScreenState(defaultInput(gameState = gameState))

        assertEquals(StoneColor.White, screenState.nextPlayer)
        assertTrue(screenState.uxOptions.showCoordinates)
        assertTrue(screenState.uxOptions.showLastMoveRing)
        assertTrue(screenState.uxOptions.showOwnershipOverlay)
        assertFalse(screenState.uxOptions.showMoveNumbers)
        assertEquals(AutoPlayDelaySetting.Default, screenState.autoPlayDelaySetting)
        assertEquals("AI turn: White", screenState.turnStatusText)
        assertEquals("Time B 1.2s / W 0.0s", screenState.turnTimeText)
        assertNull(screenState.resumePrompt)
    }

    @Test
    fun buildGameScreenStateShowsResumePromptOnlyAfterStartupAndIdle() {
        val resumableState = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
        val snapshot = SavedGameSnapshot(
            gameState = resumableState,
            playerSetup = PlayerSetup(),
            playLevel = PlayLevelSetting(),
            topMovesEnabled = true,
            savedAtMillis = 123L,
        )

        assertNull(
            buildGameScreenState(
                defaultInput(
                    pendingSavedSession = snapshot,
                    shouldShowResumePrompt = true,
                    hasCompletedEngineStartup = false,
                    isEngineBusy = false,
                ),
            ).resumePrompt,
        )
        assertNull(
            buildGameScreenState(
                defaultInput(
                    pendingSavedSession = snapshot,
                    shouldShowResumePrompt = true,
                    hasCompletedEngineStartup = true,
                    isEngineBusy = true,
                ),
            ).resumePrompt,
        )

        val visible = buildGameScreenState(
            defaultInput(
                pendingSavedSession = snapshot,
                shouldShowResumePrompt = true,
                hasCompletedEngineStartup = true,
                isEngineBusy = false,
            ),
        )

        assertEquals(snapshot, visible.resumePrompt?.snapshot)
    }

    @Test
    fun buildGameScreenStateBuildsDefaultActionButtonStates() {
        val screenState = buildGameScreenState(defaultInput())

        assertEquals(
            listOf(
                GameActionButtonRole.Pass,
                GameActionButtonRole.Undo,
                GameActionButtonRole.TopMoves,
                GameActionButtonRole.Eval,
            ),
            screenState.actionButtons.map { it.role },
        )
        assertTrue(screenState.actionButtons.first { it.role == GameActionButtonRole.Pass }.enabled)
        assertTrue(screenState.actionButtons.first { it.role == GameActionButtonRole.Pass }.isFilled)
        assertFalse(screenState.actionButtons.first { it.role == GameActionButtonRole.Undo }.enabled)
        assertFalse(screenState.actionButtons.first { it.role == GameActionButtonRole.TopMoves }.isFilled)
        assertTrue(screenState.actionButtons.first { it.role == GameActionButtonRole.Eval }.enabled)
    }

    @Test
    fun buildGameScreenStateKeepsTopMovesButtonActiveWhileBusyWhenToggleIsOn() {
        val screenState = buildGameScreenState(
            defaultInput(
                isEngineBusy = true,
                topMovesEnabled = true,
            ),
        )

        val topMoves = screenState.actionButtons.first { it.role == GameActionButtonRole.TopMoves }
        assertTrue(topMoves.enabled)
        assertTrue(topMoves.isFilled)
        assertFalse(screenState.actionButtons.first { it.role == GameActionButtonRole.Pass }.enabled)
        assertFalse(screenState.actionButtons.first { it.role == GameActionButtonRole.Eval }.enabled)
    }

    @Test
    fun buildGameScreenStateInputCanBeDerivedFromControllerState() {
        val gameState = GameState.empty(nextPlayer = StoneColor.White)
        val controller = GameSessionControllerState(
            core = GameSessionCoreState(
                gameState = gameState,
                isGameEnded = false,
                analysisState = GameSessionAnalysisState.empty(gameState, candidateText = "analysis"),
                scoreState = GameSessionScoreState.reset(
                    scoreText = "score",
                    scoreSnapshots = listOf(localScoreSnapshot(gameState)),
                    endgameLog = "endgame",
                ),
                runtimeState = GameSessionRuntimeState(
                    playLevel = PlayLevelSetting(level = 3),
                    engineProfile = EngineProfile(name = "Test"),
                    analysisPreset = AnalysisPreset.Lite,
                ),
                moveReviewState = GameSessionMoveReviewState.reset(
                    moveReviewText = "review",
                    lastMoveText = "White pass",
                ),
                engineMessage = "engine",
            ),
            settings = GameSessionSettingsState(
                playerSetup = PlayerSetup(),
                autoPlayDelaySetting = AutoPlayDelaySetting.Slow,
                searchTimeSettings = SearchTimeSettings(b16Millis = 1_500L),
                topMovesEnabled = true,
                boardSize = BoardSize.Nine,
            ),
            benchmark = EngineBenchmarkUiState(
                benchmarkText = "bench",
                searchTimeBenchmarkAverages = mapOf(16 to 1_200.0),
            ),
            savedSession = SavedSessionUiState(),
            autoAiTurn = AutoAiTurnUiState(),
            positionCacheOptimization = PositionAnalysisCacheOptimizationUiState(),
        )

        val input = buildGameScreenStateInput(
            controller = controller,
            uxOptions = KaTrainUxOptions(showMoveNumbers = true),
            engineName = "KataGo",
            engineDiagnostic = "ready",
            isEngineReady = true,
            isEngineBusy = false,
            analysisCacheStats = "entries=1",
            isScoreGraphExpanded = true,
            turnTimeText = "Time B 0.0s / W 0.0s",
            hasCompletedEngineStartup = true,
        )

        assertEquals(gameState, input.gameState)
        assertEquals(AutoPlayDelaySetting.Slow, input.autoPlayDelaySetting)
        assertEquals(mapOf(16 to 1_200.0), input.searchTimeBenchmarkAverages)
        assertEquals(PlayLevelSetting(level = 3), input.playLevel)
        assertEquals(StoneColor.White, input.matchSeats.current.player)
        assertTrue(input.topMovesEnabled)
        assertEquals("analysis", input.candidateText)
        assertEquals("score", input.scoreText)
        assertEquals("review", input.moveReviewText)
        assertTrue(input.uxOptions.showMoveNumbers)
    }

    @Test
    fun goCoachScreenStateAssemblerBuildsScreenStateFromRuntimeSnapshots() {
        val gameState = GameState.empty(nextPlayer = StoneColor.White)
        val controller = GameSessionControllerState(
            core = GameSessionCoreState(
                gameState = gameState,
                isGameEnded = false,
                analysisState = GameSessionAnalysisState.empty(gameState, candidateText = "analysis"),
                scoreState = GameSessionScoreState.reset(
                    scoreText = "score",
                    scoreSnapshots = listOf(localScoreSnapshot(gameState)),
                    endgameLog = "endgame",
                ),
                runtimeState = GameSessionRuntimeState(
                    playLevel = PlayLevelSetting(level = 2),
                    engineProfile = EngineProfile(name = "Assembler"),
                    analysisPreset = AnalysisPreset.Lite,
                ),
                moveReviewState = GameSessionMoveReviewState.reset(
                    moveReviewText = "review",
                    lastMoveText = "White pass",
                ),
                engineMessage = "engine",
            ),
            settings = GameSessionSettingsState(
                playerSetup = PlayerSetup(),
                autoPlayDelaySetting = AutoPlayDelaySetting.Normal,
                searchTimeSettings = SearchTimeSettings(b16Millis = 1_000L),
                topMovesEnabled = true,
                boardSize = BoardSize.Nine,
            ),
            benchmark = EngineBenchmarkUiState(
                benchmarkText = "bench",
                searchTimeBenchmarkAverages = mapOf(16 to 950.0),
            ),
            savedSession = SavedSessionUiState(),
            autoAiTurn = AutoAiTurnUiState(),
            positionCacheOptimization = PositionAnalysisCacheOptimizationUiState(),
        )

        val screenState = GoCoachScreenStateAssembler.assemble(
            GoCoachScreenStateAssembler.Input(
                controller = controller,
                uxOptions = KaTrainUxOptions(showMoveNumbers = true),
                engineRuntime = GoCoachScreenStateAssembler.EngineRuntime(
                    name = "KataGo",
                    diagnostic = "ready",
                    isReady = true,
                    isBusy = false,
                    hasCompletedStartup = true,
                ),
                displayRuntime = GoCoachScreenStateAssembler.DisplayRuntime(
                    analysisCacheStats = "entries=1",
                    isScoreGraphExpanded = true,
                    turnTimeText = "Time B 0.0s / W 0.0s",
                ),
            ),
        )

        assertEquals(gameState, screenState.gameState)
        assertEquals("KataGo", screenState.engine.name)
        assertEquals("ready", screenState.engine.diagnostic)
        assertEquals("entries=1", screenState.analysis.cacheStats)
        assertEquals("score", screenState.score.text)
        assertTrue(screenState.score.isGraphExpanded)
        assertTrue(screenState.uxOptions.showMoveNumbers)
        assertEquals("Time B 0.0s / W 0.0s", screenState.turnTimeText)
    }

    private fun defaultInput(
        gameState: GameState = GameState.empty(),
        pendingSavedSession: SavedGameSnapshot? = null,
        shouldShowResumePrompt: Boolean = false,
        hasCompletedEngineStartup: Boolean = true,
        isEngineBusy: Boolean = false,
        topMovesEnabled: Boolean = false,
    ): GameScreenStateInput =
        GameScreenStateInput(
            gameState = gameState,
            matchMode = MatchMode.HumanVsAi,
            playerSetup = PlayerSetup(),
            autoPlayDelaySetting = AutoPlayDelaySetting.Default,
            searchTimeSettings = SearchTimeSettings(),
            searchTimeBenchmarkAverages = emptyMap(),
            playLevel = PlayLevelSetting(),
            matchSeats = PlayerSetup().seatSnapshot(
                nextPlayer = gameState.nextPlayer,
                isEngineReady = true,
                isEngineBusy = isEngineBusy,
            ),
            uxOptions = KaTrainUxOptions(),
            engineName = "KataGo",
            engineDiagnostic = "ready",
            engineProfile = EngineProfile(),
            isEngineReady = true,
            isEngineBusy = isEngineBusy,
            engineMessage = "ready",
            analysisPreset = AnalysisPreset.Lite,
            analysisCacheStats = "entries=0, hits=0, misses=0",
            topMovesEnabled = topMovesEnabled,
            candidateMoves = emptyList(),
            candidateText = "none",
            reviewAnalysis = MoveAnalysisSnapshot.empty(gameState),
            reviewCandidateMoves = emptyList(),
            moveReviews = emptyList(),
            moveReviewText = "none",
            lastMoveText = "None",
            scoreText = "No score estimate yet.",
            scoreEstimate = null,
            scoreSnapshots = emptyList(),
            isScoreGraphExpanded = false,
            turnTimeText = "Time B 1.2s / W 0.0s",
            pendingSavedSession = pendingSavedSession,
            shouldShowResumePrompt = shouldShowResumePrompt,
            cacheOptimizationPrompt = null,
            hasCompletedEngineStartup = hasCompletedEngineStartup,
            isGameEnded = false,
            endgameLog = "No endgame result recorded.",
        )
}
