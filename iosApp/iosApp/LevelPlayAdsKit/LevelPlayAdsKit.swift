import Foundation
import UIKit
import IronSource
import UserMessagingPlatform

@objc(LevelPlayAdsKitBridge) public final class LevelPlayAdsKitBridge: NSObject {

    private static var interstitialAd: LPMInterstitialAd?
    private static var rewardedAd: LPMRewardedAd?
    private static var bannerAd: LPMBannerAdView?
    private static var interstitialDelegate: InterstitialDelegate?
    private static var rewardedDelegate: RewardedDelegate?
    private static var bannerDelegate: BannerDelegate?
    private static var bannerIsReady = false
    private static var bannerRetryCount = 0
    private static var bannerViewController: UIViewController?
    private static var bannerContainer: AdaptiveBannerContainer?
    private static var eventHandler: LPAKCallback?
    private static var testSuiteEnabled = false

    private static let bannerRetryDelay: TimeInterval = 10
    private static let maxBannerRetries = 5

    private static func emit(_ event: LPAKEvent, message: String? = nil, error: NSError? = nil) {
        eventHandler?(event.rawValue, message, error)
    }

    // MARK: - Consent (Google UMP - SDK-provided GDPR/CCPA/COPPA form)

    @objc public static func requestConsent(testMode: Bool, debugGeography: String, completion: @escaping LPAKConsentCallback) {
        DispatchQueue.main.async {
            let params = RequestParameters()
            if testMode {
                let debug = DebugSettings()
                let rawValue: Int
                switch debugGeography {
                case "US": rawValue = 3
                case "EEA": rawValue = 1
                default: rawValue = 0
                }
                debug.geography = DebugGeography(rawValue: rawValue) ?? .EEA
                params.debugSettings = debug
                NSLog("UMP: test mode on, forced geography raw=\(String(describing: debug.geography.rawValue))")
            }
            NSLog("UMP: requesting consent info update")
            ConsentInformation.shared.requestConsentInfoUpdate(with: params) { error in
                if let error = error {
                    NSLog("UMP: consent info update error \(error.localizedDescription)")
                    completion(false, false, error as NSError)
                    return
                }
                NSLog("UMP: consent info OK, canRequestAds=\(ConsentInformation.shared.canRequestAds), presenting SDK consent form")
                ConsentForm.loadAndPresentIfRequired(from: topViewController()) { formError in
                    NSLog("UMP: form flow done, error=\(formError?.localizedDescription ?? "none")")
                    completion(ConsentInformation.shared.canRequestAds, usPrivacyDoNotSell, formError as NSError?)
                }
            }
        }
    }

    /// CCPA signal from the UMP-provided US Privacy String stored after the US-state
    /// consent form. Second character 'Y' = opted out of the sale of personal information.
    /// Absent string (not a US-regulated region) resolves to `false` (no do-not-sell).
    private static var usPrivacyDoNotSell: Bool {
        let usPrivacy = UserDefaults.standard.string(forKey: "IABUSPrivacy_String") ?? ""
        return usPrivacy.count >= 2 && Array(usPrivacy)[1] == "Y"
    }

    // MARK: - SDK Init

    @objc public static func setTestSuiteEnabled(_ enabled: Bool) {
        testSuiteEnabled = enabled
    }

    @objc public static func applyPrivacyConsent(_ consent: Bool, doNotSell: Bool, childDirected: Bool) {
        LPMPrivacySettings.setGDPRConsent(consent)
        LPMPrivacySettings.setCCPA(doNotSell)
        LPMPrivacySettings.setCOPPA(childDirected)
    }

    @objc public static func initializeSdkWithAppKey(_ appKey: String, callback: @escaping LPAKCallback) {
        eventHandler = callback
        if testSuiteEnabled {
            LevelPlay.setMetaDataWithKey("is_test_suite", value: "enable")
        }
        let request = LPMInitRequestBuilder(appKey: appKey).build()
        LevelPlay.initWith(request) { config, error in
            if let error = error {
                let nsError = error as NSError
                emit(.sdkInitFailed, message: nsError.localizedDescription, error: nsError)
            } else {
                emit(.sdkInitSuccess)
            }
        }
    }

