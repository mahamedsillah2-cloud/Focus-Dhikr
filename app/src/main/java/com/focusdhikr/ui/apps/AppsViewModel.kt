package com.focusdhikr.ui.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusdhikr.AppGraph
import com.focusdhikr.data.repo.InstalledApp
import com.focusdhikr.domain.model.TrackedApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppsUiState(
    val tracked: List<TrackedApp> = emptyList(),
    val installed: List<InstalledApp> = emptyList(),
    val loadingInstalled: Boolean = false,
    val query: String = "",
    val picking: Boolean = false,
    val editing: TrackedApp? = null,
) {
    val filteredInstalled: List<InstalledApp>
        get() {
            val trackedPackages = tracked.map { it.packageName }.toSet()
            val q = query.trim()
            return installed
                .filter { it.packageName !in trackedPackages }
                .filter { q.isBlank() || it.label.contains(q, ignoreCase = true) }
        }
}

class AppsViewModel(application: Application) : AndroidViewModel(application) {

    private val graph = AppGraph.get(application)
    private val repository = graph.repository

    private val _state = MutableStateFlow(AppsUiState())
    val state: StateFlow<AppsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeTrackedApps().collect { apps ->
                _state.update { it.copy(tracked = apps) }
            }
        }
    }

    fun openPicker() {
        _state.update { it.copy(picking = true, query = "") }
        if (_state.value.installed.isEmpty()) loadInstalled()
    }

    fun closePicker() = _state.update { it.copy(picking = false) }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun edit(app: TrackedApp?) = _state.update { it.copy(editing = app) }

    private fun loadInstalled() {
        _state.update { it.copy(loadingInstalled = true) }
        viewModelScope.launch {
            val apps = graph.installedApps.load()
            _state.update { it.copy(installed = apps, loadingInstalled = false) }
        }
    }

    fun track(app: InstalledApp, limitMinutes: Int) {
        viewModelScope.launch {
            repository.track(
                TrackedApp(
                    packageName = app.packageName,
                    label = app.label,
                    dailyLimitMillis = limitMinutes * 60_000L,
                )
            )
            _state.update { it.copy(picking = false) }
        }
    }

    fun setLimitMinutes(packageName: String, minutes: Int) {
        viewModelScope.launch { repository.setLimit(packageName, minutes * 60_000L) }
    }

    fun setEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch { repository.setAppEnabled(packageName, enabled) }
    }

    fun setStrict(packageName: String, strict: Boolean) {
        viewModelScope.launch { repository.setAppStrict(packageName, strict) }
    }

    fun untrack(packageName: String) {
        viewModelScope.launch {
            repository.untrack(packageName)
            _state.update { it.copy(editing = null) }
        }
    }
}
