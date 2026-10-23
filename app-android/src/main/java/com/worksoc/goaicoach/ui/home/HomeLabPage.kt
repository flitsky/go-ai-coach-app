package com.worksoc.goaicoach.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.attendance.utcDayIndex
import com.worksoc.goaicoach.application.dailyquote.pickDailyQuote
import com.worksoc.goaicoach.application.premium.state.FeatureId
import com.worksoc.goaicoach.persistence.DailyQuoteStore
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.l10n.DailyQuote
import com.worksoc.goaicoach.ui.l10n.DailyQuoteCatalog
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.QuoteLineBreak
import com.worksoc.goaicoach.ui.l10n.keepWordsWhole
import com.worksoc.goaicoach.ui.l10n.labAddMyQuoteTitleFor
import com.worksoc.goaicoach.ui.l10n.labFortuneTitleFor
import com.worksoc.goaicoach.ui.l10n.labQuoteBookHintFor
import com.worksoc.goaicoach.ui.l10n.labQuoteBookTitleFor
import com.worksoc.goaicoach.ui.l10n.labQuoteTodayFor
import com.worksoc.goaicoach.ui.l10n.quoteLocaleTag
import com.worksoc.goaicoach.ui.l10n.studyComingSoonFor
import kotlin.random.Random

/**
 * 셋째 페이지 「실험실」(#280, 사용자 2026-10-22 — 처음에는 「학습 하기」였고, 학습은 대국 페이지의 타일로 갔다).
 * 맨 위 명언록 → 반 폭 칸 넷: 나만의 명언 추가하기(준비 중) · 오늘의 운세(준비 중, #276) / 준비 중 · **바둑판 사진 분석**(오른쪽 아래, 사용자).
 *
 * ⚠️ 아직 없는 실험은 회색 + 「준비 중」 배지이고 눌리지 않는다(학습 허브의 U-16과 같은 결). 스토어 글에는 적지 않는다(함정 51).
 */
