package com.example.xcpro.weglide.domain

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class WeGlidePostFlightPromptCoordinator @Inject constructor() {

    private val _pendingPrompt = MutableStateFlow<WeGlidePostFlightUploadPrompt?>(null)
    val pendingPrompt: StateFlow<WeGlidePostFlightUploadPrompt?> = _pendingPrompt.asStateFlow()

    fun show(prompt: WeGlidePostFlightUploadPrompt) {
        _pendingPrompt.value = prompt
    }

    fun dismiss(localFlightId: String? = null) {
        val current = _pendingPrompt.value ?: return
        if (localFlightId == null || current.request.localFlightId == localFlightId) {
            _pendingPrompt.value = null
        }
    }
}
