package com.trust3.xcpro.tasks.core

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

    const val START_GATE_OPEN_TIME_MILLIS = "startGateOpenTimeMillis"
    const val START_GATE_CLOSE_TIME_MILLIS = "startGateCloseTimeMillis"
    const val START_TOLERANCE_METERS = "startToleranceMeters"
    const val PRE_START_ALTITUDE_METERS = "preStartAltitudeMeters"
    const val START_ALTITUDE_REFERENCE = "startAltitudeReference"
    const val START_DIRECTION_OVERRIDE_DEGREES = "startDirectionOverrideDegrees"
    const val MAX_START_ALTITUDE_METERS = "maxStartAltitudeMeters"
    const val MAX_START_GROUNDSPEED_MS = "maxStartGroundspeedMs"
    const val PEV_ENABLED = "pevEnabled"
    const val PEV_WAIT_TIME_MINUTES = "pevWaitTimeMinutes"
    const val PEV_START_WINDOW_MINUTES = "pevStartWindowMinutes"
    const val PEV_MAX_PRESSES = "pevMaxPresses"
    const val PEV_DEDUPE_SECONDS = "pevDedupeSeconds"
    const val PEV_MIN_INTERVAL_MINUTES = "pevMinIntervalMinutes"
    const val PEV_PRESS_TIMESTAMPS_MILLIS = "pevPressTimestampsMillis"

    const val FINISH_CLOSE_TIME_MILLIS = "finishCloseTimeMillis"
    const val FINISH_MIN_ALTITUDE_METERS = "finishMinAltitudeMeters"
    const val FINISH_ALTITUDE_REFERENCE = "finishAltitudeReference"
    const val FINISH_DIRECTION_OVERRIDE_DEGREES = "finishDirectionOverrideDegrees"
    const val FINISH_ALLOW_STRAIGHT_IN_BELOW_MIN_ALTITUDE = "finishAllowStraightInBelowMinAltitude"
    const val FINISH_REQUIRE_LAND_WITHOUT_DELAY = "finishRequireLandWithoutDelay"
    const val FINISH_LAND_WITHOUT_DELAY_WINDOW_SECONDS = "finishLandWithoutDelayWindowSeconds"
    const val FINISH_LANDING_SPEED_THRESHOLD_MS = "finishLandingSpeedThresholdMs"
    const val FINISH_LANDING_HOLD_SECONDS = "finishLandingHoldSeconds"
    const val FINISH_CONTEST_BOUNDARY_RADIUS_METERS = "finishContestBoundaryRadiusMeters"
    const val FINISH_STOP_PLUS_FIVE_ENABLED = "finishStopPlusFiveEnabled"
    const val FINISH_STOP_PLUS_FIVE_MINUTES = "finishStopPlusFiveMinutes"
    const val RACING_VALIDATION_PROFILE = "racingValidationProfile"
}

enum class RacingAltitudeReference {
    MSL,
    QNH;

