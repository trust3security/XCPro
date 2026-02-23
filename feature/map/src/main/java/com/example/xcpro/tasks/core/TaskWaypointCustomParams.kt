package com.example.xcpro.tasks.core

internal object TaskWaypointParamKeys {
    const val RADIUS_METERS = "radiusMeters"
    const val OUTER_RADIUS_METERS = "outerRadiusMeters"
    const val INNER_RADIUS_METERS = "innerRadiusMeters"
    const val START_ANGLE_DEGREES = "startAngleDegrees"
    const val END_ANGLE_DEGREES = "endAngleDegrees"
    const val LINE_WIDTH_METERS = "lineWidthMeters"
    const val TARGET_LAT = "targetLat"
    const val TARGET_LON = "targetLon"
    const val IS_TARGET_POINT_CUSTOMIZED = "isTargetPointCustomized"
    const val AAT_MINIMUM_TIME_SECONDS = "aatMinimumTimeSeconds"
    const val AAT_MAXIMUM_TIME_SECONDS = "aatMaximumTimeSeconds"

    const val TARGET_PARAM = "targetParam"
    const val TARGET_LOCKED = "targetLocked"
    const val OZ_TYPE = "ozType"
    const val OZ_PARAMS = "ozParams"
    const val OZ_ANGLE_DEG = "angleDeg"
    const val OZ_LENGTH_METERS = "lengthMeters"
    const val OZ_WIDTH_METERS = "widthMeters"

    const val KEYHOLE_INNER_RADIUS_METERS = "keyholeInnerRadiusMeters"
    const val KEYHOLE_ANGLE = "keyholeAngle"
    const val FAI_QUADRANT_OUTER_RADIUS_METERS = "faiQuadrantOuterRadiusMeters"
    const val LEGACY_KEYHOLE_INNER_RADIUS_KM = "keyholeInnerRadius"
    const val LEGACY_FAI_QUADRANT_OUTER_RADIUS_KM = "faiQuadrantOuterRadius"
}

internal data class PersistedOzParams(
    val radiusMeters: Double? = null,
    val outerRadiusMeters: Double? = null,
    val innerRadiusMeters: Double? = null,
    val angleDeg: Double? = null,
    val lengthMeters: Double? = null,
    val widthMeters: Double? = null
) {
    fun effectiveRadiusMeters(): Double? = radiusMeters ?: outerRadiusMeters

    fun toMap(): Map<String, Double?> {
        val result = mutableMapOf<String, Double?>()
        if (lengthMeters != null) result[TaskWaypointParamKeys.OZ_LENGTH_METERS] = lengthMeters
        if (widthMeters != null) result[TaskWaypointParamKeys.OZ_WIDTH_METERS] = widthMeters
        if (radiusMeters != null) result[TaskWaypointParamKeys.RADIUS_METERS] = radiusMeters
        if (outerRadiusMeters != null) result[TaskWaypointParamKeys.OUTER_RADIUS_METERS] = outerRadiusMeters
        if (innerRadiusMeters != null) result[TaskWaypointParamKeys.INNER_RADIUS_METERS] = innerRadiusMeters
        if (angleDeg != null) result[TaskWaypointParamKeys.OZ_ANGLE_DEG] = angleDeg
        return result
    }

    companion object {
        fun from(source: Map<String, Double?>): PersistedOzParams {
            return PersistedOzParams(
                radiusMeters = source[TaskWaypointParamKeys.RADIUS_METERS],
                outerRadiusMeters = source[TaskWaypointParamKeys.OUTER_RADIUS_METERS],
                innerRadiusMeters = source[TaskWaypointParamKeys.INNER_RADIUS_METERS],
                angleDeg = source[TaskWaypointParamKeys.OZ_ANGLE_DEG],
                lengthMeters = source[TaskWaypointParamKeys.OZ_LENGTH_METERS],
                widthMeters = source[TaskWaypointParamKeys.OZ_WIDTH_METERS]
            )
        }
    }
}

