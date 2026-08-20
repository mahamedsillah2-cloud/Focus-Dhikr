package com.focusdhikr.core

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import com.focusdhikr.service.AppBlockAccessibilityService

/**
 * The four permissions this app actually needs, each with the official way to
 * check it and the official screen that grants it.
 *
 * None of these can be requested with a normal runtime dialog: usage access and
 * overlay are appops, accessibility is a system toggle. So the onboarding flow
 * sends the user to the right settings page and re-checks on resume.
 * See docs/LIMITES_PLATAFORMA.md.
 */
object Permissions {

    /** Required. Without it there is no usage data at all. */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(
                "android.permission.PACKAGE_USAGE_STATS"
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /**
     * Required. Draws the pause screen, and - just as importantly - is the
     * documented exemption that lets the service start an Activity from the
     * background on API 29+.
     */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun overlayIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )

    /** Optional but strongly recommended: turns 1-2s of latency into none. */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = "${context.packageName}/${AppBlockAccessibilityService::class.java.name}"
        val enabled = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
        }.getOrNull() ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (entry in splitter) {
            if (entry.equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    fun accessibilityIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** Optional. Improves survival under Doze on stock Android. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @Suppress("BatteryLife")
    fun batteryOptimizationIntent(context: Context): Intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )

    fun appDetailsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )

    /** True when the app can do its core job at all. */
    fun coreReady(context: Context): Boolean =
        hasUsageAccess(context) && canDrawOverlays(context)
}