@Composable
internal fun HomeLabPage(onBoardScanClick: () -> Unit, modifier: Modifier = Modifier) {
    val strings = LocalUiStrings.current
    val language = strings.language
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.Space16, vertical = AppSpacing.Space16),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12),
    ) {
        QuoteBookCard()
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12)) {
            LabPlaceholderTile(title = labAddMyQuoteTitleFor(language), icon = Icons.Filled.Edit, modifier = Modifier.weight(1f))
            LabPlaceholderTile(title = labFortuneTitleFor(language), icon = Icons.Filled.Star, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12)) {
            LabPlaceholderTile(title = null, icon = Icons.Filled.Add, modifier = Modifier.weight(1f))
            MenuTile(
                tile = HomeMenuTile(strings.featureShortName(FeatureId.BoardScan), onBoardScanClick) { BoardScanPreviewIcon() },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 명언록의 한 장 — 명언과, 오늘의 것인지. */
private class QuoteBookEntry(val quote: DailyQuote, val isToday: Boolean)

/**
 * 명언록에 실을 장들 — **오늘의 명언이 가운데**이고, 앞은 이번 바퀴에 이미 본 명언, 뒤는 섞은 차례에서 아직 안 본 명언이다(`pickDailyQuote`의 차례 그대로).
 * 오늘의 명언을 아직 고르지 않았으면 여기서 고른다 — 명언록에서 보면 「본 것」이다.
 *
 * ⚠️ **날짜를 붙이지 않는다**(사용자 2026-10-22) — 진행도는 본 명언만 적고 본 날은 적지 않아, 앱을 열지 않은 날이 있으면 지난 명언의 날짜를 맞출 수 없다.
 *   차례(지난 것 · 오늘 · 다음 것)는 언제나 맞다. 오늘의 것에만 「오늘」을 단다.
 */
private fun quoteBookOf(store: DailyQuoteStore, today: Long): List<QuoteBookEntry> {
    val pick = pickDailyQuote(DailyQuoteCatalog.quotes.map { it.id }, store.load(), today, Random.Default)
    if (pick.changed) store.save(pick.progress)
    val todayId = pick.quoteId ?: return emptyList()
    val order = pick.progress.order
    val shown = pick.progress.shown
    val at = order.indexOf(todayId)
    if (at < 0) return emptyList()
    val past = order.take(at).filter { it in shown }
    val future = order.drop(at + 1).filter { it !in shown }
    return buildList {
        past.forEach { id -> DailyQuoteCatalog.quote(id)?.let { add(QuoteBookEntry(it, isToday = false)) } }
        DailyQuoteCatalog.quote(todayId)?.let { add(QuoteBookEntry(it, isToday = true)) }
        future.forEach { id -> DailyQuoteCatalog.quote(id)?.let { add(QuoteBookEntry(it, isToday = false)) } }
    }
}

/**
 * 명언록 카드(#280) — 오늘의 명언에서 시작해 **옆으로 밀면** 지난 · 다음 날의 명언이 나온다.
 * ⚠️ 홈의 페이지 넘기기 안에 있는 넘기기다 — 카드 위에서 민 것은 카드가 먼저 가져가고, 카드가 끝(가장 지난 명언)에 닿으면 페이지가 넘어간다. 카드 밖에서 밀면 페이지가 넘어간다.
 */
@Composable
private fun QuoteBookCard() {
    val context = LocalContext.current
    val language = LocalUiStrings.current.language
    val colors = MaterialTheme.colorScheme
    val entries = remember(context) { quoteBookOf(DailyQuoteStore(context), utcDayIndex(System.currentTimeMillis())) }
    if (entries.isEmpty()) return
    val todayIndex = entries.indexOfFirst { it.isToday }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = todayIndex) { entries.size }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.Corner14),
        color = colors.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(vertical = AppSpacing.Space16)) {
            Text(
                text = labQuoteBookTitleFor(language),
                fontSize = AppTextSize.Text16,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppSpacing.Space16),
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.Space8),
                contentPadding = PaddingValues(horizontal = AppSpacing.Space16),
                pageSpacing = AppSpacing.Space16,
            ) { index ->
                val entry = entries[index]
                val line = entry.quote.lineFor(language)
                // 장마다 글 길이가 달라 키가 다르다 — 최소 키를 두어 밀 때 카드가 덜 출렁이게 한다.
                Column(modifier = Modifier.fillMaxWidth().heightIn(min = QuoteBookPageMinHeight)) {
                    if (entry.isToday) {
                        Text(text = labQuoteTodayFor(language), fontSize = AppTextSize.Text12, fontWeight = FontWeight.Bold, color = colors.primary)
                        Spacer(modifier = Modifier.size(AppSpacing.Space8))
                    }
                    Text(
                        text = keepWordsWhole(line.text, language),
                        fontSize = AppTextSize.Text16,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface,
                        style = LocalTextStyle.current.copy(lineBreak = QuoteLineBreak, localeList = LocaleList(language.quoteLocaleTag)),
                    )
                    Spacer(modifier = Modifier.size(AppSpacing.Space6))
                    Text(text = "— " + line.by, fontSize = AppTextSize.Text13, color = colors.onSurfaceVariant)
                }
            }
            Text(
                text = labQuoteBookHintFor(language),
                fontSize = AppTextSize.Text12,
                color = colors.secondary,
                modifier = Modifier.padding(start = AppSpacing.Space16, end = AppSpacing.Space16, top = AppSpacing.Space8),
            )
        }
    }
}

private val QuoteBookPageMinHeight = 96.dp

/**
 * 아직 없는 실험의 칸 — [MenuTile]과 같은 크기 · 같은 자리의 그림이지만 흐리고 눌리지 않는다. [title]이 없으면 이름 없는 빈자리다(사용자의 「(예정)」).
 * ⚠️ `clickable`을 달고 끄지 않는다 — 아예 달지 않는다(학습 허브의 줄과 같은 이유: 리플 잔상 · 접근성이 「누를 수 있는 것」으로 읽는다).
 */
@Composable
private fun LabPlaceholderTile(title: String?, icon: ImageVector, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val language = LocalUiStrings.current.language
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadius.Corner14))
            .background(colors.surfaceVariant.copy(alpha = 0.5f))
            .heightIn(min = LabTileMinHeight)
            .padding(start = AppSpacing.Space8, top = AppSpacing.Space10, end = AppSpacing.Space12, bottom = AppSpacing.Space10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(LabTileIconBoxSize), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = colors.onSurfaceVariant.copy(alpha = 0.45f), modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.width(AppSpacing.Space10))
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space4)) {
            if (title != null) {
                Text(text = title, fontSize = AppTextSize.Text14, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant.copy(alpha = 0.55f), maxLines = 2)
            }
            Text(
                text = studyComingSoonFor(language),
                fontSize = AppTextSize.Text12,
                fontWeight = FontWeight.Bold,
                color = colors.secondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(AppRadius.Corner6))
                    .background(colors.surface)
                    .padding(horizontal = AppSpacing.Space8, vertical = AppSpacing.Space2),
            )
        }
    }
}

private val LabTileMinHeight = 72.dp
private val LabTileIconBoxSize = 52.dp