    // MARK: - Test Suite

    @objc public static func launchTestSuite() {
        guard testSuiteEnabled, let viewController = topViewController() else {
            return
        }
        LevelPlay.launchTestSuite(viewController)
    }

    // MARK: - Interstitial

    @objc public static func loadInterstitialWithAdUnitId(_ adUnitId: String) {
        let ad = LPMInterstitialAd(adUnitId: adUnitId)
        let delegate = InterstitialDelegate()
        interstitialDelegate = delegate
        ad.setDelegate(delegate)
        interstitialAd = ad
        ad.loadAd()
    }

    @objc public static func isInterstitialReady() -> Bool {
        interstitialAd?.isAdReady() ?? false
    }

    @objc public static func showInterstitial() {
        guard let ad = interstitialAd, ad.isAdReady(), let viewController = topViewController() else {
            return
        }
        ad.showAd(viewController: viewController, placementName: nil)
    }

    // MARK: - Rewarded

    @objc public static func loadRewardedWithAdUnitId(_ adUnitId: String) {
        let ad = LPMRewardedAd(adUnitId: adUnitId)
        let delegate = RewardedDelegate()
        rewardedDelegate = delegate
        ad.setDelegate(delegate)
        rewardedAd = ad
        ad.loadAd()
    }

    @objc public static func isRewardedReady() -> Bool {
        rewardedAd?.isAdReady() ?? false
    }

    @objc public static func showRewarded() {
        guard let ad = rewardedAd, ad.isAdReady(), let viewController = topViewController() else {
            return
        }
        ad.showAd(viewController: viewController, placementName: nil)
    }

    // MARK: - Banner

