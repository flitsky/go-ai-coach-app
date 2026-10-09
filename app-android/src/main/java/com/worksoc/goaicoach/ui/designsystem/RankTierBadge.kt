package com.worksoc.goaicoach.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.min
import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.shared.policy.KgsRankTier
import com.worksoc.goaicoach.shared.policy.tier

/**
 * 기력 구간([KgsRankTier])마다의 색 — 테두리의 금속 셋(밝음·중간·깊음), 윤곽선, 안쪽 판, 보석, 후광.
 *
 * ⚠️ **골드 구간의 금색은 프리미엄 금색(`PremiumGold`)이 아니다.** 이 앱에서 `PremiumGold`는 "프리미엄 기능이다"라는
 * 뜻이라(`PremiumTheme.kt`) 빌려 쓰지 않는다 — 여기 금색은 더 노랗고 밝으며, 어두운 판을 두른 **테두리**로만 나온다.
 */
internal data class RankTierColors(
    val metalLight: Color,
    val metal: Color,
    val metalDeep: Color,
    val outline: Color,
    val plateTop: Color,
    val plateBottom: Color,
    val gem: Color,
    val gemDeep: Color,
    val glow: Color,
)

internal object RankTierPalette {
    /** 판 위의 급수 글자 — 어느 구간이든 판이 어두워 흰색이 읽힌다. */
    val Label = Color.White

    /** 보석·판 위에 얹는 반사광. */
    val Highlight = Color.White

    fun of(tier: KgsRankTier): RankTierColors = when (tier) {
        KgsRankTier.Bronze -> RankTierColors(
            metalLight = Color(0xFFEBB78E), metal = Color(0xFFB5733D), metalDeep = Color(0xFF7A4720),
            outline = Color(0xFF3F2410), plateTop = Color(0xFF50362A), plateBottom = Color(0xFF2B1B13),
            gem = Color(0xFFEBB78E), gemDeep = Color(0xFFB5733D), glow = Color(0xFFB5733D),
        )
        KgsRankTier.Silver -> RankTierColors(
            metalLight = Color(0xFFF6F9FC), metal = Color(0xFFB4BEC8), metalDeep = Color(0xFF6C7782),
            outline = Color(0xFF343C45), plateTop = Color(0xFF414A54), plateBottom = Color(0xFF21272E),
            gem = Color(0xFFF6F9FC), gemDeep = Color(0xFFB4BEC8), glow = Color(0xFFB4BEC8),
        )
        KgsRankTier.Gold -> RankTierColors(
            metalLight = Color(0xFFFFF0A6), metal = Color(0xFFE6B02E), metalDeep = Color(0xFF946A10),
            outline = Color(0xFF4A3406), plateTop = Color(0xFF4C3B12), plateBottom = Color(0xFF261C06),
            gem = Color(0xFFFFF0A6), gemDeep = Color(0xFFE6B02E), glow = Color(0xFFE6B02E),
        )
        KgsRankTier.Platinum -> RankTierColors(
            metalLight = Color(0xFFD8FFF6), metal = Color(0xFF4FC7B5), metalDeep = Color(0xFF1E776C),
            outline = Color(0xFF0B3833), plateTop = Color(0xFF14453F), plateBottom = Color(0xFF08231F),
            gem = Color(0xFF8CF7E3), gemDeep = Color(0xFF27A794), glow = Color(0xFF4FC7B5),
        )
        KgsRankTier.Diamond -> RankTierColors(
            metalLight = Color(0xFFE6F2FF), metal = Color(0xFF74ABFF), metalDeep = Color(0xFF2C52BD),
            outline = Color(0xFF122256), plateTop = Color(0xFF1A2E6B), plateBottom = Color(0xFF0A1232),
            gem = Color(0xFFA9DBFF), gemDeep = Color(0xFF3B7BE6), glow = Color(0xFF74ABFF),
        )
        KgsRankTier.Master -> RankTierColors(
            metalLight = Color(0xFFF5DBFF), metal = Color(0xFFB862F2), metalDeep = Color(0xFF62219F),
            outline = Color(0xFF2A0B4A), plateTop = Color(0xFF341558), plateBottom = Color(0xFF15062B),
            gem = Color(0xFFEDB0FF), gemDeep = Color(0xFF9B3DDB), glow = Color(0xFFB862F2),
        )
        KgsRankTier.Grandmaster -> RankTierColors(
            metalLight = Color(0xFFFFF1B0), metal = Color(0xFFF2B63C), metalDeep = Color(0xFFA5661A),
            outline = Color(0xFF4A0A0F), plateTop = Color(0xFF7A141C), plateBottom = Color(0xFF2B050B),
            gem = Color(0xFFFF7A70), gemDeep = Color(0xFFC81E2B), glow = Color(0xFFFF6B4A),
        )
    }
}

