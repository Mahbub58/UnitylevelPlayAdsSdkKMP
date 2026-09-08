#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(int32_t, LPAKEvent) {
    LPAKEventSdkInitSuccess = 0,
    LPAKEventSdkInitFailed = 1,
    LPAKEventInterstitialLoaded = 2,
    LPAKEventInterstitialLoadFailed = 3,
    LPAKEventInterstitialDisplayed = 4,
    LPAKEventInterstitialDisplayFailed = 5,
    LPAKEventInterstitialClicked = 6,
    LPAKEventInterstitialClosed = 7,
    LPAKEventRewardedLoaded = 8,
    LPAKEventRewardedLoadFailed = 9,
    LPAKEventRewardedDisplayed = 10,
    LPAKEventRewardedDisplayFailed = 11,
    LPAKEventRewardedClicked = 12,
    LPAKEventRewardedClosed = 13,
    LPAKEventRewardedEarned = 14,
    LPAKEventBannerLoaded = 15,
    LPAKEventBannerLoadFailed = 16,
    LPAKEventBannerDisplayed = 17,
    LPAKEventBannerDisplayFailed = 18,
    LPAKEventBannerClicked = 19
};

typedef void (^LPAKCallback)(int32_t event, NSString * _Nullable message, NSError * _Nullable error);
typedef void (^LPAKConsentCallback)(BOOL canRequestAds, BOOL doNotSell, NSError * _Nullable error);

@interface LevelPlayAdsKitBridge : NSObject

+ (void)requestConsentWithTestMode:(BOOL)testMode
                   debugGeography:(NSString *)debugGeography
                       completion:(LPAKConsentCallback)completion;

+ (void)initializeSdkWithAppKey:(NSString *)appKey callback:(LPAKCallback)callback;
+ (void)applyPrivacyConsent:(BOOL)consent doNotSell:(BOOL)doNotSell childDirected:(BOOL)childDirected;
+ (void)setTestSuiteEnabled:(BOOL)enabled;
+ (void)launchTestSuite;
+ (void)loadInterstitialWithAdUnitId:(NSString *)adUnitId;
+ (BOOL)isInterstitialReady;
+ (void)showInterstitial;
+ (void)loadRewardedWithAdUnitId:(NSString *)adUnitId;
+ (BOOL)isRewardedReady;
+ (void)showRewarded;
+ (UIView * _Nullable)createBannerWithAdUnitId:(NSString *)adUnitId;
+ (BOOL)isBannerReady;
+ (void)destroyBannerWithAdUnitId:(NSString *)adUnitId;

@end

NS_ASSUME_NONNULL_END