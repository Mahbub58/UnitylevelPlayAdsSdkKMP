package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
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

    val bannerReady by manager.bannerState.collectAsState()
    val ready = bannerReady is AdsState.Ready

    DisposableEffect(Unit) {
        onDispose { manager.destroyBanner() }
    }

    if (bannerView != null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .then(if (ready) Modifier.height(50.dp) else Modifier.height(0.dp)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            UIKitView(
                factory = { bannerView },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            )
        }
    } else {
        Box(modifier = modifier.height(50.dp))
    }
}