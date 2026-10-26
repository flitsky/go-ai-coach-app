package com.worksoc.goaicoach.ui.shell

import androidx.compose.runtime.Composable
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.shared.domain.GameState

/**
 * 바둑판 사진 분석 화면의 자리(백로그 #294 — S18). 안드로이드는 `BoardScanScreen`(카메라 · OpenCV), iOS는 첫 판에 싣지 않아(U-84)
 * 입구가 감춰지므로 여기 오지 않는다 — 와도 곧바로 돌아간다.
 */
@Composable
internal expect fun BoardScanSlot(
    engineClient: EngineSessionClient,
    onBackClick: () -> Unit,
    onStartGameWithState: (GameState) -> Unit,
)
