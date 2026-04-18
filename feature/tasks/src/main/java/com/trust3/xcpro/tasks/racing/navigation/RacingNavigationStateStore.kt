package com.trust3.xcpro.tasks.racing.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class RacingNavigationStateStore(
    initialState: RacingNavigationState = RacingNavigationState()
) {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<RacingNavigationState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RacingNavigationEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<RacingNavigationEvent> = _events.asSharedFlow()

    fun update(state: RacingNavigationState, event: RacingNavigationEvent?) {
        _state.value = state
        if (event != null) {
            _events.tryEmit(event)
        }
    }

    fun restore(state: RacingNavigationState) {
        _state.value = state
    }

    fun reset(taskSignature: String = "") {
        _state.value = RacingNavigationState(taskSignature = taskSignature)
    }
}