/**
 * 구간마다 테두리가 **무엇을 더 갖는가.** 숫자는 [DesignGrid](한 변 60)칸 기준이다 — 실제 크기는 그리는 쪽이 곱한다.
 *
 * 약한 구간의 장식은 센 구간이 전부 물려받는다(둥근 테 → 겹테 → 모서리 징 → 깎은 모서리와 보석 → 깃과 후광 → 왕관 → 큰 왕관과 늘임 보석).
 * 그래서 한 구간 오를 때마다 "하나가 더 붙었다"로 읽힌다. ⚠️ 깃·왕관이 설 자리는 판을 줄여서 낸다 — 센 구간일수록 판이 작지만
 * 그 구간의 글자(`1단`~`9단`)도 짧다. 장식이 없는 구간은 자리를 거의 다 채운다 — 옆 카드의 바둑판 그림과 크기가 맞는다.
 */
private data class RankTierFrameShape(
    val sideInset: Float,
    val topInset: Float,
    val bottomInset: Float,
    val innerLine: Boolean = false,
    val cornerStuds: Boolean = false,
    val chamfered: Boolean = false,
    val topGem: Boolean = false,
    val feathers: Int = 0,
    val glow: Boolean = false,
    val crownSpikes: Int = 0,
    val pendant: Boolean = false,
)

private fun rankTierFrameShapeOf(tier: KgsRankTier): RankTierFrameShape = when (tier) {
    KgsRankTier.Bronze -> RankTierFrameShape(sideInset = 1.5f, topInset = 1.5f, bottomInset = 1.5f)
    KgsRankTier.Silver -> RankTierFrameShape(sideInset = 1.5f, topInset = 1.5f, bottomInset = 1.5f, innerLine = true)
    KgsRankTier.Gold -> RankTierFrameShape(
        sideInset = 1.5f, topInset = 1.5f, bottomInset = 1.5f,
        innerLine = true, cornerStuds = true,
    )
    KgsRankTier.Platinum -> RankTierFrameShape(
        sideInset = 1.5f, topInset = 5f, bottomInset = 1.5f,
        innerLine = true, cornerStuds = true, chamfered = true, topGem = true,
    )
    KgsRankTier.Diamond -> RankTierFrameShape(
        sideInset = 7f, topInset = 8f, bottomInset = 5f,
        innerLine = true, cornerStuds = true, chamfered = true, topGem = true, feathers = 1, glow = true,
    )
    KgsRankTier.Master -> RankTierFrameShape(
        sideInset = 9f, topInset = 11f, bottomInset = 6f,
        innerLine = true, cornerStuds = true, chamfered = true, topGem = true, feathers = 2, glow = true, crownSpikes = 3,
    )
    KgsRankTier.Grandmaster -> RankTierFrameShape(
        sideInset = 10f, topInset = 13f, bottomInset = 8f,
        innerLine = true, cornerStuds = true, chamfered = true, topGem = true, feathers = 3, glow = true, crownSpikes = 5,
        pendant = true,
    )
}

/**
 * 기력 한 칸을 **구간의 테두리**에 담아 보인다(백로그 #236) — 테두리는 구간이 오를수록 화려해지고, 글자는 급수 그대로다(`20급`·`3단`).
 *
 * 그림 자산 없이 캔버스로 그린다. 받은 자리의 짧은 변에 맞춘 정사각형을 가운데에 놓고, 글자가 판보다 넓으면
 * (`20 kyu`, 큰 글꼴 배율) 글자 크기를 줄여 한 줄에 넣는다.
 */
