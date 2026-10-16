package com.opencode2api.android.core

import android.content.Context
import com.opencode2api.android.data.AdminClient
import com.opencode2api.android.data.GatewayConfigStore
import com.opencode2api.android.data.SettingsStore

/**
 * Process-wide singletons.
 *
 * The gateway must be a single process per install — two instances would fight
 * over the same loopback port — so the runner, the config store, and the log
 * buffer are created once here and shared by the foreground service and every
 * screen. Deliberately not a DI framework: there are three objects, and a
 * container would be more indirection than the problem has.
 */
object GatewayRuntime {

    @Volatile
    private var initialized = false

    lateinit var configStore: GatewayConfigStore
        private set

    lateinit var settings: SettingsStore
        private set

    lateinit var logs: LogBus
        private set

    lateinit var runner: GatewayRunner
        private set

    /**
     * Talker for the gateway's own admin API. Shared so that the session cookie
     * and CSRF token survive navigation — logging in on one screen has to count
     * as logged in on the next.
     */
    lateinit var admin: AdminClient
        private set

    /** Idempotent; safe to call from `Application.onCreate`. */
    fun attach(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            configStore = GatewayConfigStore(app)
            settings = SettingsStore(app)
            logs = LogBus()
            runner = GatewayRunner(app, configStore, logs)
            admin = AdminClient(app)
            initialized = true
        }
    }
}
