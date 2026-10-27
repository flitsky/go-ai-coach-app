import GoogleMobileAds
import SharedUI
import UIKit
import UserMessagingPlatform

/// 광고(백로그 #296 — U-86 사용자 2026-10-27: 안드로이드와 똑같이 · ATT 창 없이 비맞춤만) — Kotlin `IosAdsSource`의 Google Mobile Ads · UMP 구현.
/// 결과 해석(보상 · 실패 사유)과 동의 게이트 · 배경음악은 Kotlin(`IosAppMonetization.showRewardedAdOnce`)이 안드로이드와 같은 길로 한다.
///
/// 광고 ID는 `Info.plist`의 세 키 — 값은 빌드 설정에서 온다(`app-ios/Config/Ads.xcconfig`: Google 공식 시험 ID, 출시 구성은
/// `Ads.local.xcconfig`(커밋하지 않는다 — 안드로이드 `local.properties`의 `admob.*` 자리)의 실제 ID). 시험 ID로는 App Store용 보관(archive)이 막힌다.
/// ATT 창을 띄우지 않으므로 광고 식별자(IDFA)를 쓰지 않는다 — `NSUserTrackingUsageDescription`도 두지 않는다. 그리고 모든 요청을 **비맞춤**(`npa=1`)으로 보낸다 —
/// IDFA가 없어도 다른 신호로 맞춤이 될 수 있어서, U-86의 「비맞춤만」을 요청에 직접 적는다([adRequest]).
final class GoogleAdsSource: NSObject, IosAdsSource, FullScreenContentDelegate, @unchecked Sendable {
    private let rewardedUnitID: String
    private let bannerUnitID: String
    private var started = false
    private var showingAd: RewardedInterstitialAd?
    private var rewardListener: IosRewardedAdListener?
    private var earnedReward: AdReward?

    /// `Info.plist`에 광고 ID가 없으면(키가 비었으면) 광고 없이 돈다.
    static func makeFromBundle() -> GoogleAdsSource? {
        let info = Bundle.main.infoDictionary ?? [:]
        guard let appID = info["GADApplicationIdentifier"] as? String, !appID.isEmpty,
              let rewarded = info["GoCoachRewardedInterstitialAdUnitID"] as? String, !rewarded.isEmpty,
              let banner = info["GoCoachBannerAdUnitID"] as? String, !banner.isEmpty else { return nil }
        return GoogleAdsSource(rewardedUnitID: rewarded, bannerUnitID: banner)
    }

    private init(rewardedUnitID: String, bannerUnitID: String) {
        self.rewardedUnitID = rewardedUnitID
        self.bannerUnitID = bannerUnitID
        super.init()
        // 안드로이드와 같은 요청 설정 — 전체 이용가(G). 아동 · 동의 연령 태그는 넣지 않는다(안드로이드 `GoAiCoachApplication`과 같은 판단).
        MobileAds.shared.requestConfiguration.maxAdContentRating = .general
    }

    /// 앱이 뜰 때 동의 정보만 새로 묻는다(폼은 띄우지 않는다 — 안드로이드 `MainActivity`의 `AdsConsentManager.refresh`와 같다).
    /// 이것이 없으면 처음 설치한 기기에서 `canRequestAds`가 첫 보상형 광고 전까지 거짓이라 다시보기 배너가 비어 있다.
    func refreshConsentInfo() {
        Task { @MainActor in
            try? await ConsentInformation.shared.requestConsentInfoUpdate(with: RequestParameters())
        }
    }

    func gatherConsent(listener: IosAdsConsentListener) {
        Task { @MainActor in
            // 매번 새로 묻는다 — 시작할 때의 확인이 끝나기 전에 광고 단추가 눌려도 공유 상태 없이 맞는다(안드로이드 `refresh`와 같은 이유).
            try? await ConsentInformation.shared.requestConsentInfoUpdate(with: RequestParameters())
            if let root = Self.topViewController() {
                try? await ConsentForm.loadAndPresentIfRequired(from: root)
            }
            listener.onDone(canRequestAds: ConsentInformation.shared.canRequestAds)
        }
    }

