package com.focusdhikr

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.focusdhikr.core.Permissions
import com.focusdhikr.service.UsageTrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FocusDhikrApp : Application() {

    val graph: AppGraph by lazy { AppGraph.get(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        scope.launch {
            // Housekeeping that must not wait for the user to open a screen:
            // drop history past the retention window and clear stale grants.
            runCatching { graph.repository.pruneHistory() }
            runCatching { graph.repository.pruneExpiredGrants(System.currentTimeMillis()) }
        }

        if (Permissions.hasUsageAccess(this)) {
            UsageTrackingService.start(this)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRACKING,
                getString(R.string.notif_channel_tracking_name),
                // The service notification is required by Android, not by us.
                // Keep it as quiet as the system allows.
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = getString(R.string.notif_channel_tracking_desc)
                setShowBadge(false)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                getString(R.string.notif_channel_reminders_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notif_channel_reminders_desc)
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val CHANNEL_TRACKING = "tracking"
        const val CHANNEL_REMINDERS = "reminders"
    }
}
