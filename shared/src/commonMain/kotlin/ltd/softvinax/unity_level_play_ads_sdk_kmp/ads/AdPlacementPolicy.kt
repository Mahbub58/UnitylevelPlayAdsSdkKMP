package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads

import kotlin.time.Clock

class AdPlacementPolicy(
    private val interstitialEveryNTransitions: Int = 3,
    private val interstitialMinIntervalMillis: Long = 60_000L,
    private val rewardedMinIntervalMillis: Long = 30_000L,
) {
    private var lvlTransitions = 0
    private var lastInterstitialShown: Long = 0L
    private var lastRewardedShown: Long = 0L

    fun shouldTriggerInterstitial(): Boolean {
        lvlTransitions += 1
        return lvlTransitions % interstitialEveryNTransitions == 0 &&
            Clock.System.now().toEpochMilliseconds() - lastInterstitialShown >= interstitialMinIntervalMillis
    }

    fun onInterstitialShown() {
        lastInterstitialShown = Clock.System.now().toEpochMilliseconds()
    }

    fun shouldTriggerRewarded(): Boolean {
        return Clock.System.now().toEpochMilliseconds() - lastRewardedShown >= rewardedMinIntervalMillis
    }

    fun onRewardedShown() {
        lastRewardedShown = Clock.System.now().toEpochMilliseconds()
    }
}