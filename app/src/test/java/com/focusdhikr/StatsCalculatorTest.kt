package com.focusdhikr

import com.focusdhikr.domain.model.BlockAttempt
import com.focusdhikr.domain.model.GateOutcome
import com.focusdhikr.domain.model.UsageDay
import com.focusdhikr.domain.usage.StatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCalculatorTest {

    private val keys = listOf("2026-03-09", "2026-03-10", "2026-03-11")

    private fun attempt(day: String, outcome: GateOutcome) = BlockAttempt(
        packageName = "com.instagram.android",
        dayKey = day,
        startedAt = 0,
        endedAt = 1,
        reachedPhase = "DECIDE",
        outcome = outcome,
        intentReason = null,
    )

    @Test
    fun `an empty period reports zeroes rather than failing`() {
        val stats = StatsCalculator.compute(keys, emptyList(), emptyList())
        assertEquals(0L, stats.usedMillis)
        assertEquals(0L, stats.reclaimedMillis)
        assertEquals(0, stats.turnedBackCount)
        assertNull(stats.turnBackRate)
    }

    @Test
    fun `rows outside the period are excluded`() {
        val usage = listOf(
            UsageDay("com.instagram.android", "2026-03-10", 60_000),
            UsageDay("com.instagram.android", "2026-01-01", 999_000),
        )
        val stats = StatsCalculator.compute(keys, usage, emptyList())
        assertEquals(60_000L, stats.usedMillis)
    }

    @Test
    fun `reclaimed time counts only turn-backs`() {
        val attempts = listOf(
            attempt("2026-03-10", GateOutcome.TURNED_BACK),
            attempt("2026-03-10", GateOutcome.TURNED_BACK),
            attempt("2026-03-11", GateOutcome.CHOSE_TO_CONTINUE),
            attempt("2026-03-11", GateOutcome.ABANDONED),
        )
        val stats = StatsCalculator.compute(keys, emptyList(), attempts)
        assertEquals(2, stats.turnedBackCount)
        assertEquals(1, stats.continuedCount)
        assertEquals(2 * StatsCalculator.FALLBACK_SESSION_MILLIS, stats.reclaimedMillis)
    }

    @Test
    fun `average session is derived from the user's own history`() {
        val usage = listOf(UsageDay("com.instagram.android", "2026-03-10", 40 * 60_000))
        val attempts = listOf(
            attempt("2026-03-10", GateOutcome.CHOSE_TO_CONTINUE),
            attempt("2026-03-10", GateOutcome.CHOSE_TO_CONTINUE),
        )
        // 40 min across 2 completed sessions -> 20 min each.
        assertEquals(20 * 60_000L, StatsCalculator.averageSessionMillis(usage, attempts))
    }

    @Test
    fun `an absurd average is clamped so the headline stays believable`() {
        val usage = listOf(UsageDay("x", "2026-03-10", 10 * 60 * 60_000))
        val attempts = listOf(attempt("2026-03-10", GateOutcome.CHOSE_TO_CONTINUE))
        val avg = StatsCalculator.averageSessionMillis(usage, attempts)
        assertTrue("10h in one session must be clamped", avg <= 45 * 60_000L)
    }

    @Test
    fun `no history falls back to a conservative estimate`() {
        assertEquals(
            StatsCalculator.FALLBACK_SESSION_MILLIS,
            StatsCalculator.averageSessionMillis(emptyList(), emptyList()),
        )
    }

    @Test
    fun `per-app and per-day totals add up to the period total`() {
        val usage = listOf(
            UsageDay("a", "2026-03-10", 1_000),
            UsageDay("b", "2026-03-10", 2_000),
            UsageDay("a", "2026-03-11", 3_000),
        )
        val stats = StatsCalculator.compute(keys, usage, emptyList())
        assertEquals(6_000L, stats.usedMillis)
        assertEquals(4_000L, stats.perApp["a"])
        assertEquals(2_000L, stats.perApp["b"])
        assertEquals(3_000L, stats.perDay["2026-03-11"])
        assertEquals(0L, stats.perDay["2026-03-09"])
        assertEquals(stats.usedMillis, stats.perDay.values.sum())
    }

    @Test
    fun `turn-back rate ignores abandoned runs`() {
        val attempts = listOf(
            attempt("2026-03-10", GateOutcome.TURNED_BACK),
            attempt("2026-03-10", GateOutcome.CHOSE_TO_CONTINUE),
            attempt("2026-03-10", GateOutcome.ABANDONED),
        )
        val stats = StatsCalculator.compute(keys, emptyList(), attempts)
        assertEquals(0.5f, stats.turnBackRate!!, 0.001f)
    }
}
