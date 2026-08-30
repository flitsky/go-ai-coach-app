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
import com.worksoc.goaicoach.shared.SearchTimeLimit
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
        assertFalse(screenState.uxOptions.showCoordinates)
        assertTrue(screenState.uxOptions.showLastMoveRing)
        assertTrue(screenState.uxOptions.showOwnershipOverlay)
        assertFalse(screenState.uxOptions.showMoveNumbers)
        assertEquals(AutoPlayDelaySetting.Default, screenState.autoPlayDelaySetting)
        assertEquals("AI turn: White", screenState.turnStatusText)
        assertEquals("Time B 1.2s / W 0.0s", screenState.turnTimeText)
        assertNull(screenState.resumePrompt)
    }

    @Test
    fun nonBlockingTopMoveSearchDoesNotDisableUndoOrEval() {
        val gameState = GameState.empty()
            .play(
                Move.Play(
                    StoneColor.Black,
                    com.worksoc.goaicoach.shared.BoardCoordinate.fromLabel("E5", BoardSize.Nine),
                ),
            )
        val screenState = buildGameScreenState(
            defaultInput(
                gameState = gameState,
                isEngineBusy = true,
                isEngineBlockingBusy = false,
            ),
        )

        val actions = screenState.actionButtons.associateBy { it.role }
        assertTrue(requireNotNull(actions[GameActionButtonRole.Undo]).enabled)
        assertTrue(requireNotNull(actions[GameActionButtonRole.Eval]).enabled)
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
                isEngineBlockingBusy = true,
                topMovesEnabled = true,
            ),
        )

        val topMoves = screenState.actionButtons.first { it.role == GameActionButtonRole.TopMoves }
        assertTrue(topMoves.enabled)
        assertTrue(topMoves.isFilled)
        assertFalse(screenState.actionButtons.first { it.role == GameActionButtonRole.Pass }.enabled)
        assertTrue(screenState.actionButtons.first { it.role == GameActionButtonRole.Eval }.enabled)
    }

    @Test
    fun coachingButtonsShareTheSameGateOnceTheGameEnded() {
        // 2026-08-30 실기 버그: 대국이 끝나거나 새 대국을 준비하는 동안 **형세 버튼만** 눌렸고,
        // 누르면 1회권이 실제로 차감됐다(9 → 8 → 7). 추천 수는 `!isGameEnded`를 봤는데 형세는
        // 보지 않아 조건이 갈려 있었다. 둘은 같은 게이트를 써야 한다.
        val ended = buildGameScreenState(
            defaultInput(isGameEnded = true, topMovesEnabled = false, showOwnershipOverlay = false),
        )

        val actions = ended.actionButtons.associateBy { it.role }
        assertFalse(requireNotNull(actions[GameActionButtonRole.Eval]).enabled)
        assertFalse(requireNotNull(actions[GameActionButtonRole.TopMoves]).enabled)
    }

    @Test
    fun coachingButtonsStayClosedUntilTheFirstMoveIsPlayed() {
        // 백로그 #43: 빈 판의 형세는 덤만 반영된 자명한 값이고 추천 수는 정석 첫수라, 판단할
        // 것이 없는데 1회권만 나갔다. 토글이 꺼진 상태로 확인해야 한다 — 켜져 있으면
        // `coachingButtonEnabled`가 "끌 수는 있어야 한다"로 열어 주기 때문이다.
        val empty = buildGameScreenState(
            defaultInput(showOwnershipOverlay = false, topMovesEnabled = false),
        ).actionButtons.associateBy { it.role }
        assertFalse(requireNotNull(empty[GameActionButtonRole.Eval]).enabled)
        assertFalse(requireNotNull(empty[GameActionButtonRole.TopMoves]).enabled)

        // 한 수만 놓이면 형세는 곧바로 열린다. 추천 수는 여기서 여전히 닫혀 있는데, 그건 이
        // 가드 때문이 아니라 **첫 수 뒤가 AI(백) 차례**여서다 — 좌석 조건은 별개이고
        // `topMovesClosesOnTheAiTurnBecauseItsGateChecksTheSeat`가 따로 고정한다.
        val afterFirstMove = buildGameScreenState(
            defaultInput(
                gameState = GameState.empty().play(
                    Move.Play(
                        StoneColor.Black,
                        com.worksoc.goaicoach.shared.BoardCoordinate.fromLabel("E5", BoardSize.Nine),
                    ),
                ),
                showOwnershipOverlay = false,
                topMovesEnabled = false,
            ),
        ).actionButtons.associateBy { it.role }
        assertTrue(requireNotNull(afterFirstMove[GameActionButtonRole.Eval]).enabled)
    }

    @Test
    fun anAlreadyShownOverlayCanStillBeTurnedOffOnAnEmptyBoard() {
        // #43의 가드를 `canRequest` 쪽에만 넣은 이유. 프리미엄 사용자가 형세를 켜 둔 채 새
        // 대국을 시작하면 빈 판인데 표시가 켜져 있는데, 그때 버튼까지 잠그면 끄지 못하고 갇힌다.
        val screenState = buildGameScreenState(defaultInput(showOwnershipOverlay = true))

        assertTrue(screenState.actionButtons.first { it.role == GameActionButtonRole.Eval }.enabled)
    }

    @Test
    fun coachingButtonsCloseWhileTheEngineBlocks() {
        // AI가 생각하는 중에 분석을 요청해 봐야 지금 국면의 답이 아니다. 그런데도 표는 나간다.
        // 차단 여부와 무관하게 `isEngineBusy`면 잠근다 — 요청을 받아 주는 쪽
        // (`buildScoreEstimateRequestPlan`)이 바로 그 조건에서 거절하기 때문이다.
        val busy = buildGameScreenState(
            defaultInput(
                isEngineBusy = true,
                isEngineBlockingBusy = false,
                topMovesEnabled = false,
                showOwnershipOverlay = false,
            ),
        )

        val actions = busy.actionButtons.associateBy { it.role }
        assertFalse(requireNotNull(actions[GameActionButtonRole.Eval]).enabled)
        assertFalse(requireNotNull(actions[GameActionButtonRole.TopMoves]).enabled)
    }

    @Test
    fun topMovesClosesOnTheAiTurnBecauseItsGateChecksTheSeat() {
        // `shouldRequestTopMoveAnalysis`는 좌석까지 본다(`seatFor(nextPlayer).isHuman`). 형세 판단
        // 게이트는 좌석을 보지 않으므로 **두 버튼의 조건이 여기서 갈리는 것이 맞다** — 각자 자기
        // 게이트와 정확히 같아야 표가 새지 않는다.
        // ⚠️ 빈 판을 쓰면 안 된다 — 백로그 #43이 수순 0수를 두 버튼 모두에 대해 막으므로
        // 형세까지 닫혀 "좌석 때문에 갈린다"는 이 테스트의 요지가 흐려진다. 흑이 한 수 두면
        // 자연스럽게 백(AI) 차례가 된다.
        val aiTurn = buildGameScreenState(
            defaultInput(
                gameState = GameState.empty().play(
                    Move.Play(
                        StoneColor.Black,
                        com.worksoc.goaicoach.shared.BoardCoordinate.fromLabel("E5", BoardSize.Nine),
                    ),
                ),
                topMovesEnabled = false,
                showOwnershipOverlay = false,
            ),
        )

        val actions = aiTurn.actionButtons.associateBy { it.role }
        assertFalse(requireNotNull(actions[GameActionButtonRole.TopMoves]).enabled)
        assertTrue(requireNotNull(actions[GameActionButtonRole.Eval]).enabled)
    }

    @Test
    fun anAlreadyOnToggleStaysEnabledSoItCanBeTurnedOff() {
        // 게이트가 닫혔다고 켜진 표시까지 잠그면 사용자가 그것을 끄지 못한 채 갇힌다.
        val ended = buildGameScreenState(
            defaultInput(isGameEnded = true, topMovesEnabled = true, showOwnershipOverlay = true),
        )

        val actions = ended.actionButtons.associateBy { it.role }
        assertTrue(requireNotNull(actions[GameActionButtonRole.Eval]).enabled)
        assertTrue(requireNotNull(actions[GameActionButtonRole.TopMoves]).enabled)
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
                searchTimeSettings = SearchTimeSettings(SearchTimeLimit.WithinThreeSeconds),
                topMovesEnabled = true,
                boardSize = BoardSize.Nine,
            ),
            benchmark = EngineBenchmarkUiState(benchmarkText = "bench"),
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
            isEngineBlockingBusy = false,
            analysisCacheStats = "entries=1",
            isScoreGraphExpanded = true,
            turnTimeText = "Time B 0.0s / W 0.0s",
            hasCompletedEngineStartup = true,
        )

        assertEquals(gameState, input.gameState)
        assertEquals(AutoPlayDelaySetting.Slow, input.autoPlayDelaySetting)
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
                searchTimeSettings = SearchTimeSettings(SearchTimeLimit.WithinOneSecond),
                topMovesEnabled = true,
                boardSize = BoardSize.Nine,
            ),
            benchmark = EngineBenchmarkUiState(benchmarkText = "bench"),
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
                    isBlockingBusy = false,
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
        isEngineBlockingBusy: Boolean = false,
        topMovesEnabled: Boolean = false,
        isGameEnded: Boolean = false,
        showOwnershipOverlay: Boolean = true,
    ): GameScreenStateInput =
        GameScreenStateInput(
            gameState = gameState,
            matchMode = MatchMode.HumanVsAi,
            playerSetup = PlayerSetup(),
            autoPlayDelaySetting = AutoPlayDelaySetting.Default,
            searchTimeSettings = SearchTimeSettings(),
            playLevel = PlayLevelSetting(),
            matchSeats = PlayerSetup().seatSnapshot(
                nextPlayer = gameState.nextPlayer,
                isEngineReady = true,
                isEngineBlockingBusy = isEngineBlockingBusy,
            ),
            uxOptions = KaTrainUxOptions(showOwnershipOverlay = showOwnershipOverlay),
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
            isGameEnded = isGameEnded,
            endgameLog = "No endgame result recorded.",
            isEngineBlockingBusy = isEngineBlockingBusy,
        )
}
