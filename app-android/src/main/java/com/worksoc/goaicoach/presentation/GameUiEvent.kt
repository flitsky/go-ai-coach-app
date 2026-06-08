package com.worksoc.goaicoach.presentation

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.persistence.SavedGameSnapshot
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset

internal sealed interface GameUiEvent {
    data object StartConfiguredGame : GameUiEvent
    data object CopyDebugReport : GameUiEvent
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

    data class ChangeScoringRule(
        val ruleset: Ruleset,
    ) : GameUiEvent

    data class ChangeUxOptions(
        val options: KaTrainUxOptions,
    ) : GameUiEvent
}
