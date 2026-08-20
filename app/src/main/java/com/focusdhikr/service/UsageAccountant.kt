package com.focusdhikr.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.focusdhikr.core.DayBoundary
import com.focusdhikr.core.Permissions
import com.focusdhikr.data.repo.FocusRepository
import com.focusdhikr.domain.usage.ForegroundEvent
import com.focusdhikr.domain.usage.SessionAccumulator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

/**
 * Turns the system's raw usage event stream into per-app, per-day totals.
 *
 * Correctness properties this class is responsible for:
 *
 *  - **Idempotent.** A watermark ([lastProcessedTs]) means the same millisecond
 *    is never counted twice, no matter how often [sync] runs.
 *  - **Gap-recovering.** If the phone was off or the process died for three
 *    hours, the next sync reads that whole window and credits it once.
 *  - **Day-boundary safe.** A session that straddles the logical day reset is
 *    split so yesterday's scrolling does not eat today's allowance.
 */
class UsageAccountant(
    private val context: Context,
    private val repository: FocusRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    private val mutex = Mutex()

    @Volatile
    private var lastProcessedTs: Long = 0L

    /**
     * Folds every usage event since the last sync into the day totals.
     *
     * @return foreground time today for each tracked app, after this sync.
     */
    suspend fun sync(now: Long = System.currentTimeMillis()): Map<String, Long> = mutex.withLock {
        if (!Permissions.hasUsageAccess(context)) return emptyMap()

        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()

        val resetHour = repository.settingsSnapshot().dayResetHour

        val from = if (lastProcessedTs > 0) {
            lastProcessedTs + 1
        } else {
            // Cold start: rebuild today from the start of the logical day, so a
            // restart at 15:00 does not report zero minutes used.
            DayBoundary.startOfDay(Instant.ofEpochMilli(now), resetHour, zone).toEpochMilli()
        }
        if (from > now) return currentTotals(now, resetHour)

        // A batch can straddle the logical day reset (typically 04:00) after the
        // phone has been off overnight. Splitting at each boundary keeps last
        // night's scrolling out of this morning's allowance.
        var cursor = from
        var carriedSessions = repository.openSessions()
        var lastEventTs = 0L

        while (cursor <= now) {
            val boundary = DayBoundary
                .endOfDay(Instant.ofEpochMilli(cursor), resetHour, zone)
                .toEpochMilli()
            val segmentEnd = minOf(boundary, now)
            val dayKey = DayBoundary.dayKeyFor(Instant.ofEpochMilli(cursor), resetHour, zone)

            val events = readEvents(manager, cursor, segmentEnd)
            val result = SessionAccumulator.accumulate(
                events = events,
                until = segmentEnd,
                openSessions = carriedSessions,
            )

            for ((packageName, millis) in result.addedMillis) {
                repository.addUsage(packageName, dayKey, millis)
            }

            carriedSessions = result.openSessions
            lastEventTs = maxOf(lastEventTs, result.lastEventTimestamp)

            if (segmentEnd >= now) break
            cursor = segmentEnd + 1
        }

        repository.replaceOpenSessions(carriedSessions)

        // Advance the watermark to `now`, not to the last event: a quiet stretch
        // with no events would otherwise be re-scanned on every pass forever.
        lastProcessedTs = maxOf(now, lastEventTs)

        return currentTotals(now, resetHour)
    }

    /** Which package is in the foreground right now, per the usage event stream. */
    fun currentForegroundPackage(now: Long = System.currentTimeMillis()): String? {
        if (!Permissions.hasUsageAccess(context)) return null
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null

        val events = readEvents(manager, now - FOREGROUND_LOOKBACK_MILLIS, now)
        return events.lastOrNull { it.resumed }?.packageName
    }

    private fun readEvents(
        manager: UsageStatsManager,
        from: Long,
        to: Long,
    ): List<ForegroundEvent> {
        val out = ArrayList<ForegroundEvent>()
        try {
            val stream: UsageEvents = manager.queryEvents(from, to)
            val event = UsageEvents.Event()
            while (stream.hasNextEvent()) {
                stream.getNextEvent(event)
                val resumed = when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> true
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> false
                    else -> continue
                }
                val pkg = event.packageName ?: continue
                out += ForegroundEvent(pkg, event.timeStamp, resumed)
            }
        } catch (e: SecurityException) {
            // Usage access was revoked between the check and the query.
            Log.w(TAG, "Usage access lost while reading events", e)
            return emptyList()
        }
        out.sortBy { it.timestamp }
        return out
    }

    private suspend fun currentTotals(now: Long, resetHour: Int): Map<String, Long> {
        val dayKey = DayBoundary.dayKeyFor(Instant.ofEpochMilli(now), resetHour, zone)
        return repository.usageRange(listOf(dayKey)).associate { it.packageName to it.millis }
    }

    /** Forces the next [sync] to rebuild from the start of the logical day. */
    fun resetWatermark() {
        lastProcessedTs = 0L
    }

    private companion object {
        const val TAG = "UsageAccountant"

        /**
         * ACTIVITY_RESUMED can be reported with a small delay, and OEMs vary. Ten
         * seconds is wide enough to always catch the transition and narrow enough
         * that a stale event never wins.
         */
        const val FOREGROUND_LOOKBACK_MILLIS = 10_000L
    }
}
