package com.focusdhikr

import android.content.Context
import com.focusdhikr.data.db.FocusDatabase
import com.focusdhikr.data.prefs.SettingsStore
import com.focusdhikr.data.repo.FocusRepository
import com.focusdhikr.data.repo.InstalledAppsRepository
import com.focusdhikr.service.BlockCoordinator
import com.focusdhikr.service.UsageAccountant

/**
 * Hand-written dependency container.
 *
 * No Hilt: this app has eight collaborators and needs them from Activities,
 * Services and an AccessibilityService alike. An explicit object is easier to
 * follow than annotation processing, and keeps build times down.
 */
class AppGraph(context: Context) {

    private val appContext: Context = context.applicationContext

    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }

    private val database: FocusDatabase by lazy { FocusDatabase.get(appContext) }

    val repository: FocusRepository by lazy { FocusRepository(database, settingsStore) }

    val installedApps: InstalledAppsRepository by lazy { InstalledAppsRepository(appContext) }

    val accountant: UsageAccountant by lazy { UsageAccountant(appContext, repository) }

    val blockCoordinator: BlockCoordinator by lazy {
        BlockCoordinator(appContext, repository, settingsStore)
    }

    companion object {
        @Volatile
        private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph(context).also { instance = it }
            }
    }
}
