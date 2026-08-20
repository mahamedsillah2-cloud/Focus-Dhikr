package com.focusdhikr.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.focusdhikr.core.DayBoundary
import com.focusdhikr.core.Permissions
import com.focusdhikr.data.prefs.SettingsStore
import com.focusdhikr.data.repo.FocusRepository
import com.focusdhikr.domain.model.BlockReason
import com.focusdhikr.domain.usage.BlockDecision
import com.focusdhikr.domain.usage.LimitEvaluator
import com.focusdhikr.ui.gate.GateActivity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

/**
 * Decides whether the pause screen should open, and opens it.
 *
 * Both detection paths funnel through here - the AccessibilityService (instant)
 * and the polling service (~1s) - so the policy lives in exactly one place and
 * the two paths can never disagree.
 */
class BlockCoordinator(
    private val context: Context,
    private val repository: FocusRepository,
    private val settingsStore: SettingsStore,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    private val mutex = Mutex()

    /** Guards against firing the gate twice for one app switch. */
    @Volatile
    private var lastGatedPackage: String? = null

    @Volatile
    private var lastGatedAt: Long = 0L

    /**
     * @param packageName the app that just came to the foreground.
     * @param usedMillisOverride today's total for that app, when the caller has
     *   just computed it. Saves a database round trip on the hot path.
     */
    suspend fun onForegroundApp(
        packageName: String,
        now: Long = System.currentTimeMillis(),
        usedMillisOverride: Long? = null,
    ): BlockDecision = mutex.withLock {
        decideLocked(packageName, now, usedMillisOverride)
    }

    private suspend fun decideLocked(
        packageName: String,
        now: Long,
        usedMillisOverride: Long?,
    ): BlockDecision {
        if (packageName == context.packageName) return BlockDecision.Allow

        // Leaving and re-entering the app resets the debounce immediately, so a
        // deliberate second attempt is never silently swallowed.
        if (packageName != lastGatedPackage) {
            lastGatedPackage = null
        }

        val app = repository.trackedApp(packageName) ?: return BlockDecision.Allow

        val settings = settingsStore.current()
        val instant = Instant.ofEpochMilli(now)
        val dayKey = DayBoundary.dayKeyFor(instant, settings.dayResetHour, zone)
        val local = instant.atZone(zone)

        val used = usedMillisOverride ?: repository.usedMillis(packageName, dayKey)

        val decision = LimitEvaluator.evaluate(
            app = app,
            usedMillis = used,
            grantExpiresAt = repository.grantExpiry(packageName, now),
            now = now,
            windows = repository.enabledWindows(),
            minuteOfDay = local.hour * 60 + local.minute,
            isoDayOfWeek = local.dayOfWeek.value,
            globalStrict = settings.strictActive(now),
        )

        if (decision is BlockDecision.Block) {
            if (shouldSuppress(packageName, now)) return decision
            lastGatedPackage = packageName
            lastGatedAt = now
            openGate(decision, used, now)
        }

        return decision
    }

    /** Called when the user leaves a gated app, so the next open re-arms the gate. */
    fun onLeftApp(packageName: String) {
        if (lastGatedPackage == packageName) lastGatedPackage = null
    }

    private fun shouldSuppress(packageName: String, now: Long): Boolean =
        packageName == lastGatedPackage && now - lastGatedAt < REARM_MILLIS

    private fun openGate(decision: BlockDecision.Block, usedMillis: Long, now: Long) {
        val reasonKey = when (decision.reason) {
            is BlockReason.LimitReached -> GateActivity.REASON_LIMIT
            is BlockReason.ScheduleWindow -> GateActivity.REASON_WINDOW
        }
        val windowLabel = (decision.reason as? BlockReason.ScheduleWindow)?.label.orEmpty()

        val intent = GateActivity.intent(
            context = context,
            packageName = decision.app.packageName,
            appLabel = decision.app.label,
            usedMillis = usedMillis,
            limitMillis = decision.app.dailyLimitMillis,
            strict = decision.strict,
            reason = reasonKey,
            windowLabel = windowLabel,
        ).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
        )

        // Starting an Activity from the background is only permitted because the
        // app holds SYSTEM_ALERT_WINDOW. If that was revoked, or an OEM blocks it
        // anyway, fall back to drawing the same screen as an overlay.
        val started = runCatching { context.startActivity(intent) }.isSuccess

        if (!started || !Permissions.canDrawOverlays(context)) {
            Log.w(TAG, "Gate activity could not be started for ${decision.app.packageName}")
        }
        if (!started) {
            BlockOverlay.show(context, decision.app.label)
        }
    }

    /** Grants the temporary access the user earned by finishing the gate. */
    suspend fun grantAfterGate(packageName: String, now: Long, emergency: Boolean = false) {
        val settings = settingsStore.current()
        val minutes = if (emergency) settings.grantMinutes.coerceAtLeast(10) else settings.grantMinutes
        repository.grantAccess(packageName, now, minutes * 60_000L, emergency)
        lastGatedPackage = packageName
        lastGatedAt = now
    }

    private companion object {
        const val TAG = "BlockCoordinator"

        /**
         * How long the same package stays debounced. Long enough that one app
         * switch fires one gate, short enough that a genuine reopen a moment
         * later is still caught.
         */
        const val REARM_MILLIS = 3_000L
    }
}
