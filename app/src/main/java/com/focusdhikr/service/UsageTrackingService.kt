package com.focusdhikr.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.focusdhikr.AppGraph
import com.focusdhikr.FocusDhikrApp
import com.focusdhikr.R
import com.focusdhikr.core.Permissions
import com.focusdhikr.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the accounting loop alive and, when accessibility is not granted,
 * detects foreground changes by polling.
 *
 * Two cadences on purpose:
 *  - accounting every [ACCOUNTING_INTERVAL_MILLIS], which is cheap and only
 *    needs to be roughly right;
 *  - foreground polling every [POLL_INTERVAL_MILLIS], which needs to be fast,
 *    and is skipped entirely when the AccessibilityService is doing the job.
 */
class UsageTrackingService : LifecycleService() {

    private lateinit var graph: AppGraph
    private var loop: Job? = null
    private var lastForegroundPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph.get(this)
        startForegroundSafely()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (loop?.isActive != true) {
            loop = lifecycleScope.launch { runLoop() }
        }
        // START_STICKY: if the system reclaims us under memory pressure we want
        // to come back. It is not a guarantee - see docs/LIMITES_PLATAFORMA.md
        // on OEM background policies.
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private suspend fun runLoop() {
        var sinceAccounting = 0L

        while (lifecycleScope.isActive) {
            val now = System.currentTimeMillis()

            if (!Permissions.hasUsageAccess(this)) {
                // Nothing useful to do without the permission; idle cheaply
                // rather than spinning, and pick up again if it is re-granted.
                delay(IDLE_INTERVAL_MILLIS)
                continue
            }

            if (sinceAccounting <= 0) {
                runCatching { graph.accountant.sync(now) }
                sinceAccounting = ACCOUNTING_INTERVAL_MILLIS
            }

            // The AccessibilityService, when enabled, reacts instantly and calls
            // the coordinator itself. Polling then would only duplicate work.
            if (!Permissions.isAccessibilityEnabled(this)) {
                pollForeground(now)
            }

            delay(POLL_INTERVAL_MILLIS)
            sinceAccounting -= POLL_INTERVAL_MILLIS
        }
    }

    private suspend fun pollForeground(now: Long) {
        val current = graph.accountant.currentForegroundPackage(now) ?: return
        if (current == lastForegroundPackage) return

        lastForegroundPackage?.let { graph.blockCoordinator.onLeftApp(it) }
        lastForegroundPackage = current

        runCatching { graph.blockCoordinator.onForegroundApp(current, now) }
    }

    private fun startForegroundSafely() {
        val notification = buildNotification()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { stopSelf() }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, FocusDhikrApp.CHANNEL_TRACKING)
            .setContentTitle(getString(R.string.notif_tracking_title))
            .setContentText(getString(R.string.notif_tracking_text_idle))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001

        /** Fast enough that the ~1s fallback path stays usable. */
        private const val POLL_INTERVAL_MILLIS = 1_000L

        /** Accounting does not need to be second-accurate. */
        private const val ACCOUNTING_INTERVAL_MILLIS = 15_000L

        private const val IDLE_INTERVAL_MILLIS = 30_000L

        fun start(context: Context) {
            val intent = Intent(context, UsageTrackingService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, UsageTrackingService::class.java)) }
        }
    }
}
