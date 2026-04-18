package com.trust3.xcpro.ogn

import java.util.Locale
import java.util.UUID

internal const val OGN_LEGACY_CLIENT_CALLSIGN = "OGNXC1"

private val OGN_CLIENT_CALLSIGN_REGEX = Regex("^[A-Z][A-Z0-9]{2,8}$")

internal fun normalizeOgnClientCallsignOrNull(value: String?): String? {
    val normalized = value
        ?.trim()
        ?.uppercase(Locale.US)
        ?.takeIf { it.matches(OGN_CLIENT_CALLSIGN_REGEX) }
    return normalized
}

internal fun generateOgnClientCallsign(): String {
    val suffix = UUID.randomUUID()
        .toString()
        .replace("-", "")
        .take(6)
        .uppercase(Locale.US)
    return "XCP$suffix"
}
