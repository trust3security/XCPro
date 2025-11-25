package com.example.xcpro.sensors

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Maps low-level sensor state + last fix info into a UI-friendly status.
 */
class GpsStatusMonitor(initial: GpsStatus = GpsStatus.Searching) {
    private val _status = MutableStateFlow(initial)
    val status: StateFlow<GpsStatus> = _status.asStateFlow()

    fun update(status: GpsStatus) {
        _status.value = status
    }
}
