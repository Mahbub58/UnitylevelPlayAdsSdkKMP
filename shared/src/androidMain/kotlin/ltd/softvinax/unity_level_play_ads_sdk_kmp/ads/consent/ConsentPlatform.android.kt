package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.consent

import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.config.AdsCredentials

/**
 * Custom consent is handled by the app's own dialog.
 * Google UMP has been removed per Unity policy - custom CMP is allowed
 * as long as you gate LevelPlay init on your own consent result and
 * forward it via LPMPrivacySettings/LevelPlayPrivacySettings (done in AdsCoordinator).
 * Here we just return the configured defaults from AdsCredentials.
 */
actual fun requestConsent(onComplete: (ConsentResult?) -> Unit) {
    // If you have a custom dialog, resolve it before calling onComplete.
    // Example: showYourCustomDialog { userChoice -> onComplete(userChoice) }
    // For now, use the defaults configured in AdsCredentials.
    onComplete(ConsentResult.default())
}
