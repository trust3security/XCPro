package com.example.xcpro.screens.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.sensors.VarioDiagnosticsSample
import com.example.xcpro.vario.VarioServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class VarioDiagnosticsViewModel @Inject constructor(
    private val varioServiceManager: VarioServiceManager
) : ViewModel() {

    private val historyLimit = 300

    private val _uiState = MutableStateFlow(VarioDiagnosticsUiState())
    val uiState: StateFlow<VarioDiagnosticsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            varioServiceManager.sensorFusionRepository.diagnosticsFlow.collect { sample ->
                if (sample != null) {
                    _uiState.update { current ->
                        val updatedHistory = (current.history + sample).takeLast(historyLimit)
                        current.copy(latest = sample, history = updatedHistory)
                    }
                }
            }
        }
    }
}

data class VarioDiagnosticsUiState(
    val latest: VarioDiagnosticsSample? = null,
    val history: List<VarioDiagnosticsSample> = emptyList()
)
