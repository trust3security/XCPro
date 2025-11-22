package com.example.dfcards.dfcards

import com.example.dfcards.FlightModeSelection

internal object FlightVisibility {
    private const val DEFAULT_PROFILE_ID = "__default_profile__"

    fun defaultVisibilityMap(): MutableMap<FlightModeSelection, Boolean> =
        FlightModeSelection.values().associateWith { true }.toMutableMap().apply {
            this[FlightModeSelection.CRUISE] = true
        }

    fun buildVisibilityMap(raw: Map<String, Boolean>?): Map<FlightModeSelection, Boolean> {
        val defaults = defaultVisibilityMap()
        raw?.forEach { (modeName, visible) ->
            val mode = runCatching { FlightModeSelection.valueOf(modeName) }.getOrNull() ?: return@forEach
            if (mode != FlightModeSelection.CRUISE) {
                defaults[mode] = visible
            }
        }
        return defaults.toMap()
    }

    fun normalizeProfileId(profileId: String?): String = profileId ?: DEFAULT_PROFILE_ID
}
