package com.worksoc.goaicoach.ui.foundation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

// 린트(NoCollectCallFound)는 이 람다 안에서 `collect`를 찾지만, 모으는 일은 넘겨받은 `onBack`이 한다 — 공용 화면의 처리기는
// 진행을 끝까지 모으거나 취소로 끝난다(`PlatformBackHandler.kt`의 계약).
@Suppress("NoCollectCallFound")
@Composable
actual fun PlatformPredictiveBackHandler(enabled: Boolean, onBack: suspend (progress: Flow<Float>) -> Unit) {
    PredictiveBackHandler(enabled = enabled) { events -> onBack(events.map { event -> event.progress }) }
}
