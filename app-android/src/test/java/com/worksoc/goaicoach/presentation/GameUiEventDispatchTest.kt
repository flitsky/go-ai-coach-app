package com.worksoc.goaicoach.presentation

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.policy.SearchTimeLimit
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.shared.domain.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameUiEventDispatchTest {
    @Test
    fun dispatchToggleTopMovesCallsShowOrHideFromCurrentState() {
        val calls = mutableListOf<String>()
        var enabled = false
        val handlers = handlers(
            isTopMovesEnabled = { enabled },
            showTopMoves = {
                calls += "show"
                enabled = true
            },
            hideTopMoves = {
                calls += "hide"
                enabled = false
            },
        )

        dispatchGameUiEvent(GameUiEvent.ToggleTopMoves, handlers)
        dispatchGameUiEvent(GameUiEvent.ToggleTopMoves, handlers)

        assertEquals(listOf("show", "hide"), calls)
    }

    @Test
    fun dispatchPlayAtAndPassSubmitMoveForCurrentPlayer() {
        val submitted = mutableListOf<Move>()
        val coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val handlers = handlers(
            currentPlayer = { StoneColor.White },
            submitMove = { move -> submitted += move },
        )

        dispatchGameUiEvent(GameUiEvent.PlayAt(coordinate), handlers)
        dispatchGameUiEvent(GameUiEvent.Pass, handlers)

        assertEquals(
            listOf(
                Move.Play(StoneColor.White, coordinate),
                Move.Pass(StoneColor.White),
            ),
            submitted,
        )
    }

    @Test
    fun dispatchResignRoutesToResignHandler() {
        val calls = mutableListOf<String>()
        val handlers = handlers(
            resignCurrentGame = { calls += "resign" },
        )

        dispatchGameUiEvent(GameUiEvent.ResignCurrentGame, handlers)

        assertEquals(listOf("resign"), calls)
    }

    @Test
    fun dispatchResumeAndDismissRouteToResumePromptHandlers() {
        val calls = mutableListOf<String>()
        val snapshot = SavedGameSnapshot(
            gameState = GameState.empty().play(Move.Pass(StoneColor.Black)),
            playerSetup = PlayerSetup(),
            playLevel = PlayLevelSetting(),
            topMovesEnabled = true,
            savedAtMillis = 123L,
        )
        val handlers = handlers(
            dismissResumePrompt = { calls += "dismiss" },
            restoreSavedSession = { restored ->
                calls += "restore:${restored.savedAtMillis}"
            },
        )

        dispatchGameUiEvent(GameUiEvent.DismissResumePrompt, handlers)
        dispatchGameUiEvent(GameUiEvent.ResumeSavedSession(snapshot), handlers)

        assertEquals(listOf("dismiss", "restore:123"), calls)
    }

    @Test
    fun dispatchChangeAutoPlayDelayRoutesToHandler() {
        var selected = AutoPlayDelaySetting.Default
        val handlers = handlers(
            changeAutoPlayDelay = { setting -> selected = setting },
        )

        dispatchGameUiEvent(GameUiEvent.ChangeAutoPlayDelay(AutoPlayDelaySetting.Slow), handlers)

        assertEquals(AutoPlayDelaySetting.Slow, selected)
    }

    @Test
    fun dispatchChangeSearchTimeSettingsRoutesToHandler() {
        var selected = SearchTimeSettings()
        val next = SearchTimeSettings(SearchTimeLimit.WithinThreeSeconds)
        val handlers = handlers(
            changeSearchTimeSettings = { settings -> selected = settings },
        )

        dispatchGameUiEvent(GameUiEvent.ChangeSearchTimeSettings(next), handlers)

        assertEquals(next, selected)
    }

    @Test
    fun manualBenchmarkMenuEventRoutesToHandler() {
        val calls = mutableListOf<String>()
        val handlers = handlers(
            showEngineBenchmark = { calls += "benchmark" },
        )

        assertTrue(calls.isEmpty())
        dispatchGameUiEvent(GameUiEvent.ShowEngineBenchmark, handlers)

        assertEquals(listOf("benchmark"), calls)
    }

    @Test
    fun dispatchToggleEvalWithGradientRoutesToHandler() {
        val calls = mutableListOf<String>()
        val handlers = handlers(
            toggleEvalWithGradient = { calls += "evalWithGradient" },
        )

        dispatchGameUiEvent(GameUiEvent.ToggleEvalWithGradient, handlers)

        assertEquals(listOf("evalWithGradient"), calls)
    }

    @Test
    fun dispatchChangeBoardSizeRoutesToHandler() {
        var selected = BoardSize.Nine
        val handlers = handlers(
            changeBoardSize = { boardSize -> selected = boardSize },
        )

        dispatchGameUiEvent(GameUiEvent.ChangeBoardSize(BoardSize.Thirteen), handlers)

        assertEquals(BoardSize.Thirteen, selected)
    }

    /**
     * 로비(`GameSetupLobby`)와 설정 화면(`SettingsScreen`)의 접바둑 드롭다운은 둘 다 이 이벤트를 낸다 —
     * 그것이 `GameSettingsController.changeHandicapCount` 한 곳에 닿아야 덤 자동 전환(refactor backlog #93)이
     * 두 화면을 함께 덮는다.
     */
    @Test
    fun dispatchChangeHandicapCountRoutesToHandler() {
        var selected = 0
        val handlers = handlers(
            changeHandicapCount = { count -> selected = count },
        )

        dispatchGameUiEvent(GameUiEvent.ChangeHandicapCount(3), handlers)

        assertEquals(3, selected)
    }

    private fun handlers(
        currentPlayer: () -> StoneColor = { StoneColor.Black },
        isTopMovesEnabled: () -> Boolean = { false },
        startConfiguredGame: () -> Unit = {},
        copyDebugReport: () -> Unit = {},
        showEngineBenchmark: () -> Unit = {},
        requestScoreEstimate: () -> Unit = {},
        toggleEvalWithGradient: () -> Unit = {},
        showTopMoves: () -> Unit = {},
        hideTopMoves: () -> Unit = {},
        undoLastTurn: () -> Unit = {},
        submitMove: (Move) -> Unit = {},
        resignCurrentGame: () -> Unit = {},
        dismissResumePrompt: () -> Unit = {},
        acceptCacheOptimizationPrompt: () -> Unit = {},
        dismissCacheOptimizationPrompt: () -> Unit = {},
        restoreSavedSession: (SavedGameSnapshot) -> Unit = {},
        changePlayerSetup: (PlayerSetup) -> Unit = {},
        changeAutoPlayDelay: (AutoPlayDelaySetting) -> Unit = {},
        changeSearchTimeSettings: (SearchTimeSettings) -> Unit = {},
        changeBoardSize: (BoardSize) -> Unit = {},
        changeScoringRule: (Ruleset) -> Unit = {},
        changeKomi: (Double) -> Unit = {},
        changeUxOptions: (KaTrainUxOptions) -> Unit = {},
        changeHandicapCount: (Int) -> Unit = {},
    ): GameUiEventHandlers =
        buildGameUiEventHandlers(
            currentPlayer = currentPlayer,
            isTopMovesEnabled = isTopMovesEnabled,
            startConfiguredGame = startConfiguredGame,
            copyDebugReport = copyDebugReport,
            showEngineBenchmark = showEngineBenchmark,
            requestScoreEstimate = requestScoreEstimate,
            toggleEvalWithGradient = toggleEvalWithGradient,
            showTopMoves = showTopMoves,
            hideTopMoves = hideTopMoves,
            undoLastTurn = undoLastTurn,
            submitMove = submitMove,
            resignCurrentGame = resignCurrentGame,
            dismissResumePrompt = dismissResumePrompt,
            acceptCacheOptimizationPrompt = acceptCacheOptimizationPrompt,
            dismissCacheOptimizationPrompt = dismissCacheOptimizationPrompt,
            restoreSavedSession = restoreSavedSession,
            changePlayerSetup = changePlayerSetup,
            changeAutoPlayDelay = changeAutoPlayDelay,
            changeSearchTimeSettings = changeSearchTimeSettings,
            changeBoardSize = changeBoardSize,
            changeScoringRule = changeScoringRule,
            changeKomi = changeKomi,
            changeUxOptions = changeUxOptions,
            changeHandicapCount = changeHandicapCount,
            reportEngineTurnWatchdogTriggered = { _, _ -> },
            forceResetEngine = {},
        )
}
