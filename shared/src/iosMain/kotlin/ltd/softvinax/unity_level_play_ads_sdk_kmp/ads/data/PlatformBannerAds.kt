package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.data

import kotlinx.coroutines.flow.StateFlow
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.domain.AdsState
import platform.UIKit.UIView

/**
 * Internal port implemented by the platform ads manager so the banner composable
 * (presentation layer) can create/destroy per-instance banner views without
 * depending on the concrete manager implementation.
 */
internal interface PlatformBannerAds {
    val bannerState: StateFlow<AdsState>
    fun createBannerView(adUnitId: String): UIView?
    fun destroyBanner(adUnitId: String)
}