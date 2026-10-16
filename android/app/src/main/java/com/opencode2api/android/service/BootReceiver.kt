package com.opencode2api.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.opencode2api.android.core.GatewayRuntime

/**
 * Brings the gateway back after a reboot or an app update, when the user has
 * asked for that.
 *
 * Starting a foreground service from `BOOT_COMPLETED` is one of the few
 * background starts Android still allows, but it is not guaranteed on every
 * OEM build, so a failure here is swallowed: the app is still perfectly usable
 * with the gateway started by hand.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        GatewayRuntime.attach(context)
        if (!GatewayRuntime.settings.current.startOnBoot) return
        runCatching { GatewayService.start(context) }
    }
}
