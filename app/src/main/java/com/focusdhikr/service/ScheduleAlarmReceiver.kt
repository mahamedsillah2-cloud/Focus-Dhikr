package com.focusdhikr.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.focusdhikr.core.Permissions

/**
 * Wakes the app at the edges of a schedule window (22:00, 08:00) so enforcement
 * reacts on time even if nothing else happened.
 *
 * Note this is a convenience, not the mechanism: [BlockCoordinator] re-checks
 * the windows on every foreground event, so a missed alarm - Doze, an OEM
 * killing the app, a revoked SCHEDULE_EXACT_ALARM - degrades the timing, never
 * the correctness.
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Permissions.hasUsageAccess(context)) {
            UsageTrackingService.start(context)
        }
        scheduleNext(context)
    }

    companion object {
        private const val REQUEST_CODE = 4242

        /** Re-arms for the next quarter hour; cheap, and robust to clock changes. */
        fun scheduleNext(context: Context, now: Long = System.currentTimeMillis()) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val quarter = 15 * 60_000L
            val next = ((now / quarter) + 1) * quarter

            val pending = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, ScheduleAlarmReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            val canSchedule = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()

            runCatching {
                if (canSchedule) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, next, pending
                    )
                } else {
                    // Exact alarms revoked: an inexact alarm still nudges us, and
                    // foreground events remain the real trigger.
                    alarmManager.set(AlarmManager.RTC_WAKEUP, next, pending)
                }
            }
        }
    }
}
