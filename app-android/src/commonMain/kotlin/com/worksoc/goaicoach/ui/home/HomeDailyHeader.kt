package com.worksoc.goaicoach.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.ui.designsystem.AppBorderWidth
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.foundation.LocalAppPlatform
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.QuoteLineBreak
import com.worksoc.goaicoach.ui.l10n.fortuneAdFailedFor
import com.worksoc.goaicoach.ui.l10n.fortuneDrawPromptFor
import com.worksoc.goaicoach.ui.l10n.fortuneForFunFor
import com.worksoc.goaicoach.ui.l10n.fortuneOpenShortFor
import com.worksoc.goaicoach.ui.l10n.fortuneRefreshButtonFor
import com.worksoc.goaicoach.ui.l10n.fortuneRefreshesLeftFor
import com.worksoc.goaicoach.ui.l10n.fortuneTomorrowShortFor
import com.worksoc.goaicoach.ui.l10n.keepWordsWhole
import com.worksoc.goaicoach.ui.l10n.labFortuneTitleFor
import com.worksoc.goaicoach.ui.l10n.quoteLocaleTag
import com.worksoc.goaicoach.ui.monetization.LocalPremiumUiState
import com.worksoc.goaicoach.ui.monetization.rememberAppMonetization
import com.worksoc.goaicoach.ui.monetization.rewardedAdButtonText
import kotlinx.coroutines.launch

/**
 * 대국 페이지 맨 위의 **고정 자리** — 오늘의 명언(두 줄) 바로 아래에 오늘의 운세(두 줄)(백로그 #303, 사용자 2026-11-06:
 * *"'명언'과 '오늘의 운세'를 대국 화면에서 바로 접근 가능하게 열어주는 것이며 고정된 상단 영역을 가져가게 하는 것"*). 그 아래는 전과 같다 —
 * 한 줄 안내(엔진 받기 · 새로워진 점 · 새 버전 — 있을 때만) → 대국 카드 둘 → 이어하기 → 메뉴 칸.
 *
 * 두 자리 모두 **줄 수로 고정**이라(두 줄씩) 내용이 무엇이든 높이가 같다 — 대국 카드는 늘 같은 자리에서 시작한다(#288의 원칙).
 * 운세의 상태는 실험실의 운세 카드와 하나다([DailyFortuneState]).
 */
@Composable
internal fun HomeDailyHeader(fortune: DailyFortuneState, modifier: Modifier = Modifier) {
    val quote = rememberDailyQuoteLineState()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.Space6)) {
        DailyQuotePaper(state = quote)
        DailyFortuneStrip(state = fortune)
    }
}

/**
 * 오늘의 운세 줄(#303) — 늘 두 줄. 왼쪽은 「오늘의 운세」 이름과 글, 오른쪽 끝은 단추 하나:
 * - 아직 안 뽑았으면 — 운세 안내 글(실험실과 같은 「누르는 순간의 기운으로 …」) + 「열어보기」(무료 — 누른 순간이 운세를 정한다).
 * - 뽑았으면 — 지금 운세(길면 두 줄에서 「…」) + 「📺 운 바꾸기」(구독자는 광고 없이 「운 바꾸기」) · 오늘의 갱신 3번을 다 썼으면 흐린 「내일 또」.
 * 운세 글을 누르면 전문 창이 열린다 — 운세 글은 한국어 중앙값 44자라 두 줄이면 절반쯤은 잘린다.
 */
@Composable
private fun DailyFortuneStrip(state: DailyFortuneState) {
    val platform = LocalAppPlatform.current
    val monetization = rememberAppMonetization()
    val language = LocalUiStrings.current.language
    val colors = MaterialTheme.colorScheme
    val withAd = !LocalPremiumUiState.current.isPurchased
    val scope = rememberCoroutineScope()
    var showWhole by remember { mutableStateOf(false) }
    val current = state.current
    val body = current?.textFor(language) ?: fortuneDrawPromptFor(language)
    val shape = RoundedCornerShape(AppRadius.Corner8)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (current != null) Modifier.clickable { showWhole = true } else Modifier),
        shape = shape,
        color = colors.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(start = AppSpacing.Space12, end = AppSpacing.Space8, top = AppSpacing.Space8, bottom = AppSpacing.Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.SemiBold)) { append(labFortuneTitleFor(language)) }
                    append("  ")
                    withStyle(SpanStyle(color = if (current != null) colors.onSurface else colors.onSurfaceVariant)) {
                        append(keepWordsWhole(body, language))
                    }
                },
                fontSize = AppTextSize.Text13,
                style = LocalTextStyle.current.copy(lineBreak = QuoteLineBreak, localeList = LocaleList(language.quoteLocaleTag)),
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(AppSpacing.Space8))
            Box(contentAlignment = Alignment.Center) {
                when {
                    state.busy -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.primary)
                    current == null -> StripPill(text = fortuneOpenShortFor(language), onClick = state::draw)
                    state.refreshesLeft > 0 -> StripPill(
                        // 줄이 좁아 실험실의 「광고 보고 운 바꾸기」 대신 짧은 이름에 📺만 단다(광고 표시는 `RewardedAdMark` 한 곳).
                        text = rewardedAdButtonText(fortuneRefreshButtonFor(language, withAd = false), withAd),
                        onClick = {
                            scope.launch {
                                state.refresh(withAd, monetization::showRewardedAdOnce) { reason ->
                                    platform.notice.show(fortuneAdFailedFor(language, reason))
                                }
                            }
                        },
                    )
                    else -> Text(text = fortuneTomorrowShortFor(language), fontSize = AppTextSize.Text12, color = colors.outline)
                }
            }
        }
    }

    if (showWhole && current != null) {
        AlertDialog(
            onDismissRequest = { showWhole = false },
            title = { Text(labFortuneTitleFor(language), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12)) {
                    Text(
                        text = keepWordsWhole(current.textFor(language), language),
                        fontSize = AppTextSize.Text16,
                        color = colors.onSurface,
                        style = LocalTextStyle.current.copy(lineBreak = QuoteLineBreak, localeList = LocaleList(language.quoteLocaleTag)),
                    )
                    Text(
                        text = fortuneRefreshesLeftFor(language, state.refreshesLeft) + " · " + fortuneForFunFor(language),
                        fontSize = AppTextSize.Text12,
                        color = colors.outline,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showWhole = false }) { Text(LocalUiStrings.current.close) } },
        )
    }
}

/** 운세 줄의 작은 초록 테두리 알약 — 실험실 운세 카드의 단추와 같은 모양을 두 줄 높이에 맞게 줄였다. */
@Composable
private fun StripPill(text: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = colors.surface,
        border = BorderStroke(AppBorderWidth.Emphasis, colors.primary),
    ) {
        Text(
            text = text,
            fontSize = AppTextSize.Text13,
            fontWeight = FontWeight.Bold,
            color = colors.primary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = AppSpacing.Space12, vertical = AppSpacing.Space6),
        )
    }
}
