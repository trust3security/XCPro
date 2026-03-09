package com.example.xcpro.ogn

import java.util.Locale

enum class OgnAddressType {
    FLARM,
    ICAO,
    UNKNOWN
}

internal data class OgnAddressIdentity(
    val type: OgnAddressType,
    val addressHex: String?
)

internal fun canonicalOgnTargetKey(
    type: OgnAddressType,
    addressHex: String?,
    fallbackId: String
): String {
    val normalizedHex = normalizeOgnHex6OrNull(addressHex)
    if (normalizedHex != null) {
        return when (type) {
            OgnAddressType.FLARM -> "FLARM:$normalizedHex"
            OgnAddressType.ICAO -> "ICAO:$normalizedHex"
            OgnAddressType.UNKNOWN -> "UNK:$normalizedHex"
        }
    }
    return "ID:${normalizeOgnAircraftKey(fallbackId)}"
}

internal fun normalizeOgnHex6OrNull(raw: String?): String? {
    val value = raw?.trim()?.uppercase(Locale.US) ?: return null
    if (value.length != 6) return null
    if (!value.all { ch -> ch in '0'..'9' || ch in 'A'..'F' }) return null
    return value
}

internal fun ognAddressTypeFromCallsignPrefix(sourceCallsign: String): OgnAddressType {
    val base = sourceCallsign.trim().uppercase(Locale.US).substringBefore("-")
    return when {
        base.startsWith("ICA") -> OgnAddressType.ICAO
        base.startsWith("FLR") -> OgnAddressType.FLARM
        else -> OgnAddressType.UNKNOWN
    }
}

internal fun ognAddressTypeFromTypeByteHex(typeByteHex: String): OgnAddressType {
    val typeByte = typeByteHex.toIntOrNull(radix = 16) ?: return OgnAddressType.UNKNOWN
    return when (typeByte and 0x03) {
        0x01 -> OgnAddressType.ICAO
        0x02 -> OgnAddressType.FLARM
        else -> OgnAddressType.UNKNOWN
    }
}

internal fun ognAddressTypeFromDdbDeviceType(deviceType: String?): OgnAddressType {
    val value = deviceType?.trim()?.uppercase(Locale.US).orEmpty()
    return when (value) {
        "F" -> OgnAddressType.FLARM
        "I" -> OgnAddressType.ICAO
        else -> OgnAddressType.UNKNOWN
    }
}

fun legacyOgnKeyFromCanonicalOrNull(key: String): String? {
    val normalized = normalizeOgnAircraftKey(key)
    val separator = normalized.indexOf(':')
    if (separator < 0 || separator == normalized.lastIndex) return null
    val tail = normalized.substring(separator + 1)
    return normalizeOgnHex6OrNull(tail)
}

fun expandOgnSelectionAliases(key: String): Set<String> {
    val normalized = normalizeOgnAircraftKey(key)
    val aliases = linkedSetOf(normalized)
    val legacy = legacyOgnKeyFromCanonicalOrNull(normalized)
    if (legacy != null) {
        aliases += legacy
    }
    return aliases
}

data class OgnSelectionLookup(
    val normalizedSelectedKeys: Set<String>,
    val normalizedSelectedLegacyKeys: Set<String>
)

fun buildOgnSelectionLookup(selectedKeys: Set<String>): OgnSelectionLookup =
    selectedKeys.map(::normalizeOgnAircraftKey).toSet().let { normalized ->
        OgnSelectionLookup(
            normalizedSelectedKeys = normalized,
            normalizedSelectedLegacyKeys = normalized.mapNotNull(::legacyOgnKeyFromCanonicalOrNull).toSet()
        )
    }

fun selectionLookupContainsOgnKey(
    lookup: OgnSelectionLookup,
    candidateKey: String
): Boolean {
    if (lookup.normalizedSelectedKeys.isEmpty()) return false
    val normalizedCandidate = normalizeOgnAircraftKey(candidateKey)
    if (lookup.normalizedSelectedKeys.contains(normalizedCandidate)) return true
    if (lookup.normalizedSelectedLegacyKeys.contains(normalizedCandidate)) return true

    val candidateLegacy = legacyOgnKeyFromCanonicalOrNull(normalizedCandidate) ?: return false
    return lookup.normalizedSelectedKeys.contains(candidateLegacy) ||
        lookup.normalizedSelectedLegacyKeys.contains(candidateLegacy)
}

internal fun selectionSetContainsOgnKey(selectedKeys: Set<String>, candidateKey: String): Boolean {
    return selectionLookupContainsOgnKey(
        lookup = buildOgnSelectionLookup(selectedKeys),
        candidateKey = candidateKey
    )
}
