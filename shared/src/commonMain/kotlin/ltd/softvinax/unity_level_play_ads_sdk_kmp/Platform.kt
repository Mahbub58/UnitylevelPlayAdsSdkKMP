package ltd.softvinax.unity_level_play_ads_sdk_kmp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform