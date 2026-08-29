package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads

data class AdsConfig(
    val appKey: String,
    val interstitialAdUnitId: String,
    val rewardedAdUnitId: String,
    val bannerAdUnitId: String,
) {
    val isConfigured: Boolean
        get() = appKey.isNotBlank() &&
            interstitialAdUnitId.isNotBlank() &&
            rewardedAdUnitId.isNotBlank() &&
            bannerAdUnitId.isNotBlank()
}

expect fun platformAdsConfig(): AdsConfig