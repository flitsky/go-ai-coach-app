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
 * 47/47로 여유가 없다 — PREMIUM_MODE.md Step 4 참고). `enableAutoServiceReconnection()`은
 * 이런 단발성 연결에는 의미가 없어 의도적으로 쓰지 않는다.
 *
 * 비소모성(non-consumable/one-time) 상품이라 `consumeAsync`는 쓰지 않는다 — 구매 완료 후
 * [AcknowledgePurchaseParams]로 확인(acknowledge)만 하면 된다(3일 이내 확인하지 않으면 Play가
 * 자동 환불한다).
 */
internal class AndroidBillingClient(
    private val activity: Activity,
    private val productId: String,
    /**
     * `INAPP`(단발 구매) 또는 `SUBS`(구독). ⚠️ **생성자로 올려 둔 것이 요점이다**(#26 착수 순서).
     * 프리미엄은 월 구독으로 가고 봇 캐릭터(#18)는 단발 구매로 남는데 **둘이 이 클래스를 공유한다** —
     * 클래스 안에서 상수로 치환하면 캐릭터 구매가 조용히 깨지고, 그 플래그가 꺼져 있어
     * 테스트로도 드러나지 않는다.
     */
    private val productType: String = BillingClient.ProductType.INAPP,
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
            // ⚠️ 오퍼 토큰을 꺼내는 자리가 상품 종류마다 **다르다** — 구독은
            // `subscriptionOfferDetails`에 있고 `oneTimePurchaseOfferDetails`는 항상 `null`이다.
            // 이 분기가 없으면 구독은 "상품 없음"으로 조용히 실패한다.
            val offerToken = billingOfferToken(
                productType = productType,
                oneTimeOfferToken = productDetails.oneTimePurchaseOfferDetails?.offerToken,
                subscriptionOfferTokens = productDetails.subscriptionOfferDetails
                    ?.map { offer -> offer.offerToken }
                    .orEmpty(),
            ) ?: return PurchaseOutcome.NotPurchased(PurchaseFailureReason.ProductUnavailable)
            outcomeFromLaunchedFlow(client, productDetails, offerToken)
        } finally {
            client.endConnection()
        }
    }

    /**
     * ⚠️ **"미소유"와 "확인 못 함"을 갈라서 돌려준다**(#26 착수 순서 1번). 예전에는 조회 실패도
     * [PurchaseFailureReason.NotFound]로 접혀, 진단 로그가 *"소유한 구매 없음"* 이라고 **거짓을
     * 적었다.** 구독에서 강등을 켜면 그 거짓이 곧 유료 구독자의 접근권 박탈이 된다.
     */
    override suspend fun restorePurchases(): PurchaseOutcome {
        val client = connectOnce() ?: return PurchaseOutcome.NotPurchased(PurchaseFailureReason.BillingUnavailable)
        return try {
            when (val query = queryOwnershipOnce(client)) {
                is OwnershipQuery.Owned -> acknowledgeAndResolveOutcome(client, query.purchase)
                OwnershipQuery.NotOwned -> PurchaseOutcome.NotPurchased(PurchaseFailureReason.NotFound)
                is OwnershipQuery.Unknown ->
                    PurchaseOutcome.NotPurchased(PurchaseFailureReason.OwnershipUnknown, query.detail)
            }
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
                            .setProductType(productType)
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

    /**
     * 소유 조회의 **세 갈래**. ⚠️ [NotOwned]와 [Unknown]을 하나의 `null`로 합치지 말 것 —
     * 그 둘을 합쳐 뒀던 것이 #26이 지목한 결함이다.
     */
    private sealed interface OwnershipQuery {
        data class Owned(val purchase: Purchase) : OwnershipQuery
        data object NotOwned : OwnershipQuery
        data class Unknown(val detail: String?) : OwnershipQuery
    }

    private suspend fun queryOwnershipOnce(client: BillingClient): OwnershipQuery =
        suspendCancellableCoroutine { continuation ->
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(productType)
                .build()
            client.queryPurchasesAsync(params) { billingResult, purchases ->
                val query = if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // Play가 답했다 — 목록에 없으면 그것이 권위 있는 "미소유"다.
                    purchases.firstOrNull { purchase -> productId in purchase.products }
                        ?.let(OwnershipQuery::Owned)
                        ?: OwnershipQuery.NotOwned
                } else {
                    // 물어봤지만 답을 못 얻었다. **미소유가 아니다.**
                    OwnershipQuery.Unknown(billingResult.debugMessage)
                }
                if (continuation.isActive) continuation.resume(query)
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
            val owned = (queryOwnershipOnce(client) as? OwnershipQuery.Owned)?.purchase
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

/**
 * 상품 종류에 맞는 오퍼 토큰을 고른다. **순수 함수라 테스트로 고정한다** —
 * `ProductDetails`는 Play SDK 타입이라 단위 테스트에서 만들 수 없어, 고르는 규칙만 떼어 냈다.
 *
 * ⚠️ **구독에서 `oneTimePurchaseOfferDetails`를 보면 언제나 `null`이다.** 그대로 두면 구독이
 * "상품 없음"으로 조용히 실패한다 — 콘솔이 막혀 실기로 못 밟아 보는 지금은 이 테스트가
 * 그 실패를 막는 유일한 장치다(#26).
 *
 * 구독 오퍼가 여러 개면 **첫 번째**를 쓴다. 이 앱의 구독은 월 1종 단일 오퍼이므로 충분하고,
 * 오퍼가 늘어나면 그때 고르는 규칙을 여기서 정하면 된다.
 */
internal fun billingOfferToken(
    productType: String,
    oneTimeOfferToken: String?,
    subscriptionOfferTokens: List<String>,
): String? =
    if (productType == BillingClient.ProductType.SUBS) {
        subscriptionOfferTokens.firstOrNull()
    } else {
        oneTimeOfferToken
    }
