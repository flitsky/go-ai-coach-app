package com.worksoc.goaicoach.presentation

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.persistence.SavedGameSnapshot
import com.worksoc.goaicoach.shared.BoardCoordinate
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
    data object UndoLastTurn : GameUiEvent
    data object Pass : GameUiEvent
    data object DismissResumePrompt : GameUiEvent

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

    data class ChangeScoringRule(
        val ruleset: Ruleset,
    ) : GameUiEvent

    data class ChangeUxOptions(
        val options: KaTrainUxOptions,
    ) : GameUiEvent
}

internal data class GameUiEventHandlers(
    val currentPlayer: () -> StoneColor,
    val isTopMovesEnabled: () -> Boolean,
    val startConfiguredGame: () -> Unit,
    val copyDebugReport: () -> Unit,
    val showEngineBenchmark: () -> Unit,
    val requestScoreEstimate: () -> Unit,
    val showTopMoves: () -> Unit,
    val hideTopMoves: () -> Unit,
    val undoLastTurn: () -> Unit,
    val submitMove: (Move) -> Unit,
    val dismissResumePrompt: () -> Unit,
    val restoreSavedSession: (SavedGameSnapshot) -> Unit,
    val changePlayerSetup: (PlayerSetup) -> Unit,
    val changeAutoPlayDelay: (AutoPlayDelaySetting) -> Unit,
    val changeSearchTimeSettings: (SearchTimeSettings) -> Unit,
    val changeScoringRule: (Ruleset) -> Unit,
    val changeUxOptions: (KaTrainUxOptions) -> Unit,
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
        GameUiEvent.ToggleTopMoves -> {
            if (handlers.isTopMovesEnabled()) {
                handlers.hideTopMoves()
            } else {
                handlers.showTopMoves()
            }
        }
        GameUiEvent.UndoLastTurn -> handlers.undoLastTurn()
        GameUiEvent.Pass -> handlers.submitMove(Move.Pass(handlers.currentPlayer()))
        GameUiEvent.DismissResumePrompt -> handlers.dismissResumePrompt()
        is GameUiEvent.ResumeSavedSession -> handlers.restoreSavedSession(event.snapshot)
        is GameUiEvent.PlayAt -> handlers.submitMove(Move.Play(handlers.currentPlayer(), event.coordinate))
        is GameUiEvent.SubmitMove -> handlers.submitMove(event.move)
        is GameUiEvent.ChangePlayerSetup -> handlers.changePlayerSetup(event.setup)
        is GameUiEvent.ChangeAutoPlayDelay -> handlers.changeAutoPlayDelay(event.setting)
        is GameUiEvent.ChangeSearchTimeSettings -> handlers.changeSearchTimeSettings(event.settings)
        is GameUiEvent.ChangeScoringRule -> handlers.changeScoringRule(event.ruleset)
        is GameUiEvent.ChangeUxOptions -> handlers.changeUxOptions(event.options)
    }
}
