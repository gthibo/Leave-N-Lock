package io.github.gthibo.leavenlock.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.gthibo.leavenlock.data.settings.SettingsRepository
import io.github.gthibo.leavenlock.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ThemePrefs(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
)

@HiltViewModel
class ThemeViewModel @Inject constructor(
    settingsRepo: SettingsRepository,
) : ViewModel() {
    val prefs: StateFlow<ThemePrefs> = settingsRepo.settings
        .map { ThemePrefs(it.themeMode, it.dynamicColor) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemePrefs())
}
