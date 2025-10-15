package com.example.xcpro.tasks.aat.models

/**
 * AAT-specific start point types - COMPLETELY INDEPENDENT from Racing
 * Maintains task type separation architecture
 *
 * These start types are specific to AAT tasks and should NEVER be used by Racing or DHT modules
 */
enum class AATStartPointType(val displayName: String, val description: String) {
    AAT_START_LINE("Start Line", "Perpendicular line to first AAT area"),
    AAT_START_CYLINDER("Start Cylinder", "Cylinder around start waypoint"),
    AAT_START_SECTOR("AAT Start Sector", "180° sector facing away from first area")
}
