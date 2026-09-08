package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.data

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.FrameLayout
import android.view.View
import com.unity3d.mediation.LevelPlay
import com.unity3d.mediation.LevelPlayAdSize
import com.unity3d.mediation.LevelPlayAdError
import com.unity3d.mediation.LevelPlayAdInfo
import com.unity3d.mediation.LevelPlayConfiguration
import com.unity3d.mediation.LevelPlayInitError
import com.unity3d.mediation.LevelPlayInitListener
import com.unity3d.mediation.LevelPlayInitRequest
import com.unity3d.mediation.LevelPlayPrivacySettings
import com.unity3d.mediation.banner.LevelPlayBannerAdView
import com.unity3d.mediation.banner.LevelPlayBannerAdViewListener
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAd
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAdListener
import com.unity3d.mediation.rewarded.LevelPlayReward
import com.unity3d.mediation.rewarded.LevelPlayRewardedAd
import com.unity3d.mediation.rewarded.LevelPlayRewardedAdListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.config.AdsConfig
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.config.AdsCredentials
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.consent.ConsentResult
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.domain.AdFormat
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.domain.AdsError
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.domain.AdsInitResult
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.domain.AdsInitState
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.domain.AdsManager
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.domain.AdsState

private const val LEGACY_NO_FILL_ERROR_CODE = 26
private const val LOAD_TIMEOUT_MS = 30_000L
private const val BANNER_RETRY_DELAY_MS = 10_000L
private const val MAX_BANNER_RETRIES = 5
private const val CONSENT_NETWORK_UNITY = "UnityAds"
private const val CONSENT_NETWORK_IRONSOURCE = "IronSource"
private const val TAG = "LevelPlayAds"

/**
 * LevelPlay (ironSource) ads manager. Each banner ad unit maps to its own
 * [LevelPlayBannerAdView] instance, enabling multiple screens to host banners
 * independently. Interstitial and rewarded ads are managed as singletons and
 * auto-reload after being closed.
 */
