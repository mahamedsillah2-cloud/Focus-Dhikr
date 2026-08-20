package com.focusdhikr

import com.focusdhikr.domain.model.ScheduleWindow
import com.focusdhikr.domain.model.TrackedApp
import com.focusdhikr.domain.model.BlockReason
import com.focusdhikr.domain.usage.BlockDecision
import com.focusdhikr.domain.usage.ForegroundEvent
import com.focusdhikr.domain.usage.LimitEvaluator
import com.focusdhikr.domain.usage.SessionAccumulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAccumulatorTest {

    private val ig = "com.instagram.android"
    private val tt = "com.zhiliaoapp.musically"

    @Test
    fun `a closed session is credited exactly once`() {
        val r = SessionAccumulator.accumulate(
            events = listOf(
                ForegroundEvent(ig, 1_000, resumed = true),
                ForegroundEvent(ig, 61_000, resumed = false),
            ),
            until = 200_000,
        )
        assertEquals(60_000L, r.addedMillis[ig])
        assertTrue(r.openSessions.isEmpty())
    }

    @Test
    fun `an open session is credited up to the batch end and carried forward`() {
        val first = SessionAccumulator.accumulate(
            events = listOf(ForegroundEvent(ig, 1_000, resumed = true)),
            until = 31_000,
        )
        assertEquals(30_000L, first.addedMillis[ig])
        assertEquals(31_000L, first.openSessions[ig])

        // The next batch must not re-count the first 30 seconds.
        val second = SessionAccumulator.accumulate(
            events = listOf(ForegroundEvent(ig, 41_000, resumed = false)),
            until = 60_000,
            openSessions = first.openSessions,
        )
        assertEquals(10_000L, second.addedMillis[ig])
        assertTrue(second.openSessions.isEmpty())
    }

    @Test
    fun `total across batches equals the real elapsed time`() {
        val batch1 = SessionAccumulator.accumulate(
            listOf(ForegroundEvent(ig, 0, resumed = true)), until = 10_000
        )
        val batch2 = SessionAccumulator.accumulate(
            emptyList(), until = 20_000, openSessions = batch1.openSessions
        )
        val batch3 = SessionAccumulator.accumulate(
            listOf(ForegroundEvent(ig, 25_000, resumed = false)),
            until = 30_000,
            openSessions = batch2.openSessions,
        )
        val total = listOf(batch1, batch2, batch3).sumOf { it.addedMillis[ig] ?: 0L }
        assertEquals(25_000L, total)
    }

    @Test
    fun `a lost PAUSE does not swallow the previous stretch`() {
        val r = SessionAccumulator.accumulate(
            events = listOf(
                ForegroundEvent(ig, 0, resumed = true),
                // No PAUSE arrived; the next RESUMED closes the first stretch.
                ForegroundEvent(ig, 5_000, resumed = true),
                ForegroundEvent(ig, 9_000, resumed = false),
            ),
            until = 20_000,
        )
        assertEquals(9_000L, r.addedMillis[ig])
    }

    @Test
    fun `a PAUSE with no matching RESUMED is ignored rather than counted`() {
        val r = SessionAccumulator.accumulate(
            events = listOf(ForegroundEvent(ig, 5_000, resumed = false)),
            until = 10_000,
        )
        assertNull(r.addedMillis[ig])
    }

    @Test
    fun `an implausibly long session is capped instead of poisoning the day`() {
        val day = 24 * 60 * 60 * 1000L
        val r = SessionAccumulator.accumulate(
            events = listOf(ForegroundEvent(ig, 0, resumed = true)),
            until = day,
        )
        assertEquals(SessionAccumulator.DEFAULT_MAX_SESSION_MILLIS, r.addedMillis[ig])
    }

    @Test
    fun `apps are accounted independently`() {
        val r = SessionAccumulator.accumulate(
            events = listOf(
                ForegroundEvent(ig, 0, resumed = true),
                ForegroundEvent(ig, 10_000, resumed = false),
                ForegroundEvent(tt, 10_000, resumed = true),
                ForegroundEvent(tt, 40_000, resumed = false),
            ),
            until = 50_000,
        )
        assertEquals(10_000L, r.addedMillis[ig])
        assertEquals(30_000L, r.addedMillis[tt])
    }

    @Test
    fun `an empty batch changes nothing`() {
        val r = SessionAccumulator.accumulate(emptyList(), until = 1_000)
        assertTrue(r.addedMillis.isEmpty())
        assertEquals(0L, r.lastEventTimestamp)
    }
}

class LimitEvaluatorTest {

    private val hour = 60 * 60 * 1000L
    private val app = TrackedApp("com.instagram.android", "Instagram", dailyLimitMillis = hour)

    private fun evaluate(
        used: Long = 0,
        grant: Long? = null,
        now: Long = 0,
        windows: List<ScheduleWindow> = emptyList(),
        minute: Int = 12 * 60,
        day: Int = 3,
        strict: Boolean = false,
        target: TrackedApp? = app,
    ) = LimitEvaluator.evaluate(target, used, grant, now, windows, minute, day, strict)

