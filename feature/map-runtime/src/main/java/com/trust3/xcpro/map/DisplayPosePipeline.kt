package com.trust3.xcpro.map

import org.maplibre.android.geometry.LatLng

class DisplayPosePipeline(
    private val minSpeedProvider: () -> Double,
    private val adaptiveSmoothingEnabled: Boolean
) {
    private var displaySmoother = buildDisplaySmoother(DisplaySmoothingProfile.SMOOTH)
    private var lastSmoothingProfile: DisplaySmoothingProfile? = null
    private var lastDisplayPoseMode: DisplayPoseMode? = null
    private var lastRawFix: DisplayPoseSmoother.RawFix? = null

    fun pushRawFix(fix: DisplayPoseSmoother.RawFix) {
        lastRawFix = fix
        displaySmoother.pushRawFix(fix)
    }

    fun resetSmoother() {
        displaySmoother.reset()
    }

    fun clear() {
        lastRawFix = null
        displaySmoother.reset()
    }

    fun selectPose(
        nowMs: Long,
        mode: DisplayPoseMode,
        smoothingProfile: DisplaySmoothingProfile
    ): DisplayPoseSmoother.DisplayPose? {
        if (smoothingProfile != lastSmoothingProfile) {
            displaySmoother = buildDisplaySmoother(smoothingProfile)
            displaySmoother.reset()
            lastSmoothingProfile = smoothingProfile
        }
        if (mode != lastDisplayPoseMode) {
            displaySmoother.reset()
            lastDisplayPoseMode = mode
        }
        val smoothedPose = displaySmoother.tick(nowMs)
        val rawPose = lastRawFix?.let { buildRawPose(it, nowMs) }
        return DisplayPoseSelector.selectPose(mode, rawPose, smoothedPose)
    }

    private fun buildRawPose(
        fix: DisplayPoseSmoother.RawFix,
        nowMs: Long
    ): DisplayPoseSmoother.DisplayPose {
        return DisplayPoseSmoother.DisplayPose(
            location = LatLng(fix.latitude, fix.longitude),
            trackDeg = fix.trackDeg,
            headingDeg = fix.headingDeg,
            orientationMode = fix.orientationMode,
            accuracyM = fix.accuracyM,
            bearingAccuracyDeg = fix.bearingAccuracyDeg,
            speedAccuracyMs = fix.speedAccuracyMs,
            speedMs = fix.speedMs,
            updatedAtMs = nowMs
        )
    }

    private fun buildDisplaySmoother(profile: DisplaySmoothingProfile): DisplayPoseSmoother {
        val minSpeedMs = minSpeedProvider()
        return DisplayPoseSmoother(
            minSpeedForHeadingMs = minSpeedMs,
            minSpeedForPredictionMs = minSpeedMs,
            config = profile.config,
            adaptiveSmoothingEnabled = adaptiveSmoothingEnabled
        )
    }
}

