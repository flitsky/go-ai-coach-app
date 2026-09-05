package com.worksoc.goaicoach.ui

import android.content.Context
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
 * ## 왜 "보이는가"와 "값을 치렀는가"를 따로 두는가 (백로그 #44, 2026-08-30)
 *
 * 처음에는 둘이 한 맵에 얹혀 있었다. 끄면 그 항목을 통째로 지웠는데, 그 순간 **"이 수순에 이미
 * 표를 냈다"는 사실까지 같이 지워졌다.** 그래서 껐다가 다시 켜면 같은 수순인데도 한 장이 또
 * 나갔다(사용자 제보: 켜기 → 끄기 → 켜기에서 2장 소모).
 *
 * 지금은 [paidAtMove]가 **지불 원장**이고 [oneShotFeatures]는 그중 **지금 보이는 것**만이다.
 * 끄는 동작은 원장을 건드리지 않으므로, 다시 켜는 탭은 [isPaidForMove]에 걸려 무료로 통과한다.
 * 원장은 다음 수가 놓일 때 [expireOneShotsAtMove]가 비운다 — 표 한 장의 유효 범위가 한 수라는
 * 규칙은 그대로다.
 *
 * ⚠️ 둘 다 메모리에만 있다(저장하지 않는다). 단발성 표시가 떠 있는 중에 앱이 죽었다 살아나면
 * 표시도 같이 사라지므로 어긋나지 않고, 저장할 만큼 오래 사는 상태도 아니다.
 */
internal data class ConsumableUiState(
    val inventory: ConsumableInventory = ConsumableInventory(),
    /** 지금 화면에 1회권으로 떠 있는 기능 = [paidAtMove] 중 사용자가 끄지 않은 것. */
    val oneShotFeatures: Set<FeatureId> = emptySet(),
    /**
     * 이 수순에 표를 낸 기능 → 낼 때의 수순. **표시를 껐다고 지우지 않는다**(#44) — 지우면
     * 다시 켤 때 또 차감된다. 다음 수로 넘어갈 때만 [expireOneShotsAtMove]가 비운다.
     */
    val paidAtMove: Map<FeatureId, Int> = emptyMap(),
    val spend: (ConsumableItem) -> ConsumableSpendDecision = { ConsumableSpendDecision.OutOfStock },
    val markOneShot: (FeatureId, Int) -> Unit = { _, _ -> },
    val clearOneShot: (FeatureId) -> Unit = {},
    /** 이번 수에서 만료된 기능들을 돌려준다 — 만료되지 않은 다른 기능까지 같이 끄지 않기 위함이다. */
    val expireOneShotsAtMove: (Int) -> Set<FeatureId> = { emptySet() },
    /**
     * 저장소를 다시 읽어 재고 표시를 맞춘다.
     *
     * ⚠️ **이 상태는 [spend]로 나가는 것만 알고 들어오는 것은 모른다.** 출석 보상은
     * `runAttendanceRewardGrant`가 저장소에 **직접** 쓰므로, 이 통로가 없으면 지급 직후 화면이
     * 옛 재고를 계속 보여준다 — 실제로 마이 페이지에서 "1일차 도장은 찍혔는데 1회권은 0개"라는
     * 모순으로 드러났다(#56). 재고를 저장소에 직접 쓰는 경로를 새로 만들면 여기도 함께 부를 것.
     */
    val refresh: () -> Unit = {},
) {
    fun countOf(item: ConsumableItem): Int = inventory.countOf(item.id)

    /** 이 기능을 1회권으로 지금 열 수 있는가(= 대응 소모품이 있고 재고가 남아 있는가). */
    fun ticketFor(featureId: FeatureId): ConsumableItem? =
        ConsumableCatalog.forFeature(featureId)?.takeIf { item -> inventory.has(item.id) }

    /** 지금 화면에 떠 있는 이 기능의 표시가 1회권으로 켠 것인가. */
    fun isOneShotActive(featureId: FeatureId): Boolean = featureId in oneShotFeatures

    /**
     * 이 수순에서 이 기능에 **이미 표를 냈는가**(#44). 그렇다면 껐다가 다시 켜는 탭은 무료다.
     *
     * 현재 수순을 인자로 받아 비교하는 것이 핵심이다 — 원장에 남아 있어도 수순이 다르면
     * `false`다. 만료 처리([expireOneShotsAtMove])가 `LaunchedEffect`라 한 프레임 늦게 도는데,
     * 그 창에서 남아 있는 옛 항목이 무료 통과로 새지 않게 막아 준다.
     */
    fun isPaidForMove(featureId: FeatureId, moveCount: Int): Boolean = paidAtMove[featureId] == moveCount
}

