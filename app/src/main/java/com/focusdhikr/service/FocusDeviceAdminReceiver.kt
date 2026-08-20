package com.focusdhikr.service

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.focusdhikr.R

/**
 * Optional uninstall friction. Off by default.
 *
 * We request no policy powers whatsoever (see res/xml/device_admin.xml, whose
 * uses-policies block is intentionally empty). The only thing we want is
 * Android's own rule that an *active* device admin cannot be uninstalled until
 * it is deactivated in Settings > Security > Device admin apps.
 *
 * That is a real, meaningful hurdle - a deliberate trip through system settings
 * - and it is fully reversible, which requirement 10 asks for.
 */
class FocusDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.device_admin_disable_warning)

    companion object {

        fun component(context: Context): ComponentName =
            ComponentName(context, FocusDeviceAdminReceiver::class.java)

        fun isActive(context: Context): Boolean {
            val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
            return runCatching { dpm.isAdminActive(component(context)) }.getOrDefault(false)
        }

        fun enableIntent(context: Context, explanation: String): Intent =
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation)
            }

        fun disable(context: Context) {
            val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return
            runCatching { dpm.removeActiveAdmin(component(context)) }
        }
    }
}
