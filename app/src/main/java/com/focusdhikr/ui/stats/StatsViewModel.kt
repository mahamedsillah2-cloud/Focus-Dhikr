package com.focusdhikr.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusdhikr.AppGraph
import com.focusdhikr.domain.usage.PeriodStats
import com.focusdhikr.domain.usage.StatsCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StatsPeriod(val days: Int, val labelEs: String) {
    TODAY(1, "Hoy"),
    WEEK(7, "7 días"),
    MONTH(30, "30 días"),
}

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val stats: PeriodStats? = null,
    val labels: Map<String, String> = emptyMap(),
    val loading: Boolean = true,
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val graph = AppGraph.get(application)
    private val repository = graph.repository

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init {
        load(StatsPeriod.WEEK)
    }

    fun load(period: StatsPeriod) {
        _state.update { it.copy(period = period, loading = true) }
        viewModelScope.launch {
            runCatching { graph.accountant.sync() }

            val dayKeys = repository.recentDayKeys(period.days)
            val usage = repository.usageRange(dayKeys)
            val attempts = repository.attempts(dayKeys)
            val stats = StatsCalculator.compute(dayKeys, usage, attempts)

            // Disabled apps included: history can reference an app the user has
            // since switched off, and "com.zhiliaoapp.musically" is not a label.
            val labels = repository.allTrackedApps().associate { app -> app.packageName to app.label }

            _state.update {
                it.copy(
                    stats = stats,
                    labels = labels,
                    loading = false,
                )
            }
        }
    }
}
