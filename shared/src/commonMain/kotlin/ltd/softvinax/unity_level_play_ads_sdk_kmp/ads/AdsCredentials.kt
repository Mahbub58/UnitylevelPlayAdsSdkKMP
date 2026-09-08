package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads

object AdsCredentials {

    /**
     * Opt-in switch for the LevelPlay Test Suite. Default OFF so normal launches behave
     * like a production build: the app UI is not covered by the Test Suite and the SDK
     * runs with real ad-serving (is_test_suite metadata is NOT set).
     *
     * Set to `true` ONLY while validating an integration:
     *   - sets `is_test_suite` metadata before init (test mode)
     *   - auto-launches the Test Suite over the app after init success
     *   - forces the debug consent dialog region ([DEBUG_CONSENT_GEOGRAPHY])
     */
    const val ENABLE_TEST_SUITE: Boolean = false

    /**
     * Debug-only: force the Google UMP SDK consent dialog to appear on the first launch
     * of a test build (by enforcing [DEBUG_CONSENT_GEOGRAPHY] in consent debug settings).
     * Default OFF so normal launches follow the device's real region (no dialog, no blocker).
     *
     * Set to `true` to verify the SDK-provided consent form (GDPR/CCPA) during testing;
     * after the user answers, consent is persisted and later launches proceed normally.
     */
    const val DEBUG_FORCE_CONSENT_DIALOG: Boolean = false

    /**
     * GDPR consent passed to the SDK (and mediated networks). `true` = user granted consent.
     */
    const val GDPR_CONSENT: Boolean = true

    /**
     * CCPA do-not-sell: `true` = user opted out of the sale of personal information.
     * NOTE: `true` suppresses personalized demand and can reduce fill.
     */
    const val CCPA_DO_NOT_SELL: Boolean = false

    /**
     * COPPA (children's privacy): when enabled the SDK is told the app is child directed.
     * NOTE: this suppresses personalized/bidding demand and reduces fill on several networks.
     * Kept `false` for full demand; set to `true` only if the app really targets children.
     */
    const val ENABLE_COPPA: Boolean = false

    /**
     * Debug-only: which region the SDK consent dialog (Google UMP) is forced to in test builds.
     * Only used when [ENABLE_TEST_SUITE] is `true`; ignored in production.
     *
     *   "EEA"  -> GDPR (EU/EEA) consent form  (default: reliably presents the SDK dialog in test mode)
     *   "US"   -> CCPA / regulated US-state consent form (do-not-sell option)
     *   "NONE" -> follow the device's real location / regulators
     */
    const val DEBUG_CONSENT_GEOGRAPHY: String = "EEA"

    // ---- Android ----
    const val ANDROID_APP_KEY: String = "27d971f7d"
    const val ANDROID_INTERSTITIAL: String = "gxxgiao5omzaqz95"
    const val ANDROID_REWARDED: String = "8ow6cxhi3gcopyd9"
    const val ANDROID_BANNER: String = "0shs04ti2gakqd9k"

    // ---- iOS ----
    const val IOS_APP_KEY: String = "27d9bd7a5"
    const val IOS_INTERSTITIAL: String = "mm99t0zi6liu8caw"
    const val IOS_REWARDED: String = "vu8wedrf3cr1kc7z"
    const val IOS_BANNER: String = "vkgmtcqo3vjyslms"
}