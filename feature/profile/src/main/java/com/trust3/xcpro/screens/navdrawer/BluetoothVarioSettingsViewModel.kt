package com.trust3.xcpro.screens.navdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BluetoothVarioSettingsViewModel @Inject constructor(
    private val useCase: BluetoothVarioSettingsUseCase
) : ViewModel() {

    val uiState: StateFlow<BluetoothVarioSettingsUiState> =
        useCase.uiState.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BluetoothVarioSettingsUiState()
        )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            useCase.refresh()
        }
    }

    fun selectDevice(address: String) {
        viewModelScope.launch {
            useCase.selectDevice(address)
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

    fun onPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            useCase.onPermissionResult(granted)
        }
    }
}
