package com.worksoc.goaicoach.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableInventory
import com.worksoc.goaicoach.application.consumable.ConsumableItem
import com.worksoc.goaicoach.application.consumable.ConsumableSpendDecision
import com.worksoc.goaicoach.application.consumable.ConsumableStorePort
import com.worksoc.goaicoach.application.consumable.runConsumableSpend
import com.worksoc.goaicoach.application.premium.FeatureId
import com.worksoc.goaicoach.application.premium.PremiumState
import com.worksoc.goaicoach.application.premium.PremiumStateStorePort
import com.worksoc.goaicoach.persistence.ConsumableInventoryStore
import com.worksoc.goaicoach.persistence.PremiumStateStore

/**
 * 화면 트리 전역에서 읽는 소모품 재고 상태([LocalPremiumUiState]와 같은 방식으로 공급된다).
 *
 * **1회권은 프리미엄 토글과 동작 모델이 다르다**(2026-08-24 사용자 확정, 킥오프 플랜 4.5절):
 * - 프리미엄 사용자의 형세 보기/추천 수는 **토글**이다 — 켜 두면 수가 진행돼도 계속 갱신된다.
 * - 무료 사용자가 1회권으로 켠 것은 **단발성**이다 — 그 순간의 결과를 한 번 보여주고, 다음 수가
 *   놓이면 자동으로 꺼진다. 이게 "1회"의 단위이며, 그래서 [oneShotFeatures]로 "지금 켜져 있는
 *   이 표시가 1회권으로 켠 것인가"를 따로 추적한다.
 *
 * 단발성으로 켜진 표시를 사용자가 직접 끄는 것은 **무료**다([clearOneShot]) — 끄는 데 또 한 장을
 * 받으면 "1회"가 반 번이 되기 때문이다.
 *
 * ⚠️ [oneShotFeatures]는 메모리에만 있다(저장하지 않는다). 단발성 표시가 떠 있는 중에 앱이
 * 죽었다 살아나면 표시도 같이 사라지므로 어긋나지 않고, 저장할 만큼 오래 사는 상태도 아니다.
 */
internal data class ConsumableUiState(
    val inventory: ConsumableInventory = ConsumableInventory(),
    val oneShotFeatures: Set<FeatureId> = emptySet(),
    val spend: (ConsumableItem) -> ConsumableSpendDecision = { ConsumableSpendDecision.OutOfStock },
    val markOneShot: (FeatureId, Int) -> Unit = { _, _ -> },
    val clearOneShot: (FeatureId) -> Unit = {},
    /** 이번 수에서 만료된 기능들을 돌려준다 — 만료되지 않은 다른 기능까지 같이 끄지 않기 위함이다. */
    val expireOneShotsAtMove: (Int) -> Set<FeatureId> = { emptySet() },
) {
    fun countOf(item: ConsumableItem): Int = inventory.countOf(item.id)

    /** 이 기능을 1회권으로 지금 열 수 있는가(= 대응 소모품이 있고 재고가 남아 있는가). */
    fun ticketFor(featureId: FeatureId): ConsumableItem? =
        ConsumableCatalog.forFeature(featureId)?.takeIf { item -> inventory.has(item.id) }

    /** 지금 화면에 떠 있는 이 기능의 표시가 1회권으로 켠 것인가. */
    fun isOneShotActive(featureId: FeatureId): Boolean = featureId in oneShotFeatures
}

internal val LocalConsumableUiState = staticCompositionLocalOf { ConsumableUiState() }

/**
 * [ConsumableUiState]의 배선 본체. `GoCoachApp.kt`는 라인/상태 훅 예산이 빠듯해
 * ([buildPremiumUiState]와 같은 이유) 저장소 생성과 상태 보유를 전부 여기로 뺐다.
 *
 * [onPremiumChanged]는 '광고 스킵권'을 썼을 때 켜진 프리미엄 상태를 셸에 되돌려 주는 통로다 —
 * 그 표는 기능 하나가 아니라 프리미엄 자체를 켜기 때문에(`ConsumableEffect.PremiumGrant`)
 * 재고뿐 아니라 프리미엄 상태도 같이 바뀐다.
 */
