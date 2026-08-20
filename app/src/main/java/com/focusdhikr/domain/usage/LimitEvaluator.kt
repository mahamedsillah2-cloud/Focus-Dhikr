package com.focusdhikr.domain.usage

import com.focusdhikr.domain.model.BlockReason
import com.focusdhikr.domain.model.ScheduleWindow
import com.focusdhikr.domain.model.TrackedApp

/** What the enforcement layer should do about an app that just came forward. */
sealed interface BlockDecision {
    /** Nothing to do. */
    data object Allow : BlockDecision

    /** Inside a grant the user already earned by going through the gate. */
    data class AllowGranted(val expiresAt: Long) : BlockDecision

    /** Show the progressive pause. */
    data class Block(val app: TrackedApp, val reason: BlockReason, val strict: Boolean) : BlockDecision
}

/**
 * Decides, for one foreground app at one instant, whether the gate should open.
 *
 * Kept free of Android types so the whole decision table is unit-testable. The
 * enforcement service is then a thin shell that gathers inputs and reacts.
 */
object LimitEvaluator {

    /**
     * @param usedMillis foreground time for this app so far in the current logical day.
     * @param grantExpiresAt when the current temporary access ends, or null.
     * @param minuteOfDay minutes since local midnight.
     * @param isoDayOfWeek 1 = Monday ... 7 = Sunday.
     * @param globalStrict the "no me dejes entrar" master switch.
     */
    fun evaluate(
        app: TrackedApp?,
        usedMillis: Long,
        grantExpiresAt: Long?,
        now: Long,
        windows: List<ScheduleWindow>,
        minuteOfDay: Int,
        isoDayOfWeek: Int,
        globalStrict: Boolean,
    ): BlockDecision {
        if (app == null || !app.enabled) return BlockDecision.Allow

        val activeWindow = windows.firstOrNull { window ->
            (window.packageName == null || window.packageName == app.packageName) &&
                window.contains(minuteOfDay, isoDayOfWeek)
        }

        val strict = globalStrict || app.strict || activeWindow != null

        // A grant earned through the gate is honoured, except inside an explicit
        // schedule window: the user asked for those to be the hardest thing here,
        // and a grant taken at 21:58 should not carry into a 22:00 cut-off.
        if (grantExpiresAt != null && now < grantExpiresAt && activeWindow == null) {
            return BlockDecision.AllowGranted(grantExpiresAt)
        }

        if (activeWindow != null) {
            return BlockDecision.Block(
                app = app,
                reason = BlockReason.ScheduleWindow(
                    label = activeWindow.label,
                    untilMinuteOfDay = activeWindow.endMinuteOfDay,
                ),
                strict = true,
            )
        }

        if (usedMillis >= app.dailyLimitMillis) {
            return BlockDecision.Block(
                app = app,
                reason = BlockReason.LimitReached(usedMillis, app.dailyLimitMillis),
                strict = strict,
            )
        }

        return BlockDecision.Allow
    }

    /** Remaining allowance today, floored at zero. */
    fun remainingMillis(app: TrackedApp, usedMillis: Long): Long =
        (app.dailyLimitMillis - usedMillis).coerceAtLeast(0)
}
