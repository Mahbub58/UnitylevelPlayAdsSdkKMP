package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads

import android.app.Activity
import android.content.Context
import android.widget.FrameLayout
import com.unity3d.mediation.LevelPlay
import com.unity3d.mediation.LevelPlayAdError
import com.unity3d.mediation.LevelPlayAdInfo
import com.unity3d.mediation.LevelPlayConfiguration
import com.unity3d.mediation.LevelPlayInitError
import com.unity3d.mediation.LevelPlayInitListener
import com.unity3d.mediation.LevelPlayInitRequest
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

    override fun init(appKey: String, onReady: (AdsInitResult) -> Unit) {
        if (appKey.isBlank()) {
            val error = AdsError.Init("LevelPlay App Key is not configured. See README.")
            _initState.value = AdsInitState.Failed(error)
            onReady(AdsInitResult.Failure(error))
            return
        }
        _initState.value = AdsInitState.Initializing
        val request = LevelPlayInitRequest.Builder(appKey).build()
        LevelPlay.init(context(), request, object : LevelPlayInitListener {
            override fun onInitSuccess(configuration: LevelPlayConfiguration) {
                _initState.value = AdsInitState.Ready
                onReady(AdsInitResult.Success)
            }

            override fun onInitFailed(error: LevelPlayInitError) {
                val adError = AdsError.Init(error.errorMessage, error.errorCode)
                _initState.value = AdsInitState.Failed(adError)
                onReady(AdsInitResult.Failure(adError))
            }
        })
    }

    override fun loadInterstitial(adUnitId: String) {
        if (adUnitId.isBlank()) {
            _interstitialState.value = AdsState.Failed(
                AdsError.Load("Interstitial Ad Unit ID is not configured. See README.", AdFormat.INTERSTITIAL, isNoFill = false)
            )
            return
        }
        val ad = LevelPlayInterstitialAd(adUnitId)
        ad.setListener(object : LevelPlayInterstitialAdListener {
            override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                _interstitialState.value = AdsState.Ready
            }

            override fun onAdLoadFailed(error: LevelPlayAdError) {
                _interstitialState.value = AdsState.Failed(error.toLoadError(AdFormat.INTERSTITIAL))
            }

            override fun onAdDisplayed(adInfo: LevelPlayAdInfo) = Unit

            override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                _interstitialState.value = AdsState.Failed(error.toShowError(AdFormat.INTERSTITIAL))
            }

            override fun onAdClicked(adInfo: LevelPlayAdInfo) = Unit

            override fun onAdClosed(adInfo: LevelPlayAdInfo) {
                ad.loadAd()
            }

            override fun onAdInfoChanged(adInfo: LevelPlayAdInfo) = Unit
        })
        interstitialAd = ad
        _interstitialState.value = AdsState.Loading
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
        val ad = LevelPlayRewardedAd(adUnitId)
        ad.setListener(object : LevelPlayRewardedAdListener {
            override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                _rewardedState.value = AdsState.Ready
            }

            override fun onAdLoadFailed(error: LevelPlayAdError) {
                _rewardedState.value = AdsState.Failed(error.toLoadError(AdFormat.REWARDED))
            }

            override fun onAdDisplayed(adInfo: LevelPlayAdInfo) = Unit

            override fun onAdRewarded(reward: LevelPlayReward, adInfo: LevelPlayAdInfo) {
                pendingRewardEarned?.invoke()
                pendingRewardEarned = null
            }

            override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                _rewardedState.value = AdsState.Failed(error.toShowError(AdFormat.REWARDED))
            }

            override fun onAdClicked(adInfo: LevelPlayAdInfo) = Unit

            override fun onAdClosed(adInfo: LevelPlayAdInfo) {
                ad.loadAd()
            }

            override fun onAdInfoChanged(adInfo: LevelPlayAdInfo) = Unit
        })
        rewardedAd = ad
        _rewardedState.value = AdsState.Loading
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
        _bannerState.value = AdsState.Loading
        val banner = LevelPlayBannerAdView(context, adUnitId)
        banner.setBannerListener(object : LevelPlayBannerAdViewListener {
            override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                _bannerState.value = AdsState.Ready
            }

            override fun onAdLoadFailed(error: LevelPlayAdError) {
                _bannerState.value = AdsState.Failed(error.toLoadError(AdFormat.BANNER))
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
        banner.loadAd()
        return banner
    }

    fun isBannerReady(): Boolean = _bannerState.value is AdsState.Ready

    fun destroyBanner() {
        bannerAd?.destroy()
        bannerAd = null
        _bannerState.value = AdsState.Idle
    }

    private fun context() = AndroidAdsHost.context
        ?: throw IllegalStateException("AndroidAdsHost context not set. Call AndroidAdsHost.setContext() in Application.onCreate()")

    private fun activity(): Activity? = AndroidAdsHost.activity
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