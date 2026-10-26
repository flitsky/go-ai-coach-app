package com.worksoc.goaicoach.ui.foundation

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

/**
 * 뒤로가기(백로그 #294 — 이식 계획 S4). 안드로이드는 `androidx.activity.compose`의 `BackHandler` · `PredictiveBackHandler`
 * 그대로, iOS는 Compose Multiplatform의 뒤로가기(가장자리 밀기)다.
 *
 * [PlatformPredictiveBackHandler]의 `progress`는 제스처 진행(0..1)이다 — 다 모으면(정상 완료) 놓은 것이고, 취소되면 거둔 것이다.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

@Composable
expect fun PlatformPredictiveBackHandler(enabled: Boolean = true, onBack: suspend (progress: Flow<Float>) -> Unit)
