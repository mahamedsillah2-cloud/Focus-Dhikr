package com.focusdhikr.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusdhikr.AppGraph
import com.focusdhikr.core.Permissions
import com.focusdhikr.data.prefs.ReminderFrequency
import com.focusdhikr.data.prefs.Settings
import com.focusdhikr.data.prefs.SpiritualDepth
import com.focusdhikr.domain.model.ScheduleWindow
import com.focusdhikr.service.FocusDeviceAdminReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissionStatus(
    val usageAccess: Boolean = false,
    val overlay: Boolean = false,
    val accessibility: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val deviceAdminActive: Boolean = false,
)

data class SettingsUiState(
    val settings: Settings = Settings(),
    val windows: List<ScheduleWindow> = emptyList(),
    val permissions: PermissionStatus = PermissionStatus(),
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val graph = AppGraph.get(application)
    private val repository = graph.repository
    private val store = graph.settingsStore

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            store.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            repository.observeWindows().collect { windows ->
                _state.update { it.copy(windows = windows) }
            }
        }
        refreshPermissions()
    }

    /** The system settings screens are outside our process, so re-read on resume. */
    fun refreshPermissions() {
        val context = getApplication<Application>()
        _state.update {
            it.copy(
                permissions = PermissionStatus(
                    usageAccess = Permissions.hasUsageAccess(context),
                    overlay = Permissions.canDrawOverlays(context),
                    accessibility = Permissions.isAccessibilityEnabled(context),
                    batteryUnrestricted = Permissions.isIgnoringBatteryOptimizations(context),
                    deviceAdminActive = FocusDeviceAdminReceiver.isActive(context),
                )
            )
        }
    }

    fun setStrictMode(enabled: Boolean) {
        viewModelScope.launch { store.setStrictMode(enabled, System.currentTimeMillis()) }
    }

    fun setStrictCooldown(minutes: Int) {
        viewModelScope.launch { store.setStrictModeCooldownMinutes(minutes) }
    }

    fun setSpiritualDepth(depth: SpiritualDepth) {
        viewModelScope.launch { store.setSpiritualDepth(depth) }
    }

    fun toggleDhikr(id: String) {
        viewModelScope.launch {
            val current = store.current().enabledDhikrIds
            val next = if (id in current) current - id else current + id
            // Never end up with none enabled while the spiritual layer is on:
            // an empty set would silently show nothing rather than "off".
            if (next.isNotEmpty()) store.setEnabledDhikrIds(next)
        }
    }

    fun setShowTranslations(value: Boolean) {
        viewModelScope.launch { store.setShowTranslations(value) }
    }

    fun setReminderFrequency(frequency: ReminderFrequency) {
        viewModelScope.launch { store.setReminderFrequency(frequency) }
    }

    fun setGrantMinutes(minutes: Int) {
        viewModelScope.launch { store.setGrantMinutes(minutes) }
    }

    fun setSentence(sentence: String) {
        viewModelScope.launch { store.setAcknowledgementSentence(sentence) }
    }

    fun setDayResetHour(hour: Int) {
        viewModelScope.launch { store.setDayResetHour(hour) }
    }

    fun setEmergencyUses(uses: Int) {
        viewModelScope.launch { store.setEmergencyUsesPerWeek(uses) }
    }

    fun setEmergencyWait(seconds: Int) {
        viewModelScope.launch { store.setEmergencyWaitSeconds(seconds) }
    }

    fun setKeepWrittenReasons(value: Boolean) {
        viewModelScope.launch {
            store.setKeepWrittenReasons(value)
            if (!value) repository.forgetWrittenReasons()
        }
    }

    fun setRetentionDays(days: Int) {
        viewModelScope.launch {
            store.setHistoryRetentionDays(days)
            repository.pruneHistory()
        }
    }

    fun forgetWrittenReasons() {
        viewModelScope.launch { repository.forgetWrittenReasons() }
    }

    fun eraseEverything() {
        viewModelScope.launch { repository.eraseEverything() }
    }

    fun saveWindow(window: ScheduleWindow) {
        viewModelScope.launch { repository.saveWindow(window) }
    }

    fun deleteWindow(id: Long) {
        viewModelScope.launch { repository.deleteWindow(id) }
    }

    fun onDeviceAdminChanged(active: Boolean) {
        viewModelScope.launch { store.setDeviceAdminEnabled(active) }
        refreshPermissions()
    }
}
