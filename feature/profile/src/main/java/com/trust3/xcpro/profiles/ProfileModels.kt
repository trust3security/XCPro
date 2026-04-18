package com.trust3.xcpro.profiles

import com.trust3.xcpro.common.flight.FlightMode

enum class AircraftType(
    val displayName: String,
    val defaultModes: List<FlightMode>
) {
    PARAGLIDER(
        displayName = "Paraglider",
        defaultModes = listOf(FlightMode.CRUISE, FlightMode.THERMAL)
    ),
    HANG_GLIDER(
        displayName = "Hang Glider",
        defaultModes = listOf(FlightMode.CRUISE, FlightMode.THERMAL)
    ),
    SAILPLANE(
        displayName = "Sailplane",
        defaultModes = listOf(FlightMode.CRUISE, FlightMode.THERMAL, FlightMode.FINAL_GLIDE)
    ),
    GLIDER(
        displayName = "Glider",
        defaultModes = listOf(FlightMode.CRUISE, FlightMode.THERMAL, FlightMode.FINAL_GLIDE)
    )
}

internal val USER_SELECTABLE_AIRCRAFT_TYPES: List<AircraftType> = listOf(
    AircraftType.SAILPLANE,
    AircraftType.PARAGLIDER,
    AircraftType.HANG_GLIDER
)

internal fun AircraftType.canonicalForPersistence(): AircraftType = when (this) {
    AircraftType.GLIDER -> AircraftType.SAILPLANE
    else -> this
}

data class UIPosition(
    val x: Float,
    val y: Float
)

data class UILayout(
    val variometerPosition: UIPosition = UIPosition(50f, 50f), // Default position
    val variometerSize: Float = 150f // Default size in pixels
)

data class ProfilePreferences(
    val units: UnitSystem = UnitSystem.METRIC,
    val theme: String = "default",
    val autoSwitchModes: Boolean = true,
    val cardAnimations: Boolean = true,
    val uiLayout: UILayout = UILayout()
)

data class ProfilePolarSettings(
    val lowSpeedKmh: Double = 80.0,
    val lowSinkMs: Double = 0.5,
    val midSpeedKmh: Double = 120.0,
    val midSinkMs: Double = 0.8,
    val highSpeedKmh: Double = 180.0,
    val highSinkMs: Double = 2.0
)

enum class UnitSystem(val displayName: String) {
    METRIC("Metric (m/s, m)"),
    IMPERIAL("Imperial (ft/min, ft)"),
    MIXED("Mixed")
}

data class UserProfile(
    val id: String,
    val name: String,
    val aircraftType: AircraftType,
    val aircraftModel: String? = null,
    val description: String? = null,
    val preferences: ProfilePreferences = ProfilePreferences(),
    val isActive: Boolean = false,
    val createdAt: Long,
    val lastUsed: Long,
    val polar: ProfilePolarSettings = ProfilePolarSettings()
) {
    fun getDisplayName(): String {
        return if (aircraftModel != null) {
            "$name ($aircraftModel)"
        } else {
            "$name - ${aircraftType.displayName}"
        }
    }
}

internal fun UserProfile.normalizedForPersistence(): UserProfile {
    val normalizedAircraftType = aircraftType.canonicalForPersistence()
    return if (normalizedAircraftType == aircraftType) {
        this
    } else {
        copy(aircraftType = normalizedAircraftType)
    }
}

internal data class NormalizedProfilesResult(
    val profiles: List<UserProfile>,
    val normalizedLegacyAircraftTypes: Boolean
)

internal fun normalizeProfilesForPersistence(profiles: List<UserProfile>): NormalizedProfilesResult {
    val normalizedProfiles = profiles.map(UserProfile::normalizedForPersistence)
    return NormalizedProfilesResult(
        profiles = normalizedProfiles,
        normalizedLegacyAircraftTypes = normalizedProfiles != profiles
    )
}

data class ProfileCreationRequest(
    val name: String,
    val aircraftType: AircraftType,
    val aircraftModel: String? = null,
    val description: String? = null,
    val copyFromProfile: UserProfile? = null
)

enum class ProfileNameCollisionPolicy {
    KEEP_BOTH_SUFFIX,
    REPLACE_EXISTING
}

enum class ProfileImportFailureReason {
    INVALID_PROFILE
}

data class ProfileImportFailure(
    val sourceName: String?,
    val reason: ProfileImportFailureReason,
    val detail: String
)

data class ProfileImportRequest(
    val profiles: List<UserProfile>,
    val keepCurrentActive: Boolean = true,
    val nameCollisionPolicy: ProfileNameCollisionPolicy = ProfileNameCollisionPolicy.KEEP_BOTH_SUFFIX,
    val preserveImportedPreferences: Boolean = true,
    val preferredImportedActiveSourceId: String? = null
)

data class ProfileImportResult(
    val requestedCount: Int,
    val importedCount: Int,
    val skippedCount: Int,
    val failures: List<ProfileImportFailure>,
    val activeProfileBefore: String?,
    val activeProfileAfter: String?,
    val importedProfileIdMap: Map<String, String> = emptyMap()
)

