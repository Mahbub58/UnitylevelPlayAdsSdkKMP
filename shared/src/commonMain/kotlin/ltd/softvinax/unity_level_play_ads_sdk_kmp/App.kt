package ltd.softvinax.unity_level_play_ads_sdk_kmp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.AdsBanner
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.AdsCoordinator
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.AdsInitState
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.AdsManager
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.AdsState
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.adsInject
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.initAdsKoin
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.platformAdsConfig

@Composable
@Preview
fun App() {
    MaterialTheme {
        val adsManager = remember { initAdsKoin(); adsInject<AdsManager>() }
        val coordinator = remember { AdsCoordinator(adsManager, platformAdsConfig()) }

        LaunchedEffect(Unit) {
            coordinator.start(onReady = {})
        }

        val initState by coordinator.initState.collectAsState()
        val interstitialState by coordinator.interstitialState.collectAsState()
        val rewardedState by coordinator.rewardedState.collectAsState()
        val bannerState by coordinator.bannerState.collectAsState()
        var coins by remember { mutableStateOf(0) }
        var lastNotice by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize()
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text("LevelPlay Ads", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("SDK: ${initState.label()}")
            Text("Interstitial: ${interstitialState.label()}")
            Text("Rewarded: ${rewardedState.label()}")
            Text("Banner: ${bannerState.label()}")
            Spacer(Modifier.height(8.dp))
            if (initState is AdsInitState.Ready) {
                AdsBanner()
            } else {
                Spacer(Modifier.height(50.dp))
            }

            Spacer(Modifier.height(24.dp))

            Text("Interstitial", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    coordinator.loadInterstitial()
                    lastNotice = "Interstitial load requested"
                }) {
                    Text("Load")
                }
                Button(onClick = {
                    lastNotice = if (coordinator.showInterstitial()) {
                        "Interstitial shown"
                    } else {
                        "Interstitial not ready - load first"
                    }
                }) {
                    Text("Show")
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Rewarded", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    coordinator.loadRewarded()
                    lastNotice = "Rewarded load requested"
                }) {
                    Text("Load")
                }
                Button(onClick = {
                    lastNotice = if (coordinator.showRewarded { coins += 1 }) {
                        "Rewarded shown - you earned 1 coin"
                    } else {
                        "Rewarded not ready - load first"
                    }
                }) {
                    Text("Show")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Coins: $coins")

            Spacer(Modifier.height(24.dp))
            Text(
                text = lastNotice,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun AdsInitState.label(): String = when (this) {
    is AdsInitState.Idle -> "Idle"
    is AdsInitState.Initializing -> "Initializing..."
    is AdsInitState.Ready -> "Ready"
    is AdsInitState.Failed -> "Failed: ${error.message}"
}

private fun AdsState.label(): String = when (this) {
    is AdsState.Idle -> "Idle"
    is AdsState.Loading -> "Loading..."
    is AdsState.Ready -> "Ready"
    is AdsState.Failed -> "Failed: ${error.message}"
}