@Composable
internal fun buildConsumableUiState(
    context: Context,
    onPremiumChanged: (PremiumState) -> Unit,
): ConsumableUiState {
    val store: ConsumableStorePort = remember(context) { ConsumableInventoryStore(context) }
    val premiumStore: PremiumStateStorePort = remember(context) { PremiumStateStore(context) }
    var inventory by remember(store) { mutableStateOf(store.load()) }
    var oneShots by remember { mutableStateOf(emptyMap<FeatureId, Int>()) }

    return ConsumableUiState(
        inventory = inventory,
        oneShotFeatures = oneShots.keys,
        spend = { item ->
            // 차감 여부 판정은 6계층(decideConsumableSpend)이 한다 — 프리미엄이 이미 활성이면
            // 재고를 건드리지 않고 통과시키는 4.5절 우선순위 규칙도 그쪽에 들어 있다.
            val decision = runConsumableSpend(item, store, premiumStore, System.currentTimeMillis())
            if (decision is ConsumableSpendDecision.Spent) {
                inventory = decision.inventory
                decision.premiumState?.let(onPremiumChanged)
            }
            decision
        },
        markOneShot = { featureId, moveCount -> oneShots = oneShots + (featureId to moveCount) },
        clearOneShot = { featureId -> oneShots = oneShots - featureId },
        expireOneShotsAtMove = { moveCount ->
            val expired = oneShots.filterValues { activatedAt -> activatedAt != moveCount }.keys
            if (expired.isNotEmpty()) oneShots = oneShots - expired
            expired
        },
    )
}


/**
 * 1회권으로 켠 단발성 표시를 다음 수에서 자동으로 끈다. `GoCoachApp.kt`의 상태 훅 예산이
 * 꽉 차 있어(46/46) 셸에 `LaunchedEffect`를 새로 두지 않고 이 컴포저블로 감쌌다 — 셸에서는
 * 호출 한 줄만 보인다([buildConsumableUiState]와 같은 이유).
 */
@Composable
internal fun OneShotAnalysisAutoClear(
    state: ConsumableUiState,
    moveCount: Int,
    hideTopMoves: () -> Unit,
    hideEval: () -> Unit,
) {
    LaunchedEffect(moveCount) {
        val expired = state.expireOneShotsAtMove(moveCount)
        if (FeatureId.TopMoves in expired) hideTopMoves()
        if (FeatureId.Eval in expired) hideEval()
    }
}

/**
 * 소모품 재고 상시 표시(백로그 #17). 대국 화면의 **액션 버튼 바로 위**에 둔다.
 *
 * 4.5절이 "위치는 상단 등 눈에 띄는 곳"이라 했지만 그보다 **사용처에 붙이는 쪽**을 택했다 —
 * 같은 항목이 "가장 중요한 요구사항"으로 지목한 것이 위치가 아니라 **"차감되는 것이 보이는 것"**
 * 이기 때문이다. 방금 누른 버튼 바로 위에서 숫자가 줄면 그 요구가 가장 직접적으로 충족된다.
 * 화면 상단으로 옮기고 싶으면 `GamePlaySection`의 호출 한 줄을 옮기면 된다.
 *
 * 재고가 0인 종류는 줄에서 빼고, 셋 다 0이면 줄 자체를 그리지 않는다 — 아직 아무 1회권도 받지
 * 못한 사용자에게 빈 선반을 보여줄 이유가 없다(출석 2일차 전에는 정상 상태다).
 */
@Composable
internal fun ConsumableInventoryBar(modifier: Modifier = Modifier) {
    val state = LocalConsumableUiState.current
    val strings = LocalUiStrings.current
    val owned = ConsumableCatalog.all.filter { item -> state.countOf(item) > 0 }
    if (owned.isEmpty()) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ActionButtonShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            owned.forEach { item ->
                ConsumableCountCell(
                    label = strings.consumableShortName(item),
                    count = state.countOf(item),
                )
            }
        }
    }
}

/**
 * 재고 한 종류. **줄어드는 순간을 눈에 띄게** 하는 것이 이 셀의 존재 이유다(#17의 핵심 요구) —
 * 숫자가 조용히 바뀌면 사용자는 재화가 나갔다는 걸 모른 채 지나간다. 차감이 감지되면 잠시
 * 강조색으로 바뀌었다가 원래 색으로 돌아온다.
 *
 * 늘어날 때(출석 지급)는 강조하지 않는다 — 그쪽은 Claim 팝업이 이미 무엇을 받았는지 보여준다.
 */
@Composable
private fun ConsumableCountCell(label: String, count: Int) {
    var previous by remember { mutableStateOf(count) }
    var justSpent by remember { mutableStateOf(false) }
    LaunchedEffect(count) {
        if (count < previous) {
            justSpent = true
            delay(SpendHighlightMillis)
            justSpent = false
        }
        previous = count
    }
    val color by animateColorAsState(
        targetValue = if (justSpent) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "consumableSpendHighlight",
    )

    Text(
        text = "$label $count",
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

/** 차감 강조가 유지되는 시간. 토스트(짧게 ~2초)보다 짧게 두어 둘이 겹쳐 보이지 않게 한다. */
private const val SpendHighlightMillis: Long = 900L
