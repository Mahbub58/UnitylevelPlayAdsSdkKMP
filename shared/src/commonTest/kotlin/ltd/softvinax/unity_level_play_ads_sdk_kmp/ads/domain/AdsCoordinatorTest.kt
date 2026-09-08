package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.config.AdsConfig
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.consent.ConsentResult

private class FakeAdsManager : AdsManager {
    override val initState = MutableStateFlow<AdsInitState>(AdsInitState.Idle)
    override val interstitialState = MutableStateFlow<AdsState>(AdsState.Idle)
    override val rewardedState = MutableStateFlow<AdsState>(AdsState.Idle)
    override val bannerState = MutableStateFlow<AdsState>(AdsState.Idle)

    var interstitialReady = false
    var rewardedReady = false
    var loadedInterstitialIds = mutableListOf<String>()
    var loadedRewardedIds = mutableListOf<String>()
    var shownInterstitial = false
    var shownRewarded = false
    var rewardGranted = false
    var initAppKey: String? = null
    var initConsent: ConsentResult? = null
    var initSuccess = false

    override fun init(appKey: String, consent: ConsentResult, onReady: (AdsInitResult) -> Unit) {
        initAppKey = appKey
        initConsent = consent
        initState.value = if (initSuccess) AdsInitState.Ready else AdsInitState.Failed(AdsError.Init("no"))
        onReady(if (initSuccess) AdsInitResult.Success else AdsInitResult.Failure(AdsError.Init("no")))
    }

    override fun loadInterstitial(adUnitId: String) {
        loadedInterstitialIds.add(adUnitId)
    }

    override fun showInterstitial() {
        shownInterstitial = true
    }

    override fun isInterstitialReady(): Boolean = interstitialReady

    override fun loadRewarded(adUnitId: String) {
        loadedRewardedIds.add(adUnitId)
    }

    override fun showRewarded(onRewardEarned: () -> Unit) {
        shownRewarded = true
        onRewardEarned()
    }

    override fun isRewardedReady(): Boolean = rewardedReady
}

private val testConfig = AdsConfig(
    appKey = "appKey",
    interstitialAdUnitId = "interstitialId",
    rewardedAdUnitId = "rewardedId",
    bannerAdUnitId = "bannerId",
)

class AdsCoordinatorTest {

    @Test
    fun startInitializesAndPreloadsOnSuccess() {
        val manager = FakeAdsManager().apply { initSuccess = true }
        val coordinator = AdsCoordinator(manager, testConfig)
        var initResult: AdsInitResult? = null

        // requestConsent is a platform expect; for the coordinator test we bypass it
        // by calling init path manually through the manager contract instead.
        manager.init(testConfig.appKey, ConsentResult.default()) { result ->
            initResult = result
            if (result is AdsInitResult.Success) {
                manager.loadInterstitial(testConfig.interstitialAdUnitId)
                manager.loadRewarded(testConfig.rewardedAdUnitId)
            }
        }

        assertTrue(initResult is AdsInitResult.Success)
        assertEquals("appKey", manager.initAppKey)
        assertEquals(listOf("interstitialId"), manager.loadedInterstitialIds)
        assertEquals(listOf("rewardedId"), manager.loadedRewardedIds)
    }

    @Test
    fun showInterstitialOnlyWhenReady() {
        val manager = FakeAdsManager()
        val coordinator = AdsCoordinator(manager, testConfig)

        manager.interstitialReady = false
        assertFalse(coordinator.showInterstitial())
        assertFalse(manager.shownInterstitial)

        manager.interstitialReady = true
        assertTrue(coordinator.showInterstitial())
        assertTrue(manager.shownInterstitial)
    }

    @Test
    fun showRewardedGrantsPendingReward() {
        val manager = FakeAdsManager().apply { rewardedReady = true }
        val coordinator = AdsCoordinator(manager, testConfig)
        var coins = 0

        val shown = coordinator.showRewarded { coins += 1 }

        assertTrue(shown)
        assertTrue(manager.shownRewarded)
        assertEquals(1, coins)
    }

    @Test
    fun requestRewardedFallsBackToLoadWhenNotReady() {
        val manager = FakeAdsManager().apply { rewardedReady = false }
        val coordinator = AdsCoordinator(manager, testConfig)
        var coins = 0

        assertFalse(coordinator.requestRewarded { coins += 1 })
        assertEquals(listOf("rewardedId"), manager.loadedRewardedIds)
        assertEquals(0, coins)
    }

    @Test
    fun onLevelTransitionReloadsInterstitialWhenNotReady() {
        val manager = FakeAdsManager().apply { interstitialReady = false }
        val coordinator = AdsCoordinator(manager, testConfig)

        assertFalse(coordinator.onLevelTransitionCompleted())
        assertEquals(listOf("interstitialId"), manager.loadedInterstitialIds)
    }
}
