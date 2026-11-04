package io.github.gthibo.leavenlock.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.gthibo.leavenlock.data.metrics.ApiMetrics
import io.github.gthibo.leavenlock.data.metrics.MetricsSnapshot
import io.github.gthibo.leavenlock.data.settings.AppSettings
import io.github.gthibo.leavenlock.data.settings.SettingsRepository
import io.github.gthibo.leavenlock.domain.ActivityLog
import io.github.gthibo.leavenlock.domain.LogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val metrics: ApiMetrics,
    private val activityLog: ActivityLog,
    settingsRepo: SettingsRepository,
) : ViewModel() {

    val snapshot: StateFlow<MetricsSnapshot> = metrics.snapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MetricsSnapshot())

    val log: StateFlow<List<LogEntry>> = activityLog.entries

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun clearMetrics() = metrics.clear()
    fun clearLog() = activityLog.clear()
}
