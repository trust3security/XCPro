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
    const val MAP_STYLE_PREFERENCES = "tier_a.map_style_preferences"
    const val SNAIL_TRAIL_PREFERENCES = "tier_a.snail_trail_preferences"
    const val ORIENTATION_PREFERENCES = "tier_a.orientation_preferences"
    const val QNH_PREFERENCES = "tier_a.qnh_preferences"
    const val WAYPOINT_FILE_PREFERENCES = "tier_a.waypoint_file_preferences"
    const val AIRSPACE_PREFERENCES = "tier_a.airspace_preferences"
    const val LEVO_VARIO_PREFERENCES = "tier_a.levo_vario_preferences"
    const val THERMALLING_MODE_PREFERENCES = "tier_a.thermalling_mode_preferences"
    const val OGN_TRAFFIC_PREFERENCES = "tier_a.ogn_traffic_preferences"
    const val OGN_TRAIL_SELECTION_PREFERENCES = "tier_a.ogn_trail_selection_preferences"
    const val ADSB_TRAFFIC_PREFERENCES = "tier_a.adsb_traffic_preferences"
    const val WEATHER_OVERLAY_PREFERENCES = "tier_a.weather_overlay_preferences"
    const val FORECAST_PREFERENCES = "tier_a.forecast_preferences"
    const val WIND_OVERRIDE_PREFERENCES = "tier_a.wind_override_preferences"
}

object ProfileSettingsSectionSets {
    val AIRCRAFT_PROFILE_SECTION_IDS = setOf(
        ProfileSettingsSectionIds.CARD_PREFERENCES,
        ProfileSettingsSectionIds.FLIGHT_MGMT_PREFERENCES,
        ProfileSettingsSectionIds.LOOK_AND_FEEL_PREFERENCES,
        ProfileSettingsSectionIds.THEME_PREFERENCES,
        ProfileSettingsSectionIds.MAP_WIDGET_LAYOUT,
        ProfileSettingsSectionIds.VARIOMETER_WIDGET_LAYOUT,
        ProfileSettingsSectionIds.GLIDER_CONFIG,
        ProfileSettingsSectionIds.UNITS_PREFERENCES,
        ProfileSettingsSectionIds.MAP_STYLE_PREFERENCES,
        ProfileSettingsSectionIds.SNAIL_TRAIL_PREFERENCES,
        ProfileSettingsSectionIds.ORIENTATION_PREFERENCES,
        ProfileSettingsSectionIds.QNH_PREFERENCES
    )

    val GLOBAL_APP_SECTION_IDS = setOf(
        ProfileSettingsSectionIds.LEVO_VARIO_PREFERENCES,
        ProfileSettingsSectionIds.THERMALLING_MODE_PREFERENCES,
        ProfileSettingsSectionIds.OGN_TRAFFIC_PREFERENCES,
        ProfileSettingsSectionIds.OGN_TRAIL_SELECTION_PREFERENCES,
        ProfileSettingsSectionIds.ADSB_TRAFFIC_PREFERENCES,
        ProfileSettingsSectionIds.WEATHER_OVERLAY_PREFERENCES,
        ProfileSettingsSectionIds.FORECAST_PREFERENCES,
        ProfileSettingsSectionIds.WIND_OVERRIDE_PREFERENCES
    )

    val CAPTURED_SECTION_IDS = AIRCRAFT_PROFILE_SECTION_IDS + GLOBAL_APP_SECTION_IDS
}

interface ProfileSettingsSnapshotProvider {
    suspend fun buildSnapshot(
        profileIds: Set<String>,
        sectionIds: Set<String> = ProfileSettingsSectionSets.CAPTURED_SECTION_IDS
    ): ProfileSettingsSnapshot
}

class NoOpProfileSettingsSnapshotProvider : ProfileSettingsSnapshotProvider {
    override suspend fun buildSnapshot(
        profileIds: Set<String>,
        sectionIds: Set<String>
    ): ProfileSettingsSnapshot =
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
