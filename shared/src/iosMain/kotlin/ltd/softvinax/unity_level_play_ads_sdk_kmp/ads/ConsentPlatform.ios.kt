package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads

import LevelPlayAdsKit.LevelPlayAdsKitBridge
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSError
import platform.Foundation.NSLog

@OptIn(ExperimentalForeignApi::class)
actual fun requestConsent(onComplete: (ConsentResult?) -> Unit) {
    LevelPlayAdsKitBridge.requestConsentWithTestMode(
        AdsCredentials.DEBUG_FORCE_CONSENT_DIALOG,
        AdsCredentials.DEBUG_CONSENT_GEOGRAPHY,
    ) { canRequestAds, doNotSell, error ->
        val e: NSError? = error
        if (e != null && !canRequestAds) {
            NSLog("ConsentUMP: consent flow failed: ${e.localizedDescription}")
            onComplete(null)
        } else {
            onComplete(
                ConsentResult(
                    canRequestAds = canRequestAds,
                    doNotSell = doNotSell,
                    childDirected = AdsCredentials.ENABLE_COPPA,
                ),
            )
        }
    }
}