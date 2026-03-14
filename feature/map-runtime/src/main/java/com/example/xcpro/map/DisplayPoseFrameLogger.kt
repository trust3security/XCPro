package com.example.xcpro.map

import android.util.Log
import com.example.xcpro.core.time.TimeBridge
import com.example.xcpro.map.config.MapFeatureFlags
import org.maplibre.android.geometry.LatLng

class DisplayPoseFrameLogger(
    private val tag: String,
    private val defaultIntervalMs: Long,
    private val timeBaseProvider: () -> DisplayClock.TimeBase?,
    private val featureFlags: MapFeatureFlags,
    private val nowElapsedMs: () -> Long = { TimeBridge.nowMonoMs() },
    private val intervalProvider: () -> Long = { featureFlags.sim2FrameLogIntervalMs }
) {
    private var lastFrameLogMs: Long = 0L

    fun logIfDue(
        frameId: Long,
        poseTimestampMs: Long,
        location: LatLng,
        trackDeg: Double,
        headingDeg: Double,
        cameraTargetBearing: Double
    ) {
        val interval = intervalProvider().takeIf { it >= 0L } ?: defaultIntervalMs
        val nowElapsed = nowElapsedMs()
        if (interval > 0L && nowElapsed - lastFrameLogMs < interval) return

        lastFrameLogMs = nowElapsed
        Log.d(
            tag,
            "framePose frame=$frameId " +
                "t=$poseTimestampMs " +
                "lat=${"%.6f".format(location.latitude)} " +
                "lon=${"%.6f".format(location.longitude)} " +
                "track=${"%.1f".format(trackDeg)} " +
                "heading=${"%.1f".format(headingDeg)} " +
                "camera=${"%.1f".format(cameraTargetBearing)} " +
                "timeBase=${timeBaseProvider() ?: "NONE"}"
        )
    }
}
