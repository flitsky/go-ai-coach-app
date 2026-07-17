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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.StoneColor
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
    val strings = LocalUiStrings.current
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
                    GraphLegendDot(ScoreLineColor, strings.scoreLead)
                    GraphLegendDot(WinRateLineColor, strings.winRate)
                }
                Text(
                    text = latest?.toHeadline(strings) ?: "${strings.movesPrefix}? ${strings.none}",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (isExpanded) {
                if (graphSnapshots.isEmpty()) {
                    Text(
                        text = strings.scoreSnapshotsEmpty,
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    ScoreGraphCanvas(graphSnapshots)
                }
                Text(
                    text = "${strings.capturesPrefix} ${strings.colorLabel(StoneColor.Black)} $capturedByBlack / ${strings.colorLabel(StoneColor.White)} $capturedByWhite",
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

private fun ScoreSnapshot.toHeadline(strings: UiStrings): String =
    "${strings.movesPrefix} $moveNumber ${formatScoreLead(strings)}${whiteWinRate?.let { " / ${formatWinRate(it)}" } ?: ""}"

private fun ScoreSnapshot.formatScoreLead(strings: UiStrings): String {
    val lead = whiteScoreLead ?: return source.label(strings)
    val rounded = ((abs(lead) * 10).roundToInt() / 10.0).toString()
    return if (lead >= 0.0) "W+$rounded" else "B+$rounded"
}

private fun formatWinRate(whiteWinRate: Double): String {
    val whitePercent = (whiteWinRate * 100).roundToInt()
    val leader = if (whiteWinRate >= 0.5) "W" else "B"
    val leaderPercent = maxOf(whitePercent, 100 - whitePercent)
    return "$leader $leaderPercent%"
}

private fun ScoreSnapshotSource.label(strings: UiStrings): String =
    when (this) {
        ScoreSnapshotSource.EngineEstimate -> strings.engineSource
        ScoreSnapshotSource.LocalAreaEstimate -> strings.localSource
        ScoreSnapshotSource.FinalScore -> strings.finalScoreSource
    }

private const val MinimumGraphSlots = 15
private val ScoreLineColor = Color(0xFF1565C0)
private val WinRateLineColor = Color(0xFF2E7D32)
private val AxisColor = Color(0xFF8D8A80)
private val GridColor = Color(0x338D8A80)

@Composable
internal fun ScoreTimelineGraph(
    snapshots: List<ScoreSnapshot>,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val heightDp = configuration.screenWidthDp.dp / 4
    
    // 짙은 세련된 네이비 블루 배경 (#1C2436)
    val backgroundBlue = Color(0xFF1C2436)
    val gridLineColor = Color(0x22FFFFFF) // 아주 희미한 흰색 점선
    val textBlueColor = Color(0xFF4FC3F7) // 라이트 블루 텍스트
    val scoreLineColor = Color(0xFF03A9F4) // 라이트 블루 실선
    val activeDotColor = Color(0xFFFF5252) // 활성 상태의 붉은색 끝 점
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp),
        color = backgroundBlue,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp)
        ) {
            val chartLeft = 16.dp.toPx()
            val chartRight = size.width - 60.dp.toPx()
            val chartTop = 12.dp.toPx()
            val chartBottom = size.height - 12.dp.toPx()
            val chartHeight = chartBottom - chartTop
            val chartWidth = chartRight - chartLeft
            val centerY = chartTop + chartHeight / 2f
            
            // 데이터 수집: 첫 시작점은 0수 0점으로 세팅
            val points = mutableListOf<Double>()
            points.add(0.0)
            snapshots.filter { it.hasScoreData }.sortedBy { it.moveNumber }.forEach {
                it.whiteScoreLead?.let { whiteLead ->
                    points.add(-whiteLead) // 흑 우세 기준
                }
            }
            
            // 반응형 최대 진폭 (최소 5.0)
            val maxAbsLead = points.maxOfOrNull { abs(it) } ?: 0.0
            val maxScale = maxOf(maxAbsLead, 5.0)
            
            // Y좌표 매핑 함수 (흑 우세는 위쪽, 백 우세는 아래쪽)
            val yForLead = { lead: Double ->
                centerY - (lead.toFloat() / maxScale.toFloat()) * (chartHeight / 2f)
            }
            
            // 점선 PathEffect 정의 (10px 실선, 10px 공백)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            
            // 수평 눈금선 5개 그리기
            val gridYs = floatArrayOf(
                chartTop,
                chartTop + chartHeight * 0.25f,
                centerY,
                chartTop + chartHeight * 0.75f,
                chartBottom
            )
            gridYs.forEachIndexed { idx, y ->
                drawLine(
                    color = if (idx == 2) Color(0x44FFFFFF) else gridLineColor,
                    start = Offset(chartLeft, y),
                    end = Offset(chartRight, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect
                )
            }
            
            // 우측 라벨 텍스트 그리기
            val labelX = chartRight + 8.dp.toPx()
            val formattedScale = ((maxScale * 10).roundToInt() / 10.0).toString()
            drawAxisText(label = "B+$formattedScale", center = Offset(labelX, chartTop), color = textBlueColor)
            drawAxisText(label = "-Jigo", center = Offset(labelX, centerY), color = textBlueColor)
            drawAxisText(label = "W+$formattedScale", center = Offset(labelX, chartBottom), color = textBlueColor)
            
            // 꺾은선 그리기
            val denominator = maxOf(points.size - 1, 15) // 최소 15개 슬롯 제공
            val xForIndex = { index: Int ->
                chartLeft + chartWidth * (index.toFloat() / denominator.toFloat())
            }
            
            var previous: Offset? = null
            points.forEachIndexed { idx, lead ->
                val x = xForIndex(idx)
                val y = yForLead(lead).coerceIn(chartTop, chartBottom)
                val current = Offset(x, y)
                
                // 선 그리기
                previous?.let { prev ->
                    drawLine(
                        color = scoreLineColor,
                        start = prev,
                        end = current,
                        strokeWidth = 2.dp.toPx()
                    )
                }
                
                // 도트 그리기
                if (idx == points.size - 1) {
                    drawCircle(
                        color = activeDotColor,
                        radius = 4.dp.toPx(),
                        center = current
                    )
                } else {
                    drawCircle(
                        color = scoreLineColor,
                        radius = 1.5.dp.toPx(),
                        center = current
                    )
                }
                
                previous = current
            }
        }
    }
}

private fun DrawScope.drawAxisText(
    label: String,
    center: Offset,
    color: Color,
) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            textAlign = Paint.Align.LEFT
            textSize = 9.dp.toPx()
            typeface = Typeface.DEFAULT_BOLD
        }
        val baseline = center.y - (paint.ascent() + paint.descent()) / 2f
        canvas.nativeCanvas.drawText(label, center.x, baseline, paint)
    }
}
