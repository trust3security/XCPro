package com.example.xcpro.ogn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OgnSciaStartupResetState {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

interface OgnSciaStartupResetCoordinator {
    val resetState: StateFlow<OgnSciaStartupResetState>

    fun startIfNeeded()

    companion object {
        val AlreadyReset: OgnSciaStartupResetCoordinator = object : OgnSciaStartupResetCoordinator {
            private val state = MutableStateFlow(OgnSciaStartupResetState.COMPLETED)

            override val resetState: StateFlow<OgnSciaStartupResetState> = state.asStateFlow()

            override fun startIfNeeded() = Unit
        }
    }
}
