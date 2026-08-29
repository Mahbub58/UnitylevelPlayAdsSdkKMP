package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AdsBanner(modifier: Modifier = Modifier) {
    PlatformAdsBannerSurface(
        adUnitId = platformAdsConfig().bannerAdUnitId,
        modifier = modifier,
    )
}

@Composable
expect fun PlatformAdsBannerSurface(
    adUnitId: String,
    modifier: Modifier,
)