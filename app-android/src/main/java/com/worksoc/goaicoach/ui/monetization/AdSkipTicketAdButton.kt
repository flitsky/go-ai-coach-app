package com.worksoc.goaicoach.ui.monetization

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.consumable.AdSkipTicketAdAvailability
import com.worksoc.goaicoach.application.consumable.AdSkipTicketAdCooldownMillis
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.adSkipTicketAdAvailability
import com.worksoc.goaicoach.application.consumable.runAdSkipTicketAdGrant
import com.worksoc.goaicoach.application.premium.port.AdRewardOutcome
import com.worksoc.goaicoach.persistence.AdSkipTicketAdStore
import com.worksoc.goaicoach.persistence.ConsumableInventoryStore
import com.worksoc.goaicoach.showRewardedAdOnce
import com.worksoc.goaicoach.ui.designsystem.AppBorderWidth
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.adSkipTicketAdButtonFor
import com.worksoc.goaicoach.ui.l10n.adSkipTicketAdCooldownDescriptionFor
import com.worksoc.goaicoach.ui.l10n.adSkipTicketAdEarnedFor
import com.worksoc.goaicoach.ui.l10n.adSkipTicketAdFailedFor
import com.worksoc.goaicoach.ui.l10n.attendanceAtStockCapNoticeFor
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 「내 정보 › 보유 중인 1회권」의 광고 스킵권 줄에 붙는 단추(백로그 #283, 사용자 2026-10-23) — **광고 1편 = 스킵권 1장, 하루 한 번.**
 * 규칙은 shared의 `adSkipTicketAdAvailability` · `runAdSkipTicketAdGrant` 한 곳이고, 여기는 그 판정을 모양으로 옮긴다.
 *
 * - 받을 수 있음: 초록 테두리 알약 「광고 보고 +1」. 누르면 리워드 광고(`showRewardedAdOnce` — 동의 · 배경음악 처리가 거기 있다, 함정 99).
 * - 광고 중: 같은 자리의 도는 표시(두 번 누를 수 없다).
 * - **쿨타임**: 받은 때부터 24시간 — 지나간 만큼 차오르는 고리와 남은 시간(시:분:초, 1초마다). 눌리지 않는다.
 * - 보유 상한(9): 흐린 「보유 상한」 — 광고를 봐도 받을 것이 없으니 광고를 띄우지 않는다.
 * - 구독 중: 그리지 않는다 — 구독자는 광고를 볼 일이 없다.
 *
 * ⚠️ 재고는 저장소에 직접 쓰므로 받은 뒤 [ConsumableUiState.refresh]를 부른다(함정 13) — 부르지 않으면 바로 옆 숫자가 그대로다.
 * ⚠️ 시계는 `System.currentTimeMillis`를 직접 읽는다 — 출석과 같다(함정 12).
 */
@Composable
internal fun AdSkipTicketAdButton(modifier: Modifier = Modifier) {
    if (LocalPremiumUiState.current.isPurchased) return
    val context = LocalContext.current
    val strings = LocalUiStrings.current
    val language = strings.language
    val consumables = LocalConsumableUiState.current
    val adStore = remember(context) { AdSkipTicketAdStore(context) }
    var lastEarned by remember(adStore) { mutableStateOf(adStore.lastEarnedAtMillis()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var watching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val availability = adSkipTicketAdAvailability(consumables.inventory, lastEarned, now)

    // 쿨타임 동안만 1초마다 시계를 다시 읽는다 — 끝나면 판정이 「받을 수 있음」으로 바뀌며 이 고리도 멈춘다.
    val cooling = availability is AdSkipTicketAdAvailability.Cooldown
    LaunchedEffect(cooling) {
        while (cooling) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    val colors = MaterialTheme.colorScheme
    when {
        watching -> Pill(modifier = modifier, border = BorderStroke(AppBorderWidth.Hairline, colors.primary)) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.primary)
        }
        availability is AdSkipTicketAdAvailability.Ready -> Pill(
            modifier = modifier,
            border = BorderStroke(AppBorderWidth.Hairline, colors.primary),
            onClick = {
                watching = true
                scope.launch {
                    val outcome = showRewardedAdOnce(context)
                    val message = if (outcome is AdRewardOutcome.NotRewarded) {
                        adSkipTicketAdFailedFor(language, outcome.reason)
                    } else {
                        // 광고를 본 뒤에 다시 판정한다(shared) — 그 사이 출석 보상이 상한까지 채웠으면 아무것도 쓰지 않는다.
                        val earnedAt = System.currentTimeMillis()
                        runAdSkipTicketAdGrant(ConsumableInventoryStore(context), adStore, earnedAt)?.let {
                            adSkipTicketAdEarnedFor(language, strings.consumableRewardName(ConsumableCatalog.PremiumOnce))
                        }
                    }
                    consumables.refresh()
                    lastEarned = adStore.lastEarnedAtMillis()
                    now = System.currentTimeMillis()
                    watching = false
                    message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                }
            },
        ) {
            Text(text = "▶ " + adSkipTicketAdButtonFor(language), fontSize = AppTextSize.Text12, fontWeight = FontWeight.Bold, color = colors.primary)
        }
        availability is AdSkipTicketAdAvailability.Cooldown -> {
            val remaining = availability.remainingMillis
            val totalSeconds = (remaining + 999) / 1_000
            val hours = totalSeconds / 3_600
            val minutes = totalSeconds % 3_600 / 60
            val seconds = totalSeconds % 60
            Pill(
                modifier = modifier.semantics { contentDescription = adSkipTicketAdCooldownDescriptionFor(language, hours, minutes) },
                color = colors.surfaceVariant,
            ) {
                // 쿨타임 고리 — 받은 뒤 지나간 만큼 차오른다(다 차면 다시 받을 수 있다).
                CircularProgressIndicator(
                    progress = { 1f - remaining.toFloat() / AdSkipTicketAdCooldownMillis },
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = colors.primary,
                    trackColor = colors.outlineVariant,
                )
                Text(
                    text = String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds),
                    fontSize = AppTextSize.Text12,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        else -> Text(
            text = attendanceAtStockCapNoticeFor(language),
            fontSize = AppTextSize.Text12,
            color = colors.secondary,
            modifier = modifier,
        )
    }
}

/** 단추 자리의 알약 — 받을 수 있을 때만 [onClick]이 있다. 키는 최소값만 준다(큰 글꼴에서 잘리지 않게, 함정 9). */
@Composable
private fun Pill(
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    color: Color = Color.Transparent,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    val inner: @Composable () -> Unit = {
        Row(
            modifier = Modifier.heightIn(min = PillMinHeight).padding(horizontal = AppSpacing.Space10, vertical = AppSpacing.Space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space6),
        ) { content() }
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, border = border, content = inner)
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = border, content = inner)
    }
}

private val PillMinHeight = 32.dp