/**
 * 1회권 사용 원장의 **순수 전이 규칙**(백로그 #44). [buildConsumableUiState]가 이 값을 상태로
 * 하나 들고 있을 뿐, 판단은 전부 여기에 있다.
 *
 * ⚠️ **컴포저블에서 뺀 이유가 곧 이 타입의 존재 이유다.** 이 결함은 "켜기 → 끄기 → 켜기"라는
 * **순서**에서만 드러나 단발 검사로는 잡히지 않는데, 전이가 `@Composable` 안에 있으면 JVM
 * 단위 테스트로 그 순서를 재현할 수 없다. 여기로 빼 두면 순서를 그대로 적어 고정할 수 있다.
 *
 * @property paidAtMove 표를 낸 기능 → 낼 때의 수순. 표시를 꺼도 남는다.
 * @property hidden 원장에는 있지만 사용자가 직접 끈 기능.
 */
internal data class OneShotLedger(
    val paidAtMove: Map<FeatureId, Int> = emptyMap(),
    val hidden: Set<FeatureId> = emptySet(),
) {
    /** 지금 화면에 떠 있어야 할 것 = 값을 치른 것 중 끄지 않은 것. */
    val visible: Set<FeatureId> get() = paidAtMove.keys - hidden

    /** 켠다 — 원장에 적고, 껐던 것이면 다시 보이게 한다. */
    fun mark(featureId: FeatureId, moveCount: Int): OneShotLedger =
        copy(paidAtMove = paidAtMove + (featureId to moveCount), hidden = hidden - featureId)

    /** 끈다 — **표시만**. 원장을 지우면 다시 켤 때 또 차감된다(이 항목의 원인 그 자체다). */
    fun hide(featureId: FeatureId): OneShotLedger = copy(hidden = hidden + featureId)

    /**
     * 수순이 넘어갔을 때 정리한다. 표 한 장의 유효 범위는 한 수다.
     *
     * @return 정리된 원장과, **이번에 만료된 기능들**. 뒤엣것을 호출부가 받아야 만료되지 않은
     *   다른 기능까지 같이 끄지 않는다.
     */
    fun expireAtMove(moveCount: Int): Pair<OneShotLedger, Set<FeatureId>> {
        val expired = paidAtMove.filterValues { paidAt -> paidAt != moveCount }.keys
        if (expired.isEmpty()) return this to emptySet()
        return OneShotLedger(paidAtMove - expired, hidden - expired) to expired
    }
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
    // 전이 규칙은 [OneShotLedger]에 있다 — 여기서는 그 값을 상태로 들고만 있는다(#44).
    var ledger by remember { mutableStateOf(OneShotLedger()) }

    return ConsumableUiState(
        inventory = inventory,
        oneShotFeatures = ledger.visible,
        paidAtMove = ledger.paidAtMove,
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
        markOneShot = { featureId, moveCount -> ledger = ledger.mark(featureId, moveCount) },
        clearOneShot = { featureId -> ledger = ledger.hide(featureId) },
        expireOneShotsAtMove = { moveCount ->
            val (next, expired) = ledger.expireAtMove(moveCount)
            if (expired.isNotEmpty()) ledger = next
            expired
        },
        refresh = { inventory = store.load() },
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

