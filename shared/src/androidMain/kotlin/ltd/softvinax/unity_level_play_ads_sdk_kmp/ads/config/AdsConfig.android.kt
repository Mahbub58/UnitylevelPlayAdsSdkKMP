package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.config

object AndroidAdsConfigHolder {
    @Volatile
    var appKey: String = ""
        private set

    fun set(appKey: String) {
        this.appKey = appKey
    }
}

actual fun platformAdsConfig(): AdsConfig = AdsConfig(
    appKey = AndroidAdsConfigHolder.appKey.ifBlank { AdsCredentials.ANDROID_APP_KEY },
    interstitialAdUnitId = LevelPlayAdUnits.INTERSTITIAL,
    rewardedAdUnitId = LevelPlayAdUnits.REWARDED,
    bannerAdUnitId = LevelPlayAdUnits.BANNER,
)