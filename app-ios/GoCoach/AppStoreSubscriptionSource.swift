import Foundation
import SharedUI
import StoreKit
import UIKit

/// App Store 구독(백로그 #296) — Kotlin `IosStoreSource`의 StoreKit 2 구현. 판정 · 진단 기록 · 강등 규칙은 Kotlin(`IosAppMonetization` →
/// `runPremiumPurchaseApplication`, 안드로이드와 같은 함수)이 하고, 여기서는 StoreKit의 답을 옮기기만 한다.
///
/// 시뮬레이터에서는 Xcode가 `app-ios/StoreKit/GoCoach.storekit`(스킴의 StoreKit Configuration)로 App Store를 흉내 낸다 — Xcode에서 실행할 때만이다.
/// `make ios-run`(simctl)으로 띄우면 상품이 없어 「쓸 수 없음」으로 끝난다.
final class AppStoreSubscriptionSource: NSObject, IosStoreSource, @unchecked Sendable {
    private var updates: Task<Void, Never>?

    override init() {
        super.init()
        // 켜지자마자 거래 소식을 듣는다 — 갱신 · 「구입 요청」 승인 · 다른 기기에서 산 것. 끝내지 않은 거래는 실행마다 다시 온다.
        // 프리미엄 상태는 Kotlin이 앱 시작 · 앞으로 올 때마다 다시 묻는다(`PremiumPurchaseRestoreEffect`) — 여기서는 거래를 끝내기만 한다.
        updates = Task.detached {
            for await result in Transaction.updates {
                if case .verified(let transaction) = result { await transaction.finish() }
            }
        }
    }

    func purchase(productId: String, listener: IosStorePurchaseListener) {
        Task { @MainActor in
            do {
                guard let product = try await Product.products(for: [productId]).first else {
                    listener.onProductUnavailable()
                    return
                }
                let result: Product.PurchaseResult
                if let scene = Self.activeWindowScene() {
                    result = try await product.purchase(confirmIn: scene)
                } else {
                    result = try await product.purchase()
                }
                switch result {
                case .success(.verified(let transaction)):
                    await transaction.finish()
                    listener.onPurchased()
                case .success(.unverified(_, let error)):
                    listener.onFailed(detail: "unverified: \(error)")
                case .userCancelled:
                    listener.onCancelled()
                case .pending:
                    listener.onPending()
                @unknown default:
                    listener.onFailed(detail: "unknown purchase result")
                }
            } catch let error as StoreKitError {
                switch error {
                case .userCancelled: listener.onCancelled()
                case .networkError, .systemError, .notAvailableInStorefront: listener.onStoreUnavailable(detail: String(describing: error))
                default: listener.onFailed(detail: String(describing: error))
                }
            } catch Product.PurchaseError.productUnavailable {
                listener.onProductUnavailable()
            } catch {
                listener.onFailed(detail: String(describing: error))
            }
        }
    }

    func currentEntitlement(productId: String, listener: IosStoreEntitlementListener) {
        Task.detached { await Self.answerEntitlement(productId: productId, listener: listener) }
    }

    func restore(productId: String, listener: IosStoreEntitlementListener) {
        Task.detached {
            do {
                try await AppStore.sync()
            } catch {
                // 맞추지 못했다(취소 · 네트워크) — 기기의 거래로 「갖고 있다」만 믿고, 「없다」는 모른다로 답한다(강등하지 않게).
                if await Self.ownsVerified(productId: productId) {
                    listener.onOwned()
                } else {
                    listener.onUnknown(detail: String(describing: error))
                }
                return
            }
            await Self.answerEntitlement(productId: productId, listener: listener)
        }
    }

    func productInfo(productId: String, listener: IosStoreProductListener) {
        Task.detached {
            guard let product = try? await Product.products(for: [productId]).first,
                  let period = product.subscription?.subscriptionPeriod else {
                listener.onUnavailable()
                return
            }
            let unit: String
            switch period.unit {
            case .day: unit = "day"
            case .week: unit = "week"
            case .month: unit = "month"
            case .year: unit = "year"
            @unknown default: unit = "unknown"
            }
            listener.onProduct(displayPrice: product.displayPrice, periodUnit: unit, periodValue: Int32(period.value))
        }
    }

    func showManageSubscriptions() {
        Task { @MainActor in
            guard let scene = Self.activeWindowScene() else { return }
            try? await AppStore.showManageSubscriptions(in: scene)
        }
    }

    /// 오프라인이어도 답한다 — 기기에 남은 서명된 거래로. 그래서 「없다」는 권위 있는 미소유다(안드로이드 Play의 `NotFound`).
    private static func answerEntitlement(productId: String, listener: IosStoreEntitlementListener) async {
        if await ownsVerified(productId: productId) {
            listener.onOwned()
        } else {
            listener.onNotOwned()
        }
    }

    private static func ownsVerified(productId: String) async -> Bool {
        for await result in Transaction.currentEntitlements {
            if case .verified(let transaction) = result, transaction.productID == productId, transaction.revocationDate == nil {
                return true
            }
        }
        return false
    }

    @MainActor private static func activeWindowScene() -> UIWindowScene? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        return scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
    }
}
