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

/**
 * [label]을 그릴 수 있는 굵은 글꼴. ⚠️ skia의 `drawString`은 글자마다 다른 글꼴로 대신 그려 주지 않는다 — Helvetica Neue에 없는
 * 한글 · 한자 · 가나는 빈 네모가 된다. 그래프 축이 「흑 +15」 · 「黒 +15」가 되면서(QA-23d, 2026-11-23) 처음 닿았다. 그래서 첫 번째로
 * 없는 글자를 그릴 수 있는 시스템 글꼴을 골라 그 줄 전체를 그것으로 그린다(숫자 · 기호는 그 글꼴에도 있다). 글자마다 한 번만 찾는다.
 */
private val fallbackTypefaces = mutableMapOf<Int, Typeface?>()

private fun typefaceFor(label: String): Typeface? {
    val base = boldTypeface ?: return null
    val missing = label.firstOrNull { ch -> !ch.isWhitespace() && base.getUTF32Glyph(ch.code) == 0.toShort() } ?: return base
    return fallbackTypefaces.getOrPut(missing.code) {
        FontMgr.default.matchFamilyStyleCharacter(null, FontStyle.BOLD, null, missing.code)
    } ?: base
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
        val font = Font(typefaceFor(label), textSizePx)
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
