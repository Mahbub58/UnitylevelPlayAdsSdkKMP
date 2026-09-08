package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.config

/**
 * Platform-specific LevelPlay Ad Unit IDs.
 * Each platform provides its own [actual] implementation with its dashboard placement IDs.
 */
expect object LevelPlayAdUnits {
    val INTERSTITIAL: String
    val REWARDED: String
    val BANNER: String
}