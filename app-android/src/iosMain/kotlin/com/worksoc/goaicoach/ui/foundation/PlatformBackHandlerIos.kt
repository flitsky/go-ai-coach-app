package com.worksoc.goaicoach.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.backhandler.PredictiveBackHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun PlatformPredictiveBackHandler(enabled: Boolean, onBack: suspend (progress: Flow<Float>) -> Unit) {
    PredictiveBackHandler(enabled = enabled) { events -> onBack(events.map { event -> event.progress }) }
}
