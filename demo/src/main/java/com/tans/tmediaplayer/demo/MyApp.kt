package com.tans.tmediaplayer.demo

import android.app.Application
import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tuiutils.systembar.AutoApplySystemBarAnnotation

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AutoApplySystemBarAnnotation.init(this)
        // tMediaPlayerLog.logLevel = tMediaPlayerLog.LogLevel.Error
    }
}