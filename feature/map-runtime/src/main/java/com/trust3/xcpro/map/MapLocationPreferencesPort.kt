package com.trust3.xcpro.map

interface MapLocationPreferencesPort {
    fun getMinSpeedThreshold(): Double
    fun setActiveProfileId(profileId: String)
}
