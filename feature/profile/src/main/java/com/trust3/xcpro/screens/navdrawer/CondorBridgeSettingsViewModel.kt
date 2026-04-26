package com.trust3.xcpro.screens.navdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trust3.xcpro.livesource.DesiredLiveMode
import com.trust3.xcpro.simulator.CondorTransportKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CondorBridgeSettingsViewModel @Inject constructor(
    private val useCase: CondorBridgeSettingsUseCase
) : ViewModel() {

    val uiState: StateFlow<CondorBridgeSettingsUiState> =
        useCase.uiState.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CondorBridgeSettingsUiState()
        )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            useCase.refresh()
        }
    }

    fun selectTransport(kind: CondorTransportKind) {
        viewModelScope.launch {
            useCase.selectTransport(kind)
        }
    }

    fun updateTcpListenPort(port: Int) {
        viewModelScope.launch {
            useCase.updateTcpListenPort(port)
        }
    }

    fun updateTcpIpAddress(address: String?) {
        viewModelScope.launch {
            useCase.updateTcpIpAddress(address)
        }
    }

    fun selectBridge(address: String) {
        viewModelScope.launch {
            useCase.selectBridge(address)
        }
    }

    fun clearSelectedBridge() {
        viewModelScope.launch {
            useCase.clearSelectedBridge()
        }
    }

    fun setDesiredLiveMode(mode: DesiredLiveMode) {
        viewModelScope.launch {
            useCase.setDesiredLiveMode(mode)
        }
    }

    fun connect() {
        viewModelScope.launch {
            useCase.connect()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            useCase.disconnect()
        }
    }
}
