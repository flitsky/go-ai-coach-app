package com.worksoc.goaicoach.ui.designsystem

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

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
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            textAlign = when (anchor) {
                CanvasTextAnchor.Left -> Paint.Align.LEFT
                CanvasTextAnchor.Center -> Paint.Align.CENTER
                CanvasTextAnchor.Right -> Paint.Align.RIGHT
            }
            textSize = textSizePx
            typeface = Typeface.DEFAULT_BOLD
        }
        val baseline = center.y - (paint.descent() + paint.ascent()) / 2f
        if (outlineColor != null) {
            val outline = Paint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = outlineWidthPx
                this.color = outlineColor.toArgb()
            }
            canvas.nativeCanvas.drawText(label, center.x, baseline, outline)
        }
        canvas.nativeCanvas.drawText(label, center.x, baseline, paint)
    }
}
