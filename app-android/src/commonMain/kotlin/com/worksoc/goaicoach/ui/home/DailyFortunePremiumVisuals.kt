package com.worksoc.goaicoach.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.Dp
import com.worksoc.goaicoach.ui.designsystem.AppBorderWidth
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.designsystem.BoostedFortunePalette
import com.worksoc.goaicoach.ui.designsystem.boostedFortunePalette
import com.worksoc.goaicoach.ui.designsystem.paperTexture
import com.worksoc.goaicoach.ui.l10n.QuoteLineBreak
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.fortuneBoostedBadgeFor
import com.worksoc.goaicoach.ui.l10n.fortuneLuckyPointLabelFor
import com.worksoc.goaicoach.ui.l10n.keepWordsWhole
import com.worksoc.goaicoach.ui.l10n.quoteLocaleTag

/**
 * 프리미엄 운세 카드의 공통 금박 한지 표면(종이 질감 + 황금 그라데이션 + 금테 + 1회성 광택).
 * [shimmerTrigger]가 바뀔 때만 금빛 광택이 대각선으로 한 번 지나가고 완전히 멈춘다(함정 103 — iOS 무한 프레임 방지).
 */
internal fun Modifier.boostedFortuneSurface(
    palette: BoostedFortunePalette,
    shape: RoundedCornerShape,
    shimmerTrigger: Any,
    borderWidth: Dp = AppBorderWidth.Emphasis,
): Modifier =
    this
        .clip(shape)
        .paperTexture(base = palette.paperBase, grain = palette.paperGrain, seed = BoostedPaperSeed)
        .background(palette.backgroundBrush)
        .oneShotGoldShimmer(triggerKey = shimmerTrigger, shimmerColor = palette.shimmerColor)
        .border(BorderStroke(borderWidth, palette.borderBrush), shape)

private fun Modifier.oneShotGoldShimmer(triggerKey: Any, shimmerColor: Color): Modifier = composed {
    val sweep = remember { Animatable(ShimmerEnd) }
    LaunchedEffect(triggerKey) {
        sweep.snapTo(ShimmerStart)
        sweep.animateTo(
            targetValue = ShimmerEnd,
            animationSpec = tween(durationMillis = ShimmerDurationMillis, easing = FastOutSlowInEasing),
        )
    }
    drawWithContent {
        drawContent()
        val progress = sweep.value
        if (progress in ShimmerActiveStart..ShimmerActiveEnd) {
            val bandWidth = size.width * 0.42f
            val centerX = size.width * progress
            val brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, shimmerColor, Color.Transparent),
                start = Offset(centerX - bandWidth, 0f),
                end = Offset(centerX + bandWidth, size.height),
            )
            drawRect(brush = brush)
        }
    }
}

/** 프리미엄 운세 카드 상단의 작은 금빛 배지(`✦ 정성으로 바꾼 운세`). */
@Composable
internal fun BoostedFortuneBadge(language: UiLanguage, modifier: Modifier = Modifier) {
    val palette = boostedFortunePalette()
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = palette.badgeBg,
        border = BorderStroke(AppBorderWidth.Hairline, palette.badgeBorder),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.Space10, vertical = AppSpacing.Space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space4),
        ) {
            Text(
                text = "✦",
                fontSize = AppTextSize.Text12,
                fontWeight = FontWeight.Bold,
                color = palette.accentText,
            )
            Text(
                text = fortuneBoostedBadgeFor(language),
                fontSize = AppTextSize.Text12,
                fontWeight = FontWeight.SemiBold,
                color = palette.accentText,
            )
        }
    }
}

/** 프리미엄 운세 하단의 `✦ 오늘의 행운 포인트` 박스 — 실험실 카드와 대국 페이지 전문 창이 함께 쓴다. */
@Composable
internal fun BoostedFortuneTipBox(language: UiLanguage, tip: String, modifier: Modifier = Modifier) {
    val palette = boostedFortunePalette()
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(AppRadius.Corner12)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.tipBg)
            .border(BorderStroke(AppBorderWidth.Hairline, palette.tipBorder), shape)
            .padding(horizontal = AppSpacing.Space12, vertical = AppSpacing.Space10),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space3)) {
            Text(
                text = "✦ " + fortuneLuckyPointLabelFor(language),
                fontSize = AppTextSize.Text12,
                fontWeight = FontWeight.Bold,
                color = palette.accentText,
            )
            Text(
                text = keepWordsWhole(tip, language),
                fontSize = AppTextSize.Text13,
                color = colors.onSurface,
                style = LocalTextStyle.current.copy(lineBreak = QuoteLineBreak, localeList = LocaleList(language.quoteLocaleTag)),
            )
        }
    }
}

private const val BoostedPaperSeed = 187
private const val ShimmerStart = -0.45f
private const val ShimmerEnd = 1.45f
private const val ShimmerActiveStart = -0.44f
private const val ShimmerActiveEnd = 1.44f
private const val ShimmerDurationMillis = 1100
