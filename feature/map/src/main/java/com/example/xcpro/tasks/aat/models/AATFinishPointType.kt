package com.example.xcpro.tasks.aat.models

/**
 * AAT-specific finish point types - COMPLETELY INDEPENDENT from Racing
 * Maintains task type separation architecture
 *
 * These finish types are specific to AAT tasks and should NEVER be used by Racing or DHT modules
 */
enum class AATFinishPointType(val displayName: String, val description: String) {
    AAT_FINISH_LINE("Finish Line", "Perpendicular line from last AAT area"),
    AAT_FINISH_CYLINDER("Finish Cylinder", "Cylinder around finish waypoint")
}
