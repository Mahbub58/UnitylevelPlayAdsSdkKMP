package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.domain

import kotlinx.coroutines.flow.StateFlow
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.consent.ConsentResult

interface AdsManager {

    fun init(appKey: String, consent: ConsentResult, onReady: (AdsInitResult) -> Unit)

    fun loadInterstitial(adUnitId: String)
    fun showInterstitial()
    fun isInterstitialReady(): Boolean

    fun loadRewarded(adUnitId: String)
    fun showRewarded(onRewardEarned: () -> Unit)
    fun isRewardedReady(): Boolean

    val initState: StateFlow<AdsInitState>
    val interstitialState: StateFlow<AdsState>
    val rewardedState: StateFlow<AdsState>
    val bannerState: StateFlow<AdsState>
}