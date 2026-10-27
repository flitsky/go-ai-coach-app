package com.worksoc.goaicoach.ios

import com.worksoc.goaicoach.application.premium.port.PremiumProductInfo
import com.worksoc.goaicoach.application.premium.port.PurchaseFailureReason
import com.worksoc.goaicoach.application.premium.port.PurchaseOutcome
import com.worksoc.goaicoach.application.premium.port.billingPeriodFromIso8601
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * App Store 구독(백로그 #296) — Swift `AppStoreSubscriptionSource`가 StoreKit 2로 구현해 [IosAppRoot]에 넘긴다(StoreKit 2는 Swift 전용이라
 * Kotlin이 직접 부르지 못한다 — [IosEngineModelSource]와 같은 이유). 안드로이드 `AndroidBillingClient`(`PurchasePort`)의 자리다.
 * 답은 리스너로 — 어느 스레드에서 와도 되고, 한 번만 온다.
 */
interface IosStoreSource {
    /** 구매 시트를 띄운다. */
    fun purchase(productId: String, listener: IosStorePurchaseListener)

    /** 지금 그 구독을 갖고 있는지 — StoreKit의 현재 권리(오프라인이면 기기에 남은 서명된 거래)로 답한다. */
    fun currentEntitlement(productId: String, listener: IosStoreEntitlementListener)

    /** 사용자가 누른 「구매 복원」 — App Store와 맞춘 뒤(`AppStore.sync`, Apple 계정 암호를 물을 수 있다) [currentEntitlement]처럼 답한다. */
    fun restore(productId: String, listener: IosStoreEntitlementListener)

    /** 구독 고지에 쓸 가격 · 주기 — 아무것도 사지 않는다. */
    fun productInfo(productId: String, listener: IosStoreProductListener)

    /** App Store의 구독 관리 시트(해지가 실제로 일어나는 자리). */
    fun showManageSubscriptions()
}

interface IosStorePurchaseListener {
    fun onPurchased()

    fun onCancelled()

    /** 승인 대기(「구입 요청」 · 결제 수단 확인) — 다음 복원 조회에서 다시 본다. */
    fun onPending()

    fun onProductUnavailable()

    /** App Store에 닿지 못했다(네트워크 등). */
    fun onStoreUnavailable(detail: String)

    fun onFailed(detail: String)
}

interface IosStoreEntitlementListener {
    fun onOwned()

    /** App Store(또는 기기의 서명된 거래)가 「갖고 있지 않다」고 답했다 — 권위 있는 미소유([PurchaseFailureReason.NotFound]). */
    fun onNotOwned()

    /** 확인하지 못했다 — 미소유와 합치지 않는다(#158의 강등이 이 구분에 걸려 있다). */
    fun onUnknown(detail: String)
}

interface IosStoreProductListener {
    /** [periodUnit]은 StoreKit의 단위 이름(`day` · `week` · `month` · `year`), [periodValue]는 그 개수. */
    fun onProduct(displayPrice: String, periodUnit: String, periodValue: Int)

    fun onUnavailable()
}

/**
 * iOS 구독 상품 ID(스레드가 정함 — 안드로이드 상품 `premium_basic` · 기본 요금제 `monthly`를 이어 붙였다). App Store Connect의 구독 상품과
 * 시뮬레이터 시험 파일(`app-ios/StoreKit/GoCoach.storekit`)이 같은 값이어야 한다(`IosStoreContractTest`).
 */
internal const val IosPremiumProductId = "premium_basic_monthly"

internal suspend fun IosStoreSource.purchaseOutcome(productId: String): PurchaseOutcome = suspendCancellableCoroutine { continuation ->
    fun finish(outcome: PurchaseOutcome) {
        if (continuation.isActive) continuation.resume(outcome)
    }
    purchase(
        productId,
        object : IosStorePurchaseListener {
            override fun onPurchased() = finish(PurchaseOutcome.Purchased)

            override fun onCancelled() = finish(PurchaseOutcome.NotPurchased(PurchaseFailureReason.UserCancelled))

            override fun onPending() = finish(PurchaseOutcome.NotPurchased(PurchaseFailureReason.Pending))

            override fun onProductUnavailable() = finish(PurchaseOutcome.NotPurchased(PurchaseFailureReason.ProductUnavailable))

            override fun onStoreUnavailable(detail: String) =
                finish(PurchaseOutcome.NotPurchased(PurchaseFailureReason.BillingUnavailable, detail))

            override fun onFailed(detail: String) = finish(PurchaseOutcome.NotPurchased(PurchaseFailureReason.PurchaseError, detail))
        },
    )
}

internal suspend fun IosStoreSource.entitlementOutcome(productId: String): PurchaseOutcome =
    ownershipOutcome { listener -> currentEntitlement(productId, listener) }

internal suspend fun IosStoreSource.restoreOutcome(productId: String): PurchaseOutcome =
    ownershipOutcome { listener -> restore(productId, listener) }

private suspend fun ownershipOutcome(ask: (IosStoreEntitlementListener) -> Unit): PurchaseOutcome = suspendCancellableCoroutine { continuation ->
    fun finish(outcome: PurchaseOutcome) {
        if (continuation.isActive) continuation.resume(outcome)
    }
    ask(
        object : IosStoreEntitlementListener {
            override fun onOwned() = finish(PurchaseOutcome.Purchased)

            override fun onNotOwned() = finish(PurchaseOutcome.NotPurchased(PurchaseFailureReason.NotFound))

            override fun onUnknown(detail: String) = finish(PurchaseOutcome.NotPurchased(PurchaseFailureReason.OwnershipUnknown, detail))
        },
    )
}

internal suspend fun IosStoreSource.premiumProductInfo(productId: String): PremiumProductInfo? = suspendCancellableCoroutine { continuation ->
    fun finish(info: PremiumProductInfo?) {
        if (continuation.isActive) continuation.resume(info)
    }
    productInfo(
        productId,
        object : IosStoreProductListener {
            // 주기는 안드로이드와 같은 표로 옮긴다 — StoreKit의 (단위, 개수)를 ISO-8601 기간으로 적어 [billingPeriodFromIso8601]에 맡긴다.
            override fun onProduct(displayPrice: String, periodUnit: String, periodValue: Int) {
                val letter = when (periodUnit) {
                    "day" -> "D"
                    "week" -> "W"
                    "month" -> "M"
                    "year" -> "Y"
                    else -> null
                }
                val period = billingPeriodFromIso8601(letter?.let { "P$periodValue$it" })
                finish(PremiumProductInfo(formattedPrice = displayPrice, period = period))
            }

            override fun onUnavailable() = finish(null)
        },
    )
}
