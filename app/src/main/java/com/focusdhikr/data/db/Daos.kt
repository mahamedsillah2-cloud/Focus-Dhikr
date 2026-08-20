package com.focusdhikr.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedAppDao {

    @Query("SELECT * FROM tracked_apps ORDER BY label COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TrackedAppEntity>>

    @Query("SELECT * FROM tracked_apps WHERE enabled = 1")
    suspend fun enabled(): List<TrackedAppEntity>

    @Query("SELECT * FROM tracked_apps WHERE packageName = :packageName")
    suspend fun find(packageName: String): TrackedAppEntity?

    @Upsert
    suspend fun upsert(app: TrackedAppEntity)

    @Query("UPDATE tracked_apps SET dailyLimitMillis = :limitMillis WHERE packageName = :packageName")
    suspend fun setLimit(packageName: String, limitMillis: Long)

    @Query("UPDATE tracked_apps SET enabled = :enabled WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean)

    @Query("UPDATE tracked_apps SET strict = :strict WHERE packageName = :packageName")
    suspend fun setStrict(packageName: String, strict: Boolean)

    @Query("DELETE FROM tracked_apps WHERE packageName = :packageName")
    suspend fun remove(packageName: String)
}

@Dao
interface UsageDao {

    @Query("SELECT * FROM usage_days WHERE dayKey = :dayKey")
    fun observeDay(dayKey: String): Flow<List<UsageDayEntity>>

    @Query("SELECT * FROM usage_days WHERE dayKey = :dayKey")
    suspend fun day(dayKey: String): List<UsageDayEntity>

    @Query("SELECT COALESCE(millis, 0) FROM usage_days WHERE dayKey = :dayKey AND packageName = :packageName")
    suspend fun millisFor(dayKey: String, packageName: String): Long?

    @Query("SELECT * FROM usage_days WHERE dayKey IN (:dayKeys)")
    fun observeRange(dayKeys: List<String>): Flow<List<UsageDayEntity>>

    @Query("SELECT * FROM usage_days WHERE dayKey IN (:dayKeys)")
    suspend fun range(dayKeys: List<String>): List<UsageDayEntity>

    /**
     * Deliberately INSERT OR IGNORE + UPDATE rather than a single upsert:
     * SQLite only learned ON CONFLICT ... DO UPDATE in 3.24, which ships with
     * Android 11. minSdk here is 26, so an upsert would compile fine and then
     * fail at runtime on older phones. FocusRepository.addUsage runs the pair
     * inside a transaction, so two accounting passes racing each other still
     * cannot lose time.
     */
    @Query("INSERT OR IGNORE INTO usage_days (packageName, dayKey, millis) VALUES (:packageName, :dayKey, 0)")
    suspend fun ensureRow(packageName: String, dayKey: String)

    @Query("UPDATE usage_days SET millis = millis + :millis WHERE packageName = :packageName AND dayKey = :dayKey")
    suspend fun incrementMillis(packageName: String, dayKey: String, millis: Long)


    @Query("DELETE FROM usage_days WHERE dayKey < :oldestDayKeyToKeep")
    suspend fun pruneBefore(oldestDayKeyToKeep: String)

    @Query("DELETE FROM usage_days")
    suspend fun clear()
}

@Dao
interface OpenSessionDao {

    @Query("SELECT * FROM open_sessions")
    suspend fun all(): List<OpenSessionEntity>

    @Upsert
    suspend fun upsertAll(sessions: List<OpenSessionEntity>)

    @Query("DELETE FROM open_sessions")
    suspend fun clear()

}

@Dao
interface BlockAttemptDao {

    @Insert
    suspend fun insert(attempt: BlockAttemptEntity): Long

    @Query(
        """
        UPDATE block_attempts
        SET endedAt = :endedAt, reachedPhase = :reachedPhase, outcome = :outcome,
            intentReason = :intentReason, writtenReason = :writtenReason
        WHERE id = :id
        """
    )
    suspend fun finish(
        id: Long,
        endedAt: Long,
        reachedPhase: String,
        outcome: String,
        intentReason: String?,
        writtenReason: String?,
    )

    @Query("SELECT COUNT(*) FROM block_attempts WHERE dayKey = :dayKey AND packageName = :packageName")
    suspend fun countToday(dayKey: String, packageName: String): Int

    @Query("SELECT * FROM block_attempts WHERE dayKey IN (:dayKeys) ORDER BY startedAt DESC")
    fun observeRange(dayKeys: List<String>): Flow<List<BlockAttemptEntity>>

    @Query("SELECT * FROM block_attempts WHERE dayKey IN (:dayKeys)")
    suspend fun range(dayKeys: List<String>): List<BlockAttemptEntity>

    @Query("DELETE FROM block_attempts WHERE dayKey < :oldestDayKeyToKeep")
    suspend fun pruneBefore(oldestDayKeyToKeep: String)

    @Query("UPDATE block_attempts SET writtenReason = NULL")
    suspend fun forgetWrittenReasons()

    @Query("DELETE FROM block_attempts")
    suspend fun clear()
}

@Dao
interface GoalDao {

    @Query("SELECT * FROM goals ORDER BY position ASC, id ASC")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE active = 1 ORDER BY position ASC, id ASC")
    suspend fun active(): List<GoalEntity>

    @Upsert
    suspend fun upsert(goal: GoalEntity): Long

    @Delete
    suspend fun delete(goal: GoalEntity)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM goals")
    suspend fun count(): Int
}

@Dao
interface ScheduleWindowDao {

    @Query("SELECT * FROM schedule_windows ORDER BY startMinuteOfDay ASC")
    fun observeAll(): Flow<List<ScheduleWindowEntity>>

    @Query("SELECT * FROM schedule_windows WHERE enabled = 1")
    suspend fun enabled(): List<ScheduleWindowEntity>

    @Upsert
    suspend fun upsert(window: ScheduleWindowEntity): Long

    @Query("DELETE FROM schedule_windows WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface GrantDao {

    @Query("SELECT * FROM grants WHERE packageName = :packageName")
    suspend fun find(packageName: String): GrantEntity?

    @Query("SELECT * FROM grants WHERE expiresAt > :now")
    fun observeActive(now: Long): Flow<List<GrantEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun grant(grant: GrantEntity)

    @Query("DELETE FROM grants WHERE packageName = :packageName")
    suspend fun revoke(packageName: String)

    @Query("DELETE FROM grants WHERE expiresAt <= :now")
    suspend fun pruneExpired(now: Long)

    @Query("DELETE FROM grants")
    suspend fun revokeAll()
}

@Dao
interface EmergencyDao {

    @Insert
    suspend fun record(use: EmergencyUseEntity)

    @Query("SELECT COUNT(*) FROM emergency_uses WHERE usedAt >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT * FROM emergency_uses WHERE usedAt >= :since ORDER BY usedAt DESC")
    fun observeSince(since: Long): Flow<List<EmergencyUseEntity>>

    @Query("DELETE FROM emergency_uses")
    suspend fun clear()
}
