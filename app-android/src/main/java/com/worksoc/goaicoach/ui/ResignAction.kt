package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor

internal fun resignCurrentGameIfAllowed(
    isGameEnded: Boolean,
    isEngineBusy: Boolean,
    currentPlayer: StoneColor,
    submitMove: (Move) -> Unit,
    markGameEnded: () -> Unit,
) {
    if (isGameEnded || isEngineBusy) {
        return
    }
    submitMove(Move.Resign(currentPlayer))
    markGameEnded()
}
