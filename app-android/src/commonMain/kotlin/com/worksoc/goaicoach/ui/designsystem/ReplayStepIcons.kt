package com.worksoc.goaicoach.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 다시보기의 「처음으로」 · 「끝으로」 단추 그림 — 막대 하나 + 세모 둘(⏮ ⏭의 생김새).
 * 글자 `⏮` `⏭`는 iOS에서 컬러 이모지로 그려져(U+FE0E로도 안 된다) 옆의 `◀◀` `◀`와 어긋났다(2026-11-10 시뮬레이터) — 두 플랫폼이 같은 그림을 쓴다.
 * 색은 단추의 글자색을 따른다(`Icon`의 tint).
 */
internal val ReplaySkipToFirstIcon: ImageVector by lazy {
    ImageVector.Builder(name = "ReplaySkipToFirst", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 6f); horizontalLineTo(6f); verticalLineTo(18f); horizontalLineTo(4f); close()
            moveTo(13f, 6f); lineTo(6f, 12f); lineTo(13f, 18f); close()
            moveTo(20f, 6f); lineTo(13f, 12f); lineTo(20f, 18f); close()
        }
        .build()
}

internal val ReplaySkipToLastIcon: ImageVector by lazy {
    ImageVector.Builder(name = "ReplaySkipToLast", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 6f); lineTo(11f, 12f); lineTo(4f, 18f); close()
            moveTo(11f, 6f); lineTo(18f, 12f); lineTo(11f, 18f); close()
            moveTo(18f, 6f); horizontalLineTo(20f); verticalLineTo(18f); horizontalLineTo(18f); close()
        }
        .build()
}
