package com.worksoc.goaicoach.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.attendance.MillisPerUtcDay
import com.worksoc.goaicoach.application.attendance.utcDayIndex
import com.worksoc.goaicoach.application.dailyfortune.drawDailyFortune
import com.worksoc.goaicoach.application.dailyfortune.refreshDailyFortune
import com.worksoc.goaicoach.application.premium.port.AdRewardOutcome
import com.worksoc.goaicoach.application.time.currentEpochMillis
import com.worksoc.goaicoach.ui.designsystem.AppBorderWidth
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.foundation.LocalAppPlatform
import com.worksoc.goaicoach.ui.l10n.DailyFortuneCatalog
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.QuoteLineBreak
import com.worksoc.goaicoach.ui.l10n.fortuneAdFailedFor
import com.worksoc.goaicoach.ui.l10n.fortuneDrawButtonFor
import com.worksoc.goaicoach.ui.l10n.fortuneDrawPromptFor
import com.worksoc.goaicoach.ui.l10n.fortuneForFunFor
import com.worksoc.goaicoach.ui.l10n.fortuneRandomTitleFor
import com.worksoc.goaicoach.ui.l10n.fortuneRefreshButtonFor
import com.worksoc.goaicoach.ui.l10n.fortuneRefreshInviteFor
import com.worksoc.goaicoach.ui.l10n.fortuneRefreshesDoneFor
import com.worksoc.goaicoach.ui.l10n.fortuneRefreshesLeftFor
import com.worksoc.goaicoach.ui.l10n.fortuneSetStarSignFor
import com.worksoc.goaicoach.ui.l10n.fortuneSetZodiacFor
import com.worksoc.goaicoach.ui.l10n.fortuneStarSignTitleFor
import com.worksoc.goaicoach.ui.l10n.fortuneZodiacTitleFor
import com.worksoc.goaicoach.ui.l10n.keepWordsWhole
import com.worksoc.goaicoach.ui.l10n.quoteLocaleTag
import com.worksoc.goaicoach.ui.l10n.studyComingSoonFor
import com.worksoc.goaicoach.ui.monetization.LocalPremiumUiState
import com.worksoc.goaicoach.ui.monetization.rememberAppMonetization
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 실험실 「오늘의 운세」의 카드(백로그 #276, 사용자 2026-10-23) — 규칙은 shared `application/dailyfortune/DailyFortune.kt` 한 곳이고, 여기는 그것을 모양으로 옮긴다.
 *
 * 사용자가 정한 구성(*"띠, 별자리 운세는 선택의 허들이 있으니, 우선 랜덤 운세 먼저"*) — 한 카드에 세 칸:
 * 1. **랜덤 운세** — 설정 없이 「▶ 오늘의 운세 뽑기」를 누른 순간의 타임스탬프로 368개 중 하나. 뽑은 운세는 그날 내내 남는다.
 *    그 아래 갱신 줄: 내러티브 한 줄(「한 수로 판이 바뀌듯 …」) → 「▶ 광고 보고 운 바꾸기」(구독자는 「▶ 운 바꾸기」 — 광고 없이) → 「오늘 남은 기회 n/3」.
 *    광고는 `showRewardedAdOnce`(동의 · 배경음악, 함정 99). 끝까지 보지 않았거나 광고가 없으면 운은 그대로다. 새 운세는 옅어졌다 나타난다(스레드가 정한 것).
 * 2. **별자리별 운세** · 3. **띠별 운세** — 근거 데이터가 없어 「(준비 중)」(사용자: 데이터 확보가 미비하면). 제목 줄 오른쪽의 「별자리 설정하기 (준비 중)」은 흐린 글자이고 눌리지 않는다
 *    (실험실의 「아직 없는 것은 누를 것처럼 두지 않는다」 — 명언록의 「+ 나만의 명언 추가 (준비 중)」과 같은 모양). 길은 아이디어 I-7.
 * 맨 아래 「운세는 재미로 보세요.」 점수 · 등급은 어디에도 없다.
 *
 * ⚠️ 날이 바뀌는 때(UTC 자정 — 한국 오전 9시)에 화면을 열어 두고 있어도 「뽑기」로 돌아간다 — 다음 자정까지 기다렸다 오늘을 다시 읽는다.
 * ⚠️ 시계는 `System.currentTimeMillis`를 직접 읽는다 — 출석과 같다(함정 12).
 */
@Composable
internal fun DailyFortuneCard(modifier: Modifier = Modifier) {
    val platform = LocalAppPlatform.current
    val monetization = rememberAppMonetization()
    val language = LocalUiStrings.current.language
    val colors = MaterialTheme.colorScheme
    val withAd = !LocalPremiumUiState.current.isPurchased
    val store = remember(platform) { platform.stores.dailyFortune }
    val poolSize = DailyFortuneCatalog.fortunes.size
    var progress by remember(store) { mutableStateOf(store.load()) }
    var today by remember { mutableLongStateOf(utcDayIndex(currentEpochMillis())) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(today) {
        val untilNextDay = (today + 1) * MillisPerUtcDay - currentEpochMillis()
        delay(untilNextDay.coerceAtLeast(1_000))
        today = utcDayIndex(currentEpochMillis())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.Corner24))
            .background(colors.surfaceVariant)
            .padding(AppSpacing.Space16),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12),
    ) {
        FortuneBlockTitle(fortuneRandomTitleFor(language))
        val shownIndex = progress.todaysIndex(today, poolSize)
        if (shownIndex == null) {
            Text(
                text = keepWordsWhole(fortuneDrawPromptFor(language), language),
                fontSize = AppTextSize.Text14,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FortunePill(
                    text = "▶ " + fortuneDrawButtonFor(language),
                    onClick = {
                        // 누른 순간 — 이 타임스탬프가 오늘의 운세를 정한다(사용자 2026-10-23).
                        val now = currentEpochMillis()
                        val day = utcDayIndex(now)
                        drawDailyFortune(store.load(), day, now, poolSize)?.let { drawn ->
                            store.save(drawn)
                            progress = drawn
                        }
                        today = day
                    },
                )
            }
        } else {
            Crossfade(targetState = shownIndex, label = "dailyFortune") { index ->
                Text(
                    text = keepWordsWhole(DailyFortuneCatalog.fortunes[index].textFor(language), language),
                    fontSize = AppTextSize.Text16,
                    color = colors.onSurface,
                    style = LocalTextStyle.current.copy(lineBreak = QuoteLineBreak, localeList = LocaleList(language.quoteLocaleTag)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val left = progress.refreshesLeftOn(today)
            if (left > 0) {
                Text(
                    text = keepWordsWhole(fortuneRefreshInviteFor(language), language),
                    fontSize = AppTextSize.Text13,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = RefreshButtonMinHeight), contentAlignment = Alignment.Center) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp, color = colors.primary)
                    } else {
                        FortunePill(
                            text = "▶ " + fortuneRefreshButtonFor(language, withAd),
                            onClick = {
                                busy = true
                                scope.launch {
                                    val outcome = if (withAd) monetization.showRewardedAdOnce() else null
                                    if (outcome is AdRewardOutcome.NotRewarded) {
                                        platform.notice.show(fortuneAdFailedFor(language, outcome.reason))
                                    } else {
                                        // 광고를 본 뒤의 순간으로 다시 뽑는다 — 광고를 보는 사이 날이 바뀌었으면 갱신이 아니라 새 날의 뽑기가 남는다.
                                        val now = currentEpochMillis()
                                        val day = utcDayIndex(now)
                                        refreshDailyFortune(store.load(), day, now, poolSize)?.let { refreshed ->
                                            store.save(refreshed)
                                            progress = refreshed
                                        }
                                        today = day
                                    }
                                    busy = false
                                }
                            },
                        )
                    }
                }
                Text(
                    text = fortuneRefreshesLeftFor(language, left),
                    fontSize = AppTextSize.Text12,
                    color = colors.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = keepWordsWhole(fortuneRefreshesDoneFor(language), language),
                    fontSize = AppTextSize.Text13,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
        ComingSoonFortuneRow(title = fortuneStarSignTitleFor(language), setLabel = fortuneSetStarSignFor(language))
        HorizontalDivider(color = colors.outlineVariant)
        ComingSoonFortuneRow(title = fortuneZodiacTitleFor(language), setLabel = fortuneSetZodiacFor(language))
        Text(
            text = fortuneForFunFor(language),
            fontSize = AppTextSize.Text12,
            color = colors.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FortuneBlockTitle(text: String) {
    Text(text = text, fontSize = AppTextSize.Text14, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

/**
 * 준비 중인 칸 — 왼쪽에 이름, 오른쪽 끝에 흐린 「별자리 설정하기 (준비 중)」. 눌리지 않는다(사용자: 데이터 확보가 미비하면 「(준비 중)」).
 * 덧말이 길면(영어 · 큰 글꼴) 두 줄까지 접히고 이름은 줄지 않는다 — 실험실 섹션 제목 줄(`LabSectionHeader`)과 같은 모양.
 */
@Composable
private fun ComingSoonFortuneRow(title: String, setLabel: String) {
    val colors = MaterialTheme.colorScheme
    val language = LocalUiStrings.current.language
    Row(verticalAlignment = Alignment.CenterVertically) {
        FortuneBlockTitle(title)
        Spacer(modifier = Modifier.width(AppSpacing.Space12))
        Text(
            text = setLabel + " (" + studyComingSoonFor(language) + ")",
            fontSize = AppTextSize.Text12,
            color = colors.secondary,
            textAlign = TextAlign.End,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 실험실 카드들과 같은 초록 테두리 알약(1일 1회 뽑기의 단추와 같은 모양). */
@Composable
private fun FortunePill(text: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = colors.surface,
        border = BorderStroke(AppBorderWidth.Emphasis, colors.primary),
    ) {
        Text(
            text = text,
            fontSize = AppTextSize.Text15,
            fontWeight = FontWeight.Bold,
            color = colors.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = AppSpacing.Space20, vertical = AppSpacing.Space10),
        )
    }
}

private val RefreshButtonMinHeight = 48.dp
