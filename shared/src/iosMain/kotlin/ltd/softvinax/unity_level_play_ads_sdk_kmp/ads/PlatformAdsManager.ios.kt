package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIView
import LevelPlayAdsKit.LevelPlayAdsKitBridge
import platform.Foundation.NSError

private const val EVENT_SDK_INIT_SUCCESS = 0
private const val EVENT_SDK_INIT_FAILED = 1
private const val EVENT_INTERSTITIAL_LOADED = 2
private const val EVENT_INTERSTITIAL_LOAD_FAILED = 3
private const val EVENT_INTERSTITIAL_DISPLAYED = 4
private const val EVENT_INTERSTITIAL_DISPLAY_FAILED = 5
private const val EVENT_INTERSTITIAL_CLICKED = 6
private const val EVENT_INTERSTITIAL_CLOSED = 7
private const val EVENT_REWARDED_LOADED = 8
private const val EVENT_REWARDED_LOAD_FAILED = 9
private const val EVENT_REWARDED_DISPLAYED = 10
private const val EVENT_REWARDED_DISPLAY_FAILED = 11
private const val EVENT_REWARDED_CLICKED = 12
private const val EVENT_REWARDED_CLOSED = 13
private const val EVENT_REWARDED_EARNED = 14
private const val EVENT_BANNER_LOADED = 15
private const val EVENT_BANNER_LOAD_FAILED = 16
private const val EVENT_BANNER_DISPLAYED = 17
private const val EVENT_BANNER_DISPLAY_FAILED = 18
private const val EVENT_BANNER_CLICKED = 19

