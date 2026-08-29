package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformAdsBannerSurface(
    adUnitId: String,
    modifier: Modifier,
) {
    val manager = remember { adsInject<AdsManager>() as? LevelPlayAdsManager }
    if (manager == null) {
        Box(modifier = modifier.height(50.dp))
        return
    }

    val bannerView = remember { manager.createBannerView(adUnitId) }

    DisposableEffect(Unit) {
        onDispose { manager.destroyBanner() }
    }

    if (bannerView != null) {
        UIKitView(
            factory = { bannerView },
            modifier = modifier.height(50.dp),
        )
    } else {
        Box(modifier = modifier.height(50.dp))
    }
}