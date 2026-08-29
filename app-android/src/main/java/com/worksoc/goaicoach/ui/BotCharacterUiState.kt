package com.worksoc.goaicoach.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.worksoc.goaicoach.application.botcharacter.BotUnlockSource
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.botcharacter.BotCharacter
import com.worksoc.goaicoach.application.botcharacter.runBotCharacterShardGrant
import com.worksoc.goaicoach.application.premium.AdRewardFailureReason
import com.worksoc.goaicoach.application.premium.AdRewardOutcome
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.botcharacter.BotCollectionStorePort
import com.worksoc.goaicoach.persistence.BotCollectionStore

/**
 * 화면 트리 전역에서 읽는 봇 캐릭터 수집 상태([LocalPremiumUiState]·[LocalConsumableUiState]와
 * 같은 방식으로 공급된다).
 *
 * #8이 도메인과 저장소만 만들고 **배선을 일부러 남겨 뒀던 자리**다("진입점 배선은 #10의 몫").
 * 여기서 저장소를 붙여 픽커(#10)가 "무엇을 갖고 있는가"를 읽을 수 있게 한다.
 */
internal data class BotCharacterUiState(
    val collection: BotCollectionState = BotCollectionState(),
    /**
     * 광고를 한 번 보여주고 조각 1개를 적립한다(#11). 시청에 실패하면 상태를 바꾸지 않고
     * 결과만 돌려주므로, 호출부가 사유를 안내할 수 있다([activateAdGrant]와 같은 계약).
     */
    val watchAdForShard: suspend (BotCharacter) -> AdRewardOutcome = {
        AdRewardOutcome.NotRewarded(AdRewardFailureReason.Unavailable)
    },
) {
    /** 지금 이 캐릭터로 대국할 수 있는가. 기본 제공은 획득 기록 없이도 통과한다(#16). */
    fun isAvailable(character: BotCharacter): Boolean = collection.isAvailable(character)

    /** 이 캐릭터에 지금까지 모인 조각 수(#11). */
    fun shardsFor(character: BotCharacter): Int = collection.shardsFor(character.id)
}

internal val LocalBotCharacterUiState = staticCompositionLocalOf { BotCharacterUiState() }

/**
 * [BotCharacterUiState]의 배선 본체. `GoCoachApp.kt`는 라인/상태 훅 예산이 빠듯해
 * ([buildPremiumUiState]·[buildConsumableUiState]와 같은 이유) 저장소 생성과 상태 보유를
 * 전부 여기로 뺀다.
 *
 * 쓰기 경로는 광고 조각 적립 하나뿐이다(#11) — 출석 보상은 `runBotCharacterUnlock`이 자기 경로로
 * 저장하고, 구매(#18)가 붙으면 그때 세 번째 경로가 생긴다.
 */
@Composable
internal fun buildBotCharacterUiState(context: Context): BotCharacterUiState {
    val store: BotCollectionStorePort = remember(context) { BotCollectionStore(context) }
    var collection by remember(store) { mutableStateOf(store.load()) }
    return BotCharacterUiState(
        collection = collection,
        watchAdForShard = { character ->
            val outcome = showRewardedAdOnce(context)
            // 시청 성공일 때만 적립한다 — 광고를 끝까지 안 봤는데 조각이 쌓이면 안 된다.
            // 적립 자체는 5계층 순수 함수가 하고(read-modify-write), 여기서는 결과만 반영한다.
            if (outcome is AdRewardOutcome.RewardEarned) {
                runBotCharacterShardGrant(character, store)?.let { grant -> collection = grant.state }
            }
            outcome
        },
    )
}

