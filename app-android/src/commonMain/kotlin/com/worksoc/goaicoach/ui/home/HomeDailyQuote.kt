package com.worksoc.goaicoach.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.worksoc.goaicoach.application.attendance.utcDayIndex
import com.worksoc.goaicoach.application.dailyquote.DailyQuoteStorePort
import com.worksoc.goaicoach.application.dailyquote.pickDailyQuote
import com.worksoc.goaicoach.application.time.currentEpochMillis
import com.worksoc.goaicoach.platform.debugLog
import com.worksoc.goaicoach.ui.designsystem.AppBorderWidth
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.designsystem.PaperPalette
import com.worksoc.goaicoach.ui.designsystem.paperTexture
import com.worksoc.goaicoach.ui.foundation.LocalAppPlatform
import com.worksoc.goaicoach.ui.l10n.DailyQuote
import com.worksoc.goaicoach.ui.l10n.DailyQuoteCatalog
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.QuoteLine
import com.worksoc.goaicoach.ui.l10n.QuoteLineBreak
import com.worksoc.goaicoach.ui.l10n.keepWordsWhole
import com.worksoc.goaicoach.ui.l10n.quoteLocaleTag
import com.worksoc.goaicoach.ui.l10n.unbreakable
import kotlin.random.Random

/**
 * 대국 페이지 맨 위의 「오늘의 명언」(백로그 #273 → #303). **늘 그 자리에 있다**(사용자 2026-11-06: *"x표시로 없애지 못하게 하기"*) —
 * 2026-10-21의 「✕는 그날만 닫는다」와 「출석 팝업이 끝난 뒤에 뜬다」를 뒤집었다. 그 아래가 오늘의 운세 줄이다(`HomeDailyHeader`).
 *
 * 하루는 출석과 같은 UTC 하루다(한국에서는 오전 9시에 바뀐다 — 사용자 2026-10-21). 날짜는 이 자리가 컴포즈될 때 한 번 읽는다 — 홈을 떠났다 돌아오면 다시 읽는다.
 * ⚠️ 저장의 「✕로 닫은 날」(`DailyQuoteProgress.dismissedDay`)은 더 읽지도 쓰지도 않지만 **필드는 남긴다** — 저장 스키마는 필드 추가만(함정 69).
 */
@Stable
internal class DailyQuoteLineState(private val store: DailyQuoteStorePort, private val today: Long) {
    /** 오늘의 명언을 고르고(처음이면 섞고) 저장한다 — 고른 명언은 「보인 것」으로 적힌다. 실을 명언이 없으면 `null`. */
    fun pickToday(): DailyQuote? {
        if (DailyQuoteCatalog.quotes.isEmpty()) return null
        val pick = pickDailyQuote(DailyQuoteCatalog.quotes.map { it.id }, store.load(), today, Random.Default)
        if (pick.changed) store.save(pick.progress)
        return pick.quoteId?.let(DailyQuoteCatalog::quote)
    }
}

@Composable
internal fun rememberDailyQuoteLineState(): DailyQuoteLineState {
    val platform = LocalAppPlatform.current
    return remember(platform) { DailyQuoteLineState(platform.stores.dailyQuote, utcDayIndex(currentEpochMillis())) }
}

/**
 * 명언 종이(#303, 사용자 2026-11-06) — **늘 두 줄 높이**이고, 말한 이는 늘 그 두 줄의 **오른쪽 아래**다. 명언이 길어 둘째 줄이 말한 이에 닿으면
 * 명언을 「…」로 줄이고 말한 이를 보인다([fitQuoteBeforeSource]). 큰 글꼴에서도 두 줄이다 — 높이는 줄 수로 정해져 대국 카드의 자리가 흔들리지 않는다.
 * 바탕은 명언록에 맞는 **종이 질감**이고 지금 화면에서 튀지 않게 옅다([PaperPalette]). 누를 곳이 없다 — 지난 명언은 실험실의 명언록에서 넘겨 본다.
 */
@Composable
internal fun DailyQuotePaper(state: DailyQuoteLineState, modifier: Modifier = Modifier) {
    val platform = LocalAppPlatform.current
    if (remember(platform) { platform.debugArmFiles.consume(DebugQuoteSweepFile) }) {
        DebugQuoteSweep(modifier)
        return
    }
    val quote = remember(state) { state.pickToday() } ?: return
    QuotePaper(line = quote.lineFor(LocalUiStrings.current.language), modifier = modifier)
}

