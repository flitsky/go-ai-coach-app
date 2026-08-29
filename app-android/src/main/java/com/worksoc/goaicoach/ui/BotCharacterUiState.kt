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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.worksoc.goaicoach.application.botcharacter.BotUnlockSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.worksoc.goaicoach.application.botcharacter.BotCharacter
import com.worksoc.goaicoach.application.botcharacter.runBotCharacterShardGrant
import com.worksoc.goaicoach.application.botcharacter.runBotCharacterUnlock
import com.worksoc.goaicoach.application.premium.AdRewardFailureReason
import com.worksoc.goaicoach.application.premium.AdRewardOutcome
import com.worksoc.goaicoach.application.premium.PurchaseFailureReason
import com.worksoc.goaicoach.application.premium.PurchaseOutcome
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
    /**
     * 저장소를 다시 읽어 화면이 든 사본을 맞춘다.
     *
     * 이 상태는 저장소를 **한 번만** 읽고 그 뒤로는 자기 쓰기 경로(광고 조각)로만 갱신되는데,
     * 컬렉션에 쓰는 경로가 그것 하나가 아니다 — 출석 보상이 `BotCollectionStore`에 직접 쓴다
     * (`runAttendanceRewardGrant`). 그래서 출석으로 조각을 받거나 캐릭터를 열어도 픽커는 앱을
     * 다시 켤 때까지 옛 숫자를 보여줬다(2026-08-29 실기 확인: 조각을 받은 직후에도 3/10).
     */
    val refresh: () -> Unit = {},
    /**
     * 캐릭터 한 종을 구매하고 성사되면 영구 소유로 기록한다(#18). [watchAdForShard]와 같은
     * 계약이다 — 실패하면 상태를 바꾸지 않고 결과만 돌려주므로 호출부가 사유를 안내할 수 있다.
     */
    val purchase: suspend (BotCharacter) -> PurchaseOutcome = {
        PurchaseOutcome.NotPurchased(PurchaseFailureReason.Unavailable)
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

    // 재설치로 로컬 컬렉션이 사라져도 **돈 주고 산 캐릭터는 되찾아야 한다**(#18). 프리미엄 쪽
    // `PremiumPurchaseRestoreEffect`와 같은 취지이고, 여기 두는 이유는 `GoCoachApp.kt`의 라인
    // 예산을 쓰지 않기 위해서다 — 이 빌더가 이미 저장소와 상태를 들고 있어 자리가 맞다.
    //
    // 플래그가 꺼져 있으면 아예 조회하지 않는다: 상품이 등록되기 전에는 매 실행마다 실패할
    // 조회를 반복할 뿐이다.
    LaunchedEffect(store) {
        if (!FeatureFlags.isBotCharacterPurchaseEnabled) return@LaunchedEffect
        val purchasable = BotCharacterCatalog.all
            .firstOrNull { candidate -> candidate.unlockSource is BotUnlockSource.Purchase }
            ?: return@LaunchedEffect
        if (store.load().isClaimed(purchasable.id)) return@LaunchedEffect
        if (performBotCharacterPurchaseRestore(context) is PurchaseOutcome.Purchased) {
            runBotCharacterUnlock(purchasable.id, store)?.let { next -> collection = next }
        }
    }

    return BotCharacterUiState(
        collection = collection,
        refresh = { collection = store.load() },
        watchAdForShard = { character ->
            val outcome = showRewardedAdOnce(context)
            // 시청 성공일 때만 적립한다 — 광고를 끝까지 안 봤는데 조각이 쌓이면 안 된다.
            // 적립 자체는 5계층 순수 함수가 하고(read-modify-write), 여기서는 결과만 반영한다.
            if (outcome is AdRewardOutcome.RewardEarned) {
                runBotCharacterShardGrant(character, store)?.let { grant -> collection = grant.state }
            }
            outcome
        },
        purchase = { character ->
            val outcome = performBotCharacterPurchase(context)
            // 결제가 확정됐을 때만 소유로 남긴다. Pending(계좌이체 등)은 아직 아니다 — 확정되면
            // 다음 실행의 복원 조회가 잡는다.
            if (outcome is PurchaseOutcome.Purchased) {
                runBotCharacterUnlock(character.id, store)?.let { next -> collection = next }
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
    adInProgress: Boolean,
    onSelect: (BotCharacter) -> Unit,
    onWatchAd: (BotCharacter) -> Unit,
    onPurchase: (BotCharacter) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalUiStrings.current
    val bots = LocalBotCharacterUiState.current

    // 픽커를 열 때마다 저장소를 다시 읽는다 — 출석 보상처럼 이 상태를 거치지 않는 쓰기 경로가
    // 있어서, 여는 시점에 맞추지 않으면 방금 받은 조각이 반영되지 않는다.
    LaunchedEffect(Unit) { bots.refresh() }

    AlertDialog(
        onDismissRequest = onDismiss,
        // **바깥 탭으로는 닫히지 않게 한다(#20).** 조각을 모으려면 광고를 5~10번 봐야 하고 그
        // 사이 화면이 광고 Activity로 갔다 돌아오는데, 그 전환에서 흘러든 터치 하나가 "바깥
        // 탭"으로 해석되면 사용자는 자기 자리를 잃는다. 닫는 길은 닫기 버튼과 뒤로 가기 둘 다
        // 그대로 남으므로 잃는 것이 없다.
        //
        // ⚠️ `adInProgress`로 dismiss를 막는 방식은 쓰지 않는다(#20에서 실패한 ①번 시도) —
        // 광고 코루틴이 먼저 재개돼 플래그가 이미 false가 된 뒤에 요청이 도착하므로 그 가드는
        // 원리적으로 늦는다. 시간 창(700ms 유예)도 같은 이유로 틀렸다: 늦게 오는 것을 시간으로
        // 쫓아가는 대신, 애초에 그 경로를 없앤다.
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(strings.botPickerTitle) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                BotCharacterCatalog.fastBeginnerRoster.forEach { character ->
                    val available = bots.isAvailable(character)
                    val shardSource = character.unlockSource as? BotUnlockSource.AdShards
                    // 유료 캐릭터는 상품이 등록되고 플래그가 켜져야 실제로 살 수 있다(#18) —
                    // 등록 전에 버튼을 노출하면 눌러 봐야 "상품을 가져오지 못했습니다"만 본다.
                    val canPurchase = !available &&
                        character.unlockSource is BotUnlockSource.Purchase &&
                        FeatureFlags.isBotCharacterPurchaseEnabled &&
                        !adInProgress
                    BotCharacterRow(
                        character = character,
                        isSelected = character.id == selected?.id,
                        isAvailable = available,
                        shards = bots.shardsFor(character),
                        // 잠겼어도 조각 경로면 탭할 수 있다 — 그 탭이 곧 광고 시청이다.
                        canWatchAd = !available && shardSource != null && !adInProgress,
                        canPurchase = canPurchase,
                        onClick = {
                            when {
                                available -> {
                                    onSelect(character)
                                    onDismiss()
                                }
                                shardSource != null && !adInProgress -> onWatchAd(character)
                                canPurchase -> onPurchase(character)
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
    canPurchase: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalUiStrings.current
    val rowModifier = Modifier
        .fillMaxWidth()
        .let { if (isAvailable || canWatchAd || canPurchase) it.clickable(onClick = onClick) else it }
        // 잠긴 줄은 흐리게 두되, 지금 열 수 있는 줄(광고·구매)은 "누를 수 있다"는 신호를 남긴다.
        .let { if (isAvailable) it else it.alpha(if (canWatchAd || canPurchase) 0.8f else 0.5f) }
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


/**
 * 조각 광고 한 번을 실행하고 결과를 토스트로 알린다(백로그 #11·#20).
 *
 * ⚠️ **이 함수는 픽커 다이얼로그가 아니라 그것을 여는 화면 쪽에서 호출해야 한다.** 광고를 띄우면
 * 다이얼로그로 dismiss 요청이 날아와 픽커가 닫히는데(2026-08-29 계측 확인), 다이얼로그 안에서
 * 코루틴을 돌리면 그 순간 스코프까지 함께 취소돼 "끝나고 다시 열어주는" 복구조차 못 한다.
 * 패널은 그 전환에서 살아남는 것이 로그로 확인됐으므로(`panel-dispose`가 찍히지 않는다) 거기서
 * 돌린다.
 */
internal suspend fun watchAdForShardAndReport(
    character: BotCharacter,
    bots: BotCharacterUiState,
    strings: UiStrings,
    context: Context,
) {
    val required = (character.unlockSource as? BotUnlockSource.AdShards)?.required ?: return
    val before = bots.shardsFor(character)
    val outcome = bots.watchAdForShard(character)
    val message = when {
        // 프리미엄 문구를 재사용하지 않는다 — 조각을 모으던 사용자에게 "프리미엄이 활성화되지
        // 않았습니다"가 뜨던 버그를 2026-08-29에 정정했다.
        outcome is AdRewardOutcome.NotRewarded -> strings.botShardAdFailedMessage(outcome.reason)
        outcome !is AdRewardOutcome.RewardEarned -> return
        before + 1 >= required -> strings.botUnlockedToast(character)
        else -> strings.botShardEarnedToast(character, before + 1, required)
    }
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/**
 * 캐릭터 한 종을 구매하고 결과를 토스트로 알린다(백로그 #18).
 *
 * [watchAdForShardAndReport]와 같은 이유로 **픽커가 아니라 그것을 여는 화면 쪽에서** 불러야
 * 한다 — 결제 시트도 Activity 전환이라 다이얼로그 스코프가 취소된다.
 *
 * 결제가 성사됐을 때만 소유를 기록한다. 기록은 조각/출석과 **같은 경로**(`runBotCharacterUnlock`)로
 * 흘려보내, 획득 사실이 어디서 왔든 컬렉션에는 한 가지 방식으로만 쌓이게 한다.
 */
internal suspend fun purchaseBotCharacterAndReport(
    character: BotCharacter,
    bots: BotCharacterUiState,
    strings: UiStrings,
    context: Context,
) {
    val message = when (val outcome = bots.purchase(character)) {
        is PurchaseOutcome.Purchased -> strings.botPurchasedToast(character)
        is PurchaseOutcome.NotPurchased -> strings.botPurchaseFailedMessage(outcome.reason)
    }
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