@Composable
internal fun RankTierBadge(rank: KgsRank, label: String, modifier: Modifier = Modifier) {
    val tier = rank.tier
    val colors = remember(tier) { RankTierPalette.of(tier) }
    val shape = remember(tier) { rankTierFrameShapeOf(tier) }
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val side = min(maxWidth, maxHeight)
        val unit = side / DesignGrid
        val baseStyle = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            shadow = Shadow(color = colors.outline, offset = Offset(0f, 1f), blurRadius = 3f),
        )
        val availableWidth = with(LocalDensity.current) {
            (unit * (DesignGrid - 2 * (shape.sideInset + FrameWidth + LabelSidePadding))).toPx()
        }
        val naturalWidth = remember(label, baseStyle) { measurer.measure(label, baseStyle, maxLines = 1).size.width }
        val style = if (naturalWidth > availableWidth) {
            baseStyle.copy(fontSize = baseStyle.fontSize * (availableWidth / naturalWidth))
        } else {
            baseStyle
        }
        Box(
            modifier = Modifier
                .size(side)
                .drawBehind { drawRankTierFrame(shape, colors) }
                .padding(
                    start = unit * shape.sideInset,
                    top = unit * shape.topInset,
                    end = unit * shape.sideInset,
                    bottom = unit * shape.bottomInset,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, color = RankTierPalette.Label, style = style, maxLines = 1, softWrap = false)
        }
    }
}

/** 테두리를 설계한 격자의 한 변 — 홈 카드의 아이콘 자리가 60dp라 한 칸이 1dp다. */
private const val DesignGrid = 60f
private const val FrameWidth = 3f
private const val CornerRadiusUnits = 9f
private const val ChamferCut = 9f

/** 테두리 안쪽 끝에서 글자까지 — 겹테의 안쪽 선(테두리에서 2.5칸 안)을 넘기고도 숨 쉴 틈이 남는 폭이다. */
private const val LabelSidePadding = 4.5f

