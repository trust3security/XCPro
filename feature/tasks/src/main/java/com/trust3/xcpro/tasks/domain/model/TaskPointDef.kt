package com.trust3.xcpro.tasks.domain.model

import com.trust3.xcpro.tasks.core.WaypointRole

/**
 * Immutable definition of a task point used by the domain layer.
 * AI-NOTE: Keep this decoupled from UI/storage; only geometry + intent.
 */
data class GeoPoint(
    val lat: Double,
    val lon: Double
)

data class TaskPointDef(
    val id: String,
    val name: String,
    val role: WaypointRole,
    val location: GeoPoint,
    val zone: ObservationZone,
    /**
     * Whether this point supports an adjustable target (AAT leg).
     */
    val allowsTarget: Boolean = false,
    /**
     * Optional current target; only meaningful when allowsTarget == true.
     */
    val target: GeoPoint? = null,
    /**
     * Target parameter 0..1 along isoline chord (for persistence/restore).
     */
    val targetParam: Double = 0.5,
    /**
     * When locked, targetParam is preserved across task refreshes.
     */
    val targetLocked: Boolean = false
)
