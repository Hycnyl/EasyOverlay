package com.hycnyl.easyoverlay

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.hycnyl.easyoverlay.model.ConfigRepository
import com.hycnyl.easyoverlay.util.AppStateUtil
import com.hycnyl.easyoverlay.util.ConfigPathUtil

class EasyOverlayApplication : Application() {

    private var startedActivityCount = 0

    override fun onCreate() {
        super.onCreate()
        if (ConfigPathUtil.getConfigRoot(this).isNotBlank()) {
            try {
                ConfigRepository.load(this)
            } catch (e: Exception) {
                android.util.Log.e("EasyOverlayApplication", "启动时预加载配置失败", e)
            }
        }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount++
                AppStateUtil.setInAppActive(this@EasyOverlayApplication, true)
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                AppStateUtil.setInAppActive(
                    this@EasyOverlayApplication,
                    startedActivityCount > 0
                )
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
