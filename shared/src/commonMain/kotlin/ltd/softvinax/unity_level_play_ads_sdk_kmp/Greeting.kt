package ltd.softvinax.unity_level_play_ads_sdk_kmp

class Greeting {
    private val platform = getPlatform()

    fun greet(): String {
        return sayHello(platform.name)
    }
}