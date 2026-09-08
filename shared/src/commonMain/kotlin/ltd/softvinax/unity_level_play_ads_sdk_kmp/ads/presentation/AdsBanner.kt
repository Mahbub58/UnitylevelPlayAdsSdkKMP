package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.config.AdsConfig
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.config.platformAdsConfig
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.domain.AdPlacementPolicy
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.domain.AdsCoordinator
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.domain.AdsManager
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.domain.AdsState
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.infrastructure.adsInject
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.infrastructure.initAdsKoin

/**
 * Reusable LevelPlay banner for common code. Can be dropped on any page/screen:
 *
 * ```kotlin
 * // Default banner (configured ad unit): classic bottom placement
 * AdsBanner(Modifier.align(Alignment.BottomCenter).fillMaxWidth())
 *
 * // Banner with an explicit ad unit, anywhere inside a scrollable page:
 * AdsBanner(adUnitId = myBannerAdUnitId)
 * ```
 *
 * Each instance owns its banner lifecycle and is destroyed when it leaves composition,
 * so multiple pages can each host their own banner independently.
 */
@Composable
fun AdsBanner(
    modifier: Modifier = Modifier,
    adUnitId: String? = null,
) {
    val resolvedAdUnitId = adUnitId ?: remember { platformAdsConfig().bannerAdUnitId }
    PlatformAdsBannerSurface(adUnitId = resolvedAdUnitId, modifier = modifier)
}

/**
 * Platform decoration for [AdsBanner]. Implemented per platform
 * (androidMain/iosMain) as an AndroidView/UIKitView hosting the banner.
 */
@Composable
expect fun PlatformAdsBannerSurface(
    adUnitId: String,
    modifier: Modifier,
)

/**
 * Resolves the shared [AdsManager] singleton, initializing dependency injection
 * on first access. Use this as the single entry point for ads from any screen.
 */
@Composable
fun rememberAdsManager(): AdsManager {
    initAdsKoin()
    return remember { adsInject<AdsManager>() }
}

/**
 * Builds an [AdsCoordinator] wired to the shared [AdsManager] and the platform
 * ad configuration. Pass a custom [config] to drive a different app key/placements.
 */
@Composable
fun rememberAdsCoordinator(
    adsManager: AdsManager = rememberAdsManager(),
    config: AdsConfig = remember { platformAdsConfig() },
    policy: AdPlacementPolicy = remember { AdPlacementPolicy() },
): AdsCoordinator = remember(adsManager, config, policy) {
    AdsCoordinator(adsManager, config, policy)
}

/** Short human-readable label of the current init state, for status UI/logging. */
fun AdsState.toStatusLabel(): String = when (this) {
    is AdsState.Idle -> "Idle"
    is AdsState.Loading -> "Loading..."
    is AdsState.Ready -> "Ready"
    is AdsState.Failed -> "Failed: ${error.message}"
}