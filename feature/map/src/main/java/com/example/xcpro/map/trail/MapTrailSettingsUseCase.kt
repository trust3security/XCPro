// Role: Provide trail settings to the UI layer via a use-case boundary.
// Invariants: The use case never mutates settings without an explicit caller action.
package com.example.xcpro.map.trail

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Use case for reading and updating map trail settings.
 */
class MapTrailSettingsUseCase @Inject constructor(
    private val preferences: MapTrailPreferences
) {
    val settingsFlow: Flow<TrailSettings> = preferences.settingsFlow

    fun setActiveProfileId(profileId: String) {
        preferences.setActiveProfileId(profileId)
    }

    fun getSettings(): TrailSettings = preferences.getSettings()

    fun readProfileSettings(profileId: String): TrailSettings =
        preferences.readProfileSettings(profileId)

    fun setSettings(settings: TrailSettings) {
        preferences.setSettings(settings)
    }

    fun writeProfileSettings(profileId: String, settings: TrailSettings) {
        preferences.writeProfileSettings(profileId, settings)
    }

    fun setTrailLength(length: TrailLength) {
        preferences.setTrailLength(length)
    }

    fun setTrailType(type: TrailType) {
        preferences.setTrailType(type)
    }

    fun setWindDriftEnabled(enabled: Boolean) {
        preferences.setWindDriftEnabled(enabled)
    }

    fun setScalingEnabled(enabled: Boolean) {
        preferences.setScalingEnabled(enabled)
    }

    fun clearProfile(profileId: String) {
        preferences.clearProfile(profileId)
    }
}
