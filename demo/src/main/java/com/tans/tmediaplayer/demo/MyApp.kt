package com.tans.tmediaplayer.demo

import android.app.Application
import android.content.Context
import com.tans.tapm.autoinit.tApmAutoInit
import com.tans.tapm.monitors.CpuPowerCostMonitor
import com.tans.tapm.monitors.CpuUsageMonitor
import com.tans.tapm.monitors.ForegroundScreenPowerCostMonitor
import com.tans.tapm.monitors.HttpRequestMonitor
import com.tans.tapm.monitors.MainThreadLagMonitor
import com.tans.tapm.monitors.MemoryUsageMonitor
import com.tans.tmediaplayer.demo.BuildConfig
import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tuiutils.systembar.AutoApplySystemBarAnnotation

class MyApp : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        tApmAutoInit.addBuilderInterceptor { builder ->
            if (BuildConfig.DEBUG) {
                builder
                    // CpuUsage
                    .addMonitor(CpuUsageMonitor())
                    // CpuPowerCost
                    .addMonitor(CpuPowerCostMonitor())
                    // ForegroundScreenPowerCost
                    .addMonitor(ForegroundScreenPowerCostMonitor())
                    // Http
                    .addMonitor(HttpRequestMonitor())
                    // MainThreadLag
                    .addMonitor(MainThreadLagMonitor())
                    // MemoryUsage
                    .addMonitor(MemoryUsageMonitor())
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        AutoApplySystemBarAnnotation.init(this)
        if(!BuildConfig.DEBUG) {
            tMediaPlayerLog.logLevel = tMediaPlayerLog.LogLevel.NoLog
        }
    }
}