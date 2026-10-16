package com.opencode2api.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.opencode2api.android.MainActivity
import com.opencode2api.android.R
import com.opencode2api.android.core.GatewayBinary
import com.opencode2api.android.core.GatewayRuntime
import com.opencode2api.android.core.GatewayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Hosts the gateway.
 *
 * The service, not the UI, owns the process. A start button press sends an
 * intent here rather than spawning anything directly, so there is exactly one
 * place that can start the gateway and exactly one place that can stop it.
 * That matters because a Compose screen can be recreated at any moment, and a
 * screen that owned the process would leak one on every rotation.
 */
class GatewayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private var notifyJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        GatewayRuntime.attach(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // Still has to go foreground first: on API 26+ a service that
                // was started with startForegroundService must post its
                // notification before it may stop.
                promoteToForeground(notification(getString(R.string.home_action_stop)))
                scope.launch {
                    GatewayRuntime.runner.stop()
                    stopSelf()
                }
                return START_NOT_STICKY
            }

            ACTION_RESTART -> {
                promoteToForeground(notification(getString(R.string.home_status_starting)))
                startGateway(restart = true)
            }

            else -> {
                promoteToForeground(notification(getString(R.string.home_status_starting)))
                startGateway()
            }
        }
        return START_STICKY
    }

    private fun startGateway(restart: Boolean = false) {
        val settings = GatewayRuntime.settings.current
        observeJob?.cancel()
        observeJob = scope.launch {
            // Restarting through the runner rather than by stopping and
            // re-starting the service keeps the two operations under one lock,
            // so a start cannot land in the middle of a teardown.
            val result = if (restart) {
                GatewayRuntime.runner.restart(settings.runMode)
            } else {
                GatewayRuntime.runner.start(settings.runMode)
            }
            if (result.isFailure) {
                // Nothing left to host; drop the notification rather than
                // leaving a permanent "starting" that never resolves.
                stopSelf()
            }
        }
        notifyJob?.cancel()
        notifyJob = scope.launch {
            GatewayRuntime.runner.state.collectLatest { state ->
                val manager =
                    ContextCompat.getSystemService(this@GatewayService, NotificationManager::class.java)
                manager?.notify(NOTIFICATION_ID, notification(titleFor(state), state))
            }
        }
    }

    private fun titleFor(state: GatewayState): String = when (state) {
        is GatewayState.Running -> getString(R.string.home_status_running)
        is GatewayState.Starting -> getString(R.string.home_status_starting)
        is GatewayState.Failed -> getString(R.string.home_status_failed)
        GatewayState.Stopped -> getString(R.string.home_status_stopped)
    }

    private fun promoteToForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun notification(title: String, state: GatewayState = GatewayState.Stopped): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, GatewayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val text = when (state) {
            is GatewayState.Running ->
                "127.0.0.1:${state.apiPort}" + if (state.root) " · Root" else ""
            is GatewayState.Failed -> state.reason
            else -> getString(R.string.app_name)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.home_action_stop), stop)
            .setOngoing(true)
            .setShowWhen(false, System.currentTimeMillis())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createChannel() {
        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.home_title)
            setShowBadge(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        observeJob?.cancel()
        notifyJob?.cancel()
        // The gateway is deliberately not stopped here. This callback also runs
        // when the system kills and restarts the service; killing the process
        // on the way out would leave a START_STICKY restart with nothing to
        // host. Stops go through ACTION_STOP, which is explicit.
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "gateway"
        private const val NOTIFICATION_ID = 0x0A2A
        const val ACTION_STOP = "com.opencode2api.android.action.STOP"
        const val ACTION_RESTART = "com.opencode2api.android.action.RESTART"

        fun start(context: Context) {
            if (!GatewayBinary.exists(context)) return
            ContextCompat.startForegroundService(
                context,
                Intent(context, GatewayService::class.java),
            )
        }

        fun restart(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, GatewayService::class.java).setAction(ACTION_RESTART),
                )
            }
        }

        fun stop(context: Context) {
            // startService from a background context is refused on API 26+, and
            // the UI can call this while it is being torn down. A failed stop
            // intent is recoverable — the user can stop again — so it must not
            // take the app down.
            runCatching {
                context.startService(
                    Intent(context, GatewayService::class.java).setAction(ACTION_STOP),
                )
            }
        }
    }
}
