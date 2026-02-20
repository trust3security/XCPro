package com.example.xcpro.adsb.domain

import kotlinx.coroutines.flow.StateFlow

interface AdsbNetworkAvailabilityPort {
    val isOnline: StateFlow<Boolean>
}

