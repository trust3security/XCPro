package com.example.xcpro.tasks.aat.models

/**
 * AAT-specific turn point types - COMPLETELY INDEPENDENT from Racing
 * Maintains task type separation architecture
 *
 * These turn point types are specific to AAT tasks and should NEVER be used by Racing or DHT modules
 */
enum class AATTurnPointType(val displayName: String, val description: String) {
    AAT_CYLINDER("AAT Cylinder", "Circular assigned area"),
    AAT_SECTOR("AAT Sector", "Sector assigned area"),
    AAT_KEYHOLE("AAT Keyhole", "Cylinder + sector combination for AAT")
}
