package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.presentation

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
import androidx.compose.ui.viewinterop.AndroidView
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.data.PlatformBannerAds
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.domain.AdsManager
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.domain.AdsState
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.infrastructure.adsInject

@Composable
actual fun PlatformAdsBannerSurface(
    adUnitId: String,
    modifier: Modifier,
) {
    val manager = remember { adsInject<AdsManager>() as? PlatformBannerAds }
    if (manager == null) {
        Box(modifier = modifier.height(50.dp))
        return
    }

    val bannerReady by manager.bannerState.collectAsState()
    val ready = bannerReady is AdsState.Ready

    DisposableEffect(adUnitId) {
        onDispose { manager.destroyBanner(adUnitId) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (ready) Modifier.wrapContentHeight() else Modifier.height(0.dp)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            factory = { context -> manager.provideBannerView(context, adUnitId) },
        )
    }
}