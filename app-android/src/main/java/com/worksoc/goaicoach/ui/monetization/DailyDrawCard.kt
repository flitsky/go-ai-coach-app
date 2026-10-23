package com.worksoc.goaicoach.ui.monetization

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.botcharacter.BotUnlockSource
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.dailydraw.DailyDrawAvailability
import com.worksoc.goaicoach.application.dailydraw.DailyDrawCooldownMillis
import com.worksoc.goaicoach.application.dailydraw.DailyDrawPrizeKind
import com.worksoc.goaicoach.application.dailydraw.DailyDrawResult
import com.worksoc.goaicoach.application.dailydraw.DailyDrawTicketsEach
import com.worksoc.goaicoach.application.dailydraw.dailyDrawAvailability
import com.worksoc.goaicoach.application.dailydraw.dailyDrawCharacterTarget
import com.worksoc.goaicoach.application.dailydraw.dailyDrawOdds
import com.worksoc.goaicoach.application.dailydraw.runDailyDraw
import com.worksoc.goaicoach.application.premium.port.AdRewardOutcome
import com.worksoc.goaicoach.persistence.BotCollectionStore
import com.worksoc.goaicoach.persistence.ConsumableInventoryStore
import com.worksoc.goaicoach.persistence.DailyDrawStore
import com.worksoc.goaicoach.showRewardedAdOnce
import com.worksoc.goaicoach.ui.designsystem.AppBorderWidth
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.UiStrings
import com.worksoc.goaicoach.ui.l10n.dailyDrawAdFailedFor
import com.worksoc.goaicoach.ui.l10n.dailyDrawButtonFor
import com.worksoc.goaicoach.ui.l10n.dailyDrawComeBackFor
import com.worksoc.goaicoach.ui.l10n.dailyDrawCooldownDescriptionFor
import com.worksoc.goaicoach.ui.l10n.dailyDrawNextInFor
import com.worksoc.goaicoach.ui.l10n.dailyDrawResultTitleFor
import com.worksoc.goaicoach.ui.l10n.dailyDrawShardLabelFor
import com.worksoc.goaicoach.ui.l10n.dailyDrawTicketsLabelFor
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 실험실 「1일 1회 뽑기」의 카드(백로그 #283, 사용자 2026-10-23) — 규칙은 shared `application/dailydraw/DailyDraw.kt` 한 곳이고, 여기는 그것을 모양으로 옮긴다.
 *
 * - 뽑을 수 있음: 초록 테두리 알약 「▶ 광고 보고 뽑기」(구독자는 「▶ 뽑기」 — 광고 없이). 광고는 `showRewardedAdOnce`(동의 · 배경음악, 함정 99).
 * - 광고 · 뽑는 중: 도는 표시(두 번 누를 수 없다).
 * - **쿨타임**: 뽑은 때부터 24시간 — 지나간 만큼 차오르는 고리와 남은 시간(시:분:초, 1초마다). 눌리지 않는다.
 * - 카드 아래에 **지금의 확률**을 늘 적는다 — 줄 수 없는 상품이 빠지면 나머지의 비율이 바뀌므로 지금 판정(`dailyDrawOdds`)에서 센다.
 *   조각 · 캐릭터에는 지금 대상의 이름을 단다. 확률을 감추지 않는 것은 스레드가 정했다(무작위 보상의 확률 공개 관행).
 * - 결과: 캐릭터를 얻었으면 기존 획득 축하 창(`BotCharacterUiState.enqueueAcquired`), 아니면 「뽑기 결과」 창.
 *
 * ⚠️ 재고 · 수집은 저장소에 직접 쓰므로 뽑은 뒤 두 화면 상태를 다시 읽는다(함정 13) — 부르지 않으면 내 정보의 숫자 · 캐릭터 목록이 그대로다.
 * ⚠️ 시계는 `System.currentTimeMillis`를 직접 읽는다 — 출석과 같다(함정 12).
 */
@Composable
internal fun DailyDrawCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val strings = LocalUiStrings.current
    val language = strings.language
    val colors = MaterialTheme.colorScheme
    val consumables = LocalConsumableUiState.current
    val bots = LocalBotCharacterUiState.current
    val withAd = !LocalPremiumUiState.current.isPurchased
    val drawStore = remember(context) { DailyDrawStore(context) }
    var lastDrawn by remember(drawStore) { mutableStateOf(drawStore.lastDrawnAtMillis()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var busy by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf<DailyDrawResult?>(null) }
    val scope = rememberCoroutineScope()
    val availability = dailyDrawAvailability(lastDrawn, now)

    // 쿨타임 동안만 1초마다 시계를 다시 읽는다 — 끝나면 판정이 「뽑을 수 있음」으로 바뀌며 이 고리도 멈춘다.
    val cooling = availability is DailyDrawAvailability.Cooldown
    LaunchedEffect(cooling) {
        while (cooling) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DailyDrawCardMinHeight)
            .clip(RoundedCornerShape(AppRadius.Corner24))
            .background(colors.surfaceVariant)
            .padding(AppSpacing.Space16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12, Alignment.CenterVertically),
    ) {
        Box(modifier = Modifier.heightIn(min = DrawButtonMinHeight), contentAlignment = Alignment.Center) {
            when {
                busy -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp, color = colors.primary)
                availability is DailyDrawAvailability.Cooldown -> CooldownLine(availability.remainingMillis)
                else -> Surface(
                    onClick = {
                        busy = true
                        scope.launch {
                            val outcome = if (withAd) showRewardedAdOnce(context) else null
                            if (outcome is AdRewardOutcome.NotRewarded) {
                                Toast.makeText(context, dailyDrawAdFailedFor(language, outcome.reason), Toast.LENGTH_SHORT).show()
                            } else {
                                // 광고를 본 뒤에 상품을 정한다(shared) — 그 사이 출석 보상이 같은 저장소에 썼을 수 있다.
                                val result = runDailyDraw(
                                    consumableStore = ConsumableInventoryStore(context),
                                    collectionStore = BotCollectionStore(context),
                                    drawStore = drawStore,
                                    nowMillis = System.currentTimeMillis(),
                                    random = Random.Default,
                                )
                                consumables.refresh()
                                bots.refresh()
                                // 캐릭터를 얻었으면 기존 획득 축하 창, 아니면 결과 창. (`character`는 다른 모듈의 값이라 스마트 캐스트가 안 된다.)
                                val acquired = result?.character?.takeIf { result.unlocked }
                                if (acquired != null) bots.enqueueAcquired(listOf(acquired)) else shown = result
                            }
                            lastDrawn = drawStore.lastDrawnAtMillis()
                            now = System.currentTimeMillis()
                            busy = false
                        }
                    },
                    shape = RoundedCornerShape(percent = 50),
                    color = colors.surface,
                    border = BorderStroke(AppBorderWidth.Emphasis, colors.primary),
                ) {
                    Text(
                        text = "▶ " + dailyDrawButtonFor(language, withAd),
                        fontSize = AppTextSize.Text15,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                        modifier = Modifier.padding(horizontal = AppSpacing.Space20, vertical = AppSpacing.Space10),
                    )
                }
            }
        }
        Text(
            text = oddsLine(strings, bots.collection, consumables),
            fontSize = AppTextSize.Text12,
            color = colors.secondary,
            textAlign = TextAlign.Center,
        )
    }

    shown?.let { result ->
        AlertDialog(
            onDismissRequest = { shown = null },
            title = { Text(dailyDrawResultTitleFor(language), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
                    Text(resultLine(strings, result), fontSize = AppTextSize.Text18, fontWeight = FontWeight.SemiBold)
                    Text(dailyDrawComeBackFor(language), fontSize = AppTextSize.Text13, color = colors.secondary)
                }
            },
            confirmButton = { TextButton(onClick = { shown = null }) { Text(strings.confirm) } },
        )
    }
}

