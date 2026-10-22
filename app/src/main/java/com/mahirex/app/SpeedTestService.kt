package com.mahirex.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Runs ONE real speed test for a home-screen widget button and stops itself. It is a foreground service because
 * Android does not allow a plain background task to use the network for ~25 s. It lives only for the length of
 * the test; nothing keeps running afterwards.
 */
class SpeedTestService : Service() {
    companion object {
        @Volatile
        var active = false

        private const val CHANNEL = "mahirex_speedtest"
        private const val NOTIF_ID = 4107
        private const val WATCHDOG_MS = 150_000L

        /** returns false when the system refused to start the service */
        fun start(ctx: Context): Boolean {
            val app = ctx.applicationContext
            if (active) return true
            if (!Net.isOnline(app)) {
                HomeWidgets.testEnd(app, SpeedRunner.ERR_OFFLINE)
                HomeWidgets.updateAll(app)
                return true
            }
            return try {
                val i = Intent(app, SpeedTestService::class.java)
                if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i) else app.startService(i)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var runner: SpeedRunner? = null

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(text: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val b: Notification.Builder
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.sp_title), NotificationManager.IMPORTANCE_LOW))
            b = Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            b = Notification.Builder(this)
        }
        b.setSmallIcon(R.drawable.ic_notif)
        b.setContentTitle(getString(R.string.app_name))
        b.setContentText(text)
        b.setOngoing(true)
        return b.build()
    }

    private fun goForeground(text: String) {
        val n = notification(text)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun setText(text: String) {
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, notification(text))
        } catch (e: Exception) {
            // notification is informational only
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // must be called for every startForegroundService(), even for a duplicate tap
        goForeground(getString(R.string.wd_testing_notif))
        if (active) return START_NOT_STICKY
        active = true
        val app = applicationContext
        HomeWidgets.testBegin(app)
        HomeWidgets.pushLive(app, true)
        val all = ServerManager.load(app)
        val id = SpeedStore.selectedId(app)
        val chosen = if (id.isEmpty()) null else all.firstOrNull { it.id == id }
        val r = SpeedRunner(app)
        runner = r
        handler.postDelayed({ r.cancel() }, WATCHDOG_MS)
        r.start(all, chosen, listener)
        return START_NOT_STICKY
    }

    private val listener = object : SpeedRunner.Listener {
        override fun onPhase(p: SpeedRunner.Phase, server: SpeedServer?) {
            val ph = when (p) {
                SpeedRunner.Phase.SELECT -> HomeWidgets.PH_SELECT
                SpeedRunner.Phase.PING -> HomeWidgets.PH_PING
                SpeedRunner.Phase.DOWN -> HomeWidgets.PH_DOWN
                SpeedRunner.Phase.UP -> HomeWidgets.PH_UP
                else -> HomeWidgets.PH_IDLE
            }
            if (ph == HomeWidgets.PH_IDLE) return
            HomeWidgets.testPhase(applicationContext, ph)
            HomeWidgets.pushLive(applicationContext, true)
            val label = when (ph) {
                HomeWidgets.PH_PING -> R.string.sp_phase_ping
                HomeWidgets.PH_DOWN -> R.string.sp_phase_down
                HomeWidgets.PH_UP -> R.string.sp_phase_up
                else -> R.string.sp_phase_select
            }
            setText(getString(label))
        }

        override fun onPing(ping: Double, jitter: Double) {
            HomeWidgets.testPing(applicationContext, ping, jitter)
            HomeWidgets.pushLive(applicationContext, true)
        }

        override fun onLive(p: SpeedRunner.Phase, mbps: Double) {
            HomeWidgets.testLive(applicationContext, mbps)
            HomeWidgets.pushLive(applicationContext, false)
        }

        override fun onPhaseResult(p: SpeedRunner.Phase, mbps: Double) {
            HomeWidgets.testResult(applicationContext, p == SpeedRunner.Phase.DOWN, mbps)
            HomeWidgets.pushLive(applicationContext, true)
        }

        override fun onDone(r: SpeedResult) {
            finish(0)
        }

        override fun onError(kind: Int) {
            finish(kind)
        }
    }

    private fun finish(err: Int) {
        if (!active) return
        active = false
        handler.removeCallbacksAndMessages(null)
        val app = applicationContext
        HomeWidgets.testEnd(app, err)
        HomeWidgets.pushLive(app, true)
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runner?.cancel()
        active = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