internal data class AATTaskTimeCustomParams(
    val minimumTimeSeconds: Double,
    val maximumTimeSeconds: Double?
) {
    fun applyTo(destination: MutableMap<String, Any>) {
        destination[TaskWaypointParamKeys.AAT_MINIMUM_TIME_SECONDS] = minimumTimeSeconds
        if (maximumTimeSeconds != null) {
            destination[TaskWaypointParamKeys.AAT_MAXIMUM_TIME_SECONDS] = maximumTimeSeconds
        } else {
            destination.remove(TaskWaypointParamKeys.AAT_MAXIMUM_TIME_SECONDS)
        }
    }

    companion object {
        fun from(
            source: Map<String, Any>,
            fallbackMinimumTimeSeconds: Double,
            fallbackMaximumTimeSeconds: Double?
        ): AATTaskTimeCustomParams {
            return AATTaskTimeCustomParams(
                minimumTimeSeconds = source.double(TaskWaypointParamKeys.AAT_MINIMUM_TIME_SECONDS) ?: fallbackMinimumTimeSeconds,
                maximumTimeSeconds = source.double(TaskWaypointParamKeys.AAT_MAXIMUM_TIME_SECONDS) ?: fallbackMaximumTimeSeconds
            )
        }
    }
}

internal data class AATWaypointCustomParams(
    val radiusMeters: Double,
    val outerRadiusMeters: Double,
    val innerRadiusMeters: Double,
    val startAngleDegrees: Double,
    val endAngleDegrees: Double,
    val lineWidthMeters: Double,
    val targetLat: Double,
    val targetLon: Double,
    val isTargetPointCustomized: Boolean
) {
    fun applyTo(destination: MutableMap<String, Any>) {
        destination[TaskWaypointParamKeys.RADIUS_METERS] = radiusMeters
        destination[TaskWaypointParamKeys.OUTER_RADIUS_METERS] = outerRadiusMeters
        destination[TaskWaypointParamKeys.INNER_RADIUS_METERS] = innerRadiusMeters
        destination[TaskWaypointParamKeys.START_ANGLE_DEGREES] = startAngleDegrees
        destination[TaskWaypointParamKeys.END_ANGLE_DEGREES] = endAngleDegrees
        destination[TaskWaypointParamKeys.LINE_WIDTH_METERS] = lineWidthMeters
        destination[TaskWaypointParamKeys.TARGET_LAT] = targetLat
        destination[TaskWaypointParamKeys.TARGET_LON] = targetLon
        destination[TaskWaypointParamKeys.IS_TARGET_POINT_CUSTOMIZED] = isTargetPointCustomized
    }

    companion object {
        fun from(
            source: Map<String, Any>,
            fallbackLat: Double,
            fallbackLon: Double,
            fallbackRadiusMeters: Double
        ): AATWaypointCustomParams {
            val radiusMeters = source.double(TaskWaypointParamKeys.RADIUS_METERS) ?: fallbackRadiusMeters
            val outerRadiusMeters = source.double(TaskWaypointParamKeys.OUTER_RADIUS_METERS) ?: radiusMeters
            val innerRadiusMeters = source.double(TaskWaypointParamKeys.INNER_RADIUS_METERS) ?: 0.0
            val startAngleDegrees = source.double(TaskWaypointParamKeys.START_ANGLE_DEGREES) ?: 0.0
            val endAngleDegrees = source.double(TaskWaypointParamKeys.END_ANGLE_DEGREES) ?: 90.0
            val lineWidthMeters = source.double(TaskWaypointParamKeys.LINE_WIDTH_METERS) ?: radiusMeters
            val targetLat = source.double(TaskWaypointParamKeys.TARGET_LAT) ?: fallbackLat
            val targetLon = source.double(TaskWaypointParamKeys.TARGET_LON) ?: fallbackLon
            val isTargetPointCustomized = source.boolean(TaskWaypointParamKeys.IS_TARGET_POINT_CUSTOMIZED)
                ?: (targetLat != fallbackLat || targetLon != fallbackLon)

            return AATWaypointCustomParams(
                radiusMeters = radiusMeters,
                outerRadiusMeters = outerRadiusMeters,
                innerRadiusMeters = innerRadiusMeters,
                startAngleDegrees = startAngleDegrees,
                endAngleDegrees = endAngleDegrees,
                lineWidthMeters = lineWidthMeters,
                targetLat = targetLat,
                targetLon = targetLon,
                isTargetPointCustomized = isTargetPointCustomized
            )
        }
    }
}

