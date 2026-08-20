package com.focusdhikr.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusdhikr.AppGraph
import com.focusdhikr.content.Reflections
import com.focusdhikr.data.prefs.Settings
import com.focusdhikr.domain.model.GateOutcome
import com.focusdhikr.domain.model.Goal
import com.focusdhikr.domain.model.TrackedApp
import com.focusdhikr.domain.usage.LimitEvaluator
import com.focusdhikr.domain.usage.StatsCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppRow(
    val app: TrackedApp,
    val usedMillis: Long,
    val remainingMillis: Long,
) {
    val limitReached: Boolean get() = remainingMillis <= 0L
    val fraction: Float
        get() = if (app.dailyLimitMillis <= 0) 1f
        else (usedMillis.toFloat() / app.dailyLimitMillis).coerceIn(0f, 1f)
}

data class HomeUiState(
    val loading: Boolean = true,
    val rows: List<AppRow> = emptyList(),
    val usedTodayMillis: Long = 0,
    val reclaimedTodayMillis: Long = 0,
    val turnedBackToday: Int = 0,
    val goals: List<Goal> = emptyList(),
    val settings: Settings = Settings(),
    val ambientLine: String = "",
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val graph = AppGraph.get(application)
    private val repository = graph.repository

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * Reloads everything the screen shows.
     *
     * Called on every resume rather than kept as a long-lived combine, because
     * the numbers depend on the accountant having just synced - a stale Flow
     * would show a total that is a minute behind reality.
     */
    fun refresh() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            runCatching { graph.accountant.sync(now) }

            val settings = repository.settingsSnapshot()
            val dayKey = repository.dayKey(now)
            val apps = repository.enabledTrackedApps()
            val usage = repository.usageRange(listOf(dayKey))
            val attempts = repository.attempts(listOf(dayKey))
            val usedByPackage = usage.associate { it.packageName to it.millis }

            val rows = apps.map { app ->
                val used = usedByPackage[app.packageName] ?: 0L
                AppRow(
                    app = app,
                    usedMillis = used,
                    remainingMillis = LimitEvaluator.remainingMillis(app, used),
                )
            }.sortedByDescending { it.usedMillis }

            val stats = StatsCalculator.compute(listOf(dayKey), usage, attempts)

            _state.update {
                it.copy(
                    loading = false,
                    rows = rows,
                    usedTodayMillis = rows.sumOf { row -> row.usedMillis },
                    reclaimedTodayMillis = stats.reclaimedMillis,
                    turnedBackToday = attempts.count { a -> a.outcome == GateOutcome.TURNED_BACK },
                    goals = repository.activeGoals(),
                    settings = settings,
                    ambientLine = Reflections.pick(
                        Reflections.ambient,
                        dayKey.hashCode(),
                    ),
                )
            }
        }
    }

    fun saveGoal(goal: Goal) {
        viewModelScope.launch {
            repository.saveGoal(goal)
            refresh()
        }
    }

    fun deleteGoal(id: Long) {
        viewModelScope.launch {
            repository.deleteGoal(id)
            refresh()
        }
    }
}
