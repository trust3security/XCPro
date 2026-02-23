package com.example.xcpro.ogn

import java.util.Locale

internal fun normalizeOgnAircraftKey(raw: String): String {
    return raw.trim().uppercase(Locale.US)
}

internal fun normalizeOgnAircraftKeyOrNull(raw: String?): String? {
    val value = raw ?: return null
    val normalized = normalizeOgnAircraftKey(value)
    return normalized.takeIf { it.isNotEmpty() }
}
