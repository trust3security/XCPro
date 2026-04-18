package com.trust3.xcpro.profiles

import com.trust3.xcpro.core.common.profiles.ProfileSettingsSectionContract
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
    const val CARD_PREFERENCES = ProfileSettingsSectionContract.CARD_PREFERENCES
    const val FLIGHT_MGMT_PREFERENCES = ProfileSettingsSectionContract.FLIGHT_MGMT_PREFERENCES
    const val LOOK_AND_FEEL_PREFERENCES = ProfileSettingsSectionContract.LOOK_AND_FEEL_PREFERENCES
    const val THEME_PREFERENCES = ProfileSettingsSectionContract.THEME_PREFERENCES
    const val MAP_WIDGET_LAYOUT = ProfileSettingsSectionContract.MAP_WIDGET_LAYOUT
    const val VARIOMETER_WIDGET_LAYOUT = ProfileSettingsSectionContract.VARIOMETER_WIDGET_LAYOUT
    const val GLIDER_CONFIG = ProfileSettingsSectionContract.GLIDER_CONFIG
    const val UNITS_PREFERENCES = ProfileSettingsSectionContract.UNITS_PREFERENCES
    const val MAP_STYLE_PREFERENCES = ProfileSettingsSectionContract.MAP_STYLE_PREFERENCES
    const val SNAIL_TRAIL_PREFERENCES = ProfileSettingsSectionContract.SNAIL_TRAIL_PREFERENCES
    const val ORIENTATION_PREFERENCES = ProfileSettingsSectionContract.ORIENTATION_PREFERENCES
    const val QNH_PREFERENCES = ProfileSettingsSectionContract.QNH_PREFERENCES
    const val WAYPOINT_FILE_PREFERENCES = ProfileSettingsSectionContract.WAYPOINT_FILE_PREFERENCES
    const val AIRSPACE_PREFERENCES = ProfileSettingsSectionContract.AIRSPACE_PREFERENCES
    const val LEVO_VARIO_PREFERENCES = ProfileSettingsSectionContract.LEVO_VARIO_PREFERENCES
    const val THERMALLING_MODE_PREFERENCES =
        ProfileSettingsSectionContract.THERMALLING_MODE_PREFERENCES
    const val OGN_TRAFFIC_PREFERENCES = ProfileSettingsSectionContract.OGN_TRAFFIC_PREFERENCES
    const val OGN_TRAIL_SELECTION_PREFERENCES =
        ProfileSettingsSectionContract.OGN_TRAIL_SELECTION_PREFERENCES
    const val ADSB_TRAFFIC_PREFERENCES = ProfileSettingsSectionContract.ADSB_TRAFFIC_PREFERENCES
    const val WEATHER_OVERLAY_PREFERENCES =
        ProfileSettingsSectionContract.WEATHER_OVERLAY_PREFERENCES
    const val FORECAST_PREFERENCES = ProfileSettingsSectionContract.FORECAST_PREFERENCES
    const val WIND_OVERRIDE_PREFERENCES = ProfileSettingsSectionContract.WIND_OVERRIDE_PREFERENCES
}

object ProfileSettingsSectionSets {
    val AIRCRAFT_PROFILE_SECTION_ORDER =
        ProfileSettingsSectionContract.AIRCRAFT_PROFILE_SECTION_ORDER

    val GLOBAL_APP_SECTION_ORDER = ProfileSettingsSectionContract.GLOBAL_APP_SECTION_ORDER

    val CAPTURED_SECTION_ORDER = ProfileSettingsSectionContract.CAPTURED_SECTION_ORDER

    val AIRCRAFT_PROFILE_SECTION_IDS = AIRCRAFT_PROFILE_SECTION_ORDER.toSet()
    val GLOBAL_APP_SECTION_IDS = GLOBAL_APP_SECTION_ORDER.toSet()
    val CAPTURED_SECTION_IDS = CAPTURED_SECTION_ORDER.toSet()
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
