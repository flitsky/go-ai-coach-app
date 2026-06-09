package com.worksoc.goaicoach.presentation

internal fun shouldCollapseMenuAfterEvent(event: GameUiEvent): Boolean =
    event == GameUiEvent.StartConfiguredGame
