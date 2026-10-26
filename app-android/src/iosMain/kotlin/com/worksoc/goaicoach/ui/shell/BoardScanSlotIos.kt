package com.worksoc.goaicoach.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.shared.domain.GameState

@Composable
internal actual fun BoardScanSlot(
    engineClient: EngineSessionClient,
    onBackClick: () -> Unit,
    onStartGameWithState: (GameState) -> Unit,
) {
    LaunchedEffect(Unit) { onBackClick() }
}
