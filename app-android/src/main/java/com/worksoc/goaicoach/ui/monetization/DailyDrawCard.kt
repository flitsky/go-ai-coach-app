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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.botcharacter.BotCharacter
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.botcharacter.BotUnlockSource
import com.worksoc.goaicoach.application.botcharacter.TopCharacterAttendanceTier
import com.worksoc.goaicoach.application.botcharacter.WeeklyRewardCycleTier
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableItem
import com.worksoc.goaicoach.application.dailydraw.DailyDrawAvailability
import com.worksoc.goaicoach.application.dailydraw.DailyDrawCooldownMillis
import com.worksoc.goaicoach.application.dailydraw.DailyDrawPrizeKind
import com.worksoc.goaicoach.application.dailydraw.DailyDrawResult
import com.worksoc.goaicoach.application.dailydraw.DailyDrawTicketsEach
import com.worksoc.goaicoach.application.dailydraw.dailyDrawAvailability
import com.worksoc.goaicoach.application.dailydraw.dailyDrawCharacterTarget
import com.worksoc.goaicoach.application.dailydraw.dailyDrawOdds
import com.worksoc.goaicoach.application.dailydraw.dailyDrawPrizeCharacter
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
import com.worksoc.goaicoach.ui.designsystem.BotCharacterAvatar
import com.worksoc.goaicoach.ui.designsystem.ShardReveal
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.UiStrings
import com.worksoc.goaicoach.ui.l10n.attendanceAtStockCapNoticeFor
import com.worksoc.goaicoach.ui.l10n.dailyDrawAdCharacterLabelFor
import com.worksoc.goaicoach.ui.l10n.dailyDrawAdFailedFor
import com.worksoc.goaicoach.ui.l10n.dailyDrawButtonFor
import com.worksoc.goaicoach.ui.l10n.dailyDrawComeBackFor
import com.worksoc.goaicoach.ui.l10n.dailyDrawCooldownDescriptionFor
import com.worksoc.goaicoach.ui.l10n.dailyDrawNextInFor
import com.worksoc.goaicoach.ui.l10n.dailyDrawResultTitleFor
import com.worksoc.goaicoach.ui.l10n.dailyDrawShardLabelFor
import com.worksoc.goaicoach.ui.l10n.dailyDrawSubstituteFootnoteFor
import com.worksoc.goaicoach.ui.l10n.dailyDrawTicketsLabelFor
import com.worksoc.goaicoach.ui.l10n.keepWordsWhole
import com.worksoc.goaicoach.ui.l10n.ownedCharacterSubstituteNoticeFor
import com.worksoc.goaicoach.ui.l10n.unbreakable
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
 * - 차례(#289, 사용자 2026-10-24): 위에 **상품 그림 30% · 확률 70%**, 그 **아래에 뽑기 단추** · 쿨타임.
 * - **지금의 확률**을 상품마다 한 줄씩 늘 적는다(「(30%) 광고 스킵권」, 사용자 2026-10-23) — 90% 묶음은 줄 수 없는 상품이 빠지면 나머지의 비율이 바뀌므로
 *   지금 판정(`dailyDrawOdds`, 합 100)을 그대로 적는다. 조각 · 캐릭터에는 대상의 이름을 달고, 줄 아래 한 줄로 「*이미 보유중일 경우 7일 단위 보상으로 대체 지급합니다.」(#289).
 *   확률을 감추지 않는 것은 스레드가 정했다(무작위 보상의 확률 공개 관행).
 * - 결과: 캐릭터를 얻었으면 기존 획득 축하 창(`BotCharacterUiState.enqueueAcquired`), 아니면 「뽑기 결과」 창 — 대체 지급이면 그 안내가 맨 위에 선다.
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
        // 위: 상품 그림 30% · 확률 70%(#289 — 사용자 2026-10-24), 아래: 뽑기 단추(사용자 2026-10-24: 아래로).
        val lines = oddsRows(strings, bots.collection, consumables)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PrizePile(lines.flatten().map { it.kind }, bots.collection, modifier = Modifier.weight(PileWidthShare))
            Column(modifier = Modifier.weight(1f - PileWidthShare).padding(start = AppSpacing.Space8), verticalArrangement = Arrangement.spacedBy(AppSpacing.Space6)) {
                OddsLines(lines)
                // 가진 캐릭터를 줄마다 표시하지 않고 한 줄로 알린다(사용자 2026-10-24 — 그 전에는 줄마다 「→ 7일 단위 보상」).
                Text(text = keepWordsWhole(dailyDrawSubstituteFootnoteFor(language), language), fontSize = AppTextSize.Text12, color = colors.secondary)
            }
        }
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
    }

    shown?.let { result ->
        AlertDialog(
            onDismissRequest = { shown = null },
            title = { Text(dailyDrawResultTitleFor(language), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
                    if (result.substituted) {
                        val name = result.character?.let(strings::botCharacterName)
                        Text(ownedCharacterSubstituteNoticeFor(language, name), fontSize = AppTextSize.Text14, color = colors.onSurfaceVariant)
                    }
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

/**
 * 지금의 확률 — 상품마다 (확률, 이름) 한 줄. `dailyDrawOdds`가 이미 합 100으로 주므로 그대로 적는다.
 * 차례는 그림과 같다([OddsDisplayRows]) — 뽑기의 칸 순서(`DailyDrawPrizeKind`)와는 따로다.
 */
private fun oddsRows(
    strings: UiStrings,
    collection: BotCollectionState,
    consumables: ConsumableUiState,
): List<List<OddsLine>> {
    val language = strings.language
    val odds = dailyDrawOdds(consumables.inventory, collection)
    val targetName = dailyDrawCharacterTarget(collection)?.let(strings::botCharacterName).orEmpty()
    fun line(kind: DailyDrawPrizeKind): OddsLine? {
        val percent = odds[kind] ?: return null
        val label = when (kind) {
            DailyDrawPrizeKind.Tickets -> dailyDrawTicketsLabelFor(language, DailyDrawTicketsEach)
            DailyDrawPrizeKind.AdSkipTicket -> strings.consumableRewardName(ConsumableCatalog.PremiumOnce)
            DailyDrawPrizeKind.CharacterShard -> dailyDrawShardLabelFor(language, targetName)
            DailyDrawPrizeKind.Character, DailyDrawPrizeKind.Day7Character, DailyDrawPrizeKind.Day28Character -> {
                val name = dailyDrawPrizeCharacter(kind, collection)?.let(strings::botCharacterName) ?: dailyDrawAdCharacterLabelFor(language)
                name
            }
        }
        // 반 폭이라 이름이 줄을 바꾼다 — 한국어는 어절 안에서 끊기지 않게(「1 / 회권」이 됐다).
        return OddsLine(kind, percent, keepWordsWhole(label, language))
    }
    return OddsDisplayRows.map { row -> row.mapNotNull(::line) }.filter { it.isNotEmpty() }
}

/** 확률 한 줄 — 어느 상품인지([kind])를 같이 들어 그림 층과 짝을 짓는다. */
private data class OddsLine(val kind: DailyDrawPrizeKind, val percent: Int, val label: String)

/**
 * 확률 줄들 — 한 줄에 상품이 둘이면 「(2%) 수제자 반상, (1%) 관장 천원」처럼 잇는다(칸이 좁으면 두 줄).
 * 확률 칸은 두 자리 폭으로 고정한다 — 「(7%)」가 「(30%)」보다 좁아 이름이 줄마다 어긋났다(#289). 숫자는 폭이 같아 「(00%)」를 투명하게 깔아 잰다.
 */
@Composable
private fun OddsLines(rows: List<List<OddsLine>>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val percentStyle = SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.primary)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.Space4)) {
        rows.forEach { row ->
            val first = row.first()
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space6)) {
                Box(contentAlignment = Alignment.CenterEnd) {
                    Text(text = "(00%)", fontSize = AppTextSize.Text13, fontWeight = FontWeight.SemiBold, color = Color.Transparent)
                    Text(text = "(${first.percent}%)", fontSize = AppTextSize.Text13, fontWeight = FontWeight.SemiBold, color = colors.primary)
                }
                // 둘을 잇는 줄은 상품 하나를 통째로 묶어 「, 」 뒤에서만 줄을 바꾼다 — 「관장 / 천원」처럼 이름이 갈렸다.
                val label = if (row.size == 1) {
                    AnnotatedString(first.label)
                } else {
                    buildAnnotatedString {
                        append(unbreakable(first.label))
                        row.drop(1).forEach { line ->
                            append(", ")
                            withStyle(percentStyle) { append("(${line.percent}%)") }
                            append("\u00A0" + unbreakable(line.label))
                        }
                    }
                }
                Text(text = label, fontSize = AppTextSize.Text13, color = colors.onSurfaceVariant)
            }
        }
    }
}

