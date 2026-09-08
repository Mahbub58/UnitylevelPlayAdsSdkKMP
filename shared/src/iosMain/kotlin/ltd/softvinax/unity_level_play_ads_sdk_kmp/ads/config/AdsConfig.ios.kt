package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.config

actual fun platformAdsConfig(): AdsConfig = AdsConfig(
    appKey = AdsCredentials.IOS_APP_KEY,
    interstitialAdUnitId = LevelPlayAdUnits.INTERSTITIAL,
    rewardedAdUnitId = LevelPlayAdUnits.REWARDED,
    bannerAdUnitId = LevelPlayAdUnits.BANNER,
)