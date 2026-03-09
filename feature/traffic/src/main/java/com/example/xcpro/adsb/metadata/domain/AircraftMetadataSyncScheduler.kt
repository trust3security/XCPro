package com.example.xcpro.adsb.metadata.domain

interface AircraftMetadataSyncScheduler {
    suspend fun onOverlayPreferenceChanged(enabled: Boolean)
    suspend fun bootstrapForOverlayPreference(overlayEnabled: Boolean)
}

