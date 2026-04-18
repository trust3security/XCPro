package com.trust3.xcpro.orientation

import android.util.Log
import com.trust3.xcpro.common.orientation.BearingSource
import com.trust3.xcpro.common.orientation.OrientationSensorData
import java.util.Locale
import kotlin.math.abs

internal class HeadingJitterLogger(
    private val snapshotProvider: (Long) -> OrientationHeadingDebugSnapshot?
) {
    private var lastHeadingSampleTime = 0L
    private var lastHeadingSampleBearing = 0.0
    private var lastJitterLogTime = 0L

    fun logIfNeeded(
        now: Long,
        bearing: Double,
        finalSource: BearingSource,
        finalValid: Boolean,
        sensorData: OrientationSensorData
    ) {
        if (lastHeadingSampleTime == 0L) {
            lastHeadingSampleTime = now
            lastHeadingSampleBearing = bearing
            return
        }

        val dt = now - lastHeadingSampleTime
        val delta = abs(shortestDeltaDegrees(lastHeadingSampleBearing, bearing))
        val dps = if (dt > 0L) (delta * 1000.0 / dt.toDouble()) else 0.0

        val isJitter = (delta >= JITTER_DELTA_DEG && dt <= JITTER_WINDOW_MS) ||
            dps >= JITTER_DEG_PER_SEC

        if (isJitter && now - lastJitterLogTime >= JITTER_LOG_COOLDOWN_MS) {
            val snapshot = snapshotProvider(now)
            if (snapshot != null) {
                val deltaText = String.format(Locale.US, "%.1f", delta)
                val dpsText = String.format(Locale.US, "%.1f", dps)
                val bearingText = String.format(Locale.US, "%.1f", bearing)
                val compassText = snapshot.compassHeading?.let { String.format(Locale.US, "%.1f", it) } ?: "na"
                val attText = snapshot.attitudeHeading?.let { String.format(Locale.US, "%.1f", it) } ?: "na"
                val activeText = snapshot.activeHeading?.let { String.format(Locale.US, "%.1f", it) } ?: "na"
                val trackText = String.format(Locale.US, "%.1f", sensorData.track)
                val gsText = String.format(Locale.US, "%.2f", sensorData.groundSpeed)
                val windSpdText = String.format(Locale.US, "%.2f", sensorData.windSpeed)
                val windFromText = String.format(Locale.US, "%.1f", sensorData.windDirectionFrom)
                Log.w(
                    JITTER_TAG,
                    "HU_JITTER delta=${deltaText}deg dt=${dt}ms dps=${dpsText} " +
                        "bearing=${bearingText} src=$finalSource valid=$finalValid " +
                        "input=${snapshot.inputSource} active=${snapshot.activeSource} activeHead=${activeText} " +
                        "compass=${compassText} " +
                        "compassAge=${snapshot.compassAgeMs ?: -1}ms compRel=${snapshot.compassReliable} " +
                        "att=${attText} " +
                        "attAge=${snapshot.attitudeAgeMs ?: -1}ms attRel=${snapshot.attitudeReliable} " +
                        "track=${trackText} gs=${gsText} " +
                        "windSpd=${windSpdText} windFrom=${windFromText} " +
                        "headSrc=${sensorData.headingSolution.source} headValid=${sensorData.headingSolution.isValid}"
                )
                lastJitterLogTime = now
            }
        }

        lastHeadingSampleTime = now
        lastHeadingSampleBearing = bearing
    }

    private companion object {
        private const val JITTER_TAG = "JITTER"
        private const val JITTER_DELTA_DEG = 10.0
        private const val JITTER_WINDOW_MS = 500L
        private const val JITTER_DEG_PER_SEC = 30.0
        private const val JITTER_LOG_COOLDOWN_MS = 1000L
    }
}
