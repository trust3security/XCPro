package com.example.dfcards.dfcards

import com.example.dfcards.FlightModeSelection
import java.util.Locale

internal object FlightVisibility {
    private const val DEFAULT_PROFILE_ID = "default-profile"
    private const val LEGACY_DEFAULT_ALIAS = "default"
    private const val LEGACY_DFCARDS_ALIAS = "__default_profile__"

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

    fun normalizeProfileId(profileId: String?): String {
        val trimmed = profileId?.trim()?.takeIf { it.isNotEmpty() } ?: return DEFAULT_PROFILE_ID
        return when (trimmed.lowercase(Locale.ROOT)) {
            DEFAULT_PROFILE_ID,
            LEGACY_DEFAULT_ALIAS,
            LEGACY_DFCARDS_ALIAS -> DEFAULT_PROFILE_ID
            else -> trimmed
        }
    }
}
