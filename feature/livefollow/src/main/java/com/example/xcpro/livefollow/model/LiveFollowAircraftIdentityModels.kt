package com.example.xcpro.livefollow.model

import java.util.Locale

enum class LiveFollowAircraftIdentityType {
    FLARM,
    ICAO,
    XC_CUSTOM
}

enum class LiveFollowAircraftAliasType {
    CALLSIGN,
    REGISTRATION,
    COMPETITION_NUMBER
}

data class LiveFollowAircraftIdentity private constructor(
    val type: LiveFollowAircraftIdentityType,
    val normalizedValue: String,
    val verified: Boolean
) {
    val canonicalKey: String = "${type.name}:$normalizedValue"

    companion object {
        fun create(
            type: LiveFollowAircraftIdentityType,
            rawValue: String,
            verified: Boolean
        ): LiveFollowAircraftIdentity? {
            val normalizedValue = normalizeIdentityValue(type = type, rawValue = rawValue)
                ?: return null
            return LiveFollowAircraftIdentity(
                type = type,
                normalizedValue = normalizedValue,
                verified = verified
            )
        }
    }
}

data class LiveFollowAircraftAlias private constructor(
    val type: LiveFollowAircraftAliasType,
    val normalizedValue: String,
    val verified: Boolean
) {
    companion object {
        fun create(
            type: LiveFollowAircraftAliasType,
            rawValue: String,
            verified: Boolean
        ): LiveFollowAircraftAlias? {
            val normalizedValue = normalizeAliasValue(rawValue) ?: return null
            return LiveFollowAircraftAlias(
                type = type,
                normalizedValue = normalizedValue,
                verified = verified
            )
        }
    }
}

data class LiveFollowIdentityProfile(
    val canonicalIdentity: LiveFollowAircraftIdentity?,
    val aliases: Set<LiveFollowAircraftAlias> = emptySet()
)

sealed interface LiveFollowIdentityResolution {
    data class ExactVerifiedMatch(
        val profile: LiveFollowIdentityProfile
    ) : LiveFollowIdentityResolution

    data class AliasVerifiedMatch(
        val profile: LiveFollowIdentityProfile,
        val matchedAlias: LiveFollowAircraftAlias
    ) : LiveFollowIdentityResolution

    data class Ambiguous(
        val reason: LiveFollowIdentityAmbiguityReason,
        val candidates: List<LiveFollowIdentityProfile>
    ) : LiveFollowIdentityResolution

    data object NoMatch : LiveFollowIdentityResolution
}

enum class LiveFollowIdentityAmbiguityReason {
    MULTIPLE_EXACT_MATCHES,
    MULTIPLE_ALIAS_MATCHES
}

private fun normalizeIdentityValue(
    type: LiveFollowAircraftIdentityType,
    rawValue: String
): String? {
    val normalized = rawValue.trim().uppercase(Locale.US)
    if (normalized.isEmpty()) return null
    return when (type) {
        LiveFollowAircraftIdentityType.FLARM,
        LiveFollowAircraftIdentityType.ICAO -> normalized.takeIf { value ->
            value.length == 6 && value.all { it in '0'..'9' || it in 'A'..'F' }
        }

        LiveFollowAircraftIdentityType.XC_CUSTOM -> normalized
    }
}

private fun normalizeAliasValue(rawValue: String): String? {
    val normalized = rawValue.trim().uppercase(Locale.US)
    return normalized.takeIf { it.isNotEmpty() }
}
