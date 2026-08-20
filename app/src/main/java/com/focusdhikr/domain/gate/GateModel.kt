package com.focusdhikr.domain.gate

import com.focusdhikr.domain.model.GateOutcome
import com.focusdhikr.domain.model.IntentReason
import java.text.Normalizer

/**
 * The phases of the progressive pause, in order.
 *
 * Every phase has a way forward. The app adds seconds and attention, never a
 * dead end: if after all of it you still want to go in, you go in.
 */
enum class GatePhase {
    /** 1. What you decided, and what you have used. */
    PAUSE,

    /** 2. What were you about to do, and does it match today. */
    INTENT,

    /** 3. A short wait with a dhikr. */
    WAIT,

    /** 4. One of your own goals, in your own words. */
    PURPOSE,

    /** 5. Write, by hand, why. */
    WRITE,

    /** 6. Your decision, acknowledged and respected. */
    DECIDE,

    /** Terminal. */
    RESOLVED,
}

/**
 * How hard this particular pass through the gate should be.
 *
 * Built by [forAttempt] so friction scales with how many times you have already
 * been here today, rather than being the same wall every time.
 */
data class GateConfig(
    val strict: Boolean,
    val waitSeconds: Int,
    val requireIntent: Boolean,
    val requirePurpose: Boolean,
    val requireWriting: Boolean,
    val minFreeTextChars: Int,
    val acknowledgementSentence: String,
    val grantMillis: Long,
) {
    companion object {
        const val DEFAULT_SENTENCE: String =
            "Reconozco que estoy eligiendo dedicar este tiempo a una distracción que yo mismo había decidido limitar."

        const val BASE_WAIT_SECONDS: Int = 10
        const val STRICT_BASE_WAIT_SECONDS: Int = 25
        const val MAX_WAIT_SECONDS: Int = 90

        val DEFAULT_GRANT_MILLIS: Long = 5 * 60_000L
        val STRICT_GRANT_MILLIS: Long = 2 * 60_000L

        /**
         * @param attemptsToday how many times the gate has already been shown for
         *   this app during the current logical day, *before* this attempt.
         * @param inScheduleWindow true when a window like 22:00-08:00 is active,
         *   which the user asked to be harder still.
         */
        fun forAttempt(
            strict: Boolean,
            attemptsToday: Int,
            inScheduleWindow: Boolean = false,
            sentence: String = DEFAULT_SENTENCE,
        ): GateConfig {
            val hard = strict || inScheduleWindow
            val base = if (hard) STRICT_BASE_WAIT_SECONDS else BASE_WAIT_SECONDS
            val step = if (hard) 10 else 5
            val wait = (base + step * attemptsToday.coerceAtLeast(0))
                .coerceAtMost(MAX_WAIT_SECONDS)

            return GateConfig(
                strict = hard,
                waitSeconds = wait,
                requireIntent = true,
                // In the relaxed mode the goal reminder only appears once you are
                // clearly insisting, so a single deliberate visit stays short.
                requirePurpose = hard || attemptsToday >= 1,
                requireWriting = hard || attemptsToday >= 2,
                minFreeTextChars = if (hard) 25 else 12,
                acknowledgementSentence = sentence,
                grantMillis = if (hard) STRICT_GRANT_MILLIS else DEFAULT_GRANT_MILLIS,
            )
        }
    }
}

/** Immutable state of one pass through the gate. */
data class GateState(
    val config: GateConfig,
    val phase: GatePhase = GatePhase.PAUSE,
    val intent: IntentReason? = null,
    /** Answer to "does this bring you closer to what you want today?" */
    val alignsWithToday: Boolean? = null,
    val waitRemainingSeconds: Int = config.waitSeconds,
    val freeText: String = "",
    val typedSentence: String = "",
    val outcome: GateOutcome? = null,
) {
    /** Phases actually used for this attempt, in order. */
    val activePhases: List<GatePhase> = buildList {
        add(GatePhase.PAUSE)
        if (config.requireIntent) add(GatePhase.INTENT)
        add(GatePhase.WAIT)
        if (config.requirePurpose) add(GatePhase.PURPOSE)
        if (config.requireWriting) add(GatePhase.WRITE)
        add(GatePhase.DECIDE)
    }

    val stepNumber: Int get() = (activePhases.indexOf(phase) + 1).coerceAtLeast(1)
    val stepCount: Int get() = activePhases.size

    val isResolved: Boolean get() = phase == GatePhase.RESOLVED

    /** Whether the "continue" affordance should be enabled right now. */
    val canAdvance: Boolean
        get() = when (phase) {
            GatePhase.PAUSE -> true
            GatePhase.INTENT -> intent != null && alignsWithToday != null
            GatePhase.WAIT -> waitRemainingSeconds <= 0
            GatePhase.PURPOSE -> true
            GatePhase.WRITE -> freeText.trim().length >= config.minFreeTextChars
            GatePhase.DECIDE -> SentenceMatcher.matches(typedSentence, config.acknowledgementSentence)
            GatePhase.RESOLVED -> false
        }
}

/**
 * Compares what the user typed against the acknowledgement sentence.
 *
 * The point is to make you slow down and read your own words, not to fight a
 * phone keyboard. So accents, case, punctuation and repeated whitespace are all
 * forgiven; the words themselves are not.
 */
object SentenceMatcher {

    fun normalize(input: String): String =
        Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .replace(PUNCTUATION, " ")
            .replace(WHITESPACE, " ")
            .trim()

    fun matches(typed: String, expected: String): Boolean =
        normalize(typed) == normalize(expected)

    /** 0f..1f, how much of [expected] has been typed correctly so far. */
    fun progress(typed: String, expected: String): Float {
        val target = normalize(expected)
        if (target.isEmpty()) return 1f
        val current = normalize(typed)
        var shared = 0
        while (shared < current.length && shared < target.length && current[shared] == target[shared]) {
            shared++
        }
        return (shared.toFloat() / target.length).coerceIn(0f, 1f)
    }

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val PUNCTUATION = Regex("[\\p{Punct}¡¿‘’“”]+")
    private val WHITESPACE = Regex("\\s+")
}