/**
 * 대국 상대(= AI 레벨) 선택 픽커(백로그 #10, 킥오프 플랜 7.1절).
 *
 * **캐릭터를 고르는 행위 자체가 곧 AI 레벨 선정이다** — 그래서 이 다이얼로그가 기존 난이도
 * 드롭다운을 대체한다. 한 줄에 이름과 티어명을 함께 적는 이유는 #9의 확정 사항이다: 이름만으로는
 * 어느 쪽이 센지 모호해 난이도 선택이라는 사실이 드러나지 않는다.
 *
 * **잠긴 캐릭터도 목록에서 숨기지 않는다.** 획득 경로가 셋으로 갈리므로(출석 / 광고 조각 / 유료)
 * 각각의 사유를 그대로 보여주는 것이 곧 "무엇을 하면 열리는가"의 안내가 된다 — 숨기면 사용자는
 * 그 캐릭터의 존재도, 얻는 방법도 모른다.
 *
 * **광고 조각 캐릭터는 여기서 바로 열 수 있다(#11)** — 줄을 탭하면 광고가 뜨고, 다 보면 조각이
 * 1개 쌓인다. 필요 수를 채우는 순간 영구 획득으로 넘어간다. 유료 캐릭터(#18)는 아직 진입점이
 * 없어 안내까지가 전부다.
 */
@Composable
internal fun BotCharacterPickerDialog(
    selected: BotCharacter?,
    onSelect: (BotCharacter) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalUiStrings.current
    val bots = LocalBotCharacterUiState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 광고가 뜨는 동안 같은 줄을 여러 번 눌러 조각이 중복 적립되지 않게 잠근다.
    var adInProgress by remember { mutableStateOf(false) }

    AlertDialog(
        // 광고 Activity 전환 중의 dismiss 요청을 막아 본 것이다. ⚠️ 이것만으로는 부족했다 —
        // 광고를 보고 돌아오면 픽커는 여전히 닫혀 있다(원인 미상, PlayerSetupPanel의 showPicker
        // 주석 참고). 무해하므로 남겨 두지만, 이 줄이 그 문제를 해결한다고 읽지 말 것.
        onDismissRequest = { if (!adInProgress) onDismiss() },
        title = { Text(strings.botPickerTitle) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                BotCharacterCatalog.fastBeginnerRoster.forEach { character ->
                    val available = bots.isAvailable(character)
                    val shardSource = character.unlockSource as? BotUnlockSource.AdShards
                    BotCharacterRow(
                        character = character,
                        isSelected = character.id == selected?.id,
                        isAvailable = available,
                        shards = bots.shardsFor(character),
                        // 잠겼어도 조각 경로면 탭할 수 있다 — 그 탭이 곧 광고 시청이다.
                        canWatchAd = !available && shardSource != null && !adInProgress,
                        onClick = {
                            when {
                                available -> {
                                    onSelect(character)
                                    onDismiss()
                                }
                                shardSource != null && !adInProgress -> scope.launch {
                                    adInProgress = true
                                    val outcome = bots.watchAdForShard(character)
                                    adInProgress = false
                                    val message = when {
                                        outcome !is AdRewardOutcome.RewardEarned ->
                                            strings.premiumAdGrantFailedMessage
                                        // 적립 직후 상태는 다음 재구성에 반영되므로 여기서 직접 센다.
                                        bots.shardsFor(character) + 1 >= shardSource.required ->
                                            strings.botUnlockedToast(character)
                                        else -> strings.botShardEarnedToast(
                                            character,
                                            bots.shardsFor(character) + 1,
                                            shardSource.required,
                                        )
                                    }
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.botPickerCloseAction) } },
    )
}

/** 픽커 한 줄. 잠긴 캐릭터는 흐리게 두고 탭을 막되, 획득 방법은 그대로 보여준다. */
@Composable
private fun BotCharacterRow(
    character: BotCharacter,
    isSelected: Boolean,
    isAvailable: Boolean,
    shards: Int,
    canWatchAd: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalUiStrings.current
    val rowModifier = Modifier
        .fillMaxWidth()
        .let { if (isAvailable || canWatchAd) it.clickable(onClick = onClick) else it }
        // 잠긴 줄은 흐리게 두되, 광고로 열 수 있는 줄은 "누를 수 있다"는 신호를 남긴다.
        .let { if (isAvailable) it else it.alpha(if (canWatchAd) 0.8f else 0.5f) }
        .padding(vertical = 8.dp)

    Column(modifier = rowModifier) {
        Text(
            text = strings.botCharacterLabel(character),
            style = MaterialTheme.typography.titleSmall,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            text = character.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 잠긴 캐릭터만 획득 방법을 덧붙인다 — 기본 제공은 안내할 것이 없다.
        if (!isAvailable) {
            strings.botUnlockHint(character.unlockSource, shards)?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
