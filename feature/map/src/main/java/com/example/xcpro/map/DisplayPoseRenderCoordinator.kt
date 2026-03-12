package com.example.xcpro.map

import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.replay.ReplayDisplayPose
import org.maplibre.android.geometry.LatLng

internal class DisplayPoseRenderCoordinator(
    private val mapState: MapScreenState,
    private val mapStateReader: MapStateReader,
    private val featureFlags: MapFeatureFlags,
    private val poseCoordinator: DisplayPoseCoordinator,
    private val replayHeadingProvider: ((Long) -> Double?)?,
    private val replayFixProvider: ((Long) -> ReplayDisplayPose?)?,
    private val trackingCameraController: MapTrackingCameraController,
    private val positionController: MapPositionController,
    private val frameLogger: DisplayPoseFrameLogger
) {
    private var latestOrientation: OrientationData = OrientationData()
    @Volatile private var lastDisplayPoseLocation: LatLng? = null
    @Volatile private var lastDisplayPoseTimestampMs: Long = 0L
    @Volatile private var lastDisplayPoseFrameId: Long = 0L
    @Volatile private var displayPoseFrameListener: ((DisplayPoseSnapshot) -> Unit)? = null
    private var displayFrameCounter: Long = 0L

    fun updateOrientation(orientation: OrientationData) {
        latestOrientation = orientation
    }

    fun getDisplayPoseLocation(): LatLng? = lastDisplayPoseLocation

    fun getDisplayPoseTimestampMs(): Long? =
        lastDisplayPoseTimestampMs.takeIf { it > 0L }

    fun getDisplayPoseSnapshot(): DisplayPoseSnapshot? {
        val location = lastDisplayPoseLocation ?: return null
        val timestamp = lastDisplayPoseTimestampMs
        if (timestamp <= 0L) return null
        val frameId = lastDisplayPoseFrameId
        if (frameId <= 0L) return null
        return DisplayPoseSnapshot(location, timestamp, frameId)
    }

    fun setDisplayPoseFrameListener(listener: ((DisplayPoseSnapshot) -> Unit)?) {
        displayPoseFrameListener = listener
    }

    fun renderDisplayFrame(onInitialCenteredZoom: (location: LatLng, zoom: Double) -> Unit) {
        val nowMs = poseCoordinator.nowMs()
        val mode = mapStateReader.displayPoseMode.value
        val smoothingProfile = mapStateReader.displaySmoothingProfile.value
        val pose = poseCoordinator.selectPose(nowMs, mode, smoothingProfile) ?: return
        val map = mapState.mapLibreMap ?: return
        val orientation = latestOrientation
        val forceTrackHeading = featureFlags.forceReplayTrackHeading &&
            poseCoordinator.timeBase == DisplayClock.TimeBase.REPLAY
        val runtimeFix = if (forceTrackHeading && featureFlags.useRuntimeReplayHeading) {
            replayFixProvider?.invoke(nowMs)
        } else {
            null
        }
        val runtimeBearing = runtimeFix?.bearingDeg
            ?: if (forceTrackHeading && featureFlags.useRuntimeReplayHeading) {
                replayHeadingProvider?.invoke(nowMs)
            } else {
                null
            }
        val poseLocation = runtimeFix?.let { fix ->
            LatLng(fix.latitude, fix.longitude)
        } ?: pose.location
        val poseTimestampMs = runtimeFix?.timestampMillis ?: pose.updatedAtMs
        val headingDeg = when {
            runtimeBearing != null -> runtimeBearing
            forceTrackHeading -> pose.trackDeg
            else -> orientation.headingDeg
        }
        val headingValid = runtimeBearing != null || forceTrackHeading || orientation.headingValid
        val trackDeg = runtimeBearing ?: pose.trackDeg
        val speedMs = runtimeFix?.speedMs ?: pose.speedMs
        val cameraTargetBearing = if (runtimeBearing != null &&
            orientation.mode != MapOrientationMode.NORTH_UP
        ) {
            runtimeBearing
        } else {
            orientation.bearing
        }

        displayFrameCounter += 1
        lastDisplayPoseFrameId = displayFrameCounter
        lastDisplayPoseLocation = poseLocation
        lastDisplayPoseTimestampMs = poseTimestampMs
        if (featureFlags.useRenderFrameSync) {
            frameLogger.logIfDue(
                frameId = lastDisplayPoseFrameId,
                poseTimestampMs = poseTimestampMs,
                location = poseLocation,
                trackDeg = trackDeg,
                headingDeg = headingDeg,
                cameraTargetBearing = cameraTargetBearing
            )
        }
        if (featureFlags.useRenderFrameSync) {
            displayPoseFrameListener?.invoke(
                DisplayPoseSnapshot(
                    location = poseLocation,
                    timestampMs = poseTimestampMs,
                    frameId = lastDisplayPoseFrameId
                )
            )
        }

        val trackingResult = trackingCameraController.updateCamera(
            MapTrackingCameraController.FrameInput(
                location = poseLocation,
                trackDeg = trackDeg,
                cameraTargetBearing = cameraTargetBearing,
                speedMs = speedMs,
                orientationMode = orientation.mode,
                timeBase = poseCoordinator.timeBase,
                nowMs = nowMs
            )
        )
        trackingResult?.initialCenteredZoom?.let { zoom ->
            onInitialCenteredZoom(poseLocation, zoom)
        }

        val cameraBearing = trackingResult?.cameraBearing
            ?: map.cameraPosition?.bearing
            ?: orientation.bearing
        val overlayBearing = if (cameraBearing.isFinite()) cameraBearing else orientation.bearing
        positionController.updateOverlay(
            location = poseLocation,
            trackBearing = trackDeg,
            headingDeg = headingDeg,
            headingValid = headingValid,
            bearingAccuracyDeg = pose.bearingAccuracyDeg,
            speedAccuracyMs = pose.speedAccuracyMs,
            mapBearing = overlayBearing,
            orientationMode = orientation.mode,
            speedMs = speedMs,
            nowMs = nowMs,
            frameId = lastDisplayPoseFrameId
        )
    }
}
