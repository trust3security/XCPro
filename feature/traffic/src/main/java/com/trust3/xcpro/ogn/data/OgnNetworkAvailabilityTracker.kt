package com.trust3.xcpro.ogn.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class OgnNetworkAvailabilityTracker(
    initialOnline: Boolean = true
) {
    private val _isOnline = MutableStateFlow(initialOnline)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    fun onAvailable(currentOnlineState: Boolean) {
        _isOnline.value = currentOnlineState
    }

    fun onLost(currentOnlineState: Boolean) {
        _isOnline.value = currentOnlineState
    }

    fun onCapabilitiesChanged(hasUsableInternet: Boolean) {
        _isOnline.value = hasUsableInternet
    }

    fun onUnavailable() {
        _isOnline.value = false
    }

    fun onRegistrationFailure() {
        // Fail-open to avoid permanent false-negative offline state.
        _isOnline.value = true
    }
}
