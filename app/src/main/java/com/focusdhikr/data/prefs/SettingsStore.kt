package com.focusdhikr.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.focusdhikr.content.DhikrLibrary
import com.focusdhikr.core.DayBoundary
import com.focusdhikr.domain.gate.GateConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focus_settings")

/**
 * On-device settings.
 *
 * DataStore rather than SharedPreferences so reads are a Flow and writes are
 * transactional; the enforcement service reads settings on a hot path and must
 * never see a torn value.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val dayResetHour = intPreferencesKey("day_reset_hour")
        val strictMode = booleanPreferencesKey("strict_mode")
        val strictModeUnlocksAt = longPreferencesKey("strict_unlocks_at")
        val strictModeCooldownMinutes = intPreferencesKey("strict_cooldown_minutes")
        val spiritualDepth = stringPreferencesKey("spiritual_depth")
        val enabledDhikrIds = stringSetPreferencesKey("enabled_dhikr_ids")
        val showTranslations = booleanPreferencesKey("show_translations")
        val reminderFrequency = stringPreferencesKey("reminder_frequency")
        val reminderQuietStart = intPreferencesKey("reminder_quiet_start")
        val reminderQuietEnd = intPreferencesKey("reminder_quiet_end")
        val acknowledgementSentence = stringPreferencesKey("acknowledgement_sentence")
        val grantMinutes = intPreferencesKey("grant_minutes")
        val emergencyUsesPerWeek = intPreferencesKey("emergency_uses_per_week")
        val emergencyWaitSeconds = intPreferencesKey("emergency_wait_seconds")
        val keepWrittenReasons = booleanPreferencesKey("keep_written_reasons")
        val historyRetentionDays = intPreferencesKey("history_retention_days")
        val deviceAdminEnabled = booleanPreferencesKey("device_admin_enabled")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings()
        return Settings(
            onboardingComplete = this[Keys.onboardingComplete] ?: defaults.onboardingComplete,
            dayResetHour = (this[Keys.dayResetHour] ?: DayBoundary.DEFAULT_RESET_HOUR).coerceIn(0, 23),
            strictMode = this[Keys.strictMode] ?: defaults.strictMode,
            strictModeUnlocksAt = this[Keys.strictModeUnlocksAt] ?: defaults.strictModeUnlocksAt,
            strictModeCooldownMinutes = this[Keys.strictModeCooldownMinutes]
                ?: defaults.strictModeCooldownMinutes,
            spiritualDepth = this[Keys.spiritualDepth]?.let { name ->
                runCatching { SpiritualDepth.valueOf(name) }.getOrNull()
            } ?: defaults.spiritualDepth,
            enabledDhikrIds = this[Keys.enabledDhikrIds]?.takeIf { it.isNotEmpty() }
                ?: DhikrLibrary.defaultEnabledIds,
            showTranslations = this[Keys.showTranslations] ?: defaults.showTranslations,
            reminderFrequency = this[Keys.reminderFrequency]?.let { name ->
                runCatching { ReminderFrequency.valueOf(name) }.getOrNull()
            } ?: defaults.reminderFrequency,
            reminderQuietStartMinute = this[Keys.reminderQuietStart] ?: defaults.reminderQuietStartMinute,
            reminderQuietEndMinute = this[Keys.reminderQuietEnd] ?: defaults.reminderQuietEndMinute,
            acknowledgementSentence = this[Keys.acknowledgementSentence]?.takeIf { it.isNotBlank() }
                ?: GateConfig.DEFAULT_SENTENCE,
            grantMinutes = (this[Keys.grantMinutes] ?: defaults.grantMinutes).coerceIn(1, 60),
            emergencyUsesPerWeek = this[Keys.emergencyUsesPerWeek] ?: defaults.emergencyUsesPerWeek,
            emergencyWaitSeconds = this[Keys.emergencyWaitSeconds] ?: defaults.emergencyWaitSeconds,
            keepWrittenReasons = this[Keys.keepWrittenReasons] ?: defaults.keepWrittenReasons,
            historyRetentionDays = (this[Keys.historyRetentionDays] ?: defaults.historyRetentionDays)
                .coerceIn(7, 365),
            deviceAdminEnabled = this[Keys.deviceAdminEnabled] ?: defaults.deviceAdminEnabled,
        )
    }

    suspend fun setOnboardingComplete(value: Boolean) = edit { it[Keys.onboardingComplete] = value }

    suspend fun setDayResetHour(hour: Int) = edit { it[Keys.dayResetHour] = hour.coerceIn(0, 23) }

    /**
     * Turning strict mode ON is immediate. Turning it OFF respects the cool-down
     * the user set for themselves while calm - that is the whole point of it.
     */
    suspend fun setStrictMode(enabled: Boolean, now: Long) = edit { prefs ->
        if (enabled) {
            prefs[Keys.strictMode] = true
            prefs[Keys.strictModeUnlocksAt] = 0L
        } else {
            val cooldown = prefs[Keys.strictModeCooldownMinutes] ?: 0
            prefs[Keys.strictMode] = false
            prefs[Keys.strictModeUnlocksAt] =
                if (cooldown > 0) now + cooldown * 60_000L else 0L
        }
    }

    suspend fun setStrictModeCooldownMinutes(minutes: Int) =
        edit { it[Keys.strictModeCooldownMinutes] = minutes.coerceIn(0, 24 * 60) }

    suspend fun setSpiritualDepth(depth: SpiritualDepth) =
        edit { it[Keys.spiritualDepth] = depth.name }

    suspend fun setEnabledDhikrIds(ids: Set<String>) =
        edit { it[Keys.enabledDhikrIds] = ids }

    suspend fun setShowTranslations(value: Boolean) = edit { it[Keys.showTranslations] = value }

    suspend fun setReminderFrequency(frequency: ReminderFrequency) =
        edit { it[Keys.reminderFrequency] = frequency.name }

    suspend fun setReminderQuietHours(startMinute: Int, endMinute: Int) = edit {
        it[Keys.reminderQuietStart] = startMinute
        it[Keys.reminderQuietEnd] = endMinute
    }

    suspend fun setAcknowledgementSentence(sentence: String) =
        edit { it[Keys.acknowledgementSentence] = sentence.trim() }

    suspend fun setGrantMinutes(minutes: Int) =
        edit { it[Keys.grantMinutes] = minutes.coerceIn(1, 60) }

    suspend fun setEmergencyUsesPerWeek(uses: Int) =
        edit { it[Keys.emergencyUsesPerWeek] = uses.coerceIn(0, 20) }

    suspend fun setEmergencyWaitSeconds(seconds: Int) =
        edit { it[Keys.emergencyWaitSeconds] = seconds.coerceIn(0, 300) }

    suspend fun setKeepWrittenReasons(value: Boolean) =
        edit { it[Keys.keepWrittenReasons] = value }

    suspend fun setHistoryRetentionDays(days: Int) =
        edit { it[Keys.historyRetentionDays] = days.coerceIn(7, 365) }

    suspend fun setDeviceAdminEnabled(value: Boolean) =
        edit { it[Keys.deviceAdminEnabled] = value }

    suspend fun clearAll() = context.dataStore.edit { it.clear() }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