internal data class RacingWaypointCustomParams(
    val keyholeInnerRadiusMeters: Double,
    val keyholeAngle: Double,
    val faiQuadrantOuterRadiusMeters: Double
) {
    fun applyTo(destination: MutableMap<String, Any>) {
        destination[TaskWaypointParamKeys.KEYHOLE_INNER_RADIUS_METERS] = keyholeInnerRadiusMeters
        destination[TaskWaypointParamKeys.KEYHOLE_ANGLE] = keyholeAngle
        destination[TaskWaypointParamKeys.FAI_QUADRANT_OUTER_RADIUS_METERS] = faiQuadrantOuterRadiusMeters
        destination.remove(TaskWaypointParamKeys.LEGACY_KEYHOLE_INNER_RADIUS_KM)
        destination.remove(TaskWaypointParamKeys.LEGACY_FAI_QUADRANT_OUTER_RADIUS_KM)
    }

    fun toMap(): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        applyTo(result)
        return result
    }

    companion object {
        private const val METERS_PER_KILOMETER = 1000.0
        private const val DEFAULT_KEYHOLE_INNER_RADIUS_METERS = 500.0
        private const val DEFAULT_FAI_QUADRANT_OUTER_RADIUS_METERS = 10_000.0

        fun from(source: Map<String, Any>): RacingWaypointCustomParams {
            val legacyKeyholeInnerKm =
                source.double(TaskWaypointParamKeys.LEGACY_KEYHOLE_INNER_RADIUS_KM)
            val legacyFaiOuterKm =
                source.double(TaskWaypointParamKeys.LEGACY_FAI_QUADRANT_OUTER_RADIUS_KM)
            return RacingWaypointCustomParams(
                keyholeInnerRadiusMeters = source.double(TaskWaypointParamKeys.KEYHOLE_INNER_RADIUS_METERS)
                    ?: legacyKeyholeInnerKm?.times(METERS_PER_KILOMETER)
                    ?: DEFAULT_KEYHOLE_INNER_RADIUS_METERS,
                keyholeAngle = source.double(TaskWaypointParamKeys.KEYHOLE_ANGLE) ?: 90.0,
                faiQuadrantOuterRadiusMeters = source.double(TaskWaypointParamKeys.FAI_QUADRANT_OUTER_RADIUS_METERS)
                    ?: legacyFaiOuterKm?.times(METERS_PER_KILOMETER)
                    ?: DEFAULT_FAI_QUADRANT_OUTER_RADIUS_METERS
            )
        }
    }
}

internal data class TargetStateCustomParams(
    val targetParam: Double,
    val targetLocked: Boolean,
    val targetLat: Double?,
    val targetLon: Double?
) {
    fun applyTo(destination: MutableMap<String, Any>) {
        destination[TaskWaypointParamKeys.TARGET_PARAM] = targetParam
        destination[TaskWaypointParamKeys.TARGET_LOCKED] = targetLocked
        if (targetLat != null) destination[TaskWaypointParamKeys.TARGET_LAT] = targetLat
        if (targetLon != null) destination[TaskWaypointParamKeys.TARGET_LON] = targetLon
    }

    companion object {
        fun from(
            source: Map<String, Any>,
            fallbackTargetParam: Double = 0.5,
            fallbackTargetLocked: Boolean = false
        ): TargetStateCustomParams {
            return TargetStateCustomParams(
                targetParam = source.double(TaskWaypointParamKeys.TARGET_PARAM) ?: fallbackTargetParam,
                targetLocked = source.boolean(TaskWaypointParamKeys.TARGET_LOCKED) ?: fallbackTargetLocked,
                targetLat = source.double(TaskWaypointParamKeys.TARGET_LAT),
                targetLon = source.double(TaskWaypointParamKeys.TARGET_LON)
            )
        }
    }
}

internal fun Map<String, Any>.double(key: String): Double? = (this[key] as? Number)?.toDouble()
internal fun Map<String, Any>.long(key: String): Long? = (this[key] as? Number)?.toLong()
internal fun Map<String, Any>.boolean(key: String): Boolean? = this[key] as? Boolean
