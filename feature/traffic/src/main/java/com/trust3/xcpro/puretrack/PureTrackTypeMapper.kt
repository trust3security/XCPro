package com.trust3.xcpro.puretrack

class PureTrackTypeMapper {
    fun map(id: Int?): PureTrackObjectType? = id?.let(KNOWN_TYPES::get)

    private companion object {
        private val KNOWN_TYPES = listOf(
            PureTrackObjectType(0, PureTrackCategory.OTHER, "Unknown"),
            PureTrackObjectType(1, PureTrackCategory.AIR, "Glider"),
            PureTrackObjectType(2, PureTrackCategory.AIR, "Tow"),
            PureTrackObjectType(3, PureTrackCategory.AIR, "Helicopter"),
            PureTrackObjectType(7, PureTrackCategory.AIR, "Paraglider"),
            PureTrackObjectType(8, PureTrackCategory.AIR, "Plane"),
            PureTrackObjectType(13, PureTrackCategory.AIR, "Drone"),
            PureTrackObjectType(16, PureTrackCategory.AIR, "Gyrocopter"),
            PureTrackObjectType(20, PureTrackCategory.OTHER, "Person")
        ).associateBy { it.id }
    }
}
