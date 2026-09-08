package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdPlacementPolicyTest {

    @Test
    fun interstitialTriggersEveryNthTransition() {
        val policy = AdPlacementPolicy(
            interstitialEveryNTransitions = 3,
            interstitialMinIntervalMillis = 0,
            rewardedMinIntervalMillis = 0,
        )

        // Fresh policy: the shouldTriggerInterstitial() increments the counter,
        // so 3rd call is the first trigger.
        assertFalse(policy.shouldTriggerInterstitial()) // 1
        assertFalse(policy.shouldTriggerInterstitial()) // 2
        assertTrue(policy.shouldTriggerInterstitial()) // 3 -> trigger
        policy.onInterstitialShown()
    }

    @Test
    fun interstitialRespectsCooldownInterval() {
        val policy = AdPlacementPolicy(
            interstitialEveryNTransitions = 1,
            interstitialMinIntervalMillis = Long.MAX_VALUE,
            rewardedMinIntervalMillis = 0,
        )

        // The purely time-based guard should block even when the counter would trigger.
        policy.onInterstitialShown()
        assertFalse(policy.shouldTriggerInterstitial())
    }

    @Test
    fun rewardedTriggeredByCooldownElapsed() {
        val policy = AdPlacementPolicy(
            interstitialEveryNTransitions = 3,
            interstitialMinIntervalMillis = 0,
            rewardedMinIntervalMillis = 0,
        )
        assertTrue(policy.shouldTriggerRewarded())
        policy.onRewardedShown()
    }

    @Test
    fun rewardedBlockedByCooldown() {
        val policy = AdPlacementPolicy(rewardedMinIntervalMillis = Long.MAX_VALUE)
        policy.onRewardedShown()
        assertFalse(policy.shouldTriggerRewarded())
    }
}
