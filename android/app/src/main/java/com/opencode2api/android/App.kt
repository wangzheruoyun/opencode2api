package com.opencode2api.android

import android.app.Application
import com.opencode2api.android.core.GatewayRuntime

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        // The service and the UI both reach for GatewayRuntime, and either can
        // be the first thing created after a process start.
        GatewayRuntime.attach(this)
    }
}
