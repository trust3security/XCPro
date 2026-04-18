package com.trust3.xcpro.adsb

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class AdsbCollisionRiskAssessment(
    val isEmergencyCollisionRisk: Boolean,
    val ineligibilityReason: AdsbEmergencyAudioIneligibilityReason? = null
)

internal class AdsbCollisionRiskEvaluator(
    private val emergencyDistanceMeters: Double = EMERGENCY_DISTANCE_METERS,
    private val collisionHeadingToleranceDeg: Double = COLLISION_HEADING_TOLERANCE_DEG,
    private val projectedCpaDistanceMeters: Double = PROJECTED_CPA_DISTANCE_METERS,
    private val projectedCpaLookaheadSec: Double = PROJECTED_CPA_LOOKAHEAD_SEC,
    private val minMotionSpeedMps: Double = MIN_MOTION_SPEED_MPS,
    private val minRelativeSpeedMps: Double = MIN_RELATIVE_SPEED_MPS,
    private val emergencyMaxAgeSec: Int = EMERGENCY_MAX_AGE_SEC
) {

    fun evaluate(
        distanceMeters: Double,
        trackDeg: Double?,
        targetSpeedMps: Double? = null,
        bearingDegFromUser: Double,
        ownshipTrackDeg: Double? = null,
        ownshipSpeedMps: Double? = null,
        altitudeDeltaMeters: Double?,
        verticalAboveMeters: Double,
        verticalBelowMeters: Double,
        hasOwnshipReference: Boolean,
        isClosing: Boolean,
        ageSec: Int
    ): AdsbCollisionRiskAssessment {
        if (!hasOwnshipReference) {
            return AdsbCollisionRiskAssessment(
                isEmergencyCollisionRisk = false,
                ineligibilityReason = AdsbEmergencyAudioIneligibilityReason.NO_OWNSHIP_REFERENCE
            )
        }
        if (!isClosing) {
            return AdsbCollisionRiskAssessment(
                isEmergencyCollisionRisk = false,
                ineligibilityReason = AdsbEmergencyAudioIneligibilityReason.NOT_CLOSING
            )
        }
        if (ageSec > emergencyMaxAgeSec) {
            return AdsbCollisionRiskAssessment(
                isEmergencyCollisionRisk = false,
                ineligibilityReason = AdsbEmergencyAudioIneligibilityReason.STALE_TARGET_SAMPLE
            )
        }
        if (!distanceMeters.isFinite() || distanceMeters > emergencyDistanceMeters) {
            return AdsbCollisionRiskAssessment(
                isEmergencyCollisionRisk = false,
                ineligibilityReason =
                    AdsbEmergencyAudioIneligibilityReason.DISTANCE_OUTSIDE_EMERGENCY_RANGE
            )
        }

        val altitudeDelta = altitudeDeltaMeters ?: return AdsbCollisionRiskAssessment(
            isEmergencyCollisionRisk = false,
            ineligibilityReason =
                AdsbEmergencyAudioIneligibilityReason.RELATIVE_ALTITUDE_UNAVAILABLE
        )
        val above = altitudeDelta
        val below = -altitudeDelta
        if (above > verticalAboveMeters || below > verticalBelowMeters) {
            return AdsbCollisionRiskAssessment(
                isEmergencyCollisionRisk = false,
                ineligibilityReason = AdsbEmergencyAudioIneligibilityReason.OUTSIDE_VERTICAL_GATE
            )
        }
        if (isExplicitlyLowMotionSpeed(targetSpeedMps = targetSpeedMps, ownshipSpeedMps = ownshipSpeedMps)) {
            return AdsbCollisionRiskAssessment(
                isEmergencyCollisionRisk = false,
                ineligibilityReason = AdsbEmergencyAudioIneligibilityReason.LOW_MOTION_SPEED
            )
        }

        val track = trackDeg ?: return AdsbCollisionRiskAssessment(
            isEmergencyCollisionRisk = false,
            ineligibilityReason = AdsbEmergencyAudioIneligibilityReason.TARGET_TRACK_UNAVAILABLE
        )
        if (!track.isFinite() || !bearingDegFromUser.isFinite()) {
            return AdsbCollisionRiskAssessment(
                isEmergencyCollisionRisk = false,
                ineligibilityReason = AdsbEmergencyAudioIneligibilityReason.HEADING_GATE_FAILED
            )
        }
        val bearingFromTargetToUser = normalizeDegrees(bearingDegFromUser + 180.0)
        val headingError = minHeadingDiffDeg(track, bearingFromTargetToUser)
        if (headingError > collisionHeadingToleranceDeg) {
            return AdsbCollisionRiskAssessment(
                isEmergencyCollisionRisk = false,
                ineligibilityReason = AdsbEmergencyAudioIneligibilityReason.HEADING_GATE_FAILED
            )
        }

        if (!hasMotionContext(
                targetTrackDeg = track,
                targetSpeedMps = targetSpeedMps,
                ownshipTrackDeg = ownshipTrackDeg,
                ownshipSpeedMps = ownshipSpeedMps
            )
        ) {
            return AdsbCollisionRiskAssessment(
                isEmergencyCollisionRisk = false,
                ineligibilityReason = AdsbEmergencyAudioIneligibilityReason.MOTION_CONFIDENCE_LOW
            )
        }

        val projectedConflictLikely = isProjectedConflictLikely(
            distanceMeters = distanceMeters,
            bearingDegFromUser = bearingDegFromUser,
            targetTrackDeg = track,
            targetSpeedMps = targetSpeedMps ?: 0.0,
            ownshipTrackDeg = ownshipTrackDeg ?: 0.0,
            ownshipSpeedMps = ownshipSpeedMps ?: 0.0
        )
        return if (projectedConflictLikely) {
            AdsbCollisionRiskAssessment(isEmergencyCollisionRisk = true)
        } else {
            AdsbCollisionRiskAssessment(
                isEmergencyCollisionRisk = false,
                ineligibilityReason =
                    AdsbEmergencyAudioIneligibilityReason.PROJECTED_CONFLICT_NOT_LIKELY
            )
        }
    }

    private fun normalizeDegrees(value: Double): Double {
        val normalized = value % 360.0
        return if (normalized < 0.0) normalized + 360.0 else normalized
    }

    private fun minHeadingDiffDeg(a: Double, b: Double): Double {
        val diff = abs(normalizeDegrees(a) - normalizeDegrees(b))
        return if (diff > 180.0) 360.0 - diff else diff
    }

    private fun hasMotionContext(
        targetTrackDeg: Double,
        targetSpeedMps: Double?,
        ownshipTrackDeg: Double?,
        ownshipSpeedMps: Double?
    ): Boolean {
        if (!targetTrackDeg.isFinite()) return false
        val targetSpeed = targetSpeedMps ?: return false
        val ownTrack = ownshipTrackDeg ?: return false
        val ownSpeed = ownshipSpeedMps ?: return false
        return targetSpeed.isFinite() &&
            ownTrack.isFinite() &&
            ownSpeed.isFinite() &&
            targetSpeed >= minMotionSpeedMps &&
            ownSpeed >= minMotionSpeedMps
    }

    private fun isExplicitlyLowMotionSpeed(
        targetSpeedMps: Double?,
        ownshipSpeedMps: Double?
    ): Boolean {
        val ownshipLowSpeed =
            ownshipSpeedMps != null &&
                ownshipSpeedMps.isFinite() &&
                ownshipSpeedMps < minMotionSpeedMps
        val targetLowSpeed =
            targetSpeedMps != null &&
                targetSpeedMps.isFinite() &&
                targetSpeedMps < minMotionSpeedMps
        return ownshipLowSpeed || targetLowSpeed
    }

    private fun isProjectedConflictLikely(
        distanceMeters: Double,
        bearingDegFromUser: Double,
        targetTrackDeg: Double,
        targetSpeedMps: Double,
        ownshipTrackDeg: Double,
        ownshipSpeedMps: Double
    ): Boolean {
        if (!distanceMeters.isFinite() || !bearingDegFromUser.isFinite()) return false
        val bearingRad = bearingDegFromUser.toRadians()
        val targetTrackRad = targetTrackDeg.toRadians()
        val ownshipTrackRad = ownshipTrackDeg.toRadians()

        val relEast = distanceMeters * sin(bearingRad)
        val relNorth = distanceMeters * cos(bearingRad)
        val targetVelEast = targetSpeedMps * sin(targetTrackRad)
        val targetVelNorth = targetSpeedMps * cos(targetTrackRad)
        val ownVelEast = ownshipSpeedMps * sin(ownshipTrackRad)
        val ownVelNorth = ownshipSpeedMps * cos(ownshipTrackRad)

        val relVelEast = targetVelEast - ownVelEast
        val relVelNorth = targetVelNorth - ownVelNorth
        val relSpeedSq = relVelEast * relVelEast + relVelNorth * relVelNorth
        if (relSpeedSq <= (minRelativeSpeedMps * minRelativeSpeedMps)) return false

        val tcpaSec = -((relEast * relVelEast) + (relNorth * relVelNorth)) / relSpeedSq
        if (!tcpaSec.isFinite() || tcpaSec < 0.0 || tcpaSec > projectedCpaLookaheadSec) return false

        val cpaEast = relEast + (relVelEast * tcpaSec)
        val cpaNorth = relNorth + (relVelNorth * tcpaSec)
        val cpaDistanceMeters = sqrt((cpaEast * cpaEast) + (cpaNorth * cpaNorth))
        return cpaDistanceMeters <= projectedCpaDistanceMeters
    }

    private fun Double.toRadians(): Double = this * (Math.PI / 180.0)

    private companion object {
        private const val EMERGENCY_DISTANCE_METERS = 1_000.0
        private const val COLLISION_HEADING_TOLERANCE_DEG = 20.0
        private const val PROJECTED_CPA_DISTANCE_METERS = 300.0
        private const val PROJECTED_CPA_LOOKAHEAD_SEC = 45.0
        private const val MIN_MOTION_SPEED_MPS = 3.0
        private const val MIN_RELATIVE_SPEED_MPS = 1.0
        private const val EMERGENCY_MAX_AGE_SEC = 20
    }
}