    @Test
    fun `an untracked app is never blocked`() {
        assertEquals(BlockDecision.Allow, evaluate(target = null, used = 10 * hour))
    }

    @Test
    fun `a disabled app is never blocked`() {
        assertEquals(
            BlockDecision.Allow,
            evaluate(target = app.copy(enabled = false), used = 10 * hour),
        )
    }

    @Test
    fun `under the limit is allowed`() {
        assertEquals(BlockDecision.Allow, evaluate(used = 30 * 60 * 1000L))
    }

    @Test
    fun `reaching the limit exactly blocks`() {
        val d = evaluate(used = hour)
        assertTrue(d is BlockDecision.Block)
        val reason = (d as BlockDecision.Block).reason
        assertTrue(reason is BlockReason.LimitReached)
    }

    @Test
    fun `a live grant is honoured`() {
        val d = evaluate(used = 2 * hour, grant = 5_000, now = 1_000)
        assertEquals(BlockDecision.AllowGranted(5_000), d)
    }

    @Test
    fun `an expired grant blocks again`() {
        val d = evaluate(used = 2 * hour, grant = 5_000, now = 5_000)
        assertTrue(d is BlockDecision.Block)
    }

    @Test
    fun `a night window blocks even with time left`() {
        val night = ScheduleWindow(
            packageName = null,
            startMinuteOfDay = 22 * 60,
            endMinuteOfDay = 8 * 60,
            label = "Noche",
        )
        val d = evaluate(used = 0, windows = listOf(night), minute = 23 * 60, day = 3)
        assertTrue(d is BlockDecision.Block)
        assertTrue((d as BlockDecision.Block).reason is BlockReason.ScheduleWindow)
        assertTrue("a window is always strict", d.strict)
    }

    @Test
    fun `a grant does not survive into a schedule window`() {
        val night = ScheduleWindow(
            packageName = null,
            startMinuteOfDay = 22 * 60,
            endMinuteOfDay = 8 * 60,
        )
        val d = evaluate(grant = Long.MAX_VALUE, now = 0, windows = listOf(night), minute = 22 * 60 + 1)
        assertTrue(d is BlockDecision.Block)
    }

    @Test
    fun `a window for another app does not block this one`() {
        val other = ScheduleWindow(
            packageName = "com.other.app",
            startMinuteOfDay = 0,
            endMinuteOfDay = 24 * 60 - 1,
        )
        assertEquals(BlockDecision.Allow, evaluate(windows = listOf(other)))
    }

    @Test
    fun `global strict mode marks a limit block as strict`() {
        val d = evaluate(used = hour, strict = true)
        assertTrue((d as BlockDecision.Block).strict)
    }

    @Test
    fun `a zero limit blocks from the first second`() {
        val d = evaluate(target = app.copy(dailyLimitMillis = 0), used = 0)
        assertTrue(d is BlockDecision.Block)
    }

    @Test
    fun `remaining time never goes negative`() {
        assertEquals(0L, LimitEvaluator.remainingMillis(app, 10 * hour))
        assertEquals(hour / 2, LimitEvaluator.remainingMillis(app, hour / 2))
    }
}

class ScheduleWindowTest {

    private fun night(days: Int = ScheduleWindow.ALL_DAYS) = ScheduleWindow(
        packageName = null,
        startMinuteOfDay = 22 * 60,
        endMinuteOfDay = 8 * 60,
        daysMask = days,
    )

    @Test
    fun `a wrapping window covers both sides of midnight`() {
        assertTrue(night().contains(23 * 60, 3))
        assertTrue(night().contains(2 * 60, 4))
        assertTrue(night().contains(22 * 60, 3))
    }

    @Test
    fun `a wrapping window excludes the middle of the day`() {
        assertTrue(!night().contains(12 * 60, 3))
        assertTrue("the end minute is exclusive", !night().contains(8 * 60, 3))
    }

    @Test
    fun `the small hours belong to the day the window started on`() {
        // Monday-only night window: 01:00 on Tuesday is still inside it.
        val mondayOnly = night(days = 0b0000001)
        assertTrue(mondayOnly.contains(23 * 60, 1))
        assertTrue(mondayOnly.contains(1 * 60, 2))
        assertTrue(!mondayOnly.contains(1 * 60, 3))
    }

    @Test
    fun `a sunday night window wraps round to monday morning`() {
        val sundayOnly = night(days = 0b1000000)
        assertTrue(sundayOnly.contains(23 * 60, 7))
        assertTrue(sundayOnly.contains(1 * 60, 1))
    }

    @Test
    fun `a same-day window behaves normally`() {
        val work = ScheduleWindow(packageName = null, startMinuteOfDay = 9 * 60, endMinuteOfDay = 17 * 60)
        assertTrue(work.contains(10 * 60, 2))
        assertTrue(!work.contains(18 * 60, 2))
        assertTrue(!work.contains(8 * 60, 2))
    }

    @Test
    fun `a disabled window never matches`() {
        assertTrue(!night().copy(enabled = false).contains(23 * 60, 3))
    }
}
