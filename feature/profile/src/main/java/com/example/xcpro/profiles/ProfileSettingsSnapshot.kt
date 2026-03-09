package com.example.xcpro.profiles

import com.google.gson.JsonElement

/**
 * Projection payload persisted with profile backups.
 *
 * This is not runtime SSOT. It is an export artifact used for profile
 * portability/import workflows.
 */
data class ProfileSettingsSnapshot(
    val version: String = "1.0",
    val sections: Map<String, JsonElement> = emptyMap()
) {
    companion object {
        fun empty(): ProfileSettingsSnapshot = ProfileSettingsSnapshot()
    }
}

/**
 * Canonical Tier A section identifiers for full profile settings export.
 */
object ProfileSettingsSectionIds {
    const val CARD_PREFERENCES = "tier_a.card_preferences"
    const val FLIGHT_MGMT_PREFERENCES = "tier_a.flight_mgmt_preferences"
    const val LOOK_AND_FEEL_PREFERENCES = "tier_a.look_and_feel_preferences"
    const val THEME_PREFERENCES = "tier_a.theme_preferences"
    const val MAP_WIDGET_LAYOUT = "tier_a.map_widget_layout"
    const val VARIOMETER_WIDGET_LAYOUT = "tier_a.variometer_widget_layout"
    const val GLIDER_CONFIG = "tier_a.glider_config"
    const val UNITS_PREFERENCES = "tier_a.units_preferences"
    const val LEVO_VARIO_PREFERENCES = "tier_a.levo_vario_preferences"
    const val THERMALLING_MODE_PREFERENCES = "tier_a.thermalling_mode_preferences"
    const val OGN_TRAFFIC_PREFERENCES = "tier_a.ogn_traffic_preferences"
    const val OGN_TRAIL_SELECTION_PREFERENCES = "tier_a.ogn_trail_selection_preferences"
    const val ADSB_TRAFFIC_PREFERENCES = "tier_a.adsb_traffic_preferences"
    const val WEATHER_OVERLAY_PREFERENCES = "tier_a.weather_overlay_preferences"
    const val FORECAST_PREFERENCES = "tier_a.forecast_preferences"
    const val WIND_OVERRIDE_PREFERENCES = "tier_a.wind_override_preferences"
}

interface ProfileSettingsSnapshotProvider {
    suspend fun buildSnapshot(profileIds: Set<String>): ProfileSettingsSnapshot
}

class NoOpProfileSettingsSnapshotProvider : ProfileSettingsSnapshotProvider {
    override suspend fun buildSnapshot(profileIds: Set<String>): ProfileSettingsSnapshot =
        ProfileSettingsSnapshot.empty()
}

data class ProfileSettingsRestoreResult(
    val appliedSections: Set<String> = emptySet(),
    val failedSections: Map<String, String> = emptyMap()
)

interface ProfileSettingsRestoreApplier {
    suspend fun apply(
        settingsSnapshot: ProfileSettingsSnapshot,
        importedProfileIdMap: Map<String, String>
    ): ProfileSettingsRestoreResult
}

class NoOpProfileSettingsRestoreApplier : ProfileSettingsRestoreApplier {
    override suspend fun apply(
        settingsSnapshot: ProfileSettingsSnapshot,
        importedProfileIdMap: Map<String, String>
    ): ProfileSettingsRestoreResult = ProfileSettingsRestoreResult()
}
