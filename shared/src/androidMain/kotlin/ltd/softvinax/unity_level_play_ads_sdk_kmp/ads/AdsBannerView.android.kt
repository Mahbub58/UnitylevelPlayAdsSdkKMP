package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

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

    DisposableEffect(Unit) {
        onDispose { manager.destroyBanner() }
    }

    AndroidView(
        modifier = modifier.height(50.dp),
        factory = { context -> manager.provideBannerView(context, adUnitId) },
    )
}