@OptIn(ExperimentalForeignApi::class)
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

    private var pendingRewardEarned: (() -> Unit)? = null

    override fun init(appKey: String, onReady: (AdsInitResult) -> Unit) {
        if (appKey.isBlank()) {
            val error = AdsError.Init("LevelPlay App Key is not configured. See README.")
            _initState.value = AdsInitState.Failed(error)
            onReady(AdsInitResult.Failure(error))
            return
        }
        _initState.value = AdsInitState.Initializing
        LevelPlayAdsKitBridge.initializeSdkWithAppKey(appKey) { event, message, error ->
            handleEvent(event, message, error, onReady)
        }
    }

    override fun loadInterstitial(adUnitId: String) {
        if (adUnitId.isBlank()) {
            _interstitialState.value = AdsState.Failed(
                AdsError.Load("Interstitial Ad Unit ID is not configured. See README.", AdFormat.INTERSTITIAL, isNoFill = false)
            )
            return
        }
        _interstitialState.value = AdsState.Loading
        LevelPlayAdsKitBridge.loadInterstitialWithAdUnitId(adUnitId)
    }

    override fun showInterstitial() {
        if (LevelPlayAdsKitBridge.isInterstitialReady()) {
            LevelPlayAdsKitBridge.showInterstitial()
        } else {
            _interstitialState.value = AdsState.Failed(
                AdsError.Show("Interstitial is not ready", AdFormat.INTERSTITIAL)
            )
        }
    }

    override fun isInterstitialReady(): Boolean = LevelPlayAdsKitBridge.isInterstitialReady()

    override fun loadRewarded(adUnitId: String) {
        if (adUnitId.isBlank()) {
            _rewardedState.value = AdsState.Failed(
                AdsError.Load("Rewarded Ad Unit ID is not configured. See README.", AdFormat.REWARDED, isNoFill = false)
            )
            return
        }
        _rewardedState.value = AdsState.Loading
        LevelPlayAdsKitBridge.loadRewardedWithAdUnitId(adUnitId)
    }

    override fun showRewarded(onRewardEarned: () -> Unit) {
        if (LevelPlayAdsKitBridge.isRewardedReady()) {
            pendingRewardEarned = onRewardEarned
            LevelPlayAdsKitBridge.showRewarded()
        } else {
            _rewardedState.value = AdsState.Failed(
                AdsError.Show("Rewarded ad is not ready", AdFormat.REWARDED)
            )
        }
    }

    override fun isRewardedReady(): Boolean = LevelPlayAdsKitBridge.isRewardedReady()

    fun createBannerView(adUnitId: String): UIView? {
        if (adUnitId.isBlank()) {
            _bannerState.value = AdsState.Failed(
                AdsError.Load("Banner Ad Unit ID is not configured. See README.", AdFormat.BANNER, isNoFill = false)
            )
            return null
        }
        _bannerState.value = AdsState.Loading
        return LevelPlayAdsKitBridge.createBannerWithAdUnitId(adUnitId)
    }

    fun isBannerReady(): Boolean = LevelPlayAdsKitBridge.isBannerReady()

    fun destroyBanner() {
        LevelPlayAdsKitBridge.destroyBanner()
        _bannerState.value = AdsState.Idle
    }

    private fun handleEvent(event: Int, message: String?, error: NSError?, onReady: (AdsInitResult) -> Unit) {
        val errorMessage = message ?: error?.localizedDescription ?: "LevelPlay returned an error"
        val errorCode = error?.code?.toInt()
        when (event) {
            EVENT_SDK_INIT_SUCCESS -> {
                _initState.value = AdsInitState.Ready
                onReady(AdsInitResult.Success)
            }
            EVENT_SDK_INIT_FAILED -> {
                val adError = AdsError.Init(errorMessage, errorCode)
                _initState.value = AdsInitState.Failed(adError)
                onReady(AdsInitResult.Failure(adError))
            }
            EVENT_INTERSTITIAL_LOADED -> _interstitialState.value = AdsState.Ready
            EVENT_INTERSTITIAL_LOAD_FAILED -> _interstitialState.value = AdsState.Failed(
                AdsError.Load(errorMessage, AdFormat.INTERSTITIAL, isNoFill = false, errorCode = errorCode)
            )
            EVENT_INTERSTITIAL_DISPLAYED -> Unit
            EVENT_INTERSTITIAL_DISPLAY_FAILED -> _interstitialState.value = AdsState.Failed(
                AdsError.Show(errorMessage, AdFormat.INTERSTITIAL, errorCode)
            )
            EVENT_INTERSTITIAL_CLICKED -> Unit
            EVENT_INTERSTITIAL_CLOSED -> {
                _interstitialState.value = AdsState.Idle
                if (config.isConfigured) {
                    loadInterstitial(config.interstitialAdUnitId)
                }
            }
            EVENT_REWARDED_LOADED -> _rewardedState.value = AdsState.Ready
            EVENT_REWARDED_LOAD_FAILED -> _rewardedState.value = AdsState.Failed(
                AdsError.Load(errorMessage, AdFormat.REWARDED, isNoFill = false, errorCode = errorCode)
            )
            EVENT_REWARDED_DISPLAYED -> Unit
            EVENT_REWARDED_DISPLAY_FAILED -> _rewardedState.value = AdsState.Failed(
                AdsError.Show(errorMessage, AdFormat.REWARDED, errorCode)
            )
            EVENT_REWARDED_CLICKED -> Unit
            EVENT_REWARDED_CLOSED -> {
                _rewardedState.value = AdsState.Idle
                if (config.isConfigured) {
                    loadRewarded(config.rewardedAdUnitId)
                }
            }
            EVENT_REWARDED_EARNED -> {
                pendingRewardEarned?.invoke()
                pendingRewardEarned = null
            }
            EVENT_BANNER_LOADED -> _bannerState.value = AdsState.Ready
            EVENT_BANNER_LOAD_FAILED -> _bannerState.value = AdsState.Failed(
                AdsError.Load(errorMessage, AdFormat.BANNER, isNoFill = false, errorCode = errorCode)
            )
            EVENT_BANNER_DISPLAYED -> Unit
            EVENT_BANNER_DISPLAY_FAILED -> _bannerState.value = AdsState.Failed(
                AdsError.Show(errorMessage, AdFormat.BANNER, errorCode)
            )
            EVENT_BANNER_CLICKED -> Unit
            else -> Unit
        }
    }
}

actual fun createPlatformAdsManager(config: AdsConfig): AdsManager =
    LevelPlayAdsManager(config)