package cn.com.dcsgo.mihx.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.com.dcsgo.mihx.domain.repository.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val facade: SettingsFacade,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            facade.uniformRandomEnabled.collect { enabled ->
                _uiState.update { it.copy(uniformRandomEnabled = enabled, isLoading = false) }
            }
        }
        viewModelScope.launch {
            facade.infinitePlayEnabled.collect { enabled ->
                _uiState.update { it.copy(infinitePlayEnabled = enabled, isLoading = false) }
            }
        }
        viewModelScope.launch {
            facade.bluetoothEnabled.collect { enabled ->
                _uiState.update { it.copy(bluetoothEnabled = enabled, isLoading = false) }
            }
        }
        viewModelScope.launch {
            facade.notificationEnabled.collect { enabled ->
                _uiState.update { it.copy(notificationEnabled = enabled, isLoading = false) }
            }
        }
        viewModelScope.launch {
            facade.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode, isLoading = false) }
            }
        }
        viewModelScope.launch {
            facade.dynamicColorEnabled.collect { enabled ->
                _uiState.update { it.copy(dynamicColorEnabled = enabled, isLoading = false) }
            }
        }
    }

    fun setUniformRandomEnabled(enabled: Boolean) {
        viewModelScope.launch { facade.setUniformRandomEnabled(enabled) }
    }

    fun setInfinitePlayEnabled(enabled: Boolean) {
        viewModelScope.launch { facade.setInfinitePlayEnabled(enabled) }
    }

    fun setBluetoothEnabled(enabled: Boolean) {
        viewModelScope.launch { facade.setBluetoothEnabled(enabled) }
    }

    fun setNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch { facade.setNotificationEnabled(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { facade.setThemeMode(mode) }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch { facade.setDynamicColorEnabled(enabled) }
    }
}
