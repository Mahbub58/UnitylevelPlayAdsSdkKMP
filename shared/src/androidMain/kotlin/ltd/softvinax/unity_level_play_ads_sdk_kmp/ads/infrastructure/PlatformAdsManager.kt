package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.infrastructure

import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.config.AdsConfig
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.data.LevelPlayAdsManager
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.domain.AdsManager

actual fun createPlatformAdsManager(config: AdsConfig): AdsManager =
    LevelPlayAdsManager(config)