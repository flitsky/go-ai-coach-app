package com.worksoc.goaicoach.ui.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Typeface

/**
 * 굵은 시스템 글꼴. ⚠️ 이름 없이(`null`) 고르면 iOS의 skia는 글리프가 없는 빈 글꼴을 돌려준다 — 판의 좌표 · 수순 번호가 통째로
 * 안 보였다(2026-10-27 시뮬레이터). 그래서 iOS에 늘 있는 이름을 차례로 고른다.
 */
private val boldTypeface: Typeface? by lazy {
    listOf("Helvetica Neue", "Helvetica", "Arial", ".SF UI Text")
        .firstNotNullOfOrNull { family -> FontMgr.default.matchFamilyStyle(family, FontStyle.BOLD) }
        ?: FontMgr.default.legacyMakeTypeface("", FontStyle.BOLD)
}

actual fun DrawScope.drawBoldCanvasText(
    label: String,
    center: Offset,
    textSizePx: Float,
    color: Color,
    anchor: CanvasTextAnchor,
    outlineColor: Color?,
    outlineWidthPx: Float,
) {
    drawIntoCanvas { canvas ->
        val font = Font(boldTypeface, textSizePx)
        val metrics = font.metrics
        val baseline = center.y - (metrics.descent + metrics.ascent) / 2f
        val width = font.measureTextWidth(label)
        val x = when (anchor) {
            CanvasTextAnchor.Left -> center.x
            CanvasTextAnchor.Center -> center.x - width / 2f
            CanvasTextAnchor.Right -> center.x - width
        }
        if (outlineColor != null) {
            val outline = Paint().apply {
                isAntiAlias = true
                mode = PaintMode.STROKE
                strokeWidth = outlineWidthPx
                this.color = outlineColor.toArgb()
            }
            canvas.nativeCanvas.drawString(label, x, baseline, font, outline)
        }
        val paint = Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
        }
        canvas.nativeCanvas.drawString(label, x, baseline, font, paint)
    }
}