private fun DrawScope.drawRankTierFrame(shape: RankTierFrameShape, colors: RankTierColors) {
    val side = size.minDimension
    val u = side / DesignGrid
    translate(left = (size.width - side) / 2f, top = (size.height - side) / 2f) {
        val plate = Rect(shape.sideInset * u, shape.topInset * u, side - shape.sideInset * u, side - shape.bottomInset * u)
        val frameWidth = FrameWidth * u
        val outlineStroke = Stroke(width = 0.9f * u, join = StrokeJoin.Round)
        val metal = Brush.linearGradient(
            0f to colors.metalLight,
            0.3f to colors.metal,
            0.55f to colors.metalDeep,
            0.8f to colors.metal,
            1f to colors.metalLight,
            start = plate.topLeft,
            end = plate.bottomRight,
        )
        val ornamentMetal = Brush.verticalGradient(
            0f to colors.metalLight,
            0.55f to colors.metal,
            1f to colors.metalDeep,
            startY = 0f,
            endY = plate.center.y,
        )

        if (shape.glow) {
            val radius = side * 0.62f
            drawCircle(
                brush = Brush.radialGradient(
                    0f to colors.glow.copy(alpha = 0.6f),
                    0.6f to colors.glow.copy(alpha = 0.28f),
                    1f to colors.glow.copy(alpha = 0f),
                    center = plate.center,
                    radius = radius,
                ),
                radius = radius,
                center = plate.center,
            )
        }

        // 깃과 왕관은 판보다 먼저 그린다 — 뿌리가 테두리 뒤로 숨어 "판에서 돋았다"로 보인다.
        repeat(shape.feathers) { index ->
            val feather = featherPath(plate, index, u)
            listOf(1f, -1f).forEach { direction ->
                scale(scaleX = direction, scaleY = 1f, pivot = Offset(side / 2f, side / 2f)) {
                    drawPath(feather, ornamentMetal)
                    drawPath(feather, colors.outline, style = outlineStroke)
                }
            }
        }
        if (shape.crownSpikes > 0) {
            val spikes = crownSpikes(plate, shape.crownSpikes, u)
            val crown = Path().apply {
                spikes.forEachIndexed { index, point -> if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) }
                close()
            }
            drawPath(crown, ornamentMetal)
            drawPath(crown, colors.outline, style = outlineStroke)
            if (shape.pendant) {
                // 큰 왕관은 뾰족한 끝마다 구슬을 단다 — 끝은 홀수 번째 꼭짓점이다.
                spikes.filterIndexed { index, _ -> index % 2 == 1 }.forEach { tip ->
                    drawCircle(colors.gem, radius = 1.5f * u, center = tip)
                    drawCircle(colors.outline, radius = 1.5f * u, center = tip, style = Stroke(width = 0.7f * u))
                }
            }
        }

        val body = platePath(plate, shape.chamfered, frameWidth / 2f, u)
        drawPath(body, Brush.verticalGradient(listOf(colors.plateTop, colors.plateBottom), startY = plate.top, endY = plate.bottom))
        if (shape.chamfered) {
            // 깎은 판에는 비스듬한 반사광 한 줄 — 유리처럼 보이게.
            clipPath(body) {
                drawPath(
                    Path().apply {
                        moveTo(plate.left, plate.top + plate.height * 0.5f)
                        lineTo(plate.left + plate.width * 0.5f, plate.top)
                        lineTo(plate.left + plate.width * 0.78f, plate.top)
                        lineTo(plate.left, plate.top + plate.height * 0.78f)
                        close()
                    },
                    RankTierPalette.Highlight.copy(alpha = 0.09f),
                )
            }
        }
        drawPath(body, metal, style = Stroke(width = frameWidth, join = StrokeJoin.Round))
        drawPath(platePath(plate, shape.chamfered, 0f, u), colors.outline, style = outlineStroke)
        drawPath(
            platePath(plate, shape.chamfered, frameWidth, u),
            colors.outline.copy(alpha = 0.6f),
            style = Stroke(width = 0.6f * u, join = StrokeJoin.Round),
        )
        if (shape.innerLine) {
            drawPath(
                platePath(plate, shape.chamfered, frameWidth + 1.7f * u, u),
                colors.metalLight.copy(alpha = 0.7f),
                style = Stroke(width = 0.8f * u, join = StrokeJoin.Round),
            )
        }

        if (shape.cornerStuds) {
            // 둥근 모서리는 호의 한가운데, 깎은 모서리는 빗변의 한가운데가 테두리의 중심선이다.
            val reach = if (shape.chamfered) ChamferCut * u / 2f else CornerRadiusUnits * u * 0.293f
            val studSize = if (shape.chamfered) 2.6f * u else 2.3f * u
            listOf(
                Offset(plate.left + reach, plate.top + reach),
                Offset(plate.right - reach, plate.top + reach),
                Offset(plate.left + reach, plate.bottom - reach),
                Offset(plate.right - reach, plate.bottom - reach),
            ).forEach { corner -> drawGem(corner, studSize, studSize, colors, u, setting = false) }
        }
        if (shape.topGem) {
            val scale = if (shape.crownSpikes > 0) 1.15f else 1f
            drawGem(Offset(plate.center.x, plate.top + frameWidth / 2f), 3.6f * u * scale, 4.6f * u * scale, colors, u, setting = true)
        }
        if (shape.pendant) {
            drawGem(Offset(plate.center.x, plate.bottom - frameWidth / 2f + 1.2f * u), 3.2f * u, 4.6f * u, colors, u, setting = true)
        }
    }
}

/** 판의 윤곽을 [inset]만큼 안으로 들인 길 — 둥근 네모이거나, 모서리를 깎은 팔각형이다. */
private fun platePath(plate: Rect, chamfered: Boolean, inset: Float, u: Float): Path {
    val rect = plate.deflate(inset)
    return Path().apply {
        if (chamfered) {
            // 팔각형을 안으로 들이면 빗변이 (2 − √2)배만큼 짧아진다.
            val cut = (ChamferCut * u - inset * 0.586f).coerceAtLeast(0f)
            moveTo(rect.left + cut, rect.top)
            lineTo(rect.right - cut, rect.top)
            lineTo(rect.right, rect.top + cut)
            lineTo(rect.right, rect.bottom - cut)
            lineTo(rect.right - cut, rect.bottom)
            lineTo(rect.left + cut, rect.bottom)
            lineTo(rect.left, rect.bottom - cut)
            lineTo(rect.left, rect.top + cut)
            close()
        } else {
            addRoundRect(RoundRect(rect, CornerRadius((CornerRadiusUnits * u - inset).coerceAtLeast(0f))))
        }
    }
}

