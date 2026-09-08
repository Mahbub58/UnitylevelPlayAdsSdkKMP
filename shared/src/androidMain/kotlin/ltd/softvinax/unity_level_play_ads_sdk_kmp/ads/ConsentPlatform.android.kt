package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

private const val TAG = "ConsentUMP"
private const val US_PRIVACY_STRING_KEY = "IABUSPrivacy_String"

actual fun requestConsent(onComplete: (ConsentResult?) -> Unit) {
    val activity = AndroidAdsHost.activity
    val context = AndroidAdsHost.context
    if (activity == null || context == null || activity.isFinishing || activity.isDestroyed) {
        Log.w(TAG, "Consent flow skipped: no active Activity/context; using defaults")
        onComplete(ConsentResult.default())
        return
    }

    Handler(Looper.getMainLooper()).post {
        try {
            val consentInformation = UserMessagingPlatform.getConsentInformation(context)

            val paramsBuilder = ConsentRequestParameters.Builder()
            if (AdsCredentials.DEBUG_FORCE_CONSENT_DIALOG) {
                val debug = ConsentDebugSettings.Builder(context)
                    .setDebugGeography(debugGeography())
                    .setForceTesting(true)
                    .build()
                paramsBuilder.setConsentDebugSettings(debug)
            }
            val params = paramsBuilder.build()

            consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                {
                    Log.i(
                        TAG,
                        "Consent info updated: status=${consentInformation.consentStatus} " +
                            "privacyOptions=${consentInformation.privacyOptionsRequirementStatus} " +
                            "formAvailable=${consentInformation.isConsentFormAvailable()} " +
                            "canRequestAds=${consentInformation.canRequestAds()}",
                    )
                    finishConsentFlow(activity, context, consentInformation, onComplete)
                },
                { error ->
                    Log.e(TAG, "Consent info update failed: code=${error.errorCode} ${error.message}")
                    onComplete(null)
                },
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Consent flow crashed: ${e.message}", e)
            onComplete(null)
        }
    }
}

private fun finishConsentFlow(
    activity: Activity,
    context: Context,
    consentInformation: ConsentInformation,
    onComplete: (ConsentResult?) -> Unit,
) {
    if (consentInformation.canRequestAds() || !consentInformation.isConsentFormAvailable()) {
        onComplete(toConsentResult(context, consentInformation))
        return
    }
    // UMP SDK-provided consent form (GDPR/CCPA). No-op on the next run loop if form not required.
    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
        Log.i(TAG, "Consent form dismissed; canRequestAds=${consentInformation.canRequestAds()}")
        onComplete(toConsentResult(context, consentInformation))
    }
}

private fun toConsentResult(context: Context, consentInformation: ConsentInformation): ConsentResult =
    ConsentResult(
        canRequestAds = consentInformation.canRequestAds(),
        // CCPA do-not-sell is taken from the UMP-provided US Privacy String when present.
        doNotSell = usPrivacyDoNotSell(context) ?: AdsCredentials.CCPA_DO_NOT_SELL,
        childDirected = AdsCredentials.ENABLE_COPPA,
    )

/**
 * Reads the IAB US Privacy String written by the UMP SDK after the CCPA/US-state
 * consent form. Second character 'Y' = user opted out of the sale of personal
 * information. Returns null when the string is absent (not a US-regulated region).
 */
private fun usPrivacyDoNotSell(context: Context): Boolean? {
    val prefs = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
    val usPrivacy = prefs.getString(US_PRIVACY_STRING_KEY, null) ?: return null
    return usPrivacy.length >= 2 && usPrivacy[1] == 'Y'
}

private fun debugGeography(): Int =
    when (AdsCredentials.DEBUG_CONSENT_GEOGRAPHY) {
        "EEA" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
        "US" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE
        else -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_DISABLED
    }