    func showRewardedAd(listener: IosRewardedAdListener) {
        Task { @MainActor in
            // SDK 시작은 처음 광고를 띄울 때 한 번(안드로이드 `AndroidRewardedInterstitialAdClient`와 같다 — 앱 시작을 늦추지 않는다).
            if !started {
                _ = await MobileAds.shared.start()
                started = true
            }
            let ad: RewardedInterstitialAd
            do {
                ad = try await RewardedInterstitialAd.load(with: rewardedUnitID, request: Self.adRequest())
            } catch {
                listener.onLoadFailed(detail: error.localizedDescription)
                return
            }
            guard let root = Self.topViewController() else {
                listener.onShowFailed(detail: "no view controller to present from")
                return
            }
            showingAd = ad
            rewardListener = listener
            earnedReward = nil
            ad.fullScreenContentDelegate = self
            // 보상은 받은 순간 적어 두고, 결과는 광고가 **닫힐 때** 준다(닫기 전에 1시간이 켜지면 광고 뒤에서 화면이 바뀐다 — 안드로이드와 같은 순서).
            ad.present(from: root) { [weak self, weak ad] in
                self?.earnedReward = ad?.adReward
            }
        }
    }

    @MainActor func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
        let listener = rewardListener
        let reward = earnedReward
        clearShowingAd()
        if let reward {
            listener?.onRewarded(type: reward.type, amount: Int32(truncating: reward.amount))
        } else {
            listener?.onDismissedWithoutReward()
        }
    }

    @MainActor func ad(_ ad: FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        let listener = rewardListener
        clearShowingAd()
        listener?.onShowFailed(detail: error.localizedDescription)
    }

    func canRequestAds() -> Bool {
        ConsentInformation.shared.canRequestAds
    }

    func isPrivacyOptionsRequired() -> Bool {
        ConsentInformation.shared.privacyOptionsRequirementStatus == .required
    }

    func showPrivacyOptions(listener: IosAdsDoneListener) {
        Task { @MainActor in
            if let root = Self.topViewController() {
                try? await ConsentForm.presentPrivacyOptionsForm(from: root)
            }
            listener.onDone()
        }
    }

    func resetConsent() {
        ConsentInformation.shared.reset()
    }

    func bannerHeight(widthPoints: Double) -> Double {
        Double(portraitAnchoredAdaptiveBanner(width: CGFloat(widthPoints)).size.height)
    }

    /// Compose가 화면을 그리는 주 스레드에서 부른다(`UIKitView`의 factory).
    func makeBanner(widthPoints: Double) -> UIView {
        MainActor.assumeIsolated {
            let banner = BannerView(adSize: portraitAnchoredAdaptiveBanner(width: CGFloat(widthPoints)))
            banner.adUnitID = bannerUnitID
            // 배경은 비워 둔다 — 자리보다 작은 소재가 오면 남는 위아래로 화면 배경이 비친다(안드로이드 `setBackgroundColor(TRANSPARENT)`와 같다).
            banner.backgroundColor = .clear
            banner.rootViewController = Self.topViewController()
            // 동의 없이는 요청하지 않는다 — 배너의 유일한 관문이다(안드로이드 `BannerAdViewAndroid`와 같다).
            if ConsentInformation.shared.canRequestAds {
                banner.load(Self.adRequest())
            }
            return banner
        }
    }

    /// 비맞춤 광고 요청(U-86) — Google의 `npa=1` 추가 매개변수.
    private static func adRequest() -> Request {
        let request = Request()
        let extras = Extras()
        extras.additionalParameters = ["npa": "1"]
        request.register(extras)
        return request
    }

    private func clearShowingAd() {
        showingAd = nil
        rewardListener = nil
        earnedReward = nil
    }

    /// 지금 맨 위의 화면 컨트롤러 — 광고 · 동의 창은 여기서 띄운다.
    @MainActor private static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let scene = scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
        var top = scene?.keyWindow?.rootViewController ?? scene?.windows.first?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
