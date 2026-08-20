package com.focusdhikr.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tracked_apps")
data class TrackedAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val dailyLimitMillis: Long,
    val enabled: Boolean,
    val strict: Boolean,
    val createdAt: Long,
)

/**
 * Foreground time per app per logical day.
 *
 * Composite primary key so the accumulator can upsert-and-add without ever
 * creating a duplicate row for the same day.
 */
@Entity(tableName = "usage_days", primaryKeys = ["packageName", "dayKey"])
data class UsageDayEntity(
    val packageName: String,
    val dayKey: String,
    val millis: Long,
)

/**
 * A session that had started but not finished when the last accounting pass ran.
 * Persisted so a process death does not lose or double-count the time.
 */
@Entity(tableName = "open_sessions")
data class OpenSessionEntity(
    @PrimaryKey val packageName: String,
    val startedAt: Long,
)

@Entity(
    tableName = "block_attempts",
    indices = [Index("dayKey"), Index("packageName")],
)
data class BlockAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val dayKey: String,
    val startedAt: Long,
    val endedAt: Long?,
    val reachedPhase: String,
    /** Null while the attempt is still in flight. */
    val outcome: String?,
    val intentReason: String?,
    /**
     * What the user wrote in phase 5, or null when the "keep my written reasons"
     * setting is off. Never leaves the device; the app has no internet permission.
     */
    val writtenReason: String?,
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String,
    val active: Boolean,
    val position: Int,
    val createdAt: Long,
)

@Entity(tableName = "schedule_windows")
data class ScheduleWindowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String?,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val daysMask: Int,
    val enabled: Boolean,
    val label: String,
)

/** Temporary access earned by walking through the gate, or via the emergency path. */
@Entity(tableName = "grants")
data class GrantEntity(
    @PrimaryKey val packageName: String,
    val grantedAt: Long,
    val expiresAt: Long,
    val emergency: Boolean,
)

/** One use of the emergency path, so the weekly allowance can be enforced. */
@Entity(tableName = "emergency_uses", indices = [Index("usedAt")])
data class EmergencyUseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val usedAt: Long,
    val reason: String,
)
