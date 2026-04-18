package com.trust3.xcpro.adsb.domain

import kotlinx.coroutines.flow.StateFlow

interface AdsbNetworkAvailabilityPort {
    val isOnline: StateFlow<Boolean>

    fun currentOnlineState(): Boolean = isOnline.value
}

