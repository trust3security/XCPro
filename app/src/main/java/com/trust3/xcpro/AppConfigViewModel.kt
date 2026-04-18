package com.trust3.xcpro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class AppConfigUiState(
    val config: JSONObject? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class AppConfigViewModel @Inject constructor(
    private val useCase: AppConfigUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppConfigUiState())
    val uiState: StateFlow<AppConfigUiState> = _uiState.asStateFlow()

    fun loadConfig() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching { useCase.readConfig() }
                .onSuccess { config ->
                    _uiState.value = AppConfigUiState(
                        config = config,
                        errorMessage = null,
                        isLoading = false
                    )
                }
                .onFailure { error ->
                    _uiState.value = AppConfigUiState(
                        config = null,
                        errorMessage = error.message,
                        isLoading = false
                    )
                }
        }
    }
}
