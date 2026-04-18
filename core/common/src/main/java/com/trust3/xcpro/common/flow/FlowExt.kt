package com.trust3.xcpro.common.flow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Converts the upstream [Flow] into a hot [StateFlow] bound to the given ViewModel scope.
 * Defaults to a 5s subscription timeout to align with Compose lifecycle expectations.
 */
fun <T> Flow<T>.inVm(
    scope: CoroutineScope,
    initial: T,
    started: SharingStarted = SharingStarted.WhileSubscribed(5_000)
): StateFlow<T> = stateIn(scope, started, initial)
