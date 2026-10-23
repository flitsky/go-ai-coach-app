package com.worksoc.goaicoach.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.attendance.utcDayIndex
import com.worksoc.goaicoach.application.dailyquote.pickDailyQuote
import com.worksoc.goaicoach.application.premium.state.FeatureId
import com.worksoc.goaicoach.persistence.DailyQuoteStore
import com.worksoc.goaicoach.ui.designsystem.AppMotion
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.l10n.DailyQuote
import com.worksoc.goaicoach.ui.l10n.DailyQuoteCatalog
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.QuoteLineBreak
import com.worksoc.goaicoach.ui.l10n.dailyDrawTitleFor
import com.worksoc.goaicoach.ui.l10n.keepWordsWhole
import com.worksoc.goaicoach.ui.l10n.labAddMyQuoteTitleFor
import com.worksoc.goaicoach.ui.l10n.labFortuneTitleFor
import com.worksoc.goaicoach.ui.l10n.labQuoteBookTitleFor
import com.worksoc.goaicoach.ui.l10n.quoteLocaleTag
import com.worksoc.goaicoach.ui.l10n.studyComingSoonFor
import com.worksoc.goaicoach.ui.monetization.DailyDrawCard
import kotlin.random.Random

/**
 * 셋째 페이지 「실험실」(#280, 사용자 2026-10-22 — 처음에는 「학습 하기」였고, 학습은 대국 페이지의 타일로 갔다).
 * **섹션 셋 + 타일 하나**(#281 · #283, 사용자 2026-10-23): 명언록(제목 줄 오른쪽에 「+ 나만의 명언 추가 (준비 중)」) → 오늘의 운세(#276 — `DailyFortuneCard`)
 * → **1일 1회 뽑기**(#283) → **바둑판 사진 분석** 타일(오른쪽 아래, 사용자 2026-10-22). 「준비 중」 칸 둘(나만의 명언 추가하기 · 이름 없는 빈자리)은 없앴다.
 *
 * ⚠️ 아직 없는 실험은 눌리지 않는다 — 누를 것처럼 보이는 모양(타일 · 단추)으로 두지 않는다(학습 허브의 U-16과 같은 결). 스토어 글에는 적지 않는다(함정 51).
 */
