package ltd.softvinax.unity_level_play_ads_sdk_kmp.ads.data

import android.app.Activity
import android.content.Context

object AndroidAdsHost {
    @Volatile
    var context: Context? = null
        private set

    @Volatile
    var activity: Activity? = null
        private set

    fun setContext(context: Context) {
        this.context = context.applicationContext
    }

    fun attachActivity(activity: Activity) {
        this.activity = activity
    }

    fun detachActivity(activity: Activity) {
        if (this.activity === activity) {
            this.activity = null
        }
    }
}