@Composable
private fun QuotePaper(line: QuoteLine, modifier: Modifier = Modifier, onFit: (cut: Boolean) -> Unit = {}) {
    val language = LocalUiStrings.current.language
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(AppRadius.Corner8)
    val textStyle = LocalTextStyle.current.copy(
        fontSize = AppTextSize.Text13,
        // 일본어는 구절 단위로 줄을 바꾼다(안드로이드 13+). 글자의 언어를 화면 언어로 — 중국어 한자가 한국 · 일본 글자꼴로 그려지지 않는다.
        lineBreak = QuoteLineBreak,
        localeList = LocaleList(language.quoteLocaleTag),
    )
    val quoteStyle = textStyle.copy(color = colors.onSurface, fontWeight = FontWeight.Medium)
    val sourceStyle = textStyle.copy(color = colors.onSurfaceVariant)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .paperTexture(base = PaperPalette.Base, grain = PaperPalette.Grain)
            .border(AppBorderWidth.Hairline, PaperPalette.Edge, shape)
            .padding(horizontal = AppSpacing.Space12, vertical = AppSpacing.Space8),
    ) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val width = constraints.maxWidth
        val source = "— " + unbreakable(line.by)
        // 말한 이가 아주 길면(긴 서양 이름 · 큰 글꼴) 폭의 [SourceMaxWidthShare]를 넘지 않게 그쪽도 「…」로 줄인다.
        val sourceMaxWidth = (width * SourceMaxWidthShare).toInt()
        val sourceWidth = remember(source, sourceStyle, width) {
            measurer.measure(source, sourceStyle, maxLines = 1, constraints = Constraints(maxWidth = sourceMaxWidth)).size.width
        }
        val gap = with(density) { AppSpacing.Space8.roundToPx() }
        val text = keepWordsWhole(line.text, language)
        val shown = remember(text, quoteStyle, width, sourceWidth) {
            fitQuoteBeforeSource(measurer, text, quoteStyle, width, secondLineLimit = width - sourceWidth - gap)
        }
        onFit(shown != text)
        Text(text = shown, style = quoteStyle, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
        Text(
            text = source,
            style = sourceStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .widthIn(max = with(density) { sourceMaxWidth.toDp() }),
        )
    }
}

/**
 * 명언을 두 줄에 넣되 **둘째 줄은 [secondLineLimit] 픽셀까지만** 쓰게 한다 — 그 뒤(오른쪽 아래)가 말한 이의 자리다(#303).
 * 이미 들어가면 그대로, 넘치면 앞에서부터 가장 길게 남기고 「…」를 붙인다(이분 탐색 — 측정 열 번 안팎, 명언 · 폭이 바뀔 때만).
 */
internal fun fitQuoteBeforeSource(measurer: TextMeasurer, text: String, style: TextStyle, width: Int, secondLineLimit: Int): String {
    fun fits(candidate: String): Boolean {
        val layout = measurer.measure(candidate, style, constraints = Constraints(maxWidth = width.coerceAtLeast(1)))
        return when (layout.lineCount) {
            1 -> true
            2 -> layout.getLineRight(1) <= secondLineLimit
            else -> false
        }
    }
    if (fits(text)) return text
    var low = 0
    var high = text.length
    while (low < high) {
        val middle = (low + high + 1) / 2
        if (fits(text.take(middle).trimEnd() + Ellipsis)) low = middle else high = middle - 1
    }
    return text.take(low).trimEnd() + Ellipsis
}

private const val Ellipsis = "…"

/**
 * 말한 이가 차지할 수 있는 폭의 몫 — 그보다 길면 말한 이도 줄인다. 스레드가 정한 값: 처음 0.5로 두었더니 360dp 영어에서
 * 「— Ten Golden Rules of Go」가 짧은 명언 옆에서도 잘렸다(2026-11-06 에뮬레이터).
 */
private const val SourceMaxWidthShare = 0.7f

/**
 * 디버그 빌드: 무장 파일 `debug-quote-sweep`이 있으면(한 번 쓰고 지운다 — 안드로이드 `files/`) 지금 언어의 명언을 **전부** 이 종이 모양으로 깔고
 * 「…」로 줄었는지를 로그로 낸다(`adb logcat -s DailyQuoteSweep`). 맨 앞에는 일부러 긴 명언(첫 명언을 세 번 이은 것)을 하나 깔아 「…」와 말한 이의 자리를
 * 눈으로 본다 — 2026-11-06에는 실제 명언이 네 언어 모두 줄지 않고 들어가, 이 길이 아니면 「…」를 볼 데가 없었다. 명언을 더하거나 고칠 때 얼마나 잘리는지 실제 배치로 보는 길이다 —
 * 단위 테스트의 폭 어림은 낱말 길이에 따른 줄바꿈 낭비를 못 본다. 「새로워진 점」 미리보기 파일(`HomeTopNotice.kt`)과 같은 방식.
 */
@Composable
private fun DebugQuoteSweep(modifier: Modifier) {
    val language = LocalUiStrings.current.language
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.Space4)) {
        DailyQuoteCatalog.quotes.firstOrNull()?.lineFor(language)?.let { first ->
            val long = QuoteLine(text = List(3) { first.text }.joinToString(" "), by = first.by)
            QuotePaper(line = long, onFit = { cut -> debugLog(DebugQuoteSweepTag, "$language synthetic-long cut=$cut") })
        }
        DailyQuoteCatalog.quotes.forEach { quote ->
            Box {
                QuotePaper(line = quote.lineFor(language), onFit = { cut -> debugLog(DebugQuoteSweepTag, "$language ${quote.id} cut=$cut") })
            }
        }
    }
}

private const val DebugQuoteSweepFile = "debug-quote-sweep"
private const val DebugQuoteSweepTag = "DailyQuoteSweep"