    @objc public static func createBannerWithAdUnitId(_ adUnitId: String) -> UIView? {
        guard let adaptiveSize = LPMAdSize.createAdaptive() else {
            return nil
        }
        let config = LPMBannerAdViewConfigBuilder()
            .set(adSize: adaptiveSize)
            .build()
        let banner = LPMBannerAdView(adUnitId: adUnitId, config: config)
        let container = AdaptiveBannerContainer(
            size: CGSize(width: UIScreen.main.bounds.width, height: CGFloat(adaptiveSize.height))
        )
        banner.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(banner)
        NSLayoutConstraint.activate([
            banner.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            banner.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            banner.topAnchor.constraint(equalTo: container.topAnchor),
            banner.bottomAnchor.constraint(equalTo: container.bottomAnchor),
        ])
        bannerContainer = container
        let delegate = BannerDelegate()
        bannerDelegate = delegate
        banner.setDelegate(delegate)
        bannerAd = banner
        bannerIsReady = false
        bannerRetryCount = 0
        DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
            guard let banner = LevelPlayAdsKitBridge.bannerAd,
                  let viewController = topViewController() else {
                return
            }
            LevelPlayAdsKitBridge.bannerViewController = viewController
            banner.loadAd(with: viewController)
        }
        return container
    }

    @objc public static func isBannerReady() -> Bool {
        bannerIsReady
    }

    @objc public static func destroyBanner() {
        bannerAd?.destroy()
        bannerAd = nil
        bannerDelegate = nil
        bannerIsReady = false
        bannerRetryCount = 0
        bannerViewController = nil
        bannerContainer = nil
    }

    // MARK: - Delegates

    private final class InterstitialDelegate: NSObject, LPMInterstitialAdDelegate {
        func didLoadAd(with adInfo: LPMAdInfo) {
            emit(.interstitialLoaded)
        }

        func didFailToLoadAd(withAdUnitId adUnitId: String, error: Error) {
            emit(.interstitialLoadFailed, message: (error as NSError).localizedDescription, error: error as NSError)
        }

        func didDisplayAd(with adInfo: LPMAdInfo) {
            emit(.interstitialDisplayed)
        }

        func didFailToDisplayAd(with adInfo: LPMAdInfo, error: Error) {
            emit(.interstitialDisplayFailed, message: (error as NSError).localizedDescription, error: error as NSError)
        }

        func didClickAd(with adInfo: LPMAdInfo) {
            emit(.interstitialClicked)
        }

        func didCloseAd(with adInfo: LPMAdInfo) {
            emit(.interstitialClosed)
            if let ad = LevelPlayAdsKitBridge.interstitialAd {
                ad.loadAd()
            }
        }

        func didChangeAdInfo(_ adInfo: LPMAdInfo) {}
    }

    private final class RewardedDelegate: NSObject, LPMRewardedAdDelegate {
        func didLoadAd(with adInfo: LPMAdInfo) {
            emit(.rewardedLoaded)
        }

        func didFailToLoadAd(withAdUnitId adUnitId: String, error: Error) {
            emit(.rewardedLoadFailed, message: (error as NSError).localizedDescription, error: error as NSError)
        }

        func didRewardAd(with adInfo: LPMAdInfo, reward: LPMReward) {
            emit(.rewardedEarned, message: reward.name, error: nil)
        }

        func didDisplayAd(with adInfo: LPMAdInfo) {
            emit(.rewardedDisplayed)
        }

        func didFailToDisplayAd(with adInfo: LPMAdInfo, error: Error) {
            emit(.rewardedDisplayFailed, message: (error as NSError).localizedDescription, error: error as NSError)
        }

        func didClickAd(with adInfo: LPMAdInfo) {
            emit(.rewardedClicked)
        }

        func didCloseAd(with adInfo: LPMAdInfo) {
            emit(.rewardedClosed)
            if let ad = LevelPlayAdsKitBridge.rewardedAd {
                ad.loadAd()
            }
        }

        func didChangeAdInfo(_ adInfo: LPMAdInfo) {}
    }

    private final class BannerDelegate: NSObject, LPMBannerAdViewDelegate {
        func didLoadAd(with adInfo: LPMAdInfo) {
            bannerIsReady = true
            LevelPlayAdsKitBridge.bannerRetryCount = 0
            emit(.bannerLoaded)
        }

        func didFailToLoadAd(withAdUnitId adUnitId: String, error: Error) {
            let nsError = error as NSError
            emit(.bannerLoadFailed, message: nsError.localizedDescription, error: nsError)
            let isNoFill = nsError.localizedDescription.lowercased().contains("no fill") ||
                nsError.localizedDescription.lowercased().contains("no_fill")
            if isNoFill && LevelPlayAdsKitBridge.bannerRetryCount < LevelPlayAdsKitBridge.maxBannerRetries {
                LevelPlayAdsKitBridge.bannerRetryCount += 1
                DispatchQueue.main.asyncAfter(deadline: .now() + LevelPlayAdsKitBridge.bannerRetryDelay) {
                    guard let banner = LevelPlayAdsKitBridge.bannerAd,
                          let viewController = LevelPlayAdsKitBridge.bannerViewController else {
                        return
                    }
                    banner.loadAd(with: viewController)
                }
            }
        }

        func didClickAd(with adInfo: LPMAdInfo) {
            emit(.bannerClicked)
        }

        func didDisplayAd(with adInfo: LPMAdInfo) {
            emit(.bannerDisplayed)
        }

        func didFailToDisplayAd(with adInfo: LPMAdInfo, error: Error) {
            emit(.bannerDisplayFailed, message: (error as NSError).localizedDescription, error: error as NSError)
        }

        func didLeaveApp(with adInfo: LPMAdInfo) {}

        func didExpandAd(with adInfo: LPMAdInfo) {}

        func didCollapseAd(with adInfo: LPMAdInfo) {}
    }

    // MARK: - Helpers

    private final class AdaptiveBannerContainer: UIView {
        private let adSize: CGSize

        init(size: CGSize) {
            adSize = size
            super.init(frame: CGRect(origin: .zero, size: size))
        }

        required init?(coder: NSCoder) {
            fatalError("init(coder:) is not supported")
        }

        override var intrinsicContentSize: CGSize {
            adSize
        }
    }

    private static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        guard let scene = scenes.first else { return nil }
        let window = scene.windows.first ?? UIApplication.shared.windows.first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}