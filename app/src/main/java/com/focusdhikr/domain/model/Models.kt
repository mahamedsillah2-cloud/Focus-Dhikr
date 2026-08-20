package com.focusdhikr.domain.model

/**
 * An app the user has chosen to put a daily limit on.
 *
 * [dailyLimitMillis] of 0 means "no time at all today" (a hard block), which is
 * different from not tracking the app at all.
 */
data class TrackedApp(
    val packageName: String,
    val label: String,
    val dailyLimitMillis: Long,
    val enabled: Boolean = true,
    /** Opt this specific app into the stricter gate, independent of global strict mode. */
    val strict: Boolean = false,
)

/**
 * A recurring window during which an app is off-limits regardless of remaining
 * time, e.g. 22:00-08:00.
 *
 * Windows that wrap past midnight are expressed with [endMinuteOfDay] <
 * [startMinuteOfDay] and handled by [contains].
 */
data class ScheduleWindow(
    val id: Long = 0,
    /** null applies the window to every tracked app. */
    val packageName: String?,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    /** Bit 0 = Monday ... bit 6 = Sunday. */
    val daysMask: Int = ALL_DAYS,
    val enabled: Boolean = true,
    val label: String = "",
) {
    /**
     * @param minuteOfDay minutes since local midnight
     * @param isoDayOfWeek 1 = Monday ... 7 = Sunday
     */
    fun contains(minuteOfDay: Int, isoDayOfWeek: Int): Boolean {
        if (!enabled) return false
        val wraps = endMinuteOfDay <= startMinuteOfDay
        val inClockRange = if (wraps) {
            minuteOfDay >= startMinuteOfDay || minuteOfDay < endMinuteOfDay
        } else {
            minuteOfDay in startMinuteOfDay until endMinuteOfDay
        }
        if (!inClockRange) return false

        // For a wrapping window the small-hours half belongs to the day the
        // window *started* on, so 01:00 on Tuesday is covered by a Monday window.
        val effectiveDay = if (wraps && minuteOfDay < endMinuteOfDay) {
            if (isoDayOfWeek == 1) 7 else isoDayOfWeek - 1
        } else {
            isoDayOfWeek
        }
        return (daysMask and (1 shl (effectiveDay - 1))) != 0
    }

    companion object {
        const val ALL_DAYS: Int = 0b1111111
    }
}

/** Something the user actually wants their time to go to. */
data class Goal(
    val id: Long = 0,
    val title: String,
    val note: String = "",
    val active: Boolean = true,
)

/** Why the user said they were opening the app, in phase 2 of the gate. */
enum class IntentReason {
    CONCRETE_REASON,
    BORED,
    SEEKING_DISTRACTION,
    HABIT,
    DONT_KNOW,
}

/** How a run through the gate ended. */
enum class GateOutcome {
    /** The user turned back. This is the number the home screen celebrates. */
    TURNED_BACK,

    /** The user went through every phase and consciously chose to continue. */
    CHOSE_TO_CONTINUE,

    /** The user used the emergency path. */
    EMERGENCY_ACCESS,

    /** The gate was shown but the user left without answering. */
    ABANDONED,
}

/** One recorded pass through the gate. Local only, never leaves the device. */
data class BlockAttempt(
    val id: Long = 0,
    val packageName: String,
    val dayKey: String,
    val startedAt: Long,
    val endedAt: Long?,
    val reachedPhase: String,
    val outcome: GateOutcome?,
    val intentReason: IntentReason?,
)

/** Accumulated foreground time for one app on one logical day. */
data class UsageDay(
    val packageName: String,
    val dayKey: String,
    val millis: Long,
)

/** Why an app is currently blocked, so the gate can say something accurate. */
sealed interface BlockReason {
    data class LimitReached(val usedMillis: Long, val limitMillis: Long) : BlockReason
    data class ScheduleWindow(val label: String, val untilMinuteOfDay: Int) : BlockReason
}
