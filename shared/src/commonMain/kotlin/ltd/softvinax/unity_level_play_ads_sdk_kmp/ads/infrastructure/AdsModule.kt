package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.infrastructure

import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.config.platformAdsConfig
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.domain.AdsManager
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

val adsModule = module {
    single<AdsManager> { createPlatformAdsManager(platformAdsConfig()) }
}

private var koinStarted = false

fun initAdsKoin() {
    if (!koinStarted) {
        startKoin { modules(adsModule) }
        koinStarted = true
    }
}

inline fun <reified T> adsInject(): T = KoinPlatform.getKoin().get()