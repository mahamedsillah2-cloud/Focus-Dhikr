package com.focusdhikr.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusdhikr.core.Permissions

/**
 * Brings the counter back after a reboot or an app update.
 *
 * Without this the app would silently stop counting after every restart, which
 * is the sort of quiet failure that makes a tool like this untrustworthy.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (Permissions.hasUsageAccess(context)) {
                    UsageTrackingService.start(context)
                }
                ScheduleAlarmReceiver.scheduleNext(context)
            }
        }
    }
}
