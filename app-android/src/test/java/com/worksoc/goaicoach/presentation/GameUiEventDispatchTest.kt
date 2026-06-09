package com.worksoc.goaicoach.presentation

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.persistence.SavedGameSnapshot
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
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

    private fun handlers(
        currentPlayer: () -> StoneColor = { StoneColor.Black },
        isTopMovesEnabled: () -> Boolean = { false },
        startConfiguredGame: () -> Unit = {},
        copyDebugReport: () -> Unit = {},
        requestScoreEstimate: () -> Unit = {},
        showTopMoves: () -> Unit = {},
        hideTopMoves: () -> Unit = {},
        undoLastTurn: () -> Unit = {},
        submitMove: (Move) -> Unit = {},
        dismissResumePrompt: () -> Unit = {},
        restoreSavedSession: (SavedGameSnapshot) -> Unit = {},
        changePlayerSetup: (PlayerSetup) -> Unit = {},
        changeAutoPlayDelay: (AutoPlayDelaySetting) -> Unit = {},
        changeScoringRule: (Ruleset) -> Unit = {},
        changeUxOptions: (KaTrainUxOptions) -> Unit = {},
    ): GameUiEventHandlers =
        GameUiEventHandlers(
            currentPlayer = currentPlayer,
            isTopMovesEnabled = isTopMovesEnabled,
            startConfiguredGame = startConfiguredGame,
            copyDebugReport = copyDebugReport,
            requestScoreEstimate = requestScoreEstimate,
            showTopMoves = showTopMoves,
            hideTopMoves = hideTopMoves,
            undoLastTurn = undoLastTurn,
            submitMove = submitMove,
            dismissResumePrompt = dismissResumePrompt,
            restoreSavedSession = restoreSavedSession,
            changePlayerSetup = changePlayerSetup,
            changeAutoPlayDelay = changeAutoPlayDelay,
            changeScoringRule = changeScoringRule,
            changeUxOptions = changeUxOptions,
        )
}
