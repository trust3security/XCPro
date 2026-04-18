package com.trust3.xcpro.ogn.domain

import kotlinx.coroutines.flow.StateFlow

interface OgnNetworkAvailabilityPort {
    val isOnline: StateFlow<Boolean>

    fun currentOnlineState(): Boolean = isOnline.value
}
