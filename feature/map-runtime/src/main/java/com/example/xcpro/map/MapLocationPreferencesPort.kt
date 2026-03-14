package com.example.xcpro.map

interface MapLocationPreferencesPort {
    fun getMinSpeedThreshold(): Double
    fun setActiveProfileId(profileId: String)
}
