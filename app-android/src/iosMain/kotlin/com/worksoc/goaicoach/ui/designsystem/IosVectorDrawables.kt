package com.worksoc.goaicoach.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// 만든 파일 — 고치지 말 것. `scripts/ios-vector-drawables.py`가 `app-android/src/main/res/drawable/reward_*.xml`에서 만든다(백로그 #294 — S9).

internal val RewardAdSkip: ImageVector by lazy {
    ImageVector.Builder(name = "reward_ad_skip", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(fill = SolidColor(Color(0xFF000000)), pathData = addPathNodes("M4.5,5 L15,12 L4.5,19 Z"))
        .addPath(fill = SolidColor(Color(0xFF000000)), pathData = addPathNodes("M16.8,5 L20,5 L20,19 L16.8,19 Z"))
        .build()
}

internal val RewardEval: ImageVector by lazy {
    ImageVector.Builder(name = "reward_eval", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(stroke = SolidColor(Color(0xFF000000)), strokeLineWidth = 2f, pathData = addPathNodes("M12,4 a8,8 0 1,0 0,16 a8,8 0 1,0 0,-16"))
        .addPath(fill = SolidColor(Color(0xFF000000)), pathData = addPathNodes("M12,4 a8,8 0 1,0 0,16 Z"))
        .build()
}

internal val RewardMoveReview: ImageVector by lazy {
    ImageVector.Builder(name = "reward_move_review", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(fill = SolidColor(Color(0xFF000000)), pathData = addPathNodes("M4,14 L7.5,14 L7.5,20 L4,20 Z"))
        .addPath(fill = SolidColor(Color(0xFF000000)), pathData = addPathNodes("M10.2,9 L13.7,9 L13.7,20 L10.2,20 Z"))
        .addPath(fill = SolidColor(Color(0xFF000000)), pathData = addPathNodes("M16.5,4 L20,4 L20,20 L16.5,20 Z"))
        .build()
}

internal val RewardTopMoves: ImageVector by lazy {
    ImageVector.Builder(name = "reward_top_moves", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(fill = SolidColor(Color(0xFF000000)), pathData = addPathNodes("M12,2.5 C13,8.4 15.6,11 21.5,12 C15.6,13 13,15.6 12,21.5 C11,15.6 8.4,13 2.5,12 C8.4,11 11,8.4 12,2.5 Z"))
        .build()
}

internal val RewardUndo: ImageVector by lazy {
    ImageVector.Builder(name = "reward_undo", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(fill = SolidColor(Color(0xFF000000)), pathData = addPathNodes("M3,9.5 L10,5 L10,14 Z"))
        .addPath(stroke = SolidColor(Color(0xFF000000)), strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round, pathData = addPathNodes("M9.5,9.5 L14,9.5 C17.3,9.5 20,12.2 20,15.5 L20,19"))
        .build()
}
