package com.example.xcpro.screens.navdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.vario.LevoVarioPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LevoVarioUiState(
    val imuAssistEnabled: Boolean = true
)

@HiltViewModel
class LevoVarioSettingsViewModel @Inject constructor(
    private val preferencesRepository: LevoVarioPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<LevoVarioUiState> = preferencesRepository.config
        .map { config -> LevoVarioUiState(imuAssistEnabled = config.imuAssistEnabled) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LevoVarioUiState()
        )

    fun setImuAssistEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setImuAssistEnabled(enabled)
        }
    }
}
