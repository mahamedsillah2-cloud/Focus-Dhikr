package com.focusdhikr

import com.focusdhikr.domain.gate.GateConfig
import com.focusdhikr.domain.gate.GateEvent
import com.focusdhikr.domain.gate.GatePhase
import com.focusdhikr.domain.gate.GateState
import com.focusdhikr.domain.gate.GateStateMachine
import com.focusdhikr.domain.gate.SentenceMatcher
import com.focusdhikr.domain.model.GateOutcome
import com.focusdhikr.domain.model.IntentReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GateStateMachineTest {

    private fun state(
        strict: Boolean = false,
        attemptsToday: Int = 0,
        inWindow: Boolean = false,
    ): GateState = GateStateMachine.initial(
        GateConfig.forAttempt(strict, attemptsToday, inWindow)
    )

    private fun run(start: GateState, vararg events: GateEvent): GateState =
        events.fold(start) { acc, e -> GateStateMachine.reduce(acc, e) }

    // --- turning back is always available, from every phase ---------------

    @Test
    fun `turning back resolves from any phase`() {
        for (phase in GatePhase.entries.filter { it != GatePhase.RESOLVED }) {
            val s = state(strict = true).copy(phase = phase)
            val out = GateStateMachine.reduce(s, GateEvent.TurnBack)
            assertEquals(GatePhase.RESOLVED, out.phase)
            assertEquals(GateOutcome.TURNED_BACK, out.outcome)
        }
    }

    @Test
    fun `a resolved gate ignores further events`() {
        val resolved = run(state(), GateEvent.TurnBack)
        val after = run(resolved, GateEvent.Advance, GateEvent.Tick, GateEvent.EditFreeText("x"))
        assertEquals(resolved, after)
    }

    // --- phase 1 -> 2 -----------------------------------------------------

    @Test
    fun `first continue moves to intent, it does not open the app`() {
        val s = run(state(), GateEvent.Advance)
        assertEquals(GatePhase.INTENT, s.phase)
        assertNotEquals(GateOutcome.CHOSE_TO_CONTINUE, s.outcome)
    }

    // --- phase 2 requires both answers -----------------------------------

    @Test
    fun `intent phase needs a reason and an alignment answer`() {
        var s = run(state(), GateEvent.Advance)
        assertFalse(s.canAdvance)

        s = run(s, GateEvent.ChooseIntent(IntentReason.BORED))
        assertFalse("reason alone is not enough", s.canAdvance)

        s = run(s, GateEvent.AnswerAlignment(false))
        assertTrue(s.canAdvance)
    }

    // --- phase 3 is a real wait ------------------------------------------

    @Test
    fun `wait cannot be skipped by pressing continue`() {
        var s = run(
            state(),
            GateEvent.Advance,
            GateEvent.ChooseIntent(IntentReason.HABIT),
            GateEvent.AnswerAlignment(false),
            GateEvent.Advance,
        )
        assertEquals(GatePhase.WAIT, s.phase)
        assertEquals(GateConfig.BASE_WAIT_SECONDS, s.waitRemainingSeconds)

        repeat(50) { s = GateStateMachine.reduce(s, GateEvent.Advance) }
        assertEquals("still waiting", GatePhase.WAIT, s.phase)

        repeat(GateConfig.BASE_WAIT_SECONDS) { s = GateStateMachine.reduce(s, GateEvent.Tick) }
        assertEquals(0, s.waitRemainingSeconds)
        assertTrue(s.canAdvance)
    }

    @Test
    fun `ticks outside the wait phase do nothing`() {
        val s = state()
        assertEquals(s, GateStateMachine.reduce(s, GateEvent.Tick))
    }

    @Test
    fun `the wait never goes negative`() {
        var s = run(
            state(),
            GateEvent.Advance,
            GateEvent.ChooseIntent(IntentReason.BORED),
            GateEvent.AnswerAlignment(true),
            GateEvent.Advance,
        )
        repeat(200) { s = GateStateMachine.reduce(s, GateEvent.Tick) }
        assertEquals(0, s.waitRemainingSeconds)
    }

    // --- friction escalates with insistence ------------------------------

    @Test
    fun `repeat attempts on the same day get longer waits`() {
        val first = GateConfig.forAttempt(strict = false, attemptsToday = 0)
        val fourth = GateConfig.forAttempt(strict = false, attemptsToday = 3)
        assertTrue(fourth.waitSeconds > first.waitSeconds)
    }

    @Test
    fun `wait is capped so the app never becomes a wall`() {
        val absurd = GateConfig.forAttempt(strict = true, attemptsToday = 500)
        assertEquals(GateConfig.MAX_WAIT_SECONDS, absurd.waitSeconds)
    }

    @Test
    fun `a single relaxed visit is short, strict mode is not`() {
        val relaxed = GateConfig.forAttempt(strict = false, attemptsToday = 0)
        assertFalse(relaxed.requirePurpose)
        assertFalse(relaxed.requireWriting)

        val strict = GateConfig.forAttempt(strict = true, attemptsToday = 0)
        assertTrue(strict.requirePurpose)
        assertTrue(strict.requireWriting)
    }

    @Test
    fun `a schedule window is treated as strict even when strict mode is off`() {
        val windowed = GateConfig.forAttempt(strict = false, attemptsToday = 0, inScheduleWindow = true)
        assertTrue(windowed.strict)
        assertTrue(windowed.requireWriting)
    }

    // --- the whole strict run --------------------------------------------

    @Test
    fun `strict mode walks all six phases and then respects the decision`() {
        val cfg = GateConfig.forAttempt(strict = true, attemptsToday = 0)
        var s = GateStateMachine.initial(cfg)

        assertEquals(
            listOf(
                GatePhase.PAUSE,
                GatePhase.INTENT,
                GatePhase.WAIT,
                GatePhase.PURPOSE,
                GatePhase.WRITE,
                GatePhase.DECIDE,
            ),
            s.activePhases,
        )
        assertEquals(6, s.stepCount)

        s = run(s, GateEvent.Advance)
        assertEquals(GatePhase.INTENT, s.phase)

        s = run(
            s,
            GateEvent.ChooseIntent(IntentReason.SEEKING_DISTRACTION),
            GateEvent.AnswerAlignment(false),
            GateEvent.Advance,
        )
        assertEquals(GatePhase.WAIT, s.phase)

        repeat(cfg.waitSeconds) { s = GateStateMachine.reduce(s, GateEvent.Tick) }
        s = run(s, GateEvent.Advance)
        assertEquals(GatePhase.PURPOSE, s.phase)

        s = run(s, GateEvent.Advance)
        assertEquals(GatePhase.WRITE, s.phase)

        s = run(s, GateEvent.EditFreeText("corto"))
        assertFalse("too short to count as writing", s.canAdvance)

        s = run(s, GateEvent.EditFreeText("Quiero ver si alguien ha respondido a mi mensaje de esta mañana."))
        assertTrue(s.canAdvance)

        s = run(s, GateEvent.Advance)
        assertEquals(GatePhase.DECIDE, s.phase)

        s = run(s, GateEvent.EditSentence("no es la frase"))
        assertFalse(s.canAdvance)

        s = run(s, GateEvent.EditSentence(cfg.acknowledgementSentence))
        assertTrue(s.canAdvance)

        s = run(s, GateEvent.Advance)
        assertEquals(GatePhase.RESOLVED, s.phase)
        assertEquals(
            "after all of it, the user's choice stands",
            GateOutcome.CHOSE_TO_CONTINUE,
            s.outcome,
        )
    }

    @Test
    fun `re-entering the wait restarts the full countdown`() {
        val cfg = GateConfig.forAttempt(strict = true, attemptsToday = 0)
        var s = GateStateMachine.initial(cfg)
        s = run(
            s,
            GateEvent.Advance,
            GateEvent.ChooseIntent(IntentReason.BORED),
            GateEvent.AnswerAlignment(false),
            GateEvent.Advance,
        )
        repeat(3) { s = GateStateMachine.reduce(s, GateEvent.Tick) }
        assertEquals(cfg.waitSeconds - 3, s.waitRemainingSeconds)

        // Simulate leaving the wait and coming back to it.
        s = s.copy(phase = GatePhase.INTENT)
        s = GateStateMachine.reduce(s, GateEvent.Advance)
        assertEquals(GatePhase.WAIT, s.phase)
        assertEquals(cfg.waitSeconds, s.waitRemainingSeconds)
    }

    @Test
    fun `abandoning is recorded separately from turning back`() {
        val s = run(state(), GateEvent.Abandon)
        assertEquals(GateOutcome.ABANDONED, s.outcome)
    }

    @Test
    fun `step numbering stays inside the active phase list`() {
        val cfg = GateConfig.forAttempt(strict = false, attemptsToday = 0)
        val s = GateStateMachine.initial(cfg)
        assertEquals(1, s.stepNumber)
        assertTrue(s.stepCount in 1..6)
    }

    // --- the sentence check is friction, not a spelling exam ---------------

    @Test
    fun `sentence matching forgives accents case and punctuation`() {
        val expected = GateConfig.DEFAULT_SENTENCE
        assertTrue(SentenceMatcher.matches(expected, expected))
        assertTrue(
            SentenceMatcher.matches(
                "reconozco que estoy eligiendo dedicar este tiempo a una distraccion que yo mismo habia decidido limitar",
                expected,
            )
        )
        assertTrue(
            SentenceMatcher.matches("  $expected  ".replace(" ", "  "), expected)
        )
    }

    @Test
    fun `sentence matching does not forgive missing or wrong words`() {
        val expected = GateConfig.DEFAULT_SENTENCE
        assertFalse(SentenceMatcher.matches("Reconozco que estoy eligiendo", expected))
        assertFalse(SentenceMatcher.matches("", expected))
        assertFalse(
            SentenceMatcher.matches(
                expected.replace("limitar", "ampliar"),
                expected,
            )
        )
    }

    @Test
    fun `sentence progress grows as the user types`() {
        val expected = GateConfig.DEFAULT_SENTENCE
        assertEquals(0f, SentenceMatcher.progress("", expected), 0.001f)
        val partial = SentenceMatcher.progress("Reconozco que estoy", expected)
        assertTrue(partial > 0f && partial < 1f)
        assertEquals(1f, SentenceMatcher.progress(expected, expected), 0.001f)
    }
}
