package com.assistente99

import android.app.Activity
import android.app.Application
import android.os.Bundle

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Pal.apply(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = AppState.track(activity)
            override fun onActivityDestroyed(activity: Activity) = AppState.untrack(activity)
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
    }
}
