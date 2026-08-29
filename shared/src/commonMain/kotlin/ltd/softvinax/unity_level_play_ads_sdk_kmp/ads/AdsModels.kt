package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads

enum class AdFormat { INTERSTITIAL, REWARDED, BANNER }

sealed interface AdsError {
    val message: String

    data class Init(
        override val message: String,
        val code: Int? = null,
    ) : AdsError

    data class Load(
        override val message: String,
        val format: AdFormat,
        val isNoFill: Boolean,
        val errorCode: Int? = null,
    ) : AdsError

    data class Show(
        override val message: String,
        val format: AdFormat,
        val errorCode: Int? = null,
    ) : AdsError
}

sealed interface AdsInitResult {
    data object Success : AdsInitResult
    data class Failure(val error: AdsError.Init) : AdsInitResult
}

sealed interface AdsInitState {
    data object Idle : AdsInitState
    data object Initializing : AdsInitState
    data object Ready : AdsInitState
    data class Failed(val error: AdsError.Init) : AdsInitState
}

sealed interface AdsState {
    data object Idle : AdsState
    data object Loading : AdsState
    data object Ready : AdsState
    data class Failed(val error: AdsError) : AdsState
}