package com.worksoc.goaicoach.ui

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.worksoc.goaicoach.application.premium.PurchaseFailureReason
import com.worksoc.goaicoach.application.premium.PurchaseOutcome
import com.worksoc.goaicoach.application.premium.PurchasePort
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 4계층(External Integration) Extended API 본체 — [PurchasePort]를 실제 Google Play Billing
 * SDK(9.1.0)에 연결하는 어댑터. `AndroidRewardedInterstitialAdClient`와 동일하게 매 호출마다
 * 새로 만들어 쓰는 얇은 래퍼다 — 구매 버튼 1개(명시적 구매) + 앱 시작 시 복원 조회 1번뿐인 이
 * 앱의 사용 패턴상, 연결을 계속 유지하는 대신 매번 연결→작업→종료를 짧게 반복하는 편이
 * `GoCoachApp.kt`에 별도 `remember`로 연결을 들고 있을 필요를 없애준다(상태 훅 예산이 이미
 * 47/47로 여유가 없다 — premium-mode/README.md Step 4 참고). `enableAutoServiceReconnection()`은
 * 이런 단발성 연결에는 의미가 없어 의도적으로 쓰지 않는다.
 *
 * 비소모성(non-consumable/one-time) 상품이라 `consumeAsync`는 쓰지 않는다 — 구매 완료 후
 * [AcknowledgePurchaseParams]로 확인(acknowledge)만 하면 된다(3일 이내 확인하지 않으면 Play가
 * 자동 환불한다).
 */
internal class AndroidBillingClient(
    private val activity: Activity,
    private val productId: String,
) : PurchasePort {
    // launchBillingFlow의 결과는 동기 반환값이 아니라 PurchasesUpdatedListener 콜백으로 온다 —
    // 이 리스너는 BillingClient 생성 시 한 번만 등록되므로, 현재 진행 중인 구매 요청의
    // continuation을 여기 담아두고 콜백에서 그것만 재개한다.
    private var pendingPurchaseContinuation: CancellableContinuation<Pair<BillingResult, List<Purchase>?>>? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        val continuation = pendingPurchaseContinuation
        pendingPurchaseContinuation = null
        if (continuation?.isActive == true) {
            continuation.resume(billingResult to purchases)
        }
    }

    override suspend fun purchasePremium(): PurchaseOutcome {
        val client = connectOnce() ?: return PurchaseOutcome.NotPurchased(PurchaseFailureReason.BillingUnavailable)
        return try {
            val productDetails = queryProductDetailsOnce(client)
                ?: return PurchaseOutcome.NotPurchased(PurchaseFailureReason.ProductUnavailable)
            val offerToken = productDetails.oneTimePurchaseOfferDetails?.offerToken
                ?: return PurchaseOutcome.NotPurchased(PurchaseFailureReason.ProductUnavailable)
            outcomeFromLaunchedFlow(client, productDetails, offerToken)
        } finally {
            client.endConnection()
        }
    }

    override suspend fun restorePurchases(): PurchaseOutcome {
        val client = connectOnce() ?: return PurchaseOutcome.NotPurchased(PurchaseFailureReason.BillingUnavailable)
        return try {
            val purchase = queryOwnedPurchaseOnce(client)
                ?: return PurchaseOutcome.NotPurchased(PurchaseFailureReason.NotFound)
            acknowledgeAndResolveOutcome(client, purchase)
        } finally {
            client.endConnection()
        }
    }

    private suspend fun connectOnce(): BillingClient? {
        val client = BillingClient.newBuilder(activity)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        val connected = suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (continuation.isActive) {
                        continuation.resume(billingResult.responseCode == BillingClient.BillingResponseCode.OK)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    if (continuation.isActive) continuation.resume(false)
                }
            })
        }
        return client.takeIf { connected }
    }

    private suspend fun queryProductDetailsOnce(client: BillingClient): ProductDetails? =
        suspendCancellableCoroutine { continuation ->
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(productId)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build(),
                    ),
                )
                .build()
            client.queryProductDetailsAsync(params) { billingResult, queryResult ->
                val details = if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryResult.productDetailsList.firstOrNull()
                } else {
                    null
                }
                if (continuation.isActive) continuation.resume(details)
            }
        }

    private suspend fun queryOwnedPurchaseOnce(client: BillingClient): Purchase? =
        suspendCancellableCoroutine { continuation ->
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            client.queryPurchasesAsync(params) { billingResult, purchases ->
                val owned = if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases.firstOrNull { purchase -> productId in purchase.products }
                } else {
                    null
                }
                if (continuation.isActive) continuation.resume(owned)
            }
        }

    private suspend fun outcomeFromLaunchedFlow(
        client: BillingClient,
        productDetails: ProductDetails,
        offerToken: String,
    ): PurchaseOutcome {
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build(),
                ),
            )
            .build()

        val (billingResult, purchases) = suspendCancellableCoroutine { continuation ->
            pendingPurchaseContinuation = continuation
            val launchResult = client.launchBillingFlow(activity, flowParams)
            if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
                pendingPurchaseContinuation = null
                if (continuation.isActive) continuation.resume(launchResult to null)
            }
        }

        if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            // 로컬 상태가 Play의 실제 소유 여부와 어긋나 있던 경우(예: 개발자 테스트 토글로
            // 초기화된 뒤 재구매를 시도) — 에러로 보여주지 않고 기존 소유권을 그대로 조회해
            // 정상 활성화로 처리한다.
            val owned = queryOwnedPurchaseOnce(client)
            return if (owned != null) acknowledgeAndResolveOutcome(client, owned) else PurchaseOutcome.Purchased
        }

        val purchase = purchases?.firstOrNull { purchase -> productId in purchase.products }
        return when {
            billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED ->
                PurchaseOutcome.NotPurchased(PurchaseFailureReason.UserCancelled)
            billingResult.responseCode != BillingClient.BillingResponseCode.OK || purchase == null ->
                PurchaseOutcome.NotPurchased(PurchaseFailureReason.PurchaseError, billingResult.debugMessage)
            else -> acknowledgeAndResolveOutcome(client, purchase)
        }
    }

    private suspend fun acknowledgeAndResolveOutcome(client: BillingClient, purchase: Purchase): PurchaseOutcome {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            return PurchaseOutcome.NotPurchased(PurchaseFailureReason.Pending)
        }
        if (purchase.isAcknowledged) return PurchaseOutcome.Purchased

        val ackResult = suspendCancellableCoroutine { continuation ->
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client.acknowledgePurchase(params) { billingResult ->
                if (continuation.isActive) continuation.resume(billingResult)
            }
        }
        return if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
            PurchaseOutcome.Purchased
        } else {
            PurchaseOutcome.NotPurchased(PurchaseFailureReason.PurchaseError, ackResult.debugMessage)
        }
    }
}
