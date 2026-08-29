package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads

import platform.Foundation.NSBundle

actual fun platformAdsConfig(): AdsConfig = AdsConfig(
    appKey = NSBundle.mainBundle.objectForInfoDictionaryKey("LevelPlayAppKey") as? String ?: "",
    interstitialAdUnitId = LevelPlayAdUnits.INTERSTITIAL,
    rewardedAdUnitId = LevelPlayAdUnits.REWARDED,
    bannerAdUnitId = LevelPlayAdUnits.BANNER,
)