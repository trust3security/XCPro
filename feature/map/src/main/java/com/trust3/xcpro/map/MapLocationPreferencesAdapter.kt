package com.trust3.xcpro.map

import com.trust3.xcpro.MapOrientationPreferences

class MapLocationPreferencesAdapter(
    private val preferences: MapOrientationPreferences
) : MapLocationPreferencesPort {
    override fun getMinSpeedThreshold(): Double = preferences.getMinSpeedThreshold()

    override fun setActiveProfileId(profileId: String) {
        preferences.setActiveProfileId(profileId)
    }
}
