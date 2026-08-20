package com.focusdhikr.data.repo

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    /** True for the usual suspects, so they can be offered first. */
    val commonlyDistracting: Boolean,
)

/**
 * Reads the launcher-visible apps.
 *
 * Uses ACTION_MAIN/CATEGORY_LAUNCHER rather than getInstalledPackages so the
 * list matches what the user actually sees on their home screen, with no system
 * plumbing in it.
 */
class InstalledAppsRepository(private val context: Context) {

    suspend fun load(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolved = runCatching {
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }.getOrElse { emptyList() }

        resolved.asSequence()
            .mapNotNull { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = runCatching { info.loadLabel(pm).toString() }
                        .getOrDefault(info.packageName),
                    icon = runCatching { info.loadIcon(pm) }.getOrNull(),
                    commonlyDistracting = info.packageName in COMMONLY_DISTRACTING,
                )
            }
            .sortedWith(
                compareByDescending<InstalledApp> { it.commonlyDistracting }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
            )
            .toList()
    }

    suspend fun labelFor(packageName: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }

    suspend fun iconFor(packageName: String): Drawable? = withContext(Dispatchers.IO) {
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

    companion object {
        /**
         * Only used to sort these to the top of the picker. The user can limit any
         * app on the device; nothing here is hardcoded into the blocking logic.
         */
        val COMMONLY_DISTRACTING: Set<String> = setOf(
            "com.instagram.android",
            "com.zhiliaoapp.musically",       // TikTok
            "com.ss.android.ugc.trill",       // TikTok (some regions)
            "com.facebook.katana",
            "com.facebook.lite",
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music",
            "com.twitter.android",            // X
            "com.reddit.frontpage",
            "com.snapchat.android",
            "com.whatsapp",
            "com.telegram.messenger",
            "org.telegram.messenger",
            "com.linkedin.android",
            "com.pinterest",
            "com.netflix.mediaclient",
            "tv.twitch.android.app",
            "com.discord",
        )
    }
}
