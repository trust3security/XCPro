package com.example.xcpro.xcprov1.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.xcprov1.bluetooth.GloStatus
import com.example.xcpro.xcprov1.model.FlightDataV1Snapshot
import com.example.xcpro.xcprov1.service.XcproV1Controller
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HawkDashboardViewModel(
    private val controller: XcproV1Controller,
    private val garminStatusFlow: StateFlow<GloStatus>,
    private val autoConnectGarmin: () -> Unit,
    private val disconnectGarmin: () -> Unit
) : ViewModel() {

    val snapshotFlow: StateFlow<FlightDataV1Snapshot?> =
        controller.snapshotFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val garminStatus: StateFlow<String> =
        garminStatusFlow
            .map { status ->
                when (status) {
                    is GloStatus.Idle -> "Idle"
                    is GloStatus.Discovering -> "Scanning…"
                    is GloStatus.Connecting -> "Connecting to ${status.deviceName}…"
                    is GloStatus.Connected -> "Connected to ${status.deviceName}"
                    is GloStatus.Disconnected -> status.reason?.let { "Disconnected ($it)" } ?: "Disconnected"
                    is GloStatus.Error -> "Error: ${status.message}"
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = "Idle"
            )

    val audioEnabled: StateFlow<Boolean> = controller.audioEnabled
    val audioTelemetry = controller.audioTelemetry

    fun connectGarmin() {
        autoConnectGarmin()
    }

    fun disconnectGarmin() {
        disconnectGarmin()
    }


    fun setAudioEnabled(enabled: Boolean) {
        controller.setAudioEnabled(enabled)
    }
}
