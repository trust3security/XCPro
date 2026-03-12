package com.example.xcpro.tasks.domain.model

/**
 * Observation zone shapes supported by XCPro task domain.
 * Keep this purely geometric (no Android/MapLibre types).
 */
sealed interface ObservationZone {
    val label: String
}

data class LineOZ(
    val lengthMeters: Double = 1000.0,
    val widthMeters: Double = 200.0
) : ObservationZone {
    override val label: String = "LINE"
}

data class CylinderOZ(
    val radiusMeters: Double
) : ObservationZone {
    override val label: String = "CYLINDER"
}

data class SectorOZ(
    val radiusMeters: Double,
    val angleDeg: Double
) : ObservationZone {
    override val label: String = "SECTOR"
}

data class KeyholeOZ(
    val innerRadiusMeters: Double,
    val outerRadiusMeters: Double,
    val angleDeg: Double
) : ObservationZone {
    override val label: String = "KEYHOLE"
}

data class AnnularSectorOZ(
    val innerRadiusMeters: Double,
    val outerRadiusMeters: Double,
    val angleDeg: Double
) : ObservationZone {
    override val label: String = "ANNULAR_SECTOR"
}

/**
 * AAT "segment" behaves like a bounded sector; we model it explicitly to mirror XC behaviour.
 */
data class SegmentOZ(
    val radiusMeters: Double,
    val angleDeg: Double
) : ObservationZone {
    override val label: String = "SEGMENT"
}
