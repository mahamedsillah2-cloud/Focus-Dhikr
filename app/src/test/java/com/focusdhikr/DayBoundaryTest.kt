package com.focusdhikr

import com.focusdhikr.core.DayBoundary
import com.focusdhikr.core.Durations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DayBoundaryTest {

    private val madrid = ZoneId.of("Europe/Madrid")

    private fun at(iso: String) = Instant.parse(iso)

    @Test
    fun `late night belongs to the day that has not ended yet`() {
        // 02:30 local on the 11th, reset hour 04:00 -> still the 10th.
        assertEquals("2026-03-10", DayBoundary.dayKeyFor(at("2026-03-11T01:30:00Z"), 4, madrid))
    }

    @Test
    fun `after the reset hour the new day has started`() {
        assertEquals("2026-03-11", DayBoundary.dayKeyFor(at("2026-03-11T07:00:00Z"), 4, madrid))
    }

    @Test
    fun `a midnight reset hour behaves like the calendar date`() {
        assertEquals("2026-03-11", DayBoundary.dayKeyFor(at("2026-03-11T01:30:00Z"), 0, madrid))
    }

    @Test
    fun `start and end of day bracket the instant`() {
        val now = at("2026-03-11T20:00:00Z")
        val start = DayBoundary.startOfDay(now, 4, madrid)
        val end = DayBoundary.endOfDay(now, 4, madrid)
        assertTrue(start.isBefore(now))
        assertTrue(end.isAfter(now))
        assertEquals(
            DayBoundary.dayKeyFor(now, 4, madrid),
            DayBoundary.dayKeyFor(start, 4, madrid),
        )
    }

    @Test
    fun `recent day keys end with today and are ordered`() {
        val keys = DayBoundary.recentDayKeys(at("2026-03-11T20:00:00Z"), 7, 4, madrid)
        assertEquals(7, keys.size)
        assertEquals("2026-03-11", keys.last())
        assertEquals("2026-03-05", keys.first())
        assertEquals(keys.sorted(), keys)
    }
}

class DurationsTest {

    @Test
    fun `durations read the way a person would say them`() {
        assertEquals("0 min", Durations.format(0))
        assertEquals("menos de 1 min", Durations.format(20_000))
        assertEquals("45 min", Durations.format(45 * 60_000L))
        assertEquals("1 h", Durations.format(60 * 60_000L))
        assertEquals("1 h 04 min", Durations.format(64 * 60_000L))
        assertEquals("2 h 30 min", Durations.format(150 * 60_000L))
    }

    @Test
    fun `negative durations are clamped, never shown as negative`() {
        assertEquals("0 min", Durations.format(-5_000))
        assertEquals("0:00", Durations.formatCompact(-5_000))
    }

    @Test
    fun `compact form pads minutes`() {
        assertEquals("1:04", Durations.formatCompact(64 * 60_000L))
        assertEquals("0:09", Durations.formatCompact(9 * 60_000L))
    }
}