/**
 * 상품 그림(사용자 2026-10-24, #289) — 위에서부터 네 줄: ① 출석 캐릭터 둘(반상 2% · 천원 1%) ② 광고 캐릭터 둘 ③ 조각 · 광고 스킵권 ④ 형세 · 추천 수 1회권 하나씩 + 「× 10」.
 * 캐릭터는 이미 가졌어도 상품이므로(대체 지급) 색으로 그린다. 나올 수 없는 조각 · 스킵권은 그림에서도 빠진다. 조각은 지금 대상의 얼굴에 한 조각만 색이 돈다.
 */
@Composable
private fun PrizePile(kinds: List<DailyDrawPrizeKind>, collection: BotCollectionState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
        listOf(AttendanceDrawCharacters, BotCharacterCatalog.shardPathCharacters()).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space4)) {
                row.forEach { character -> BotCharacterAvatar(character = character, size = PileAvatarSize, seamColor = colors.surfaceVariant) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8), verticalAlignment = Alignment.CenterVertically) {
            if (DailyDrawPrizeKind.CharacterShard in kinds) {
                dailyDrawCharacterTarget(collection)?.let { target ->
                    val required = (target.unlockSource as? BotUnlockSource.AdShards)?.required ?: 1
                    BotCharacterAvatar(
                        character = target,
                        size = PileGlyphSize,
                        available = false,
                        reveal = ShardReveal(acquired = 1, required = required),
                        seamColor = colors.surfaceVariant,
                    )
                }
            }
            if (DailyDrawPrizeKind.AdSkipTicket in kinds) PileGlyph(ConsumableCatalog.PremiumOnce, colors.secondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space4), verticalAlignment = Alignment.CenterVertically) {
            PileGlyph(ConsumableCatalog.EvalOnce, colors.primary)
            PileGlyph(ConsumableCatalog.TopMovesOnce, colors.tertiary)
            Text(text = "× $DailyDrawTicketsEach", fontSize = AppTextSize.Text13, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun PileGlyph(item: ConsumableItem, tint: Color) {
    val glyph = consumableGlyphRes(item) ?: return
    Icon(painter = painterResource(glyph), contentDescription = null, tint = tint, modifier = Modifier.size(PileGlyphSize))
}

/** 뽑기로 나오는 출석 캐릭터 둘 — 7일차(반상) · 28일차(천원), 등급 순. 카탈로그에서 읽는다(이름 · 회차를 여기 적지 않는다). */
private val AttendanceDrawCharacters: List<BotCharacter> =
    BotCharacterCatalog.forAttendanceTier(WeeklyRewardCycleTier) + BotCharacterCatalog.forAttendanceTier(TopCharacterAttendanceTier)

/** 그림 칸의 몫 — 나머지는 확률 줄(사용자 2026-10-24: 30% · 70%). */
private const val PileWidthShare = 0.3f
private val PileAvatarSize = 36.dp
private val PileGlyphSize = 24.dp

/** 확률 줄의 차례 — 그림의 차례와 같다(사용자 2026-10-24): 출석 캐릭터 둘(한 줄) → 광고 캐릭터 → 조각 → 광고 스킵권 → 1회권. */
private val OddsDisplayRows: List<List<DailyDrawPrizeKind>> = listOf(
    listOf(DailyDrawPrizeKind.Day7Character, DailyDrawPrizeKind.Day28Character),
    listOf(DailyDrawPrizeKind.Character),
    listOf(DailyDrawPrizeKind.CharacterShard),
    listOf(DailyDrawPrizeKind.AdSkipTicket),
    listOf(DailyDrawPrizeKind.Tickets),
)

/** 결과 창의 본문. 캐릭터를 얻은 경우는 이 창이 아니라 획득 축하 창이 뜬다. */
private fun resultLine(strings: UiStrings, result: DailyDrawResult): String {
    // 대체 지급(#289) — 실제로 들어간 수를 적고, 상한에 걸려 0장이면 출석 팝업처럼 「보유 상한」을 밝힌다.
    if (result.substituted) {
        return result.substituteGrants.joinToString("\n") { grant ->
            strings.consumableRewardName(grant.item) + " " +
                if (grant.amount > 0) strings.consumableRewardAmount(grant.amount) else "(" + attendanceAtStockCapNoticeFor(strings.language) + ")"
        }
    }
    return when (result.kind) {
        DailyDrawPrizeKind.Tickets -> listOf(ConsumableCatalog.EvalOnce, ConsumableCatalog.TopMovesOnce)
            .joinToString("\n") { item -> strings.consumableRewardName(item) + " " + strings.consumableRewardAmount(DailyDrawTicketsEach) }
        DailyDrawPrizeKind.AdSkipTicket -> strings.consumableRewardName(ConsumableCatalog.PremiumOnce) + " " + strings.consumableRewardAmount(1)
        DailyDrawPrizeKind.CharacterShard, DailyDrawPrizeKind.Character, DailyDrawPrizeKind.Day7Character, DailyDrawPrizeKind.Day28Character -> {
            val character = requireNotNull(result.character) { "조각 · 캐릭터 결과에는 캐릭터가 실린다(runDailyDraw)." }
            val required = (character.unlockSource as? BotUnlockSource.AdShards)?.required ?: 0
            strings.botShardEarnedToast(character, result.shards, required)
        }
    }
}

private val DailyDrawCardMinHeight = 96.dp
private val DrawButtonMinHeight = 48.dp
