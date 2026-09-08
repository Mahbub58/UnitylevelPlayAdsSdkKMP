package ltd.softvinax.unity_level_play_ads_sdk_kmp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.config.AndroidAdsConfigHolder
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.data.AndroidAdsHost
import ltd.softvinax.unity_level_play_ads_sdk_kmp.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashLogger()
        enableEdgeToEdge()

        AndroidAdsConfigHolder.set(BuildConfig.LEVELPLAY_APP_KEY)
        AndroidAdsHost.setContext(applicationContext)
        AndroidAdsHost.attachActivity(this)

        if (BuildConfig.DEBUG) {
            Thread {
                try {
                    val info = AdvertisingIdClient.getAdvertisingIdInfo(applicationContext)
                    val msg = "GAID=${info.id} isLimitAdTrackingEnabled=${info.isLimitAdTrackingEnabled}"
                    Log.i("LevelPlay_GAID", msg)
                } catch (e: Throwable) {
                    Log.e("LevelPlay_GAID", "Failed to get GAID: ${e.message}", e)
                }
            }.start()
        }

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        AndroidAdsHost.detachActivity(this)
        super.onDestroy()
    }

    private fun installCrashLogger() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("LevelPlay_CRASH", "Uncaught exception on thread ${thread.name}", throwable)
            Log.w("LevelPlay_CRASH", "=== SUMMARY ===")
            val trace = Log.getStackTraceString(throwable)
            val lines = trace.lineSequence().filter { it.isNotBlank() }.take(30)
            lines.forEach { Log.e("LevelPlay_CRASH", "  $it") }
            Log.i("LevelPlay_CRASH", "crash_logcat_tag=LevelPlay_CRASH")
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
