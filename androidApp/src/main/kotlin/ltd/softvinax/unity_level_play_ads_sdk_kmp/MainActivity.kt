package ltd.softvinax.unity_level_play_ads_sdk_kmp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.AndroidAdsConfigHolder
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.AndroidAdsHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidAdsConfigHolder.set(BuildConfig.LEVELPLAY_APP_KEY)
        AndroidAdsHost.setContext(applicationContext)
        AndroidAdsHost.attachActivity(this)

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        AndroidAdsHost.detachActivity(this)
        super.onDestroy()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}