package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.consent

import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.config.AdsCredentials

/**
 * Custom consent is handled by the app's own dialog.
 * Google UMP removed - see Android counterpart for Unity policy note.
 */
actual fun requestConsent(onComplete: (ConsentResult?) -> Unit) {
    onComplete(ConsentResult.default())
}
