package com.focusdhikr.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A "day" in this app does not necessarily start at midnight.
 *
 * If you go to bed at 02:00, a midnight reset splits your night in two and hands
 * back a fresh Instagram allowance at exactly the worst moment. So the logical
 * day starts at a configurable hour (04:00 by default) and every limit, streak
 * and statistic is keyed off that boundary.
 *
 * Pure JVM: no Android imports, so it is unit-testable without an emulator.
 */
object DayBoundary {

    const val DEFAULT_RESET_HOUR: Int = 4

    /**
     * Stable key for the logical day an instant belongs to, as an ISO date.
     *
     * An instant at 02:30 with [resetHour] = 4 belongs to the *previous*
     * calendar date, because that night has not ended yet.
     */
    fun dayKeyFor(instant: Instant, resetHour: Int, zone: ZoneId): String =
        dateFor(instant, resetHour, zone).toString()

    fun dateFor(instant: Instant, resetHour: Int, zone: ZoneId): LocalDate {
        val local = ZonedDateTime.ofInstant(instant, zone)
        return if (local.hour < resetHour) local.toLocalDate().minusDays(1) else local.toLocalDate()
    }

    /** Instant at which the logical day containing [instant] began. */
    fun startOfDay(instant: Instant, resetHour: Int, zone: ZoneId): Instant =
        dateFor(instant, resetHour, zone)
            .atTime(LocalTime.of(resetHour, 0))
            .atZone(zone)
            .toInstant()

    /** Instant at which the logical day containing [instant] ends (exclusive). */
    fun endOfDay(instant: Instant, resetHour: Int, zone: ZoneId): Instant =
        dateFor(instant, resetHour, zone)
            .plusDays(1)
            .atTime(LocalTime.of(resetHour, 0))
            .atZone(zone)
            .toInstant()

    /** The [count] most recent logical day keys, oldest first, ending with today. */
    fun recentDayKeys(now: Instant, count: Int, resetHour: Int, zone: ZoneId): List<String> {
        require(count > 0) { "count must be positive" }
        val today = dateFor(now, resetHour, zone)
        return (count - 1 downTo 0).map { today.minusDays(it.toLong()).toString() }
    }
}
