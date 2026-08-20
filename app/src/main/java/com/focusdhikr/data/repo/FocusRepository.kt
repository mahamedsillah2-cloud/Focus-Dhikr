package com.focusdhikr.data.repo

import com.focusdhikr.core.DayBoundary
import com.focusdhikr.data.db.EmergencyUseEntity
import com.focusdhikr.data.db.FocusDatabase
import com.focusdhikr.data.db.GoalEntity
import com.focusdhikr.data.db.GrantEntity
import com.focusdhikr.data.db.OpenSessionEntity
import com.focusdhikr.data.prefs.SettingsStore
import com.focusdhikr.domain.model.BlockAttempt
import com.focusdhikr.domain.model.GateOutcome
import com.focusdhikr.domain.model.Goal
import com.focusdhikr.domain.model.ScheduleWindow
import com.focusdhikr.domain.model.TrackedApp
import com.focusdhikr.domain.model.UsageDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId

/**
 * The single door to stored state.
 *
 * Nothing above this class touches Room or DataStore directly, so "all data
 * stays on this device" is a property of one file rather than a promise spread
 * across the codebase.
 */
class FocusRepository(
    private val db: FocusDatabase,
    private val settingsStore: SettingsStore,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    val settings = settingsStore.settings

    /** One-shot read of the current settings, for hot paths that cannot collect. */
    suspend fun settingsSnapshot(): com.focusdhikr.data.prefs.Settings = settingsStore.current()

    // --- tracked apps -----------------------------------------------------

    fun observeTrackedApps(): Flow<List<TrackedApp>> =
        db.trackedApps().observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun enabledTrackedApps(): List<TrackedApp> =
        db.trackedApps().enabled().map { it.toDomain() }

    suspend fun trackedApp(packageName: String): TrackedApp? =
        db.trackedApps().find(packageName)?.toDomain()

    suspend fun track(app: TrackedApp, now: Long = System.currentTimeMillis()) {
        db.trackedApps().upsert(app.toEntity(now))
    }

    suspend fun setLimit(packageName: String, limitMillis: Long) =
        db.trackedApps().setLimit(packageName, limitMillis.coerceAtLeast(0))

    suspend fun setAppEnabled(packageName: String, enabled: Boolean) =
        db.trackedApps().setEnabled(packageName, enabled)

    suspend fun setAppStrict(packageName: String, strict: Boolean) =
        db.trackedApps().setStrict(packageName, strict)

    suspend fun untrack(packageName: String) {
        db.trackedApps().remove(packageName)
        db.grants().revoke(packageName)
    }

    // --- usage ------------------------------------------------------------

    suspend fun dayKey(now: Long = System.currentTimeMillis()): String {
        val resetHour = settingsStore.current().dayResetHour
        return DayBoundary.dayKeyFor(Instant.ofEpochMilli(now), resetHour, zone)
    }

    suspend fun recentDayKeys(count: Int, now: Long = System.currentTimeMillis()): List<String> {
        val resetHour = settingsStore.current().dayResetHour
        return DayBoundary.recentDayKeys(Instant.ofEpochMilli(now), count, resetHour, zone)
    }

    fun observeUsage(dayKey: String): Flow<List<UsageDay>> =
        db.usage().observeDay(dayKey).map { list -> list.map { it.toDomain() } }

    fun observeUsageRange(dayKeys: List<String>): Flow<List<UsageDay>> =
        db.usage().observeRange(dayKeys).map { list -> list.map { it.toDomain() } }

    suspend fun usageRange(dayKeys: List<String>): List<UsageDay> =
        db.usage().range(dayKeys).map { it.toDomain() }

    suspend fun usedMillis(packageName: String, dayKey: String): Long =
        db.usage().millisFor(dayKey, packageName) ?: 0L

    suspend fun addUsage(packageName: String, dayKey: String, millis: Long) {
        if (millis > 0) db.usage().addMillis(packageName, dayKey, millis)
    }

    suspend fun openSessions(): Map<String, Long> =
        db.openSessions().all().associate { it.packageName to it.startedAt }

    suspend fun replaceOpenSessions(sessions: Map<String, Long>) {
        db.openSessions().replaceAll(sessions.map { OpenSessionEntity(it.key, it.value) })
    }

    // --- gate attempts ----------------------------------------------------

    suspend fun attemptsToday(packageName: String, dayKey: String): Int =
        db.blockAttempts().countToday(dayKey, packageName)

    suspend fun beginAttempt(
        packageName: String,
        dayKey: String,
        startedAt: Long,
        initialPhase: String,
    ): Long = db.blockAttempts().insert(
        com.focusdhikr.data.db.BlockAttemptEntity(
            packageName = packageName,
            dayKey = dayKey,
            startedAt = startedAt,
            endedAt = null,
            reachedPhase = initialPhase,
            outcome = null,
            intentReason = null,
            writtenReason = null,
        )
    )

    suspend fun finishAttempt(
        id: Long,
        endedAt: Long,
        reachedPhase: String,
        outcome: GateOutcome,
        intentReason: String?,
        writtenReason: String?,
    ) {
        val keep = settingsStore.current().keepWrittenReasons
        db.blockAttempts().finish(
            id = id,
            endedAt = endedAt,
            reachedPhase = reachedPhase,
            outcome = outcome.name,
            intentReason = intentReason,
            writtenReason = if (keep) writtenReason?.trim()?.takeIf { it.isNotEmpty() } else null,
        )
    }

    fun observeAttempts(dayKeys: List<String>): Flow<List<BlockAttempt>> =
        db.blockAttempts().observeRange(dayKeys).map { list -> list.map { it.toDomain() } }

    suspend fun attempts(dayKeys: List<String>): List<BlockAttempt> =
        db.blockAttempts().range(dayKeys).map { it.toDomain() }

    // --- goals ------------------------------------------------------------

    fun observeGoals(): Flow<List<Goal>> =
        db.goals().observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun activeGoals(): List<Goal> = db.goals().active().map { it.toDomain() }

    suspend fun saveGoal(goal: Goal, now: Long = System.currentTimeMillis()): Long =
        db.goals().upsert(
            GoalEntity(
                id = goal.id,
                title = goal.title.trim(),
                note = goal.note.trim(),
                active = goal.active,
                position = if (goal.id == 0L) db.goals().count() else 0,
                createdAt = now,
            )
        )

    suspend fun deleteGoal(id: Long) = db.goals().deleteById(id)

    // --- schedule windows -------------------------------------------------

    fun observeWindows(): Flow<List<ScheduleWindow>> =
        db.scheduleWindows().observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun enabledWindows(): List<ScheduleWindow> =
        db.scheduleWindows().enabled().map { it.toDomain() }

    suspend fun saveWindow(window: ScheduleWindow): Long =
        db.scheduleWindows().upsert(window.toEntity())

    suspend fun deleteWindow(id: Long) = db.scheduleWindows().deleteById(id)

    // --- grants -----------------------------------------------------------

    suspend fun grantExpiry(packageName: String, now: Long): Long? =
        db.grants().find(packageName)?.takeIf { it.expiresAt > now }?.expiresAt

    suspend fun grantAccess(packageName: String, now: Long, durationMillis: Long, emergency: Boolean) {
        db.grants().grant(
            GrantEntity(
                packageName = packageName,
                grantedAt = now,
                expiresAt = now + durationMillis,
                emergency = emergency,
            )
        )
    }

    suspend fun revokeGrant(packageName: String) = db.grants().revoke(packageName)

    suspend fun revokeAllGrants() = db.grants().revokeAll()

    suspend fun pruneExpiredGrants(now: Long) = db.grants().pruneExpired(now)

    // --- emergency --------------------------------------------------------

    suspend fun emergencyUsesThisWeek(now: Long): Int =
        db.emergency().countSince(now - SEVEN_DAYS_MILLIS)

    suspend fun recordEmergencyUse(packageName: String, now: Long, reason: String) =
        db.emergency().record(EmergencyUseEntity(packageName = packageName, usedAt = now, reason = reason))

    // --- privacy ----------------------------------------------------------

    /** Drops history older than the retention window the user chose. */
    suspend fun pruneHistory(now: Long = System.currentTimeMillis()) {
        val settings = settingsStore.current()
        val keys = DayBoundary.recentDayKeys(
            Instant.ofEpochMilli(now),
            settings.historyRetentionDays,
            settings.dayResetHour,
            zone,
        )
        val oldest = keys.first()
        db.usage().pruneBefore(oldest)
        db.blockAttempts().pruneBefore(oldest)
        db.grants().pruneExpired(now)
    }

    suspend fun forgetWrittenReasons() = db.blockAttempts().forgetWrittenReasons()

    /** The nuclear option offered in Settings: erase everything this app knows. */
    suspend fun eraseEverything() {
        db.usage().clear()
        db.blockAttempts().clear()
        db.emergency().clear()
        db.grants().revokeAll()
        db.openSessions().clear()
        settingsStore.clearAll()
    }

    private companion object {
        const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
