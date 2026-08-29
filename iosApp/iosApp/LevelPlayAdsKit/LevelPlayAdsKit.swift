import Foundation
import UIKit
import IronSource

@objc public final class LevelPlayAdsKitBridge: NSObject {

    private static var interstitialAd: LPMInterstitialAd?
    private static var rewardedAd: LPMRewardedAd?
    private static var bannerAd: LPMBannerAdView?
    private static var interstitialDelegate: InterstitialDelegate?
    private static var rewardedDelegate: RewardedDelegate?
    private static var bannerDelegate: BannerDelegate?
    private static var bannerIsReady = false
    private static var eventHandler: LPAKCallback?

    private static func emit(_ event: LPAKEvent, message: String? = nil, error: NSError? = nil) {
        eventHandler?(event.rawValue, message, error)
    }

    // MARK: - SDK Init

    @objc public static func initializeSdkWithAppKey(_ appKey: String, callback: @escaping LPAKCallback) {
        eventHandler = callback
        let builder = LPMInitRequestBuilder(appKey: appKey)
        builder.withLegacyAdFormats([IS_INTERSTITIAL, IS_REWARDED_VIDEO, IS_BANNER])
        let request = builder.build()
        LevelPlay.initWithRequest(request) { config, error in
            if let error = error {
                let nsError = error as NSError
                emit(.sdkInitFailed, message: nsError.localizedDescription, error: nsError)
            } else {
                emit(.sdkInitSuccess)
            }
        }
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
        let banner = LPMBannerAdView(adUnitId: adUnitId)
        let delegate = BannerDelegate()
        bannerDelegate = delegate
        banner.setDelegate(delegate)
        banner.translatesAutoresizingMaskIntoConstraints = false
        bannerAd = banner
        bannerIsReady = false
        if let viewController = topViewController() {
            banner.loadAd(with: viewController)
        }
        return banner
    }

    @objc public static func isBannerReady() -> Bool {
        bannerIsReady
    }

    @objc public static func destroyBanner() {
        bannerAd?.destroy()
        bannerAd = nil
        bannerDelegate = nil
        bannerIsReady = false
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
            emit(.bannerLoaded)
        }

        func didFailToLoadAd(withAdUnitId adUnitId: String, error: Error) {
            emit(.bannerLoadFailed, message: (error as NSError).localizedDescription, error: error as NSError)
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