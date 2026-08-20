package com.focusdhikr.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackedAppEntity::class,
        UsageDayEntity::class,
        OpenSessionEntity::class,
        BlockAttemptEntity::class,
        GoalEntity::class,
        ScheduleWindowEntity::class,
        GrantEntity::class,
        EmergencyUseEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class FocusDatabase : RoomDatabase() {

    abstract fun trackedApps(): TrackedAppDao
    abstract fun usage(): UsageDao
    abstract fun openSessions(): OpenSessionDao
    abstract fun blockAttempts(): BlockAttemptDao
    abstract fun goals(): GoalDao
    abstract fun scheduleWindows(): ScheduleWindowDao
    abstract fun grants(): GrantDao
    abstract fun emergency(): EmergencyDao

    companion object {
        private const val NAME = "focus_dhikr.db"

        @Volatile
        private var instance: FocusDatabase? = null

        fun get(context: Context): FocusDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FocusDatabase::class.java,
                    NAME,
                ).build().also { instance = it }
            }
    }
}