    companion object {
        fun from(value: String?): RacingAltitudeReference {
            return value
                ?.let { raw -> entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
                ?: MSL
        }
    }
}

data class RacingPevCustomParams(
    val enabled: Boolean = false,
    val waitTimeMinutes: Int? = null,
    val startWindowMinutes: Int? = null,
    val maxPressesPerLaunch: Int = 3,
    val dedupeSeconds: Long = 30L,
    val minIntervalMinutes: Int = 10,
    val pressTimestampsMillis: List<Long> = emptyList()
) {
    fun applyTo(destination: MutableMap<String, Any>) {
        destination[TaskWaypointParamKeys.PEV_ENABLED] = enabled
        if (waitTimeMinutes != null) {
            destination[TaskWaypointParamKeys.PEV_WAIT_TIME_MINUTES] = waitTimeMinutes
        } else {
            destination.remove(TaskWaypointParamKeys.PEV_WAIT_TIME_MINUTES)
        }
        if (startWindowMinutes != null) {
            destination[TaskWaypointParamKeys.PEV_START_WINDOW_MINUTES] = startWindowMinutes
        } else {
            destination.remove(TaskWaypointParamKeys.PEV_START_WINDOW_MINUTES)
        }
        destination[TaskWaypointParamKeys.PEV_MAX_PRESSES] = maxPressesPerLaunch
        destination[TaskWaypointParamKeys.PEV_DEDUPE_SECONDS] = dedupeSeconds
        destination[TaskWaypointParamKeys.PEV_MIN_INTERVAL_MINUTES] = minIntervalMinutes
        if (pressTimestampsMillis.isNotEmpty()) {
            destination[TaskWaypointParamKeys.PEV_PRESS_TIMESTAMPS_MILLIS] = pressTimestampsMillis
        } else {
            destination.remove(TaskWaypointParamKeys.PEV_PRESS_TIMESTAMPS_MILLIS)
        }
    }

    companion object {
        private const val DEFAULT_MAX_PRESSES = 3
        private const val DEFAULT_DEDUPE_SECONDS = 30L
        private const val DEFAULT_MIN_INTERVAL_MINUTES = 10

        fun from(source: Map<String, Any>): RacingPevCustomParams {
            val wait = source.int(TaskWaypointParamKeys.PEV_WAIT_TIME_MINUTES)
            val window = source.int(TaskWaypointParamKeys.PEV_START_WINDOW_MINUTES)
            val enabled = source.boolean(TaskWaypointParamKeys.PEV_ENABLED) ?: (wait != null || window != null)
            val maxPresses = (source.int(TaskWaypointParamKeys.PEV_MAX_PRESSES) ?: DEFAULT_MAX_PRESSES)
                .coerceIn(1, 10)
            val dedupeSeconds = (source.long(TaskWaypointParamKeys.PEV_DEDUPE_SECONDS) ?: DEFAULT_DEDUPE_SECONDS)
                .coerceIn(0L, 300L)
            val minIntervalMinutes =
                (source.int(TaskWaypointParamKeys.PEV_MIN_INTERVAL_MINUTES) ?: DEFAULT_MIN_INTERVAL_MINUTES)
                    .coerceIn(0, 60)
            val presses = parsePressTimestamps(source[TaskWaypointParamKeys.PEV_PRESS_TIMESTAMPS_MILLIS])

            return RacingPevCustomParams(
                enabled = enabled,
                waitTimeMinutes = wait,
                startWindowMinutes = window,
                maxPressesPerLaunch = maxPresses,
                dedupeSeconds = dedupeSeconds,
                minIntervalMinutes = minIntervalMinutes,
                pressTimestampsMillis = presses
            )
        }

        private fun parsePressTimestamps(value: Any?): List<Long> {
            val rawList = value as? List<*> ?: return emptyList()
            return rawList
                .mapNotNull { (it as? Number)?.toLong() }
                .sorted()
        }
    }
}

data class RacingStartCustomParams(
    val gateOpenTimeMillis: Long? = null,
    val gateCloseTimeMillis: Long? = null,
    val toleranceMeters: Double = 500.0,
    val preStartAltitudeMeters: Double? = null,
    val altitudeReference: RacingAltitudeReference = RacingAltitudeReference.MSL,
    val directionOverrideDegrees: Double? = null,
    val maxStartAltitudeMeters: Double? = null,
    val maxStartGroundspeedMs: Double? = null,
    val pev: RacingPevCustomParams = RacingPevCustomParams()
) {
    fun applyTo(destination: MutableMap<String, Any>) {
        if (gateOpenTimeMillis != null) {
            destination[TaskWaypointParamKeys.START_GATE_OPEN_TIME_MILLIS] = gateOpenTimeMillis
        } else {
            destination.remove(TaskWaypointParamKeys.START_GATE_OPEN_TIME_MILLIS)
        }
        if (gateCloseTimeMillis != null) {
            destination[TaskWaypointParamKeys.START_GATE_CLOSE_TIME_MILLIS] = gateCloseTimeMillis
        } else {
            destination.remove(TaskWaypointParamKeys.START_GATE_CLOSE_TIME_MILLIS)
        }
        destination[TaskWaypointParamKeys.START_TOLERANCE_METERS] = toleranceMeters
        if (preStartAltitudeMeters != null) {
            destination[TaskWaypointParamKeys.PRE_START_ALTITUDE_METERS] = preStartAltitudeMeters
        } else {
            destination.remove(TaskWaypointParamKeys.PRE_START_ALTITUDE_METERS)
        }
        destination[TaskWaypointParamKeys.START_ALTITUDE_REFERENCE] = altitudeReference.name
        if (directionOverrideDegrees != null) {
            destination[TaskWaypointParamKeys.START_DIRECTION_OVERRIDE_DEGREES] = directionOverrideDegrees
        } else {
            destination.remove(TaskWaypointParamKeys.START_DIRECTION_OVERRIDE_DEGREES)
        }
        if (maxStartAltitudeMeters != null) {
            destination[TaskWaypointParamKeys.MAX_START_ALTITUDE_METERS] = maxStartAltitudeMeters
        } else {
            destination.remove(TaskWaypointParamKeys.MAX_START_ALTITUDE_METERS)
        }
        if (maxStartGroundspeedMs != null) {
            destination[TaskWaypointParamKeys.MAX_START_GROUNDSPEED_MS] = maxStartGroundspeedMs
        } else {
            destination.remove(TaskWaypointParamKeys.MAX_START_GROUNDSPEED_MS)
        }
        pev.applyTo(destination)
    }

    companion object {
        private const val DEFAULT_TOLERANCE_METERS = 500.0

        fun from(source: Map<String, Any>): RacingStartCustomParams {
            return RacingStartCustomParams(
                gateOpenTimeMillis = source.long(TaskWaypointParamKeys.START_GATE_OPEN_TIME_MILLIS),
                gateCloseTimeMillis = source.long(TaskWaypointParamKeys.START_GATE_CLOSE_TIME_MILLIS),
                toleranceMeters = source.double(TaskWaypointParamKeys.START_TOLERANCE_METERS)
                    ?.takeIf { it > 0.0 } ?: DEFAULT_TOLERANCE_METERS,
                preStartAltitudeMeters = source.double(TaskWaypointParamKeys.PRE_START_ALTITUDE_METERS)
                    ?.takeIf { it.isFinite() },
                altitudeReference = RacingAltitudeReference.from(
                    source[TaskWaypointParamKeys.START_ALTITUDE_REFERENCE] as? String
                ),
                directionOverrideDegrees = source.double(TaskWaypointParamKeys.START_DIRECTION_OVERRIDE_DEGREES)
                    ?.takeIf { it.isFinite() },
                maxStartAltitudeMeters = source.double(TaskWaypointParamKeys.MAX_START_ALTITUDE_METERS)
                    ?.takeIf { it.isFinite() },
                maxStartGroundspeedMs = source.double(TaskWaypointParamKeys.MAX_START_GROUNDSPEED_MS)
                    ?.takeIf { it.isFinite() },
                pev = RacingPevCustomParams.from(source)
            )
        }
    }
}

data class RacingFinishCustomParams(
    val closeTimeMillis: Long? = null,
    val minAltitudeMeters: Double? = null,
    val altitudeReference: RacingAltitudeReference = RacingAltitudeReference.MSL,
    val directionOverrideDegrees: Double? = null,
    val allowStraightInBelowMinAltitude: Boolean = false,
    val requireLandWithoutDelay: Boolean = false,
    val landWithoutDelayWindowSeconds: Long = 600L,
    val landingSpeedThresholdMs: Double = 5.0,
    val landingHoldSeconds: Long = 20L,
    val contestBoundaryRadiusMeters: Double? = null,
    val stopPlusFiveEnabled: Boolean = false,
    val stopPlusFiveMinutes: Long = 5L
) {
    fun applyTo(destination: MutableMap<String, Any>) {
        if (closeTimeMillis != null) destination[TaskWaypointParamKeys.FINISH_CLOSE_TIME_MILLIS] = closeTimeMillis
        else destination.remove(TaskWaypointParamKeys.FINISH_CLOSE_TIME_MILLIS)
        if (minAltitudeMeters != null) destination[TaskWaypointParamKeys.FINISH_MIN_ALTITUDE_METERS] = minAltitudeMeters
        else destination.remove(TaskWaypointParamKeys.FINISH_MIN_ALTITUDE_METERS)
        destination[TaskWaypointParamKeys.FINISH_ALTITUDE_REFERENCE] = altitudeReference.name
        if (directionOverrideDegrees != null) {
            destination[TaskWaypointParamKeys.FINISH_DIRECTION_OVERRIDE_DEGREES] = directionOverrideDegrees
        } else {
            destination.remove(TaskWaypointParamKeys.FINISH_DIRECTION_OVERRIDE_DEGREES)
        }
        destination[TaskWaypointParamKeys.FINISH_ALLOW_STRAIGHT_IN_BELOW_MIN_ALTITUDE] = allowStraightInBelowMinAltitude
        destination[TaskWaypointParamKeys.FINISH_REQUIRE_LAND_WITHOUT_DELAY] = requireLandWithoutDelay
        destination[TaskWaypointParamKeys.FINISH_LAND_WITHOUT_DELAY_WINDOW_SECONDS] = landWithoutDelayWindowSeconds
        destination[TaskWaypointParamKeys.FINISH_LANDING_SPEED_THRESHOLD_MS] = landingSpeedThresholdMs
        destination[TaskWaypointParamKeys.FINISH_LANDING_HOLD_SECONDS] = landingHoldSeconds
        if (contestBoundaryRadiusMeters != null) {
            destination[TaskWaypointParamKeys.FINISH_CONTEST_BOUNDARY_RADIUS_METERS] = contestBoundaryRadiusMeters
        } else {
            destination.remove(TaskWaypointParamKeys.FINISH_CONTEST_BOUNDARY_RADIUS_METERS)
        }
        destination[TaskWaypointParamKeys.FINISH_STOP_PLUS_FIVE_ENABLED] = stopPlusFiveEnabled
        destination[TaskWaypointParamKeys.FINISH_STOP_PLUS_FIVE_MINUTES] = stopPlusFiveMinutes
    }

    companion object {
        private const val DEFAULT_LAND_WITHOUT_DELAY_WINDOW_SECONDS = 600L
        private const val DEFAULT_LANDING_SPEED_THRESHOLD_MS = 5.0
        private const val DEFAULT_LANDING_HOLD_SECONDS = 20L
        private const val DEFAULT_STOP_PLUS_FIVE_MINUTES = 5L

        fun from(source: Map<String, Any>): RacingFinishCustomParams {
            return RacingFinishCustomParams(
                closeTimeMillis = source.long(TaskWaypointParamKeys.FINISH_CLOSE_TIME_MILLIS),
                minAltitudeMeters = source.double(TaskWaypointParamKeys.FINISH_MIN_ALTITUDE_METERS)
                    ?.takeIf { it.isFinite() },
                altitudeReference = RacingAltitudeReference.from(
                    source[TaskWaypointParamKeys.FINISH_ALTITUDE_REFERENCE] as? String
                ),
                directionOverrideDegrees = source.double(TaskWaypointParamKeys.FINISH_DIRECTION_OVERRIDE_DEGREES)
                    ?.takeIf { it.isFinite() },
                allowStraightInBelowMinAltitude = source.boolean(
                    TaskWaypointParamKeys.FINISH_ALLOW_STRAIGHT_IN_BELOW_MIN_ALTITUDE
                ) ?: false,
                requireLandWithoutDelay = source.boolean(TaskWaypointParamKeys.FINISH_REQUIRE_LAND_WITHOUT_DELAY) ?: false,
                landWithoutDelayWindowSeconds = (source.long(
                    TaskWaypointParamKeys.FINISH_LAND_WITHOUT_DELAY_WINDOW_SECONDS
                ) ?: DEFAULT_LAND_WITHOUT_DELAY_WINDOW_SECONDS).coerceIn(30L, 86_400L),
                landingSpeedThresholdMs = (source.double(TaskWaypointParamKeys.FINISH_LANDING_SPEED_THRESHOLD_MS)
                    ?: DEFAULT_LANDING_SPEED_THRESHOLD_MS).coerceIn(0.5, 50.0),
                landingHoldSeconds = (source.long(TaskWaypointParamKeys.FINISH_LANDING_HOLD_SECONDS)
                    ?: DEFAULT_LANDING_HOLD_SECONDS).coerceIn(5L, 3_600L),
                contestBoundaryRadiusMeters = source.double(TaskWaypointParamKeys.FINISH_CONTEST_BOUNDARY_RADIUS_METERS)
                    ?.takeIf { it.isFinite() && it > 0.0 },
                stopPlusFiveEnabled = source.boolean(TaskWaypointParamKeys.FINISH_STOP_PLUS_FIVE_ENABLED) ?: false,
                stopPlusFiveMinutes = (source.long(TaskWaypointParamKeys.FINISH_STOP_PLUS_FIVE_MINUTES)
                    ?: DEFAULT_STOP_PLUS_FIVE_MINUTES).coerceIn(1L, 60L)
            )
        }
    }
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

data class AATTaskTimeCustomParams(
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

data class AATWaypointCustomParams(
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

data class RacingWaypointCustomParams(
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
internal fun Map<String, Any>.int(key: String): Int? = (this[key] as? Number)?.toInt()
internal fun Map<String, Any>.boolean(key: String): Boolean? = this[key] as? Boolean
