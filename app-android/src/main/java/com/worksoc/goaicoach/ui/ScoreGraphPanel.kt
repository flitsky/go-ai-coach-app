package com.worksoc.goaicoach.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
internal fun ScoreGraphPanel(
    snapshots: List<ScoreSnapshot>,
    capturedByBlack: Int,
    capturedByWhite: Int,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val graphSnapshots = snapshots.filter { it.hasScoreData }
    val latest = graphSnapshots.lastOrNull()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandedChange(!isExpanded) },
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GraphLegendDot(ScoreLineColor, "Score")
                    GraphLegendDot(WinRateLineColor, "Win Rate")
                }
                Text(
                    text = latest?.toHeadline() ?: "M? waiting",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (isExpanded) {
                if (graphSnapshots.isEmpty()) {
                    Text(
                        text = "Score snapshots will appear after the first estimate.",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    ScoreGraphCanvas(graphSnapshots)
                }
                Text(
                    text = "Captures B $capturedByBlack / W $capturedByWhite",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ScoreGraphCanvas(
    snapshots: List<ScoreSnapshot>,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
    ) {
        val chartLeft = 10.dp.toPx()
        val chartRight = size.width - 24.dp.toPx()
        val chartTop = 8.dp.toPx()
        val chartBottom = size.height - 8.dp.toPx()
        val chartHeight = chartBottom - chartTop
        val centerY = chartTop + chartHeight / 2f
        val chartWidth = chartRight - chartLeft

        drawLine(
            color = AxisColor,
            start = Offset(chartLeft, centerY),
            end = Offset(chartRight, centerY),
            strokeWidth = 1.dp.toPx(),
        )
        drawLine(
            color = GridColor,
            start = Offset(chartLeft, chartTop),
            end = Offset(chartRight, chartTop),
            strokeWidth = 1.dp.toPx(),
        )
        drawLine(
            color = GridColor,
            start = Offset(chartLeft, chartBottom),
            end = Offset(chartRight, chartBottom),
            strokeWidth = 1.dp.toPx(),
        )
        val axisLabelX = chartRight + 10.dp.toPx()
        drawAxisLabel(label = "B", center = Offset(axisLabelX, chartTop))
        drawAxisLabel(label = "W", center = Offset(axisLabelX, chartBottom))

        val denominator = maxOf(snapshots.size - 1, MinimumGraphSlots)
        val xForIndex = { index: Int ->
            chartLeft + chartWidth * (index.toFloat() / denominator.toFloat())
        }
        val scoreScale = snapshots
            .mapNotNull { it.whiteScoreLead?.let { lead -> -lead } }
            .scaleFor(granularity = 5.0)
        val winRateScale = snapshots
            .mapNotNull { it.whiteWinRate?.let { winRate -> (0.5 - winRate) * 100.0 } }
            .scaleFor(granularity = 10.0)

        drawSeries(
            snapshots = snapshots,
            color = ScoreLineColor,
            yValue = { snapshot ->
                snapshot.whiteScoreLead?.let { whiteLead ->
                    val blackLead = -whiteLead
                    centerY - (blackLead.toFloat() / scoreScale.toFloat()) * (chartHeight / 2f)
                }
            },
            xForIndex = xForIndex,
        )
        drawSeries(
            snapshots = snapshots,
            color = WinRateLineColor,
            yValue = { snapshot ->
                snapshot.whiteWinRate?.let { winRate ->
                    val blackDelta = ((0.5 - winRate) * 100.0).toFloat()
                    centerY - (blackDelta / winRateScale.toFloat()) * (chartHeight / 2f)
                }
            },
            xForIndex = xForIndex,
        )
    }
}

private fun DrawScope.drawAxisLabel(
    label: String,
    center: Offset,
) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(93, 89, 79)
            textAlign = Paint.Align.CENTER
            textSize = 11.dp.toPx()
            typeface = Typeface.DEFAULT_BOLD
        }
        val baseline = center.y - (paint.ascent() + paint.descent()) / 2f
        canvas.nativeCanvas.drawText(label, center.x, baseline, paint)
    }
}

private fun DrawScope.drawSeries(
    snapshots: List<ScoreSnapshot>,
    color: Color,
    yValue: (ScoreSnapshot) -> Float?,
    xForIndex: (Int) -> Float,
) {
    var previous: Offset? = null
    snapshots.forEachIndexed { index, snapshot ->
        val y = yValue(snapshot) ?: return@forEachIndexed
        val current = Offset(xForIndex(index), y.coerceIn(0f, size.height))
        val previousPoint = previous
        if (previousPoint != null) {
            drawLine(
                color = color,
                start = previousPoint,
                end = current,
                strokeWidth = 2.dp.toPx(),
            )
        }
        drawCircle(
            color = color,
            radius = 3.dp.toPx(),
            center = current,
        )
        previous = current
    }
}

@Composable
private fun GraphLegendDot(
    color: Color,
    label: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

private fun List<Double>.scaleFor(granularity: Double): Double {
    val maxMagnitude = maxOf(map { abs(it) }.maxOrNull() ?: 0.0, granularity)
    return ceil(maxMagnitude / granularity) * granularity
}

private fun ScoreSnapshot.toHeadline(): String =
    "M$moveNumber ${formatScoreLead()}${whiteWinRate?.let { " / ${formatWinRate(it)}" } ?: ""}"

private fun ScoreSnapshot.formatScoreLead(): String {
    val lead = whiteScoreLead ?: return source.label
    val rounded = ((abs(lead) * 10).roundToInt() / 10.0).toString()
    return if (lead >= 0.0) "W+$rounded" else "B+$rounded"
}

private fun formatWinRate(whiteWinRate: Double): String {
    val whitePercent = (whiteWinRate * 100).roundToInt()
    val leader = if (whiteWinRate >= 0.5) "W" else "B"
    val leaderPercent = maxOf(whitePercent, 100 - whitePercent)
    return "$leader $leaderPercent%"
}

private val ScoreSnapshotSource.label: String
    get() = when (this) {
        ScoreSnapshotSource.EngineEstimate -> "engine"
        ScoreSnapshotSource.LocalAreaEstimate -> "local"
        ScoreSnapshotSource.FinalScore -> "final"
    }

private const val MinimumGraphSlots = 15
private val ScoreLineColor = Color(0xFF2E7D32)
private val WinRateLineColor = Color(0xFF1565C0)
private val AxisColor = Color(0xFF8D8A80)
private val GridColor = Color(0x338D8A80)
