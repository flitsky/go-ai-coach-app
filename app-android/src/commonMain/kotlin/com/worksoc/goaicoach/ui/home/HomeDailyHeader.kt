package com.worksoc.goaicoach.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
import com.worksoc.goaicoach.ui.l10n.fortuneRefreshInviteTwoLinesFor
import com.worksoc.goaicoach.ui.l10n.fortuneRefreshesDoneFor
import com.worksoc.goaicoach.ui.l10n.fortuneRefreshesLeftFor
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
 * - 뽑았으면 — 지금 운세(길면 두 줄에서 「…」) + **작은 새로고침 아이콘**(사용자 2026-11-06: *"운바꾸기 버튼이 운세를 너무 많이 가리네 … 갱신 이모지만 우측 끝에 간결히"*).
 *   누르면 창에서 광고를 볼지 마지막으로 정한다 — 「한 수로 판이 바뀌듯 …」 아래 「📺 광고 보고 운 바꾸기」(구독자는 광고 없이 「운 바꾸기」) · 오늘 남은 기회.
 *   오늘의 갱신 3번을 다 썼으면 아이콘이 흐려지고, 누르면 같은 창이 「오늘은 운을 충분히 바꿨어요 …」를 말한다.
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
    var showRefresh by remember { mutableStateOf(false) }
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
                    else -> RefreshIconButton(
                        dimmed = state.refreshesLeft <= 0,
                        contentDescription = fortuneRefreshButtonFor(language, withAd = false),
                        onClick = { showRefresh = true },
                    )
                }
            }
        }
    }

    if (showRefresh) {
        FortuneRefreshDialog(
            refreshesLeft = state.refreshesLeft,
            withAd = withAd,
            onRefresh = {
                showRefresh = false
                scope.launch {
                    state.refresh(withAd, monetization::showRewardedAdOnce) { reason ->
                        platform.notice.show(fortuneAdFailedFor(language, reason))
                    }
                }
            },
            onClose = { showRefresh = false },
        )
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

/**
 * 운 바꾸기 창(#303) — 광고를 볼지 여기서 마지막으로 정한다: 두 줄 안내(「한 수로 판이 바뀌듯,」 ↵ 「오늘의 운도 …」) → 「📺 광고 보고 운 바꾸기」(구독자는 광고 없이)
 * → 오늘 남은 기회 → 닫기. 오늘의 갱신을 다 썼으면 「오늘은 운을 충분히 바꿨어요 …」와 닫기뿐이다.
 *
 * 기본 `AlertDialog`의 모서리 · 바탕색 · 그림자를 그대로 쓰되 안쪽 간격은 여기서 정한다 — 기본 창은 글과 단추 줄 사이에 큰 여백을 붙여
 * 창이 세로로 늘어져 보였다(사용자 2026-11-06: *"「오늘 남은 기회 3/3」 텍스트 위 아래의 여백을 현재의 절반으로"* — 위 약 24 → 12dp, 아래 약 40 → 20dp).
 */
@Composable
private fun FortuneRefreshDialog(refreshesLeft: Int, withAd: Boolean, onRefresh: () -> Unit, onClose: () -> Unit) {
    val language = LocalUiStrings.current.language
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp),
        ) {
            Column(
                modifier = Modifier.padding(start = AppSpacing.Space24, end = AppSpacing.Space24, top = AppSpacing.Space24, bottom = AppSpacing.Space8),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = keepWordsWhole(
                        if (refreshesLeft > 0) fortuneRefreshInviteTwoLinesFor(language) else fortuneRefreshesDoneFor(language),
                        language,
                    ),
                    fontSize = AppTextSize.Text15,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    style = LocalTextStyle.current.copy(lineBreak = QuoteLineBreak, localeList = LocaleList(language.quoteLocaleTag)),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (refreshesLeft > 0) {
                    Spacer(modifier = Modifier.height(AppSpacing.Space16))
                    StripPill(text = rewardedAdButtonText(fortuneRefreshButtonFor(language, withAd), withAd), large = true, onClick = onRefresh)
                    Spacer(modifier = Modifier.height(AppSpacing.Space4))
                    Text(text = fortuneRefreshesLeftFor(language, refreshesLeft), fontSize = AppTextSize.Text12, color = colors.secondary)
                } else {
                    Spacer(modifier = Modifier.height(AppSpacing.Space8))
                }
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onClose) { Text(LocalUiStrings.current.close) }
                }
            }
        }
    }
}

/**
 * 운세 줄 오른쪽 끝의 새로고침 아이콘(#303) — 운세 글을 가리지 않게 작게. [dimmed]면 오늘의 갱신을 다 쓴 것이라 흐리게 그린다(눌리기는 한다 — 창이 이유를 말한다).
 * 이모지(🔄) 대신 앱의 초록 아이콘을 쓴다 — 안드로이드의 그 이모지는 파란 네모라 이 줄에서 튄다(스레드가 정한 것).
 */
@Composable
private fun RefreshIconButton(dimmed: Boolean, contentDescription: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint = if (dimmed) colors.outline else colors.primary
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.surface,
        border = BorderStroke(AppBorderWidth.Emphasis, tint.copy(alpha = if (dimmed) 0.5f else 1f)),
        modifier = Modifier.size(RefreshIconButtonSize),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

private val RefreshIconButtonSize = 36.dp

/** 운세 줄의 작은 초록 테두리 알약 — 실험실 운세 카드의 단추와 같은 모양을 두 줄 높이에 맞게 줄였다. [large]는 창 안의 단추(실험실 카드와 같은 크기). */
@Composable
private fun StripPill(text: String, onClick: () -> Unit, large: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = colors.surface,
        border = BorderStroke(AppBorderWidth.Emphasis, colors.primary),
    ) {
        Text(
            text = text,
            fontSize = if (large) AppTextSize.Text15 else AppTextSize.Text13,
            fontWeight = FontWeight.Bold,
            color = colors.primary,
            maxLines = 1,
            modifier = if (large) {
                Modifier.padding(horizontal = AppSpacing.Space20, vertical = AppSpacing.Space10)
            } else {
                Modifier.padding(horizontal = AppSpacing.Space12, vertical = AppSpacing.Space6)
            },
        )
    }
}
