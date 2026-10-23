package com.worksoc.goaicoach.ui.setup

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.BuildConfig
import com.worksoc.goaicoach.application.attendance.utcDayIndex
import com.worksoc.goaicoach.application.dailyquote.pickDailyQuote
import com.worksoc.goaicoach.persistence.DailyQuoteStore
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.guide.GuideBlockingOverlays
import com.worksoc.goaicoach.ui.l10n.DailyQuote
import com.worksoc.goaicoach.ui.l10n.DailyQuoteCatalog
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.QuoteLineBreak
import com.worksoc.goaicoach.ui.l10n.UiStringsDownloadGuide
import com.worksoc.goaicoach.ui.l10n.keepWordsWhole
import com.worksoc.goaicoach.ui.l10n.quoteLocaleTag
import com.worksoc.goaicoach.ui.l10n.unbreakable
import com.worksoc.goaicoach.ui.monetization.AttendanceClaimReplaySignal
import java.io.File
import kotlin.random.Random

/**
 * 홈 한 줄 자리의 「오늘의 한 줄 명언」(백로그 #273) — 차례는 `homeTopNoticeOf`의 **맨 끝**(엔진 받기 > 새로워진 점 > 새 버전 알림 > 명언)이고,
 * 그날의 출석 팝업이 끝난 뒤에 뜬다(사용자 2026-10-20). ✕는 **그날만** 닫는다(사용자 2026-10-21).
 *
 * 하루는 출석과 같은 UTC 하루다(한국에서는 오전 9시에 바뀐다 — 사용자 2026-10-21: 출석 팝업을 닫으면 새 명언이 오는 한 흐름).
 * 날짜는 이 홈이 컴포즈될 때 한 번 읽는다 — 홈을 떠났다 돌아오면 다시 읽는다.
 */
@Stable
internal class DailyQuoteLineState(private val store: DailyQuoteStore, private val today: Long) {
    var dismissedToday by mutableStateOf(store.load().isDismissedOn(today))
        private set

    /**
     * 한 줄 자리에 명언을 놓을 차례인가 — 실을 명언이 있고, 출석 팝업이 지나갔고, 오늘 닫지 않았다.
     * 「출석 팝업이 지나갔다」 = 이번 실행의 출석 체크인이 돌았고(`lastCheckedInDay`) 지금 떠 있는 팝업이 없다(`GuideBlockingOverlays` — 출석 보상 ·
     * 캐릭터 획득 축하 등 팝업 전부). 체크인은 셸의 게이트(스플래시 · 엔진 준비 화면 · 초기화 안내 · 기력 변동 창)가 다 지난 뒤에 도므로 명언은 그 뒤에 온다.
     * ⚠️ 새 전역 상태를 만들지 않는다(`ShellStateLedgerContractTest`) — 있는 두 값으로 가린다.
     */
    val wantsSlot: Boolean
        get() = DailyQuoteCatalog.quotes.isNotEmpty() &&
            AttendanceClaimReplaySignal.lastCheckedInDay != null &&
            !GuideBlockingOverlays.isShowing &&
            !dismissedToday

    /**
     * 오늘의 명언을 고르고(처음이면 섞고) 저장한다. ⚠️ 자리를 실제로 얻었을 때만 부른다 — 여기서 고른 명언은 「보인 것」으로 적히므로,
     * 다른 안내가 자리를 차지한 날에 미리 고르면 사용자가 못 본 명언이 소진된다.
     */
    fun pickToday(): DailyQuote? {
        val pick = pickDailyQuote(DailyQuoteCatalog.quotes.map { it.id }, store.load(), today, Random.Default)
        if (pick.changed) store.save(pick.progress)
        return pick.quoteId?.let(DailyQuoteCatalog::quote)
    }

    fun dismissToday() {
        store.save(store.load().copy(dismissedDay = today))
        dismissedToday = true
    }
}

@Composable
internal fun rememberDailyQuoteLineState(): DailyQuoteLineState {
    val context = LocalContext.current
    return remember(context) { DailyQuoteLineState(DailyQuoteStore(context), utcDayIndex(System.currentTimeMillis())) }
}



