package com.worksoc.goaicoach.ui.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/** 캔버스 글자의 가로 기준 — [center].x가 글자의 왼쪽 끝 · 가운데 · 오른쪽 끝이다. */
enum class CanvasTextAnchor { Left, Center, Right }

/**
 * 캔버스에 굵은 글자 한 줄 — 판의 좌표 · 수순 번호 · 추천 수 표식과 형세 그래프의 축 글자(백로그 #294 — 이식 계획 S11).
 * 세로는 [center].y에 글자 높이의 가운데를 맞춘다(`y - (descent + ascent) / 2`). [outlineColor]가 있으면 그 테두리를 먼저 그린다.
 * 안드로이드는 오늘의 `android.graphics.Paint`(굵은 기본 글꼴) 코드 그대로, iOS는 skia 글꼴로 같은 셈을 한다.
 */
expect fun DrawScope.drawBoldCanvasText(
    label: String,
    center: Offset,
    textSizePx: Float,
    color: Color,
    anchor: CanvasTextAnchor = CanvasTextAnchor.Center,
    outlineColor: Color? = null,
    outlineWidthPx: Float = 0f,
)