@Composable
internal fun HomeLabPage(onBoardScanClick: () -> Unit, modifier: Modifier = Modifier) {
    val strings = LocalUiStrings.current
    val language = strings.language
    // 가로 여백은 줄마다 준다 — 명언록의 넘기기만은 화면 끝까지 닿아야 양옆 카드가 잘리지 않고 보인다(#281).
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(vertical = AppSpacing.Space16),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12),
    ) {
        QuoteBookCarousel()
        Spacer(modifier = Modifier.height(AppSpacing.Space4))
        // 오늘의 운세(#276, 사용자 2026-10-23) — 랜덤 운세(누른 순간으로 뽑기 · 광고로 하루 3번 갱신) · 별자리별 · 띠별(준비 중). 규칙은 shared `DailyFortune.kt`.
        LabSectionHeader(title = labFortuneTitleFor(language))
        DailyFortuneCard(modifier = Modifier.padding(horizontal = AppSpacing.Space16))
        Spacer(modifier = Modifier.height(AppSpacing.Space4))
        // 1일 1회 뽑기(#283, 사용자 2026-10-23) — 광고 1편(구독자는 광고 없이)으로 하루 한 번. 규칙은 shared `DailyDraw.kt`, 카드는 `ui.monetization`.
        LabSectionHeader(title = dailyDrawTitleFor(language))
        DailyDrawCard(modifier = Modifier.padding(horizontal = AppSpacing.Space16))
        Spacer(modifier = Modifier.height(AppSpacing.Space4))
        // 바둑판 사진 분석은 오른쪽 아래의 반 폭 타일 그대로다(사용자 2026-10-22) — 왼쪽은 비운다.
        Row(modifier = Modifier.padding(horizontal = AppSpacing.Space16), horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12)) {
            Spacer(modifier = Modifier.weight(1f))
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
 * 명언록(#280) — 오늘의 명언에서 시작해 **옆으로 밀면** 지난 · 다음 날의 명언이 나온다.
 *
 * #281(사용자 2026-10-23): 명언 하나가 **카드 한 장**이고 양옆 카드의 끝이 보인다(카드 메뉴 UX) — 앞뒤에 명언이 더 있다는 것을 글이 아니라 모양으로 알린다.
 * 그래서 「옆으로 밀어…」 안내와 「오늘」 표시를 없앴다(오늘의 것만 달라 보이지 않게). 출처 줄은 오른쪽 끝에 붙는다.
 *
 * **카드의 키**: 보이는 카드(지금 것과 양옆)의 **가장 긴 명언에 맞춘다** — 고정 최소 높이로 빈 칸을 남기지 않으면서, 넘길 때 양옆 카드의 키가
 * 들쭉날쭉하지 않게. 각 카드는 제 글의 높이를 적고(최소 높이를 받지 않는 안쪽에서 잰다 — 바깥에서 재면 늘린 키가 다시 적혀 줄지 않는다),
 * 키는 스프링으로 바뀐다. 처음 잰 키는 곧바로 쓴다(열자마자 자라는 움직임이 없게).
 *
 * ⚠️ 홈의 페이지 넘기기 안에 있는 넘기기다 — 카드 위에서 민 것은 카드가 먼저 가져가고, 끝(가장 지난 명언)에 닿으면 페이지가 넘어간다. 카드 밖에서 밀면 페이지가 넘어간다.
 */
@Composable
private fun QuoteBookCarousel() {
    val context = LocalContext.current
    val language = LocalUiStrings.current.language
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val entries = remember(context) { quoteBookOf(DailyQuoteStore(context), utcDayIndex(System.currentTimeMillis())) }
    if (entries.isEmpty()) return
    val todayIndex = entries.indexOfFirst { it.isToday }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = todayIndex) { entries.size }
    val heights = remember { mutableStateMapOf<Int, Int>() }
    val shown = pagerState.currentPage
    val tallestPx = (shown - 1..shown + 1).maxOf { heights[it] ?: 0 }
    val cardMinHeight = remember { Animatable(0f) }
    LaunchedEffect(tallestPx) {
        if (tallestPx == 0) return@LaunchedEffect
        val target = with(density) { tallestPx.toDp().value }
        if (cardMinHeight.value == 0f) cardMinHeight.snapTo(target) else cardMinHeight.animateTo(target, AppMotion.defaultSpatial())
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
        // 「나만의 명언 추가」(사용자 2026-10-23)는 아직 없어 제목 줄의 흐린 글자뿐이고 눌리지 않는다.
        LabSectionHeader(
            title = labQuoteBookTitleFor(language),
            trailing = "+ " + labAddMyQuoteTitleFor(language) + " (" + studyComingSoonFor(language) + ")",
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = QuoteBookSideInset),
            pageSpacing = AppSpacing.Space10,
            verticalAlignment = Alignment.Top,
        ) { index ->
            val line = entries[index].quote.lineFor(language)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppRadius.Corner24))
                    .background(colors.surfaceVariant)
                    .heightIn(min = cardMinHeight.value.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { heights[index] = it.height }
                        .padding(horizontal = AppSpacing.Space18, vertical = AppSpacing.Space16),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8),
                ) {
                    Text(
                        text = keepWordsWhole(line.text, language),
                        fontSize = AppTextSize.Text16,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface,
                        style = LocalTextStyle.current.copy(lineBreak = QuoteLineBreak, localeList = LocaleList(language.quoteLocaleTag)),
                    )
                    Text(
                        text = "— " + line.by,
                        fontSize = AppTextSize.Text13,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** 명언록 넘기기의 양옆 여백 — 이 폭에서 사이 간격(10dp)을 뺀 만큼 양옆 카드가 보인다. 스레드가 정한 값(#281). */
private val QuoteBookSideInset = 32.dp

/**
 * 실험실 섹션의 제목 줄(#281) — 왼쪽에 이름, 오른쪽 끝에 흐린 덧말([trailing], 아직 없는 기능의 예고). 덧말은 눌리지 않는다.
 * 덧말이 길면(영어 · 큰 글꼴) 두 줄까지 접히고 이름은 줄지 않는다.
 */
@Composable
private fun LabSectionHeader(title: String, trailing: String? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.Space16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, fontSize = AppTextSize.Text16, fontWeight = FontWeight.Bold, color = colors.onBackground)
        if (trailing != null) {
            Spacer(modifier = Modifier.width(AppSpacing.Space12))
            Text(
                text = trailing,
                fontSize = AppTextSize.Text12,
                color = colors.secondary,
                textAlign = TextAlign.End,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
