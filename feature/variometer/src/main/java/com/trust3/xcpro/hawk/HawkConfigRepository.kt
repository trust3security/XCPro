package com.trust3.xcpro.hawk

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class HawkConfigRepository @Inject constructor() {
    private val _config = MutableStateFlow(HawkConfig())
    val config: StateFlow<HawkConfig> = _config.asStateFlow()

    fun update(transform: (HawkConfig) -> HawkConfig) {
        _config.value = transform(_config.value)
    }

    fun setEnabled(enabled: Boolean) {
        update { it.copy(enabled = enabled) }
    }

    fun setDebugLogging(enabled: Boolean) {
        update { it.copy(debugLogging = enabled) }
    }
}