class LevelPlayAdsManager(
    private val config: AdsConfig,
) : AdsManager, PlatformBannerAds {

    private val _initState = MutableStateFlow<AdsInitState>(AdsInitState.Idle)
    override val initState: StateFlow<AdsInitState> = _initState

    private val _interstitialState = MutableStateFlow<AdsState>(AdsState.Idle)
    override val interstitialState: StateFlow<AdsState> = _interstitialState

    private val _rewardedState = MutableStateFlow<AdsState>(AdsState.Idle)
    override val rewardedState: StateFlow<AdsState> = _rewardedState

    private val _bannerState = MutableStateFlow<AdsState>(AdsState.Idle)
    override val bannerState: StateFlow<AdsState> = _bannerState

    private var interstitialAd: LevelPlayInterstitialAd? = null
    private var rewardedAd: LevelPlayRewardedAd? = null
    private var pendingRewardEarned: (() -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private var interstitialTimeout: Runnable? = null
    private var rewardedTimeout: Runnable? = null

    private class BannerInstance(val view: LevelPlayBannerAdView) {
        var retryCount = 0
        var loadTimeout: Runnable? = null
    }

    private val bannerInstances = mutableMapOf<String, BannerInstance>()

    private fun scheduleLoadTimeout(
        format: AdFormat,
        timeoutHolder: (Runnable) -> Unit,
        cancelPrevious: () -> Unit,
        fail: () -> Unit,
    ) {
        val timeout = Runnable { fail() }
        cancelPrevious()
        timeoutHolder(timeout)
        mainHandler.postDelayed(timeout, LOAD_TIMEOUT_MS)
    }

    // ---- Init ----

    override fun init(appKey: String, consent: ConsentResult, onReady: (AdsInitResult) -> Unit) {
        if (appKey.isBlank()) {
            val error = AdsError.Init("LevelPlay App Key is not configured. See README.")
            _initState.value = AdsInitState.Failed(error)
            onReady(AdsInitResult.Failure(error))
            return
        }
        Log.i(
            TAG,
            "Initializing LevelPlay SDK with appKey=$appKey " +
                "consent{canRequestAds=${consent.canRequestAds} doNotSell=${consent.doNotSell} " +
                "childDirected=${consent.childDirected}}",
        )
        _initState.value = AdsInitState.Initializing
        applyPrivacySettings(consent)
        if (AdsCredentials.ENABLE_TEST_SUITE) {
            activity()?.let { LevelPlay.validateIntegration(it) }
            LevelPlay.setMetaData("is_test_suite", "enable")
        }
        val request = LevelPlayInitRequest.Builder(appKey).build()
        LevelPlay.init(context(), request, object : LevelPlayInitListener {
            override fun onInitSuccess(configuration: LevelPlayConfiguration) {
                Log.i(TAG, "LevelPlay SDK initialized. provider=sdk")
                _initState.value = AdsInitState.Ready
                onReady(AdsInitResult.Success)
                if (AdsCredentials.ENABLE_TEST_SUITE) {
                    activity()?.let { LevelPlay.launchTestSuite(it) }
                }
            }

            override fun onInitFailed(error: LevelPlayInitError) {
                Log.e(TAG, "LevelPlay SDK init failed: code=${error.errorCode} message=${error.errorMessage}")
                val adError = AdsError.Init(error.errorMessage, error.errorCode)
                _initState.value = AdsInitState.Failed(adError)
                onReady(AdsInitResult.Failure(adError))
            }
        })
    }

    // ---- Interstitial ----

    override fun loadInterstitial(adUnitId: String) {
        if (adUnitId.isBlank()) {
            _interstitialState.value = AdsState.Failed(
                AdsError.Load("Interstitial Ad Unit ID is not configured. See README.", AdFormat.INTERSTITIAL, isNoFill = false)
            )
            return
        }
        Log.i(TAG, "Loading interstitial with adUnitId=$adUnitId")
        _interstitialState.value = AdsState.Loading
        scheduleLoadTimeout(
            AdFormat.INTERSTITIAL,
            timeoutHolder = { interstitialTimeout = it },
            cancelPrevious = { interstitialTimeout?.let { r -> mainHandler.removeCallbacks(r) } },
            fail = {
                _interstitialState.value = AdsState.Failed(
                    AdsError.Load(
                        "Interstitial load timed out after ${LOAD_TIMEOUT_MS / 1000}s. " +
                            "Make sure this ad unit is active (not 'temp') and approved in the LevelPlay dashboard.",
                        AdFormat.INTERSTITIAL,
                        isNoFill = true,
                    )
                )
            },
        )
        val ad = LevelPlayInterstitialAd(adUnitId)
        interstitialAd = ad
        ad.setListener(object : LevelPlayInterstitialAdListener {
            override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                interstitialTimeout?.let { mainHandler.removeCallbacks(it) }
                interstitialTimeout = null
                Log.i(TAG, "Interstitial loaded: ${adInfo.adUnitId}")
                _interstitialState.value = AdsState.Ready
            }

            override fun onAdLoadFailed(error: LevelPlayAdError) {
                interstitialTimeout?.let { mainHandler.removeCallbacks(it) }
                interstitialTimeout = null
                Log.e(TAG, "Interstitial load failed: code=${error.errorCode} message=${error.errorMessage}")
                _interstitialState.value = AdsState.Failed(error.toLoadError(AdFormat.INTERSTITIAL))
            }

            override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {
                Log.i(TAG, "Interstitial displayed")
            }

            override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                Log.e(TAG, "Interstitial display failed: ${error.errorMessage}")
                _interstitialState.value = AdsState.Failed(error.toShowError(AdFormat.INTERSTITIAL))
            }

            override fun onAdClicked(adInfo: LevelPlayAdInfo) = Unit

            override fun onAdClosed(adInfo: LevelPlayAdInfo) {
                Log.i(TAG, "Interstitial closed")
                _interstitialState.value = AdsState.Idle
                if (config.isConfigured) {
                    loadInterstitial(config.interstitialAdUnitId)
                }
            }

            override fun onAdInfoChanged(adInfo: LevelPlayAdInfo) = Unit
        })
        ad.loadAd()
    }

    override fun showInterstitial() {
        val ad = interstitialAd
        if (ad == null || !ad.isAdReady) {
            _interstitialState.value = AdsState.Failed(
                AdsError.Show("Interstitial is not ready", AdFormat.INTERSTITIAL)
            )
            return
        }
        Log.i(TAG, "Showing interstitial")
        ad.showAd(activity() ?: return)
    }

    override fun isInterstitialReady(): Boolean = interstitialAd?.isAdReady == true

    // ---- Rewarded ----

    override fun loadRewarded(adUnitId: String) {
        if (adUnitId.isBlank()) {
            _rewardedState.value = AdsState.Failed(
                AdsError.Load("Rewarded Ad Unit ID is not configured. See README.", AdFormat.REWARDED, isNoFill = false)
            )
            return
        }
        Log.i(TAG, "Loading rewarded with adUnitId=$adUnitId")
        _rewardedState.value = AdsState.Loading
        scheduleLoadTimeout(
            AdFormat.REWARDED,
            timeoutHolder = { rewardedTimeout = it },
            cancelPrevious = { rewardedTimeout?.let { r -> mainHandler.removeCallbacks(r) } },
            fail = {
                _rewardedState.value = AdsState.Failed(
                    AdsError.Load(
                        "Rewarded load timed out after ${LOAD_TIMEOUT_MS / 1000}s. " +
                            "Make sure this ad unit is active (not 'temp') and approved in the LevelPlay dashboard.",
                        AdFormat.REWARDED,
                        isNoFill = true,
                    )
                )
            },
        )
        val ad = LevelPlayRewardedAd(adUnitId)
        rewardedAd = ad
        ad.setListener(object : LevelPlayRewardedAdListener {
            override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                rewardedTimeout?.let { mainHandler.removeCallbacks(it) }
                rewardedTimeout = null
                Log.i(TAG, "Rewarded loaded: ${adInfo.adUnitId}")
                _rewardedState.value = AdsState.Ready
            }

            override fun onAdLoadFailed(error: LevelPlayAdError) {
                rewardedTimeout?.let { mainHandler.removeCallbacks(it) }
                rewardedTimeout = null
                Log.e(TAG, "Rewarded load failed: code=${error.errorCode} message=${error.errorMessage}")
                _rewardedState.value = AdsState.Failed(error.toLoadError(AdFormat.REWARDED))
            }

            override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {
                Log.i(TAG, "Rewarded displayed")
            }

            override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                Log.e(TAG, "Rewarded display failed: ${error.errorMessage}")
                _rewardedState.value = AdsState.Failed(error.toShowError(AdFormat.REWARDED))
            }

            override fun onAdClicked(adInfo: LevelPlayAdInfo) = Unit

            override fun onAdClosed(adInfo: LevelPlayAdInfo) {
                Log.i(TAG, "Rewarded closed")
                _rewardedState.value = AdsState.Idle
                if (config.isConfigured) {
                    loadRewarded(config.rewardedAdUnitId)
                }
            }

            override fun onAdInfoChanged(adInfo: LevelPlayAdInfo) = Unit

            override fun onAdRewarded(reward: LevelPlayReward, adInfo: LevelPlayAdInfo) {
                Log.i(TAG, "Rewarded earned: ${reward.amount} ${reward.name}")
                pendingRewardEarned?.invoke()
                pendingRewardEarned = null
            }
        })
        ad.loadAd()
    }

    override fun showRewarded(onRewardEarned: () -> Unit) {
        val ad = rewardedAd
        if (ad == null || !ad.isAdReady) {
            _rewardedState.value = AdsState.Failed(
                AdsError.Show("Rewarded ad is not ready", AdFormat.REWARDED)
            )
            return
        }
        Log.i(TAG, "Showing rewarded")
        pendingRewardEarned = onRewardEarned
        ad.showAd(activity() ?: return)
    }

    override fun isRewardedReady(): Boolean = rewardedAd?.isAdReady == true

    // ---- Banner (multi-instance) ----

    override fun provideBannerView(context: Context, adUnitId: String): View {
        if (adUnitId.isBlank()) {
            _bannerState.value = AdsState.Failed(
                AdsError.Load("Banner Ad Unit ID is not configured. See README.", AdFormat.BANNER, isNoFill = false)
            )
            return FrameLayout(context)
        }
        bannerInstances[adUnitId]?.let { return it.view }

        Log.i(TAG, "Creating banner with adUnitId=$adUnitId")
        val bannerWidthDp = screenWidthDp(context)
        val adaptiveSize = LevelPlayAdSize.createAdaptiveAdSize(context, bannerWidthDp)
            ?: LevelPlayAdSize.createCustomSize(bannerWidthDp, 50)
        Log.i(TAG, "Banner size: width=${adaptiveSize.width}dp height=${adaptiveSize.height}dp")
        val banner = LevelPlayBannerAdView(
            context,
            adUnitId,
            LevelPlayBannerAdView.Config(adaptiveSize, null, null),
        )
        banner.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        )
        val instance = BannerInstance(banner)
        banner.setBannerListener(object : LevelPlayBannerAdViewListener {
            override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                cancelBannerTimeout(adUnitId)
                bannerInstances[adUnitId]?.let { it.retryCount = 0 }
                Log.i(TAG, "Banner loaded: ${adInfo.adUnitId}")
                _bannerState.value = AdsState.Ready
            }

            override fun onAdLoadFailed(error: LevelPlayAdError) {
                cancelBannerTimeout(adUnitId)
                Log.e(TAG, "Banner load failed: code=${error.errorCode} message=${error.errorMessage}")
                val current = bannerInstances[adUnitId]
                if (current != null && error.isNoFill() && current.retryCount < MAX_BANNER_RETRIES) {
                    current.retryCount++
                    Log.i(TAG, "Banner no-fill, retrying (${current.retryCount}/$MAX_BANNER_RETRIES)")
                    _bannerState.value = AdsState.Loading
                    mainHandler.postDelayed({
                        if (bannerInstances[adUnitId] != null) {
                            scheduleBannerTimeout(adUnitId)
                            current.view.loadAd()
                        }
                    }, BANNER_RETRY_DELAY_MS)
                } else {
                    _bannerState.value = AdsState.Failed(error.toLoadError(AdFormat.BANNER))
                }
            }

            override fun onAdDisplayed(adInfo: LevelPlayAdInfo) = Unit

            override fun onAdDisplayFailed(adInfo: LevelPlayAdInfo, error: LevelPlayAdError) {
                _bannerState.value = AdsState.Failed(error.toShowError(AdFormat.BANNER))
            }

            override fun onAdClicked(adInfo: LevelPlayAdInfo) = Unit

            override fun onAdExpanded(adInfo: LevelPlayAdInfo) = Unit

            override fun onAdCollapsed(adInfo: LevelPlayAdInfo) = Unit

            override fun onAdLeftApplication(adInfo: LevelPlayAdInfo) = Unit
        })
        bannerInstances[adUnitId] = instance
        _bannerState.value = AdsState.Loading
        scheduleBannerTimeout(adUnitId)
        mainHandler.postDelayed({
            bannerInstances[adUnitId]?.view?.let { bannerView ->
                Log.i(TAG, "Delayed banner load after SDK warmup")
                bannerView.loadAd()
            }
        }, 3000)
        return banner
    }

    override fun destroyBanner(adUnitId: String) {
        val instance = bannerInstances.remove(adUnitId) ?: return
        cancelBannerTimeout(adUnitId)
        instance.view.destroy()
        Log.i(TAG, "Banner destroyed: $adUnitId")
        if (bannerInstances.isEmpty()) {
            _bannerState.value = AdsState.Idle
        }
    }

    private fun scheduleBannerTimeout(adUnitId: String) {
        cancelBannerTimeout(adUnitId)
        val timeout = Runnable {
            _bannerState.value = AdsState.Failed(
                AdsError.Load(
                    "Banner load timed out after ${LOAD_TIMEOUT_MS / 1000}s. " +
                        "Make sure this ad unit is active (not 'temp') and approved in the LevelPlay dashboard.",
                    AdFormat.BANNER,
                    isNoFill = true,
                )
            )
        }
        bannerInstances[adUnitId]?.loadTimeout = timeout
        mainHandler.postDelayed(timeout, LOAD_TIMEOUT_MS)
    }

    private fun cancelBannerTimeout(adUnitId: String) {
        bannerInstances[adUnitId]?.loadTimeout?.let { mainHandler.removeCallbacks(it) }
        bannerInstances[adUnitId]?.loadTimeout = null
    }

    // ---- Platform host ----

    private fun context() = AndroidAdsHost.context
        ?: throw IllegalStateException("AndroidAdsHost context not set. Call AndroidAdsHost.setContext() in Application.onCreate()")

    private fun activity(): Activity? = AndroidAdsHost.activity

    private fun applyPrivacySettings(consent: ConsentResult) {
        runCatching {
            LevelPlayPrivacySettings.setGDPRConsents(
                mapOf(
                    CONSENT_NETWORK_UNITY to consent.canRequestAds,
                    CONSENT_NETWORK_IRONSOURCE to consent.canRequestAds,
                ),
            )
            LevelPlayPrivacySettings.setCCPA(consent.doNotSell)
            LevelPlayPrivacySettings.setCOPPA(consent.childDirected)
            Log.i(
                TAG,
                "Privacy settings applied via LevelPlayPrivacySettings: " +
                    "GDPR=${consent.canRequestAds} CCPA-doNotSell=${consent.doNotSell} " +
                    "COPPA-childDirected=${consent.childDirected}",
            )
        }.onFailure {
            Log.e(TAG, "Failed to apply privacy settings: ${it.message}", it)
        }
    }
}

private fun LevelPlayAdError.toLoadError(format: AdFormat): AdsError.Load =
    AdsError.Load(
        message = errorMessage,
        format = format,
        isNoFill = isNoFill(),
        errorCode = errorCode,
    )

private fun LevelPlayAdError.toShowError(format: AdFormat): AdsError.Show =
    AdsError.Show(
        message = errorMessage,
        format = format,
        errorCode = errorCode,
    )

private fun LevelPlayAdError.isNoFill(): Boolean =
    errorCode == LEGACY_NO_FILL_ERROR_CODE ||
        errorMessage.lowercase().contains("no fill") ||
        errorMessage.lowercase().contains("no_fill")

private fun screenWidthDp(context: Context): Int =
    (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt()