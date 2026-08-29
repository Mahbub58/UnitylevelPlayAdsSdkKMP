package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads

import kotlinx.coroutines.flow.StateFlow

class AdsCoordinator(
    private val adsManager: AdsManager,
    private val config: AdsConfig,
    private val policy: AdPlacementPolicy = AdPlacementPolicy(),
) {
    private var pendingRewardAction: (() -> Unit)? = null

    val initState: StateFlow<AdsInitState> = adsManager.initState
    val interstitialState: StateFlow<AdsState> = adsManager.interstitialState
    val rewardedState: StateFlow<AdsState> = adsManager.rewardedState
    val bannerState: StateFlow<AdsState> = adsManager.bannerState

    fun start(onReady: (AdsInitResult) -> Unit) {
        adsManager.init(config.appKey) { result ->
            if (result is AdsInitResult.Success) {
                adsManager.loadInterstitial(config.interstitialAdUnitId)
                adsManager.loadRewarded(config.rewardedAdUnitId)
            }
            onReady(result)
        }
    }

    fun onLevelTransitionCompleted(): Boolean {
        return when {
            policy.shouldTriggerInterstitial() && adsManager.isInterstitialReady() -> {
                policy.onInterstitialShown()
                adsManager.showInterstitial()
                true
            }
            !adsManager.isInterstitialReady() -> {
                adsManager.loadInterstitial(config.interstitialAdUnitId)
                false
            }
            else -> false
        }
    }

    fun loadInterstitial() {
        adsManager.loadInterstitial(config.interstitialAdUnitId)
    }

    fun showInterstitial(): Boolean {
        return if (adsManager.isInterstitialReady()) {
            adsManager.showInterstitial()
            true
        } else {
            false
        }
    }

    fun requestRewarded(rewardAction: () -> Unit): Boolean {
        return if (policy.shouldTriggerRewarded() && adsManager.isRewardedReady()) {
            policy.onRewardedShown()
            pendingRewardAction = rewardAction
            adsManager.showRewarded(onRewardEarned = ::grantPendingReward)
            true
        } else {
            adsManager.loadRewarded(config.rewardedAdUnitId)
            false
        }
    }

    fun loadRewarded() {
        adsManager.loadRewarded(config.rewardedAdUnitId)
    }

    fun showRewarded(onRewardEarned: () -> Unit): Boolean {
        return if (adsManager.isRewardedReady()) {
            pendingRewardAction = onRewardEarned
            adsManager.showRewarded(onRewardEarned = ::grantPendingReward)
            true
        } else {
            false
        }
    }

    private fun grantPendingReward() {
        val action = pendingRewardAction
        pendingRewardAction = null
        action?.invoke()
    }
}