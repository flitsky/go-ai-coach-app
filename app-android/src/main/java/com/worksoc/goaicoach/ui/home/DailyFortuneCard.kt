package com.worksoc.goaicoach.ui.home

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.attendance.MillisPerUtcDay
import com.worksoc.goaicoach.application.attendance.utcDayIndex
import com.worksoc.goaicoach.application.dailyfortune.DailyFortuneReading
import com.worksoc.goaicoach.application.dailyfortune.FortuneSign
import com.worksoc.goaicoach.application.dailyfortune.FortuneStrand
import com.worksoc.goaicoach.application.dailyfortune.dailyFortuneReading
import com.worksoc.goaicoach.application.dailyfortune.refreshDailyFortune
import com.worksoc.goaicoach.application.premium.port.AdRewardOutcome
import com.worksoc.goaicoach.persistence.DailyFortuneStore
import com.worksoc.goaicoach.showRewardedAdOnce
import com.worksoc.goaicoach.ui.designsystem.AppBorderWidth
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.l10n.DailyFortuneCatalog
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.QuoteLineBreak
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.fortuneAdFailedFor
import com.worksoc.goaicoach.ui.l10n.fortuneChangeSignFor
import com.worksoc.goaicoach.ui.l10n.fortuneForFunFor
import com.worksoc.goaicoach.ui.l10n.fortunePickButtonFor
import com.worksoc.goaicoach.ui.l10n.fortunePickPromptFor
import com.worksoc.goaicoach.ui.l10n.fortunePickerNoteFor
import com.worksoc.goaicoach.ui.l10n.fortunePickerTitleFor
import com.worksoc.goaicoach.ui.l10n.fortuneRefreshButtonFor
import com.worksoc.goaicoach.ui.l10n.fortuneRefreshInviteFor
import com.worksoc.goaicoach.ui.l10n.fortuneRefreshesDoneFor
import com.worksoc.goaicoach.ui.l10n.fortuneRefreshesLeftFor
import com.worksoc.goaicoach.ui.l10n.fortuneSignNameFor
import com.worksoc.goaicoach.ui.l10n.fortuneStarTabFor
import com.worksoc.goaicoach.ui.l10n.fortuneStrandLabelFor
import com.worksoc.goaicoach.ui.l10n.fortuneZodiacTabFor
import com.worksoc.goaicoach.ui.l10n.keepWordsWhole
import com.worksoc.goaicoach.ui.l10n.quoteLocaleTag
import com.worksoc.goaicoach.ui.l10n.starSignDatesFor
import com.worksoc.goaicoach.ui.monetization.LocalPremiumUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 실험실 「오늘의 운세」의 카드(백로그 #276, 사용자 2026-10-23) — 규칙은 shared `application/dailyfortune/DailyFortune.kt` 한 곳이고, 여기는 그것을 모양으로 옮긴다.
 *
 * - 아직 고르지 않음: 안내 한 줄 + 「띠 · 별자리 고르기」 알약 → 고르는 창(탭 둘 · 열둘씩, 별자리는 날짜를 함께).
 * - 고른 뒤: 이름과 「다시 고르기」 → 띠 운세(또는 별자리 운세) · 바둑 운세 · 행운의 자리 → 갱신 줄 → 「운세는 재미로 보세요」.
 * - **갱신**(사용자): 첫 운세는 무료, 「광고 보고 운 바꾸기」(구독자는 「운 바꾸기」 — 광고 없이) · 하루 3번. 단추 위에 그 의지를 바둑에 빗댄 한 줄이 있다.
 *   광고는 `showRewardedAdOnce`(동의 · 배경음악, 함정 99). 끝까지 보지 않았거나 광고가 없으면 운은 그대로다.
 *   새 운세는 옅어졌다 나타난다 — 「무엇이 나올까」 돌려 보는 맛(스레드가 정한 것). 점수 · 등급은 어디에도 없다.
 *
 * ⚠️ 날이 바뀌는 때(UTC 자정 — 한국 오전 9시)에 화면을 열어 두고 있어도 새 운세로 바뀐다 — 다음 자정까지 기다렸다 오늘을 다시 읽는다.
 * ⚠️ 시계는 `System.currentTimeMillis`를 직접 읽는다 — 출석과 같다(함정 12).
 */
@Composable
internal fun DailyFortuneCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val language = LocalUiStrings.current.language
    val colors = MaterialTheme.colorScheme
    val withAd = !LocalPremiumUiState.current.isPurchased
    val store = remember(context) { DailyFortuneStore(context) }
    var progress by remember(store) { mutableStateOf(store.load()) }
    var today by remember { mutableLongStateOf(utcDayIndex(System.currentTimeMillis())) }
    var busy by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(today) {
        val untilNextDay = (today + 1) * MillisPerUtcDay - System.currentTimeMillis()
        delay(untilNextDay.coerceAtLeast(1_000))
        today = utcDayIndex(System.currentTimeMillis())
    }

    val sign = progress.sign
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FortuneCardMinHeight)
            .clip(RoundedCornerShape(AppRadius.Corner24))
            .background(colors.surfaceVariant)
            .padding(AppSpacing.Space16),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12, Alignment.CenterVertically),
    ) {
        if (sign == null) {
            Text(
                text = keepWordsWhole(fortunePickPromptFor(language), language),
                fontSize = AppTextSize.Text14,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FortunePill(text = fortunePickButtonFor(language), onClick = { picking = true })
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = fortuneSignNameFor(language, sign),
                    fontSize = AppTextSize.Text16,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { picking = true }) {
                    Text(text = fortuneChangeSignFor(language), fontSize = AppTextSize.Text13, color = colors.primary)
                }
            }
            val reading = dailyFortuneReading(sign, today, progress.refreshesUsedOn(today), DailyFortuneCatalog.sizes)
            Crossfade(targetState = reading, label = "dailyFortune") { shown -> FortuneReadingBody(shown, language) }
            HorizontalDivider(color = colors.outlineVariant)
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
                                    val outcome = if (withAd) showRewardedAdOnce(context) else null
                                    if (outcome is AdRewardOutcome.NotRewarded) {
                                        Toast.makeText(context, fortuneAdFailedFor(language, outcome.reason), Toast.LENGTH_SHORT).show()
                                    } else {
                                        // 광고를 본 뒤에 날을 다시 읽는다 — 광고를 보는 사이 날이 바뀌었으면 새 날의 첫 갱신이다.
                                        val day = utcDayIndex(System.currentTimeMillis())
                                        refreshDailyFortune(store.load(), day)?.let { refreshed ->
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
            Text(
                text = fortuneForFunFor(language),
                fontSize = AppTextSize.Text12,
                color = colors.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (picking) {
        FortuneSignPicker(
            current = sign,
            language = language,
            onPick = { picked ->
                val next = store.load().withSign(picked)
                store.save(next)
                progress = next
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** 운세 본문 — 띠(별자리) 운세 · 바둑 운세 · 행운의 자리. 갱신하면 통째로 옅어졌다 바뀐다. */
@Composable
private fun FortuneReadingBody(reading: DailyFortuneReading, language: UiLanguage) {
    val colors = MaterialTheme.colorScheme
    val main = DailyFortuneCatalog.pool(reading.mainStrand)[reading.mainIndex]
    val go = DailyFortuneCatalog.go[reading.goIndex]
    val lucky = DailyFortuneCatalog.luckyPoints[reading.luckyPointIndex]
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12)) {
        FortuneStrandBlock(fortuneStrandLabelFor(language, reading.mainStrand), main.textFor(language), language)
        FortuneStrandBlock(fortuneStrandLabelFor(language, FortuneStrand.Go), go.textFor(language), language)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
            Text(
                text = fortuneStrandLabelFor(language, FortuneStrand.LuckyPoint),
                fontSize = AppTextSize.Text13,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
            )
            Text(text = lucky.textFor(language), fontSize = AppTextSize.Text15, fontWeight = FontWeight.Bold, color = colors.onSurface)
        }
    }
}

@Composable
private fun FortuneStrandBlock(label: String, text: String, language: UiLanguage) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space4)) {
        Text(text = label, fontSize = AppTextSize.Text13, fontWeight = FontWeight.SemiBold, color = colors.primary)
        Text(
            text = keepWordsWhole(text, language),
            fontSize = AppTextSize.Text15,
            color = colors.onSurface,
            style = LocalTextStyle.current.copy(lineBreak = QuoteLineBreak, localeList = LocaleList(language.quoteLocaleTag)),
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

/**
 * 띠 · 별자리를 고르는 창 — 탭 둘(띠 / 별자리), 열둘을 띠는 세 칸씩 · 별자리는 두 칸씩. 별자리 칸에는 날짜(월 · 일)를 작게 적는다. 누르면 곧바로 고르고 닫힌다.
 * 아래에 「생년월일은 묻지 않아요 …」 한 줄(사용자 결정 — 나이를 모르고, 기기에만 남는다).
 */
@Composable
private fun FortuneSignPicker(
    current: FortuneSign?,
    language: UiLanguage,
    onPick: (FortuneSign) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalUiStrings.current
    val colors = MaterialTheme.colorScheme
    var showStars by remember { mutableStateOf(current is FortuneSign.Star) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(fortunePickerTitleFor(language), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
                    PickerTab(fortuneZodiacTabFor(language), selected = !showStars, onClick = { showStars = false })
                    PickerTab(fortuneStarTabFor(language), selected = showStars, onClick = { showStars = true })
                }
                val choices = FortuneSign.all.filter { (it is FortuneSign.Star) == showStars }
                choices.chunked(if (showStars) StarPickerColumns else ZodiacPickerColumns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
                        row.forEach { choice ->
                            PickerChip(choice, selected = choice == current, language = language, onClick = { onPick(choice) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
                Text(text = fortunePickerNoteFor(language), fontSize = AppTextSize.Text12, color = colors.secondary)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.close) } },
    )
}

@Composable
private fun PickerTab(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = if (selected) colors.primary else colors.surface,
        border = BorderStroke(AppBorderWidth.Hairline, if (selected) colors.primary else colors.outlineVariant),
    ) {
        Text(
            text = text,
            fontSize = AppTextSize.Text14,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) colors.onPrimary else colors.onSurface,
            modifier = Modifier.padding(horizontal = AppSpacing.Space16, vertical = AppSpacing.Space8),
        )
    }
}

@Composable
private fun PickerChip(choice: FortuneSign, selected: Boolean, language: UiLanguage, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = PickerChipMinHeight),
        shape = RoundedCornerShape(AppRadius.Corner12),
        color = if (selected) colors.primaryContainer else colors.surface,
        border = BorderStroke(if (selected) AppBorderWidth.Emphasis else AppBorderWidth.Hairline, if (selected) colors.primary else colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AppSpacing.Space4, vertical = AppSpacing.Space8),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = fortuneSignNameFor(language, choice),
                fontSize = AppTextSize.Text13,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                textAlign = TextAlign.Center,
            )
            if (choice is FortuneSign.Star) {
                Text(
                    text = starSignDatesFor(language, choice.sign),
                    fontSize = AppTextSize.Text12,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 카드의 최소 키 — 「준비 중」 카드와 같다(#281). 스레드가 정한 값. */
private val FortuneCardMinHeight = 96.dp
private val RefreshButtonMinHeight = 48.dp

/**
 * 고르는 창의 칸 — 최소 키는 손가락이 닿는 48dp. 띠는 한 줄에 셋, 별자리는 둘(이름 밑에 날짜가 붙고, 영어 「Sagittarius」 · 「Nov 22–Dec 21」이
 * 360dp 폭의 세 칸에 들지 않는다). 스레드가 정한 값.
 */
private val PickerChipMinHeight = 48.dp
private const val ZodiacPickerColumns = 3
private const val StarPickerColumns = 2
