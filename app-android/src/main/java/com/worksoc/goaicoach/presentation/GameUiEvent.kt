package com.worksoc.goaicoach.presentation

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.StoneColor

internal sealed interface GameUiEvent {
    data object StartConfiguredGame : GameUiEvent
    data object CopyDebugReport : GameUiEvent
    data object ShowEngineBenchmark : GameUiEvent
    data object RequestScoreEstimate : GameUiEvent
    data object ToggleTopMoves : GameUiEvent
    data object ToggleEvalWithGradient : GameUiEvent
    data object UndoLastTurn : GameUiEvent
    data object Pass : GameUiEvent
    data object ResignCurrentGame : GameUiEvent
    data object DismissResumePrompt : GameUiEvent
    data object AcceptCacheOptimizationPrompt : GameUiEvent
    data object DismissCacheOptimizationPrompt : GameUiEvent

    data class ResumeSavedSession(
        val snapshot: SavedGameSnapshot,
    ) : GameUiEvent

    data class PlayAt(
        val coordinate: BoardCoordinate,
    ) : GameUiEvent

    data class SubmitMove(
        val move: Move,
    ) : GameUiEvent

    data class ChangePlayerSetup(
        val setup: PlayerSetup,
    ) : GameUiEvent

    data class ChangeAutoPlayDelay(
        val setting: AutoPlayDelaySetting,
    ) : GameUiEvent

    data class ChangeSearchTimeSettings(
        val settings: SearchTimeSettings,
    ) : GameUiEvent

    data class ChangeBoardSize(
        val boardSize: BoardSize,
    ) : GameUiEvent

    data class ChangeScoringRule(
        val ruleset: Ruleset,
    ) : GameUiEvent

    data class ChangeUxOptions(
        val options: KaTrainUxOptions,
    ) : GameUiEvent

    data class ChangeHandicapCount(
        val count: Int,
    ) : GameUiEvent
}

internal data class GameUiEventHandlers(
    val currentPlayer: () -> StoneColor,
    val isTopMovesEnabled: () -> Boolean,
    val startConfiguredGame: () -> Unit,
    val copyDebugReport: () -> Unit,
    val showEngineBenchmark: () -> Unit,
    val requestScoreEstimate: () -> Unit,
    val toggleEvalWithGradient: () -> Unit,
    val showTopMoves: () -> Unit,
    val hideTopMoves: () -> Unit,
    val undoLastTurn: () -> Unit,
    val submitMove: (Move) -> Unit,
    val resignCurrentGame: () -> Unit,
    val dismissResumePrompt: () -> Unit,
    val acceptCacheOptimizationPrompt: () -> Unit,
    val dismissCacheOptimizationPrompt: () -> Unit,
    val restoreSavedSession: (SavedGameSnapshot) -> Unit,
    val changePlayerSetup: (PlayerSetup) -> Unit,
    val changeAutoPlayDelay: (AutoPlayDelaySetting) -> Unit,
    val changeSearchTimeSettings: (SearchTimeSettings) -> Unit,
    val changeBoardSize: (BoardSize) -> Unit,
    val changeScoringRule: (Ruleset) -> Unit,
    val changeUxOptions: (KaTrainUxOptions) -> Unit,
    val changeHandicapCount: (Int) -> Unit,
)

internal fun buildGameUiEventHandlers(
    currentPlayer: () -> StoneColor,
    isTopMovesEnabled: () -> Boolean,
    startConfiguredGame: () -> Unit,
    copyDebugReport: () -> Unit,
    showEngineBenchmark: () -> Unit,
    requestScoreEstimate: () -> Unit,
    toggleEvalWithGradient: () -> Unit,
    showTopMoves: () -> Unit,
    hideTopMoves: () -> Unit,
    undoLastTurn: () -> Unit,
    submitMove: (Move) -> Unit,
    resignCurrentGame: () -> Unit,
    dismissResumePrompt: () -> Unit,
    acceptCacheOptimizationPrompt: () -> Unit,
    dismissCacheOptimizationPrompt: () -> Unit,
    restoreSavedSession: (SavedGameSnapshot) -> Unit,
    changePlayerSetup: (PlayerSetup) -> Unit,
    changeAutoPlayDelay: (AutoPlayDelaySetting) -> Unit,
    changeSearchTimeSettings: (SearchTimeSettings) -> Unit,
    changeBoardSize: (BoardSize) -> Unit,
    changeScoringRule: (Ruleset) -> Unit,
    changeUxOptions: (KaTrainUxOptions) -> Unit,
    changeHandicapCount: (Int) -> Unit,
): GameUiEventHandlers =
    GameUiEventHandlers(
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
        changeUxOptions = changeUxOptions,
        changeHandicapCount = changeHandicapCount,
    )

internal fun dispatchGameUiEvent(
    event: GameUiEvent,
    handlers: GameUiEventHandlers,
) {
    when (event) {
        GameUiEvent.StartConfiguredGame -> handlers.startConfiguredGame()
        GameUiEvent.CopyDebugReport -> handlers.copyDebugReport()
        GameUiEvent.ShowEngineBenchmark -> handlers.showEngineBenchmark()
        GameUiEvent.RequestScoreEstimate -> handlers.requestScoreEstimate()
        GameUiEvent.ToggleEvalWithGradient -> handlers.toggleEvalWithGradient()
        GameUiEvent.ToggleTopMoves -> {
            if (handlers.isTopMovesEnabled()) {
                handlers.hideTopMoves()
            } else {
                handlers.showTopMoves()
            }
        }
        GameUiEvent.UndoLastTurn -> handlers.undoLastTurn()
        GameUiEvent.Pass -> handlers.submitMove(Move.Pass(handlers.currentPlayer()))
        GameUiEvent.ResignCurrentGame -> handlers.resignCurrentGame()
        GameUiEvent.DismissResumePrompt -> handlers.dismissResumePrompt()
        GameUiEvent.AcceptCacheOptimizationPrompt -> handlers.acceptCacheOptimizationPrompt()
        GameUiEvent.DismissCacheOptimizationPrompt -> handlers.dismissCacheOptimizationPrompt()
        is GameUiEvent.ResumeSavedSession -> handlers.restoreSavedSession(event.snapshot)
        is GameUiEvent.PlayAt -> handlers.submitMove(Move.Play(handlers.currentPlayer(), event.coordinate))
        is GameUiEvent.SubmitMove -> handlers.submitMove(event.move)
        is GameUiEvent.ChangePlayerSetup -> handlers.changePlayerSetup(event.setup)
        is GameUiEvent.ChangeAutoPlayDelay -> handlers.changeAutoPlayDelay(event.setting)
        is GameUiEvent.ChangeSearchTimeSettings -> handlers.changeSearchTimeSettings(event.settings)
        is GameUiEvent.ChangeBoardSize -> handlers.changeBoardSize(event.boardSize)
        is GameUiEvent.ChangeScoringRule -> handlers.changeScoringRule(event.ruleset)
        is GameUiEvent.ChangeUxOptions -> handlers.changeUxOptions(event.options)
        is GameUiEvent.ChangeHandicapCount -> handlers.changeHandicapCount(event.count)
    }
}
