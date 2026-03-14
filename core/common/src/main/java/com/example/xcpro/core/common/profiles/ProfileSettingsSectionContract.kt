package com.example.xcpro.core.common.profiles

import com.google.gson.JsonElement

object ProfileSettingsSectionContract {
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

    val AIRCRAFT_PROFILE_SECTION_ORDER = listOf(
        CARD_PREFERENCES,
        FLIGHT_MGMT_PREFERENCES,
        LOOK_AND_FEEL_PREFERENCES,
        THEME_PREFERENCES,
        MAP_WIDGET_LAYOUT,
        VARIOMETER_WIDGET_LAYOUT,
        GLIDER_CONFIG,
        UNITS_PREFERENCES,
        MAP_STYLE_PREFERENCES,
        SNAIL_TRAIL_PREFERENCES,
        ORIENTATION_PREFERENCES,
        QNH_PREFERENCES
    )

    val GLOBAL_APP_SECTION_ORDER = listOf(
        LEVO_VARIO_PREFERENCES,
        THERMALLING_MODE_PREFERENCES,
        OGN_TRAFFIC_PREFERENCES,
        OGN_TRAIL_SELECTION_PREFERENCES,
        ADSB_TRAFFIC_PREFERENCES,
        WEATHER_OVERLAY_PREFERENCES,
        FORECAST_PREFERENCES,
        WIND_OVERRIDE_PREFERENCES
    )

    val CAPTURED_SECTION_ORDER = AIRCRAFT_PROFILE_SECTION_ORDER + GLOBAL_APP_SECTION_ORDER
    val CAPTURED_SECTION_IDS = CAPTURED_SECTION_ORDER.toSet()
}

interface ProfileSettingsCaptureContributor {
    val sectionIds: Set<String>

    suspend fun captureSection(
        sectionId: String,
        profileIds: Set<String>
    ): JsonElement?
}

interface ProfileSettingsApplyContributor {
    val sectionIds: Set<String>

    suspend fun applySection(
        sectionId: String,
        payload: JsonElement,
        importedProfileIdMap: Map<String, String>
    )
}
