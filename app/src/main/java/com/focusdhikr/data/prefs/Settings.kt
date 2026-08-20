package com.focusdhikr.data.prefs

import com.focusdhikr.content.DhikrLibrary
import com.focusdhikr.core.DayBoundary
import com.focusdhikr.domain.gate.GateConfig

/** How often ambient reminders may appear (requirement 6). */
enum class ReminderFrequency(val perDay: Int) {
    NEVER(0),
    RARE(1),
    OCCASIONAL(3),
    REGULAR(6);

    val labelEs: String
        get() = when (this) {
            NEVER -> "Nunca"
            RARE -> "Una vez al día"
            OCCASIONAL -> "Tres veces al día"
            REGULAR -> "Cada pocas horas"
        }
}

/** How much scripture appears inside the gate. Spiritual content is opt-out, never forced. */
enum class SpiritualDepth {
    /** No adhkar and no citations anywhere. The app still works fully. */
    OFF,

    /** A single dhikr during the wait. The default: present, not preachy. */
    SUBTLE,

    /** Dhikr plus a themed Qur'an or hadith citation at the purpose phase. */
    FULL;

    val labelEs: String
        get() = when (this) {
            OFF -> "Desactivado"
            SUBTLE -> "Discreto"
            FULL -> "Completo"
        }
}

/**
 * Everything the user can configure.
 *
 * Held as one immutable snapshot so a screen never sees a half-updated mix of
 * old and new values.
 */
data class Settings(
    val onboardingComplete: Boolean = false,
    val dayResetHour: Int = DayBoundary.DEFAULT_RESET_HOUR,

    /** The "no me dejes entrar" master switch. */
    val strictMode: Boolean = false,

    /**
     * Turning strict mode off does not take effect until this instant, if the
     * user chose a cool-down while calm. 0 means off.
     */
    val strictModeUnlocksAt: Long = 0L,
    val strictModeCooldownMinutes: Int = 0,

    val spiritualDepth: SpiritualDepth = SpiritualDepth.SUBTLE,
    val enabledDhikrIds: Set<String> = DhikrLibrary.defaultEnabledIds,
    val showTranslations: Boolean = true,

    val reminderFrequency: ReminderFrequency = ReminderFrequency.RARE,
    val reminderQuietStartMinute: Int = 22 * 60,
    val reminderQuietEndMinute: Int = 8 * 60,

    val acknowledgementSentence: String = GateConfig.DEFAULT_SENTENCE,
    val grantMinutes: Int = 5,

    /** Emergency path budget, per rolling 7 days. 0 disables the path entirely. */
    val emergencyUsesPerWeek: Int = 3,
    val emergencyWaitSeconds: Int = 30,

    /** When false, phase-5 text is used and immediately discarded. */
    val keepWrittenReasons: Boolean = true,
    /** Days of history to keep. Older rows are pruned automatically. */
    val historyRetentionDays: Int = 90,

    val deviceAdminEnabled: Boolean = false,
) {
    /** True when strict mode is on, including while a cool-down is still running. */
    fun strictActive(now: Long): Boolean = strictMode || now < strictModeUnlocksAt
}
