package cn.com.dcsgo.mihx.app.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.com.dcsgo.mihx.core.model.ThemeMode
import cn.com.dcsgo.mihx.domain.repository.PlayerSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: PlayerSettingsRepository,
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val lyricFontScale: StateFlow<Float> = settingsRepository.lyricFontScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setLyricFontScale(scale: Float) {
        viewModelScope.launch {
            settingsRepository.setLyricFontScale(scale)
        }
    }
}
