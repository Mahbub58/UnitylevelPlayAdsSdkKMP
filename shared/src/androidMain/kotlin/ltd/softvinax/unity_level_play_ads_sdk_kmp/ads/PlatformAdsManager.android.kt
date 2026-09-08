package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.FrameLayout
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

private const val LEGACY_NO_FILL_ERROR_CODE = 26
private const val LOAD_TIMEOUT_MS = 30_000L
private const val BANNER_RETRY_DELAY_MS = 10_000L
private const val MAX_BANNER_RETRIES = 5
private const val CONSENT_NETWORK_UNITY = "UnityAds"
private const val CONSENT_NETWORK_IRONSOURCE = "IronSource"
private const val TAG = "LevelPlayAds"

class LevelPlayAdsManager(
    config: AdsConfig,
) : AdsManager {

    private val config: AdsConfig = config

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
    private var bannerAd: LevelPlayBannerAdView? = null
    private var pendingRewardEarned: (() -> Unit)? = null
    private var bannerRetryCount = 0

    private val mainHandler = Handler(Looper.getMainLooper())

    private var interstitialTimeout: Runnable? = null
    private var rewardedTimeout: Runnable? = null
    private var bannerTimeout: Runnable? = null

    private fun scheduleLoadTimeout(format: AdFormat) {
        val timeout = Runnable {
            val message = "Ad load timed out after ${LOAD_TIMEOUT_MS / 1000}s. " +
                "Make sure this ad unit is active (not 'temp') and approved in the LevelPlay dashboard."
            when (format) {
                AdFormat.INTERSTITIAL -> _interstitialState.value = AdsState.Failed(
                    AdsError.Load(message, AdFormat.INTERSTITIAL, isNoFill = true)
                )
                AdFormat.REWARDED -> _rewardedState.value = AdsState.Failed(
                    AdsError.Load(message, AdFormat.REWARDED, isNoFill = true)
                )
                AdFormat.BANNER -> _bannerState.value = AdsState.Failed(
                    AdsError.Load(message, AdFormat.BANNER, isNoFill = true)
                )
            }
        }
        val previous = when (format) {
            AdFormat.INTERSTITIAL -> interstitialTimeout
            AdFormat.REWARDED -> rewardedTimeout
            AdFormat.BANNER -> bannerTimeout
        }
        previous?.let { mainHandler.removeCallbacks(it) }
        when (format) {
            AdFormat.INTERSTITIAL -> interstitialTimeout = timeout
            AdFormat.REWARDED -> rewardedTimeout = timeout
            AdFormat.BANNER -> bannerTimeout = timeout
        }
        mainHandler.postDelayed(timeout, LOAD_TIMEOUT_MS)
    }

    private fun cancelLoadTimeout(format: AdFormat) {
        val timeout = when (format) {
            AdFormat.INTERSTITIAL -> interstitialTimeout
            AdFormat.REWARDED -> rewardedTimeout
            AdFormat.BANNER -> bannerTimeout
        }
        timeout?.let { mainHandler.removeCallbacks(it) }
    }

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
                Log.i(TAG, "SDK init success")
                _initState.value = AdsInitState.Ready
                onReady(AdsInitResult.Success)
                if (AdsCredentials.ENABLE_TEST_SUITE) {
                    launchTestSuite()
                }
            }

            override fun onInitFailed(error: LevelPlayInitError) {
                Log.e(TAG, "SDK init failed: code=${error.errorCode} message=${error.errorMessage}")
                val adError = AdsError.Init(error.errorMessage, error.errorCode)
                _initState.value = AdsInitState.Failed(adError)
                onReady(AdsInitResult.Failure(adError))
            }
        })
    }

    private fun launchTestSuite() {
        val activity = activity()
        if (activity == null) {
            Log.w(TAG, "Test Suite not launched: no active Activity available")
            return
        }
        if (activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "Test Suite not launched: Activity is finishing/destroyed")
            return
        }
        mainHandler.post {
            try {
                LevelPlay.launchTestSuite(activity)
                Log.i(TAG, "LevelPlay Test Suite launched")
            } catch (e: Throwable) {
                Log.e(TAG, "LevelPlay Test Suite launch failed: ${e.message}", e)
            }
        }
    }

    override fun loadInterstitial(adUnitId: String) {
        if (adUnitId.isBlank()) {
            _interstitialState.value = AdsState.Failed(
                AdsError.Load("Interstitial Ad Unit ID is not configured. See README.", AdFormat.INTERSTITIAL, isNoFill = false)
            )
            return
        }
        Log.i(TAG, "Loading interstitial with adUnitId=$adUnitId")
        val ad = LevelPlayInterstitialAd(adUnitId)
        ad.setListener(object : LevelPlayInterstitialAdListener {
            override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                cancelLoadTimeout(AdFormat.INTERSTITIAL)
                Log.i(TAG, "Interstitial loaded: ${adInfo.adUnitId}")
                _interstitialState.value = AdsState.Ready
            }

            override fun onAdLoadFailed(error: LevelPlayAdError) {
                cancelLoadTimeout(AdFormat.INTERSTITIAL)
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
                Log.i(TAG, "Interstitial closed, reloading")
                ad.loadAd()
            }

            override fun onAdInfoChanged(adInfo: LevelPlayAdInfo) = Unit
        })
        interstitialAd = ad
        _interstitialState.value = AdsState.Loading
        scheduleLoadTimeout(AdFormat.INTERSTITIAL)
        ad.loadAd()
    }

    override fun showInterstitial() {
        val activity = activity()
        val ad = interstitialAd
        if (ad == null) {
            _interstitialState.value = AdsState.Failed(
                AdsError.Show("Interstitial not loaded", AdFormat.INTERSTITIAL)
            )
            return
        }
        if (activity == null) {
            _interstitialState.value = AdsState.Failed(
                AdsError.Show("No Activity available to present the ad", AdFormat.INTERSTITIAL)
            )
            return
        }
        if (ad.isAdReady) {
            Log.i(TAG, "Showing interstitial")
            ad.showAd(activity)
        } else {
            _interstitialState.value = AdsState.Failed(
                AdsError.Show("Interstitial is not ready", AdFormat.INTERSTITIAL)
            )
        }
    }

    override fun isInterstitialReady(): Boolean = interstitialAd?.isAdReady == true

    override fun loadRewarded(adUnitId: String) {
        if (adUnitId.isBlank()) {
            _rewardedState.value = AdsState.Failed(
                AdsError.Load("Rewarded Ad Unit ID is not configured. See README.", AdFormat.REWARDED, isNoFill = false)
            )
            return
        }
        Log.i(TAG, "Loading rewarded with adUnitId=$adUnitId")
        val ad = LevelPlayRewardedAd(adUnitId)
        ad.setListener(object : LevelPlayRewardedAdListener {
            override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                cancelLoadTimeout(AdFormat.REWARDED)
                Log.i(TAG, "Rewarded loaded: ${adInfo.adUnitId}")
                _rewardedState.value = AdsState.Ready
            }

            override fun onAdLoadFailed(error: LevelPlayAdError) {
                cancelLoadTimeout(AdFormat.REWARDED)
                Log.e(TAG, "Rewarded load failed: code=${error.errorCode} message=${error.errorMessage}")
                _rewardedState.value = AdsState.Failed(error.toLoadError(AdFormat.REWARDED))
            }

            override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {
                Log.i(TAG, "Rewarded displayed")
            }

            override fun onAdRewarded(reward: LevelPlayReward, adInfo: LevelPlayAdInfo) {
                cancelLoadTimeout(AdFormat.REWARDED)
                Log.i(TAG, "Rewarded earned: ${reward.name}")
                pendingRewardEarned?.invoke()
                pendingRewardEarned = null
            }

            override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                Log.e(TAG, "Rewarded display failed: ${error.errorMessage}")
                _rewardedState.value = AdsState.Failed(error.toShowError(AdFormat.REWARDED))
            }

            override fun onAdClicked(adInfo: LevelPlayAdInfo) = Unit

            override fun onAdClosed(adInfo: LevelPlayAdInfo) {
                Log.i(TAG, "Rewarded closed, reloading")
                ad.loadAd()
            }

            override fun onAdInfoChanged(adInfo: LevelPlayAdInfo) = Unit
        })
        rewardedAd = ad
        _rewardedState.value = AdsState.Loading
        scheduleLoadTimeout(AdFormat.REWARDED)
        ad.loadAd()
    }

    override fun showRewarded(onRewardEarned: () -> Unit) {
        val activity = activity()
        val ad = rewardedAd
        when {
            ad == null -> _rewardedState.value = AdsState.Failed(
                AdsError.Show("Rewarded ad not loaded", AdFormat.REWARDED)
            )
            activity == null -> _rewardedState.value = AdsState.Failed(
                AdsError.Show("No Activity available to present the ad", AdFormat.REWARDED)
            )
            !ad.isAdReady -> _rewardedState.value = AdsState.Failed(
                AdsError.Show("Rewarded ad is not ready", AdFormat.REWARDED)
            )
            else -> {
                Log.i(TAG, "Showing rewarded")
                pendingRewardEarned = onRewardEarned
                ad.showAd(activity)
            }
        }
    }

    override fun isRewardedReady(): Boolean = rewardedAd?.isAdReady == true

    fun provideBannerView(context: Context, adUnitId: String): FrameLayout {
        if (adUnitId.isBlank()) {
            _bannerState.value = AdsState.Failed(
                AdsError.Load("Banner Ad Unit ID is not configured. See README.", AdFormat.BANNER, isNoFill = false)
            )
            return FrameLayout(context)
        }
        Log.i(TAG, "Loading banner with adUnitId=$adUnitId")
        bannerRetryCount = 0
        val adaptiveSize = LevelPlayAdSize.createAdaptiveAdSize(context)
        val banner = LevelPlayBannerAdView(
            context,
            adUnitId,
            LevelPlayBannerAdView.Config(adaptiveSize, null, null),
        )
        banner.setBannerListener(object : LevelPlayBannerAdViewListener {
            override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                cancelLoadTimeout(AdFormat.BANNER)
                bannerRetryCount = 0
                Log.i(TAG, "Banner loaded: ${adInfo.adUnitId}")
                _bannerState.value = AdsState.Ready
            }

            override fun onAdLoadFailed(error: LevelPlayAdError) {
                cancelLoadTimeout(AdFormat.BANNER)
                Log.e(TAG, "Banner load failed: code=${error.errorCode} message=${error.errorMessage}")
                if (error.isNoFill() && bannerRetryCount < MAX_BANNER_RETRIES) {
                    bannerRetryCount++
                    Log.i(TAG, "Banner no-fill, retrying ($bannerRetryCount/$MAX_BANNER_RETRIES)")
                    _bannerState.value = AdsState.Loading
                    mainHandler.postDelayed({
                        if (bannerAd != null) {
                            scheduleLoadTimeout(AdFormat.BANNER)
                            banner.loadAd()
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
        bannerAd = banner
        _bannerState.value = AdsState.Loading
        scheduleLoadTimeout(AdFormat.BANNER)
        mainHandler.postDelayed({
            if (bannerAd != null) {
                Log.i(TAG, "Delayed banner load after SDK warmup")
                banner.loadAd()
            }
        }, 3000)
        return banner
    }

    fun isBannerReady(): Boolean = _bannerState.value is AdsState.Ready

    fun destroyBanner() {
        cancelLoadTimeout(AdFormat.BANNER)
        bannerRetryCount = 0
        bannerAd?.destroy()
        bannerAd = null
        _bannerState.value = AdsState.Idle
    }

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

actual fun createPlatformAdsManager(config: AdsConfig): AdsManager =
    LevelPlayAdsManager(config)

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