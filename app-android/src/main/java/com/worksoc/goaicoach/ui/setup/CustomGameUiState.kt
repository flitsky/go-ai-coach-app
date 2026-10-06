package com.worksoc.goaicoach.ui.setup

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.customgame.CustomGameAdjustment
import com.worksoc.goaicoach.application.customgame.CustomGameState
import com.worksoc.goaicoach.application.customgame.runCustomGameAdjustment
import com.worksoc.goaicoach.application.engine.runEngineIo
import com.worksoc.goaicoach.application.gamehistory.findRecordedGame
import com.worksoc.goaicoach.persistence.CustomGameStore
import com.worksoc.goaicoach.persistence.GameHistoryStore
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.customGameAutoAdjustDescriptionFor
import com.worksoc.goaicoach.ui.l10n.customGameAutoAdjustLabelFor
import com.worksoc.goaicoach.ui.l10n.customGamePromotedFor
import com.worksoc.goaicoach.ui.l10n.customGamePromotionBlockedFor
import com.worksoc.goaicoach.ui.l10n.customRankDialogTitleFor
import com.worksoc.goaicoach.ui.l10n.customRankLockedNoteFor
import com.worksoc.goaicoach.ui.l10n.customRankOfficialNoteFor
import com.worksoc.goaicoach.ui.l10n.customRankStrongerFor
import com.worksoc.goaicoach.ui.l10n.customRankWeakerFor
import com.worksoc.goaicoach.ui.l10n.kgsRankLabelFor
import kotlinx.coroutines.delay

/**
 * **커스텀 대국**(백로그 #217)의 화면 상태 — 다음 판의 상대 급수, 자동 조정 켬/끔, 승급 랠리.
 *
 * `GoCoachApp.kt`의 상태 훅 예산이 꽉 차 있어([buildCustomGameUiState]) 저장소 생성과 상태 보유를 여기로 뺐다 —
 * 셸에서는 호출 한 줄만 보인다(`buildConsumableUiState`와 같은 이유). 좌석 설정 패널이 [LocalCustomGameUiState]로 읽는다.
 */
internal data class CustomGameUiState(
    val state: CustomGameState = CustomGameState(),
    /**
     * 이 기기에서 커스텀 대국을 열 수 있는가 — **사람 모델이 있을 때만**이다. 없으면 급수를 골라도 그 급수처럼 두지 못하므로
     * (가까운 캐릭터의 지금 방식으로 떨어진다) 고르는 자리를 아예 보이지 않는다.
     */
    val isAvailable: Boolean = false,
    /** 상태를 바꾸고 저장한다. */
    val update: (CustomGameState) -> Unit = {},
    /** 끝나서 기록된 판을 승급 랠리에 반영한다 — 같은 판은 한 번만 센다(`runCustomGameAdjustment`). */
    val countRecordedGame: suspend (GameState, BotCollectionState, Boolean) -> CustomGameAdjustment? = { _, _, _ -> null },
)

internal val LocalCustomGameUiState = staticCompositionLocalOf { CustomGameUiState() }

@Composable
internal fun buildCustomGameUiState(
    context: Context,
    isAvailable: Boolean,
): CustomGameUiState {
    val store = remember(context) { CustomGameStore(context) }
    var state by remember(store) { mutableStateOf(store.load()) }
    return CustomGameUiState(
        state = state,
        isAvailable = isAvailable,
        update = { next ->
            store.save(next)
            state = next
        },
        countRecordedGame = { gameState, collection, subscriptionActive ->
            val recorded = runEngineIo { GameHistoryStore(context).findRecordedGame(gameState.moves) }
            recorded?.let { (entry, _) ->
                runCustomGameAdjustment(entry, store, collection, subscriptionActive)?.also { adjustment -> state = adjustment.state }
            }
        },
    )
}

/**
 * 대국이 끝나 결과가 나면 그 판을 승급 랠리에 반영하고, 급수가 오르면(또는 범위 끝이라 못 오르면) 한 줄로 알린다.
 *
 * ⚠️ **결과가 난 뒤에 돈다**([isResultKnown]) — 「끝났다」는 표시는 계가보다 먼저 켜지고, 기록은 결과가 나야 붙는다
 * (`ScoreRecordRemeasureEffect`가 같은 자리에서 한 번 놓쳤다). 기록 붙이기는 같은 프레임의 다른 효과가 하므로 잠깐 다시 찾는다.
 * 오른 급수는 **다음 대국을 시작할 때** 좌석에 옮겨 적는다(`withCustomRankForNextGame`) — 끝난 판의 화면은 둔 급수 그대로다.
 */
@Composable
internal fun CustomGameRecordedEffect(
    custom: CustomGameUiState,
    isGameEnded: Boolean,
    isResultKnown: Boolean,
    sessionGeneration: Long,
    gameState: GameState,
    collection: BotCollectionState,
    subscriptionActive: Boolean,
) {
    val context = LocalContext.current
    val language = LocalUiStrings.current.language
    val latestCustom by rememberUpdatedState(custom)
    val latestCollection by rememberUpdatedState(collection)
    val latestSubscription by rememberUpdatedState(subscriptionActive)
    LaunchedEffect(isGameEnded, isResultKnown, sessionGeneration, gameState.moves.size) {
        if (!isGameEnded || !isResultKnown) return@LaunchedEffect
        var adjustment: CustomGameAdjustment? = null
        repeat(RecordedGameLookupTries) { attempt ->
            if (adjustment != null) return@repeat
            if (attempt > 0) delay(RecordedGameLookupRetryMillis)
            adjustment = latestCustom.countRecordedGame(gameState, latestCollection, latestSubscription)
        }
        val message = adjustment?.announcementFor(language) ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}

private fun CustomGameAdjustment.announcementFor(language: UiLanguage): String? =
    promotedTo?.let { rank -> customGamePromotedFor(language, rank) }
        ?: blocked?.let { block -> customGamePromotionBlockedFor(language, block) }

/**
 * 상대의 급수를 고르는 창 — 한 칸씩 올리고 내린다. 고를 수 있는 가장 센 급수([strongestSelectable])에서 멈추고,
 * 그 끝이 9단이 아니면 무엇을 하면 더 열리는지 말한다.
 */
@Composable
internal fun CustomRankDialog(
    initialRank: KgsRank,
    strongestSelectable: KgsRank,
    initialAutoAdjust: Boolean,
    onConfirm: (KgsRank, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalUiStrings.current
    val language = strings.language
    var rank by remember { mutableStateOf(minOf(initialRank, strongestSelectable)) }
    var autoAdjust by remember { mutableStateOf(initialAutoAdjust) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(customRankDialogTitleFor(language), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space10)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = { rank = rank.weakerBy(1) },
                        enabled = rank > KgsRank.Weakest,
                        modifier = Modifier.testTag(CustomRankWeakerTag),
                    ) { Text(customRankWeakerFor(language)) }
                    Text(
                        text = kgsRankLabelFor(language, rank),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag(CustomRankValueTag),
                    )
                    FilledTonalButton(
                        onClick = { rank = minOf(rank.strongerBy(1), strongestSelectable) },
                        enabled = rank < strongestSelectable,
                        modifier = Modifier.testTag(CustomRankStrongerTag),
                    ) { Text(customRankStrongerFor(language)) }
                }
                if (rank >= strongestSelectable && strongestSelectable < KgsRank.Strongest) {
                    Text(
                        text = customRankLockedNoteFor(language, strongestSelectable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Text(
                    text = customRankOfficialNoteFor(language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = customGameAutoAdjustLabelFor(language),
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(checked = autoAdjust, onCheckedChange = { autoAdjust = it })
                }
                Text(
                    text = customGameAutoAdjustDescriptionFor(language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(rank, autoAdjust) }) { Text(strings.confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

internal const val CustomRankWeakerTag = "custom-rank-weaker"
internal const val CustomRankStrongerTag = "custom-rank-stronger"
internal const val CustomRankValueTag = "custom-rank-value"

private const val RecordedGameLookupTries = 4
private const val RecordedGameLookupRetryMillis = 400L