/**
 * 판의 **왼쪽** 옆구리에서 위로 휘어 오르는 깃 하나 — 오른쪽은 그리는 쪽이 뒤집어 쓴다.
 * [index]가 클수록 아래에 붙고 조금 작다(0이 맨 위의 가장 큰 깃).
 */
private fun featherPath(plate: Rect, index: Int, u: Float): Path {
    val lower = plate.top + (18f + index * 9f) * u
    val upper = lower - 8f * u
    val tipX = (1f + index * 1.5f) * u
    val tipY = upper - (9f - index) * u
    val rootX = plate.left + 1.5f * u
    return Path().apply {
        moveTo(rootX, lower)
        quadraticTo(tipX, lower - 1f * u, tipX, tipY)
        quadraticTo((tipX + plate.left) / 2f + 1f * u, upper - 1f * u, rootX, upper)
        close()
    }
}

/** 판 윗변에 얹는 왕관의 꼭짓점 — 왼쪽 밑에서 오른쪽 밑으로, 홀수 번째가 뾰족한 끝이다. */
private fun crownSpikes(plate: Rect, spikes: Int, u: Float): List<Offset> {
    // (가운데에서 옆으로, 판 윗변에서 위로) — 왼쪽 절반만 적고 오른쪽은 거울로 잇는다.
    val leftHalf = if (spikes >= 5) {
        listOf(14f to -1.5f, 15f to 6f, 10.5f to 2f, 7.5f to 9f, 3.75f to 3f)
    } else {
        listOf(12f to -1.5f, 13f to 6f, 6.5f to 1.5f)
    }
    val apexHeight = if (spikes >= 5) 12f else 9.5f
    fun point(across: Float, up: Float) = Offset(plate.center.x + across * u, plate.top - up * u)
    return leftHalf.map { (across, up) -> point(-across, up) } +
        point(0f, apexHeight) +
        leftHalf.reversed().map { (across, up) -> point(across, up) }
}

/** 마름모 보석 — 왼쪽 면은 밝고 오른쪽 면은 깊으며, 위쪽에 반사광이 앉는다. [setting]이면 금속 받침을 두른다. */
private fun DrawScope.drawGem(center: Offset, halfWidth: Float, halfHeight: Float, colors: RankTierColors, u: Float, setting: Boolean) {
    fun rhombus(grow: Float) = Path().apply {
        moveTo(center.x, center.y - halfHeight - grow)
        lineTo(center.x + halfWidth + grow, center.y)
        lineTo(center.x, center.y + halfHeight + grow)
        lineTo(center.x - halfWidth - grow, center.y)
        close()
    }
    if (setting) drawPath(rhombus(1.3f * u), colors.metalLight)
    drawPath(rhombus(if (setting) 1.3f * u else 0f), colors.outline, style = Stroke(width = 0.8f * u, join = StrokeJoin.Round))
    drawPath(rhombus(0f), colors.gem)
    drawPath(
        Path().apply {
            moveTo(center.x, center.y - halfHeight)
            lineTo(center.x + halfWidth, center.y)
            lineTo(center.x, center.y + halfHeight)
            close()
        },
        colors.gemDeep,
    )
    drawPath(
        Path().apply {
            moveTo(center.x, center.y - halfHeight * 0.75f)
            lineTo(center.x - halfWidth * 0.6f, center.y - halfHeight * 0.05f)
            lineTo(center.x - halfWidth * 0.15f, center.y - halfHeight * 0.05f)
            close()
        },
        RankTierPalette.Highlight.copy(alpha = 0.75f),
    )
}