/**
 * 명언 한 줄 — 「명언 — 말한 이」를 두 줄 안에(사용자 2026-10-21), 오른쪽에 ✕. 자르지 않는다: 글꼴을 크게(1.3배) 하면 세 줄이 될 수 있고,
 * 그 높이는 홈의 가중치 여백이 받아 메뉴 카드는 움직이지 않는다. 안내 줄(새 버전 · 새로워진 점)과 달리 누를 곳이 없어 테두리 없이 조용하게 그리고,
 * 출석 팝업이 닫힌 뒤 나타난 것이 보이도록 천천히 떠오른다.
 */
@Composable
internal fun DailyQuoteLine(state: DailyQuoteLineState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (remember(context) { debugQuoteSweepArmed(context.filesDir) }) {
        DebugQuoteSweep(modifier)
        return
    }
    val quote = remember(state) { state.pickToday() } ?: return
    val appear = remember { MutableTransitionState(false) }.apply { targetState = true }
    AnimatedVisibility(
        visibleState = appear,
        modifier = modifier,
        enter = fadeIn(tween(durationMillis = 700)) + expandVertically(tween(durationMillis = 400)),
    ) {
        QuoteRow(quote = quote, onDismiss = state::dismissToday)
    }
}

@Composable
private fun QuoteRow(quote: DailyQuote, onDismiss: () -> Unit, onTextLayout: (TextLayoutResult) -> Unit = {}) {
    val language = LocalUiStrings.current.language
    val line = quote.lineFor(language)
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.Corner8)),
        color = colors.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(start = AppSpacing.Space12, top = AppSpacing.Space6, bottom = AppSpacing.Space6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.onSurface, fontWeight = FontWeight.Medium)) { append(keepWordsWhole(line.text, language)) }
                    withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append("  —\u00A0" + unbreakable(line.by)) }
                },
                fontSize = AppTextSize.Text12,
                // 일본어는 구절 단위로 줄을 바꾼다(안드로이드 13+). ⚠️ `LineBreak.Heading`(줄 길이 고르기)은 쓰지 않는다 — 두 줄에 들던 영어가 세 줄이 됐다.
                // 글자의 언어를 화면 언어로 — 일본어 구절 줄바꿈이 일본어 글자에만 듣고, 중국어 한자가 한국·일본 글자꼴로 그려지지 않는다.
                style = LocalTextStyle.current.copy(lineBreak = QuoteLineBreak, localeList = LocaleList(language.quoteLocaleTag)),
                onTextLayout = onTextLayout,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = UiStringsDownloadGuide.close(language),
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * 디버그 빌드: `files/debug-quote-sweep`이 있으면(한 번 쓰고 지운다) 지금 언어의 명언을 **전부** 이 자리 모양으로 깔고 줄 수를 로그로 낸다
 * (`adb logcat -s DailyQuoteSweep`). 명언을 더하거나 고칠 때 두 줄을 넘는 것을 실제 배치로 찾는 길이다 — 단위 테스트의 폭 어림은 낱말 길이에 따른
 * 줄바꿈 낭비를 못 본다(2026-10-21: 어림 80칸인데 세 줄이 된 영어가 있었다). 「새로워진 점」 미리보기 파일(`HomeTopNotice.kt`)과 같은 방식.
 */
private fun debugQuoteSweepArmed(filesDir: File): Boolean = BuildConfig.DEBUG && File(filesDir, DebugQuoteSweepFile).let { it.isFile && it.delete() }

@Composable
private fun DebugQuoteSweep(modifier: Modifier) {
    val language = LocalUiStrings.current.language
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.Space4)) {
        DailyQuoteCatalog.quotes.forEach { quote ->
            QuoteRow(quote = quote, onDismiss = {}, onTextLayout = { layout ->
                Log.i(DebugQuoteSweepTag, "$language ${quote.id} lines=${layout.lineCount}")
            })
        }
    }
}

private const val DebugQuoteSweepFile = "debug-quote-sweep"
private const val DebugQuoteSweepTag = "DailyQuoteSweep"
