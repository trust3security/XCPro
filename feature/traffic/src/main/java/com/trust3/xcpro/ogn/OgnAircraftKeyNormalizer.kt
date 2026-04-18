package com.trust3.xcpro.ogn

import java.util.Locale

fun normalizeOgnAircraftKey(raw: String): String {
    return raw.trim().uppercase(Locale.US)
}

fun normalizeOgnAircraftKeyOrNull(raw: String?): String? {
    val value = raw ?: return null
    val normalized = normalizeOgnAircraftKey(value)
    return normalized.takeIf { it.isNotEmpty() }
}
