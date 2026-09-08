package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.consent

import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.config.AdsCredentials

/**
 * Outcome of the platform consent flow (Google User Messaging Platform),
 * mapped to the values LevelPlay's consent APIs expect.
 *
 * - [canRequestAds] is the GDPR consent signal (true = user granted consent / ads may be requested).
 * - [doNotSell] is the CCPA signal (true = user opted out of the sale of personal information).
 * - [childDirected] is the COPPA signal (true = app is child directed).
 */
data class ConsentResult(
    val canRequestAds: Boolean,
    val doNotSell: Boolean,
    val childDirected: Boolean,
) {
    companion object {
        /** Fallback used when the consent SDK is unavailable or the flow fails. */
        fun default(): ConsentResult = ConsentResult(
            canRequestAds = AdsCredentials.GDPR_CONSENT,
            doNotSell = AdsCredentials.CCPA_DO_NOT_SELL,
            childDirected = AdsCredentials.ENABLE_COPPA,
        )
    }
}

/**
 * Runs the platform consent flow (Google UMP: SDK-provided consent form for GDPR/CCPA/COPPA)
 * and delivers the mapped [ConsentResult]. Invoked on the main thread; `onComplete` may be
 * called asynchronously. `null` means the flow failed and callers should fall back to
 * [ConsentResult.default].
 */
expect fun requestConsent(onComplete: (ConsentResult?) -> Unit)