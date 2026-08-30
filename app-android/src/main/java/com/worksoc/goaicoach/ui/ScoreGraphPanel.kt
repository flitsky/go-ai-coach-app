package com.worksoc.goaicoach.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.StoneColor
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
internal fun ScoreTimelineGraph(
    snapshots: List<ScoreSnapshot>,
    capturedByBlack: Int,
    capturedByWhite: Int,
    whiteWinRate: Double?,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val configuration = LocalConfiguration.current
    val targetHeight = if (isExpanded) (configuration.screenWidthDp.dp / 4) else 44.dp
    
    // 부드럽게 접히고 늘어나는 높이 애니메이션
    val heightDp by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "ScoreTimelineGraphHeight"
    )
    
    // 밝고 연한 프리미엄 슬레이트/스카이 블루 톤 구성
    val backgroundLight = Color(0xFFF8FAFC) // 연한 그레이빛 화이트
    val borderLightColor = Color(0xFFE2E8F0) // 연한 보더
    val gridLineColor = Color(0xFFE2E8F0) // 연한 가이드선 그레이
    val textBlueColor = Color(0xFF64748B) // 차분한 슬레이트 블루그레이 텍스트
    val scoreLineColor = Color(0xFF3B82F6) // 선명하고 시원한 꺾은선 블루
    val activeDotColor = Color(0xFFEF4444) // 화사한 붉은색 끝 점
    val jigoLineColor = Color(0xFF94A3B8) // 명확한 비김 기준선 그레이

    // 데이터 가공 및 캐싱: Composable 레벨에서 계산하여 Canvas 프레임 오버헤드 방지
    val points = androidx.compose.runtime.remember(snapshots) {
        val list = mutableListOf<Double>()
        list.add(0.0)
        snapshots.filter { it.hasScoreData }
            .sortedBy { it.moveNumber }
            .forEach {
                it.whiteScoreLead?.let { whiteLead ->
                    list.add(-whiteLead) // 흑 우세 기준
                }
            }
        list
    }

    val maxScale = androidx.compose.runtime.remember(points) {
        val maxAbsLead = points.maxOfOrNull { abs(it) } ?: 0.0
        maxOf(ceil(maxAbsLead / 5.0) * 5.0, 5.0)
    }

    val currentScoreLabel = androidx.compose.runtime.remember(points) {
        val latestLead = if (points.size > 1) points.last() else 0.0
        val latestAbs = abs(latestLead)
        val roundedLatest = ((latestAbs * 10).roundToInt() / 10.0).toString()
        when {
            latestLead > 0.0 -> "B +$roundedLatest"
            latestLead < 0.0 -> "W +$roundedLatest"
            else -> "0.0"
        }
    }

    // 승률(%)은 계산은 되지만 그동안 UI 어디에도 노출되지 않던 값이라, 항상 보이는 이 요약 바에 흡수한다.
    val winRateLabel = whiteWinRate?.let { rate ->
        val blackPct = ((1.0 - rate) * 100).roundToInt()
        val whitePct = (rate * 100).roundToInt()
        "${strings.colorLabel(StoneColor.Black)} $blackPct% · ${strings.colorLabel(StoneColor.White)} $whitePct%"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            // 펼친 그래프는 `Canvas(fillMaxSize)`가 정확한 높이를 알아야 하므로 **고정**이어야
            // 하지만, 접힌 요약 바는 하한이어야 한다(#30). 44dp 고정일 때 가운데 칸의 두 줄
            // (스코어차 `bodySmall` 14sp + 승률 `labelSmall` 11sp)이 배율 2.0배에서 약 72dp를
            // 요구해 아랫줄이 통째로 썰렸다. 이 바는 스크롤되는 Column 안에 있으므로 커져도
            // 화면이 깨지지 않는다.
            //
            // ⚠️ 양쪽 모두 `heightIn`으로 통일하지 마라 — 펼친 쪽은 maxHeight가 Infinity가 돼
            // 안쪽 `Canvas(fillMaxSize)`가 무너진다.
            .then(if (isExpanded) Modifier.height(heightDp) else Modifier.heightIn(min = heightDp))
            .clickable { onExpandedChange(!isExpanded) },
        color = backgroundLight,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, borderLightColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        if (heightDp <= 48.dp) {
            // 접힌 상태: 흑/백 사석 수 + 현재 스코어차 + 승률을 한눈에 보여준다.
            // 세 칸 모두 `weight`를 준다(#30). 예전에는 가중치 없는 자식 셋 + `SpaceBetween`이라,
            // 글꼴이 커지면 남는 공간이 사라지면서 글자끼리 맞붙었다 — 영어 360dp에서
            // `White 0%Captures 0`처럼 띄어쓰기 없이 붙어 렌더됐다. 가중치를 주면 칸 경계가
            // 고정되고, 넘치는 쪽은 자기 칸 안에서 말줄임된다.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${strings.colorLabel(StoneColor.Black)} ${strings.capturesPrefix} $capturedByBlack",
                    color = textBlueColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Column(
                    modifier = Modifier.weight(1.2f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = currentScoreLabel,
                        color = textBlueColor,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (winRateLabel != null) {
                        Text(
                            text = winRateLabel,
                            color = textBlueColor,
                            style = MaterialTheme.typography.labelSmall,
                            // 스코어차와 달리 승률은 **두 줄을 허용한다.** 배율 2.0배에서
                            // `흑 29% · 백 71%`가 이 칸(약 100dp)에 한 줄로는 안 들어가는데,
                            // 바가 이제 하한 높이라 줄이 늘어도 잘리지 않는다 — 말줄임으로
                            // 한쪽 승률을 지우는 것보다 두 줄로 다 보여주는 편이 낫다.
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Text(
                    text = "${strings.colorLabel(StoneColor.White)} ${strings.capturesPrefix} $capturedByWhite",
                    color = textBlueColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            // 펼쳐진 상태
            Box(modifier = Modifier.fillMaxSize()) {
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
                            color = if (idx == 2) jigoLineColor else gridLineColor,
                            start = Offset(chartLeft, y),
                            end = Offset(chartRight, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )
                    }
                    
                    // 우측 라벨 텍스트 그리기
                    val labelX = chartRight + 8.dp.toPx()
                    val formattedScale = maxScale.toInt().toString()
                    
                    drawAxisText(label = "B +$formattedScale", center = Offset(labelX, chartTop), color = textBlueColor)
                    drawAxisText(label = "W +$formattedScale", center = Offset(labelX, chartBottom), color = textBlueColor)
                    
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
                            
                            // 최신 스코어 텍스트를 활성 점 옆에 그립니다
                            val isNearRightEdge = current.x > (chartLeft + chartWidth * 0.85f)
                            val textX = if (isNearRightEdge) {
                                current.x - 8.dp.toPx()
                            } else {
                                current.x + 8.dp.toPx()
                            }
                            val textAlign = if (isNearRightEdge) Paint.Align.RIGHT else Paint.Align.LEFT
                            drawAxisText(
                                label = currentScoreLabel,
                                center = Offset(textX, current.y),
                                color = textBlueColor, // 우측 축 텍스트와 동일한 색상 적용
                                align = textAlign,
                                sizeDp = 9 // 원래 폰트 사이즈 복구
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
                
                // 좌측 상단에 닫기 직관성을 주는 X 표시 (✕)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 8.dp)
                ) {
                    Text(
                        text = "✕",
                        color = textBlueColor,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawAxisText(
    label: String,
    center: Offset,
    color: Color,
    align: Paint.Align = Paint.Align.LEFT,
    sizeDp: Int = 9,
) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            textAlign = align
            textSize = sizeDp.dp.toPx()
            typeface = Typeface.DEFAULT_BOLD
        }
        val baseline = center.y - (paint.ascent() + paint.descent()) / 2f
        canvas.nativeCanvas.drawText(label, center.x, baseline, paint)
    }
}