/** 쿨타임 줄 — 지나간 만큼 차오르는 고리 + 「다음 뽑기까지 23:59:41」. 읽어 줄 때는 시간 · 분으로. */
@Composable
private fun CooldownLine(remainingMillis: Long) {
    val language = LocalUiStrings.current.language
    val colors = MaterialTheme.colorScheme
    val totalSeconds = (remainingMillis + 999) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds % 3_600 / 60
    val seconds = totalSeconds % 60
    Row(
        modifier = Modifier.semantics { contentDescription = dailyDrawCooldownDescriptionFor(language, hours, minutes) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space10),
    ) {
        CircularProgressIndicator(
            progress = { 1f - remainingMillis.toFloat() / DailyDrawCooldownMillis },
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.5.dp,
            color = colors.primary,
            trackColor = colors.outlineVariant,
        )
        Text(text = dailyDrawNextInFor(language), fontSize = AppTextSize.Text14, color = colors.onSurfaceVariant)
        Text(
            text = String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds),
            fontSize = AppTextSize.Text18,
            fontWeight = FontWeight.Bold,
            color = colors.onSurfaceVariant,
        )
    }
}

/** 지금의 확률 한 줄 — 줄 수 없는 상품을 뺀 나머지의 비율(합 100%). */
private fun oddsLine(
    strings: UiStrings,
    collection: BotCollectionState,
    consumables: ConsumableUiState,
): String {
    val language = strings.language
    val odds = dailyDrawOdds(consumables.inventory, collection)
    val total = odds.values.sum()
    val targetName = dailyDrawCharacterTarget(collection)?.let(strings::botCharacterName).orEmpty()
    return odds.entries.joinToString(" · ") { (kind, weight) ->
        val label = when (kind) {
            DailyDrawPrizeKind.CharacterShard -> dailyDrawShardLabelFor(language, targetName)
            DailyDrawPrizeKind.Tickets -> dailyDrawTicketsLabelFor(language, DailyDrawTicketsEach)
            DailyDrawPrizeKind.AdSkipTicket -> strings.consumableRewardName(ConsumableCatalog.PremiumOnce)
            DailyDrawPrizeKind.Character -> targetName
        }
        "$label ${(weight * 100 + total / 2) / total}%"
    }
}

/** 결과 창의 본문 한 줄. 캐릭터를 얻은 경우는 이 창이 아니라 획득 축하 창이 뜬다. */
private fun resultLine(strings: UiStrings, result: DailyDrawResult): String =
    when (result.kind) {
        DailyDrawPrizeKind.Tickets -> listOf(ConsumableCatalog.EvalOnce, ConsumableCatalog.TopMovesOnce)
            .joinToString("\n") { item -> strings.consumableRewardName(item) + " " + strings.consumableRewardAmount(DailyDrawTicketsEach) }
        DailyDrawPrizeKind.AdSkipTicket -> strings.consumableRewardName(ConsumableCatalog.PremiumOnce) + " " + strings.consumableRewardAmount(1)
        DailyDrawPrizeKind.CharacterShard, DailyDrawPrizeKind.Character -> {
            val character = requireNotNull(result.character) { "조각 · 캐릭터 결과에는 캐릭터가 실린다(runDailyDraw)." }
            val required = (character.unlockSource as? BotUnlockSource.AdShards)?.required ?: 0
            strings.botShardEarnedToast(character, result.shards, required)
        }
    }

private val DailyDrawCardMinHeight = 96.dp
private val DrawButtonMinHeight = 48.dp
