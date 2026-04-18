package com.trust3.xcpro.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfilesConfigUiState(
    val configContent: String? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class ProfilesConfigViewModel @Inject constructor(
    private val useCase: ProfilesConfigUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfilesConfigUiState())
    val uiState: StateFlow<ProfilesConfigUiState> = _uiState.asStateFlow()

    fun loadConfig() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching { useCase.readConfig() }
                .onSuccess { config ->
                    if (config != null) {
                        _uiState.value = ProfilesConfigUiState(
                            configContent = config.toString(2),
                            errorMessage = null,
                            isLoading = false
                        )
                    } else {
                        _uiState.value = ProfilesConfigUiState(
                            configContent = null,
                            errorMessage = "configuration.json not found in internal storage",
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.value = ProfilesConfigUiState(
                        configContent = null,
                        errorMessage = "Error loading configuration.json: ${error.message}",
                        isLoading = false
                    )
                }
        }
    }
}
