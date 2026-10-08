package com.worksoc.goaicoach.ui.history

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.ui.designsystem.AppBorderWidth
import com.worksoc.goaicoach.ui.designsystem.AppElevation
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.ScoreGraphPalette
import com.worksoc.goaicoach.ui.l10n.UiStrings
import com.worksoc.goaicoach.ui.l10n.blackLeadLabel
import com.worksoc.goaicoach.ui.play.winRateLabelFor
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * **다시보기의 큰 형세 그래프**(backlog #226, 사용자 피드백 2026-10-08: 「그래프 확장 및 스크롤」).
 *
 * 사용자가 정한 것(2026-10-08): 기본은 작은 요약 바이고, 누르면 **화면의 1/3쯤으로** 커지며 **그래프와 세부 정보**를 함께 보인다.
 * **좌우로 스크롤**되고, **스크롤하면 그 지점으로 수순이 이동**한다.
 *
 * 그래서 이 그래프는 대국 화면의 것(`ScoreTimelineGraph` — 꺼 둔 채다)과 다르게 그린다:
 * - **판 전체**의 형세를 그린다(지금 수순까지만이 아니라) — 넘겨 볼 앞뒤가 있어야 스크롤이 뜻이 있다.
 * - 가운데에 **표시선이 고정**돼 있고 그래프가 그 밑을 지나간다. 표시선 아래의 수가 곧 지금 수순이다([replayGraphMoveAtScroll]).
 * - 수순을 다른 길(이전·다음 버튼, 변곡점 칩)로 옮기면 그래프가 따라온다. 손으로 넘기는 동안은 따라오게 하지 않는다 — 서로를 밀면 떨린다.
 *
 * 닫는 것은 위의 세부 정보 줄이다(`✕`). 그래프를 누르면 닫히지 않고 **누른 자리의 수로 간다** — 넘기려던 손끝에 창이 닫히지 않게.
 */
@Composable
internal fun ReplayScoreGraphPanel(
    snapshots: List<ScoreSnapshot>,
    moveNumber: Int,
    lastMoveNumber: Int,
    capturedByBlack: Int,
    capturedByWhite: Int,
    strings: UiStrings,
    onSeek: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val readings = remember(snapshots) { replayScoreReadings(snapshots) }
    val current = remember(snapshots, moveNumber) {
        snapshots.filter { it.hasScoreData && it.moveNumber <= moveNumber }.maxByOrNull { it.moveNumber }
    }
    // 창의 실제 높이에서 잰다 — `Configuration.screenHeightDp`는 대상 SDK에 따라 시스템 바를 다르게 빼고 dp로 반올림돼 있다(lint).
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(windowHeight * ReplayScoreGraphScreenShare)
            .testTag(ReplayScoreGraphPanelTag),
        color = ScoreGraphPalette.Background,
        shape = RoundedCornerShape(AppRadius.Corner8),
        border = BorderStroke(AppBorderWidth.Hairline, ScoreGraphPalette.Border),
        tonalElevation = AppElevation.Level0,
        shadowElevation = AppElevation.Level0,
    ) {
        Column {
            // 세부 정보 — 지금 수순의 형세·승률과 사석. 이 줄을 누르면 닫힌다.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClose)
                    .padding(horizontal = AppSpacing.Space12, vertical = AppSpacing.Space8)
                    .testTag(ReplayScoreGraphCloseTag),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.Space2)) {
                    Text(
                        // 이 수까지 잰 형세가 없으면(앞부분 기록이 없는 옛 판 · 이어 둔 판) 점수를 지어내지 않는다 — 수순만 적는다.
                        text = listOfNotNull("$moveNumber / $lastMoveNumber", current?.whiteScoreLead?.let { blackLeadLabel(-it) }).joinToString(" · "),
                        color = ScoreGraphPalette.Title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(
                            current?.whiteWinRate?.let { winRateLabelFor(it, strings) },
                            "${strings.colorLabel(StoneColor.Black)} ${strings.capturesPrefix} $capturedByBlack",
                            "${strings.colorLabel(StoneColor.White)} ${strings.capturesPrefix} $capturedByWhite",
                        ).joinToString(" · "),
                        color = ScoreGraphPalette.Label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(text = "✕", color = ScoreGraphPalette.Label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            ScrubbableScoreGraph(
                readings = readings,
                moveNumber = moveNumber,
                lastMoveNumber = lastMoveNumber,
                onSeek = onSeek,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 가로로 넘기는 그래프 본체 — 가운데 표시선 아래의 수가 지금 수순이다. */
@Composable
private fun ScrubbableScoreGraph(
    readings: List<ReplayScoreReading>,
    moveNumber: Int,
    lastMoveNumber: Int,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pxPerMove = with(LocalDensity.current) { ReplayScoreGraphMoveWidth.toPx() }
    val scrollState = rememberScrollState(initial = replayGraphScrollForMove(moveNumber, pxPerMove))
    val latestMove by rememberUpdatedState(moveNumber)
    val latestOnSeek by rememberUpdatedState(onSeek)
    // 우리가 그래프를 옮기는 중인가(수순을 따라가거나 수 자리에 맞추는 중) — 그 움직임을 「사용자가 넘겼다」로 읽지 않으려고 든다.
    var isFollowing by remember { mutableStateOf(false) }

    // 수순이 다른 길로 바뀌면(버튼 · 변곡점 칩) 그래프가 따라온다. 손으로 넘기는 중에는 건드리지 않는다.
    LaunchedEffect(moveNumber, pxPerMove) {
        val target = replayGraphScrollForMove(moveNumber, pxPerMove)
        if (!scrollState.isScrollInProgress && scrollState.value != target) {
            isFollowing = true
            try {
                scrollState.scrollTo(target)
            } finally {
                isFollowing = false
            }
        }
    }
    // 손으로 넘기면 표시선 아래의 수로 수순을 옮긴다.
    LaunchedEffect(scrollState, pxPerMove, lastMoveNumber) {
        snapshotFlow { scrollState.value }.collect { value ->
            if (!isFollowing && scrollState.isScrollInProgress) {
                val move = replayGraphMoveAtScroll(value, pxPerMove, lastMoveNumber)
                if (move != latestMove) latestOnSeek(move)
            }
        }
    }
    // 손을 떼고 멈춘 자리가 수와 수 사이면 그 수의 자리에 맞춘다 — 표시선이 점 위에 선다.
    // ⚠️ 「넘기는 중인가」를 이 효과의 **키로 쓰지 않는다** — 맞추는 움직임 자체가 그 값을 뒤집어 효과가 스스로를 취소한다.
    LaunchedEffect(scrollState, pxPerMove, lastMoveNumber) {
        snapshotFlow { scrollState.isScrollInProgress }.collect { inProgress ->
            if (!inProgress && !isFollowing) {
                val target = replayGraphScrollForMove(replayGraphMoveAtScroll(scrollState.value, pxPerMove, lastMoveNumber), pxPerMove)
                if (scrollState.value != target) {
                    isFollowing = true
                    try {
                        scrollState.animateScrollTo(target)
                    } finally {
                        isFollowing = false
                    }
                }
            }
        }
    }

    val maxScale = remember(readings) {
        val maxAbsLead = readings.maxOfOrNull { abs(it.blackLead) } ?: 0.0
        maxOf(ceil(maxAbsLead / 5.0) * 5.0, 5.0)
    }
    BoxWithConstraints(modifier = modifier.testTag(ReplayScoreGraphScrollTag)) {
        val viewportWidth = maxWidth
        val viewportWidthPx = with(LocalDensity.current) { viewportWidth.toPx() }
        // 첫 수와 마지막 수도 가운데 표시선까지 올 수 있게, 양쪽에 화면 폭의 절반씩 빈 자리를 둔다.
        val contentWidth = viewportWidth + ReplayScoreGraphMoveWidth * lastMoveNumber
        Box(modifier = Modifier.fillMaxSize().horizontalScroll(scrollState)) {
            Canvas(
                modifier = Modifier
                    .width(contentWidth)
                    .fillMaxHeight()
                    .pointerInput(pxPerMove, lastMoveNumber) {
                        // 누른 자리의 수로 간다 — 이 캔버스의 x는 스크롤된 내용 안의 좌표다.
                        detectTapGestures { tap ->
                            latestOnSeek(replayGraphMoveAtScroll((tap.x - viewportWidthPx / 2f).roundToInt(), pxPerMove, lastMoveNumber))
                        }
                    },
            ) {
                val chartTop = 10.dp.toPx()
                val chartBottom = size.height - 12.dp.toPx()
                val chartHeight = chartBottom - chartTop
                if (chartHeight <= 0f) return@Canvas
                val centerY = chartTop + chartHeight / 2f
                val xForMove = { move: Int -> viewportWidthPx / 2f + move * pxPerMove }
                val yForLead = { lead: Double -> (centerY - (lead / maxScale).toFloat() * (chartHeight / 2f)).coerceIn(chartTop, chartBottom) }

                val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                floatArrayOf(chartTop, chartTop + chartHeight * 0.25f, centerY, chartTop + chartHeight * 0.75f, chartBottom).forEachIndexed { index, y ->
                    drawLine(
                        color = if (index == 2) ScoreGraphPalette.JigoLine else ScoreGraphPalette.GridLine,
                        start = Offset(xForMove(0), y),
                        end = Offset(xForMove(lastMoveNumber), y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dash,
                    )
                }
                // 열 수마다 눈금과 수순 번호 — 넘기면서 어디쯤인지 알 수 있게.
                (0..lastMoveNumber step ReplayScoreGraphTickEvery).forEach { move ->
                    drawLine(ScoreGraphPalette.GridLine, Offset(xForMove(move), chartTop), Offset(xForMove(move), chartBottom), strokeWidth = 1.dp.toPx())
                    drawGraphText(move.toString(), Offset(xForMove(move) + 3.dp.toPx(), chartBottom + 6.dp.toPx()), ScoreGraphPalette.Label)
                }
                var previous: Offset? = null
                readings.forEach { reading ->
                    val point = Offset(xForMove(reading.moveNumber), yForLead(reading.blackLead))
                    previous?.let { drawLine(ScoreGraphPalette.ScoreLine, it, point, strokeWidth = 2.dp.toPx()) }
                    previous = point
                }
                readings.forEach { reading ->
                    val isCurrent = reading.moveNumber == moveNumber
                    drawCircle(
                        color = if (isCurrent) ScoreGraphPalette.ActiveDot else ScoreGraphPalette.ScoreLine,
                        radius = (if (isCurrent) 4.5f else 1.5f).dp.toPx(),
                        center = Offset(xForMove(reading.moveNumber), yForLead(reading.blackLead)),
                    )
                }
            }
        }
        // 넘겨도 제자리에 있는 것들 — 가운데 표시선과 세로축의 눈금 글자.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val chartTop = 10.dp.toPx()
            val chartBottom = size.height - 12.dp.toPx()
            drawLine(ScoreGraphPalette.ActiveDot, Offset(size.width / 2f, chartTop), Offset(size.width / 2f, chartBottom), strokeWidth = 1.5.dp.toPx())
            val scale = maxScale.toInt().toString()
            drawGraphText("B +$scale", Offset(size.width - 6.dp.toPx(), chartTop + 5.dp.toPx()), ScoreGraphPalette.Label, Paint.Align.RIGHT)
            drawGraphText("W +$scale", Offset(size.width - 6.dp.toPx(), chartBottom - 5.dp.toPx()), ScoreGraphPalette.Label, Paint.Align.RIGHT)
        }
    }
}

private fun DrawScope.drawGraphText(
    label: String,
    center: Offset,
    color: Color,
    align: Paint.Align = Paint.Align.LEFT,
) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            textAlign = align
            textSize = 9.dp.toPx()
            typeface = Typeface.DEFAULT_BOLD
        }
        val baseline = center.y - (paint.ascent() + paint.descent()) / 2f
        canvas.nativeCanvas.drawText(label, center.x, baseline, paint)
    }
}

/** 큰 그래프의 높이 ÷ 화면 높이 — 사용자: *"화면의 1/3 수준가량"*(2026-10-08). */
internal const val ReplayScoreGraphScreenShare: Float = 1f / 3f

/** 큰 그래프에서 한 수의 가로 폭 — 폰 한 화면에 서른 수쯤 보인다(스레드가 정했다). */
internal val ReplayScoreGraphMoveWidth = 12.dp

private const val ReplayScoreGraphTickEvery = 10

/** 그래프의 한 점 — [moveNumber]번째 수 직후의 형세(흑 기준 점수차, 흑이 앞서면 +). */
internal data class ReplayScoreReading(
    val moveNumber: Int,
    val blackLead: Double,
)

/**
 * 판 전체의 형세 — 0수(0집)에서 시작해 수순대로. 점수차가 없는 기록(승률만 있는 것)은 점이 되지 않는다.
 * 같은 수에 기록이 둘이면(다시 잰 값) 뒤의 것을 쓴다.
 */
internal fun replayScoreReadings(snapshots: List<ScoreSnapshot>): List<ReplayScoreReading> {
    val byMove = snapshots
        .filter { it.hasScoreData && it.moveNumber > 0 }
        .mapNotNull { snapshot -> snapshot.whiteScoreLead?.let { whiteLead -> snapshot.moveNumber to -whiteLead } }
        .toMap()
    return listOf(ReplayScoreReading(moveNumber = 0, blackLead = 0.0)) +
        byMove.entries.sortedBy { it.key }.map { (moveNumber, blackLead) -> ReplayScoreReading(moveNumber, blackLead) }
}

/** 그래프를 [scrollPx]만큼 넘겼을 때 가운데 표시선 아래에 오는 수 — 가장 가까운 수로, 0수와 마지막 수 사이에서. */
internal fun replayGraphMoveAtScroll(scrollPx: Int, pxPerMove: Float, lastMoveNumber: Int): Int =
    if (pxPerMove <= 0f) 0 else (scrollPx / pxPerMove).roundToInt().coerceIn(0, lastMoveNumber.coerceAtLeast(0))

/** [moveNumber]번째 수가 가운데 표시선에 오는 스크롤 위치. */
internal fun replayGraphScrollForMove(moveNumber: Int, pxPerMove: Float): Int =
    (moveNumber.coerceAtLeast(0) * pxPerMove).roundToInt()

internal const val ReplayScoreGraphPanelTag = "replay-score-graph-panel"
internal const val ReplayScoreGraphCloseTag = "replay-score-graph-close"
internal const val ReplayScoreGraphScrollTag = "replay-score-graph-scroll"
