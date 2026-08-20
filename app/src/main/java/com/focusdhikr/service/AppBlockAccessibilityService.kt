package com.focusdhikr.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import com.focusdhikr.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Instant detection of app launches.
 *
 * This is the difference between the pause screen appearing before the first
 * scroll and appearing a second and a half later, after the feed has already
 * loaded. That second and a half is exactly the moment the app exists to catch.
 *
 * What it does NOT do, deliberately:
 *  - read the content of any app;
 *  - store any text it sees;
 *  - send anything anywhere (the app has no internet permission at all).
 *
 * On Android 13+ this is a restricted setting for sideloaded builds and has to
 * be unlocked from the app's info page first. See docs/INSTALACION.md.
 */
class AppBlockAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var graph: AppGraph

    private var lastPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        graph = AppGraph.get(this)

        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }

        // The polling service can stand down to the accounting-only cadence now.
        UsageTrackingService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (packageName == this.packageName) return

        if (packageName == lastPackage) return
        lastPackage?.let { graph.blockCoordinator.onLeftApp(it) }
        lastPackage = packageName

        if (packageName in SETTINGS_PACKAGES) {
            guardOwnSettingsPage(event)
            return
        }

        scope.launch {
            runCatching { graph.blockCoordinator.onForegroundApp(packageName) }
        }
    }

    /**
     * Friction, not a lock.
     *
     * When strict mode is on and the user navigates to *this app's* entry in
     * system settings - the page with the Uninstall and Force stop buttons -
     * we press Back once. It costs a few seconds of determination. It is not a
     * barrier and is not meant to be: Android gives no normal app the power to
     * prevent its own uninstall, and pretending otherwise would be a lie. See
     * docs/LIMITES_PLATAFORMA.md section A6.
     */
    private fun guardOwnSettingsPage(event: AccessibilityEvent) {
        scope.launch {
            val strict = runCatching {
                graph.settingsStore.current().strictActive(System.currentTimeMillis())
            }.getOrDefault(false)
            if (!strict) return@launch

            val root = runCatching { rootInActiveWindow }.getOrNull() ?: return@launch
            val mentionsUs = runCatching {
                root.findAccessibilityNodeInfosByText(APP_LABEL)?.isNotEmpty() == true
            }.getOrDefault(false)
            runCatching { root.recycle() }

            if (mentionsUs) {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val APP_LABEL = "Focus Dhikr"

        val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.miui.securitycenter",
            "com.samsung.android.settings",
        )
    }
}
