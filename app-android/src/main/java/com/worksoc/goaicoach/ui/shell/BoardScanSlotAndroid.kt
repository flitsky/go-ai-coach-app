package com.worksoc.goaicoach.ui.shell

import androidx.compose.runtime.Composable
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.ui.vision.BoardScanScreen

@Composable
internal actual fun BoardScanSlot(
    engineClient: EngineSessionClient,
    onBackClick: () -> Unit,
    onStartGameWithState: (GameState) -> Unit,
) {
    BoardScanScreen(engineClient = engineClient, onBackClick = onBackClick, onStartGameWithState = onStartGameWithState)
}
