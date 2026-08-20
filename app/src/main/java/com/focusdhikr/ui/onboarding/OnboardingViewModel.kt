package com.focusdhikr.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusdhikr.AppGraph
import com.focusdhikr.core.Permissions
import com.focusdhikr.service.ScheduleAlarmReceiver
import com.focusdhikr.service.UsageTrackingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val usageAccess: Boolean = false,
    val overlay: Boolean = false,
    val accessibility: Boolean = false,
    val battery: Boolean = false,
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val graph = AppGraph.get(application)

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val context = getApplication<Application>()
        _state.update {
            it.copy(
                usageAccess = Permissions.hasUsageAccess(context),
                overlay = Permissions.canDrawOverlays(context),
                accessibility = Permissions.isAccessibilityEnabled(context),
                battery = Permissions.isIgnoringBatteryOptimizations(context),
            )
        }
    }

    fun complete() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            graph.settingsStore.setOnboardingComplete(true)
            UsageTrackingService.start(context)
            ScheduleAlarmReceiver.scheduleNext(context)
        }
    }
}
