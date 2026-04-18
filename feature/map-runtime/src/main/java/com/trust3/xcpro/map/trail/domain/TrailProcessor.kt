package com.trust3.xcpro.map.trail.domain

import com.trust3.xcpro.map.trail.TrailGeo
import com.trust3.xcpro.map.trail.TrailGeoPoint
import com.trust3.xcpro.map.trail.TrailSample
import com.trust3.xcpro.map.trail.TrailStore
import com.trust3.xcpro.sensors.domain.FlightMetricsConstants
import com.trust3.xcpro.sensors.domain.LiveWindValidityPolicy
import com.trust3.xcpro.weather.wind.model.WindState
import kotlin.math.abs

/**
 * Owns trail point storage and transforms sensor data into renderable trail state.
 */
class TrailProcessor {
    private val varioResolver = ResolveTrailVarioUseCase()
    private val circlingResolver = ResolveCirclingUseCase()
    private val replayInterpolator = ReplayTrailInterpolator()
    private val replayWindSmoother = TrailWindSmoother(
        tauMs = REPLAY_WIND_SMOOTH_MS,
        minValidSpeedMs = REPLAY_WIND_VALID_MIN_SPEED_MS
    )
    private val liveWindSmoother = TrailWindSmoother(
        tauMs = LIVE_WIND_SMOOTH_MS,
        minValidSpeedMs = LIVE_WIND_VALID_MIN_SPEED_MS
    )
    private val liveStore = TrailStore(minDeltaMillis = LIVE_MIN_DELTA_MS)
    private val replayStore = TrailStore(minDeltaMillis = 0L)
    private val syntheticReplayStore = TrailStore(
        maxSize = SYNTHETIC_REPLAY_MAX_SIZE,
        minDeltaMillis = 0L,
        noThinMillis = SYNTHETIC_REPLAY_NO_THIN_MS
    )

    private var lastIsReplay: Boolean? = null
    private var lastLiveTimeBase: TrailTimeBase? = null
    private var lastReplayRetentionMode: TrailReplayRetentionMode? = null
    private var lastRenderCircling: Boolean? = null
    private var lastRenderableWind: TrailWindSample? = null

    fun resetAll() {
        liveStore.clear()
        replayStore.clear()
        syntheticReplayStore.clear()
        replayInterpolator.reset()
        replayWindSmoother.reset()
        liveWindSmoother.reset()
        circlingResolver.reset()
        lastIsReplay = null
        lastLiveTimeBase = null
        lastReplayRetentionMode = null
        lastRenderCircling = null
        lastRenderableWind = null
    }

    fun update(input: TrailUpdateInput): TrailUpdateResult? {
        var modeChanged = false
        if (lastIsReplay != null && lastIsReplay != input.isReplay) {
            resetForModeChange()
            modeChanged = true
        }
        lastIsReplay = input.isReplay

        val gps = input.data.gps ?: return null
        val lat = gps.position.latitude
        val lon = gps.position.longitude
        if (!TrailGeo.isValidCoordinate(lat, lon)) {
            return null
        }

        val sampleTime = resolveSampleTime(input)
        val timestamp = sampleTime.timestampMillis
        val altitude = if (input.data.baroAltitude.value.isFinite()) {
            input.data.baroAltitude.value
        } else {
            gps?.altitude?.value ?: 0.0
        }
        val vario = varioResolver.resolve(input.data, input.isReplay)
        val isCircling = circlingResolver.resolve(input.data, input.isReplay)
        val baseWind = if (input.isReplay) {
            resolveReplayWindSample(input.windState)
        } else {
            resolveLiveWindSample(
                windState = input.windState,
                airspeedSourceLabel = input.data.airspeedSource
            )
        }
        val wind = if (input.isReplay) {
            replayWindSmoother.update(
                speedMs = baseWind.speedMs,
                directionFromDeg = baseWind.directionFromDeg,
                timestampMs = timestamp
            )
        } else {
            liveWindSmoother.update(
                speedMs = baseWind.speedMs,
                directionFromDeg = baseWind.directionFromDeg,
                timestampMs = timestamp
            )
        }

        var storeReset = false
        if (!input.isReplay) {
            val liveTimeBaseChanged = lastLiveTimeBase != null && lastLiveTimeBase != sampleTime.timeBase
            if (liveTimeBaseChanged) {
                liveStore.clear()
                liveWindSmoother.reset()
                storeReset = true
            }
            lastLiveTimeBase = sampleTime.timeBase
            lastReplayRetentionMode = null
        } else {
            lastLiveTimeBase = null
            val replayRetentionChanged = lastReplayRetentionMode != null &&
                lastReplayRetentionMode != input.replayRetentionMode
            if (replayRetentionChanged) {
                clearReplayState()
                storeReset = true
            }
            lastReplayRetentionMode = input.replayRetentionMode
        }
        val store = when {
            input.isReplay && input.replayRetentionMode == TrailReplayRetentionMode.SYNTHETIC_VALIDATION ->
                syntheticReplayStore
            input.isReplay -> replayStore
            else -> liveStore
        }
        var sampleAdded = false
        if ((input.isFlying || input.isReplay) && timestamp > 0L) {
            val sample = TrailSample(
                latitude = lat,
                longitude = lon,
                timestampMillis = timestamp,
                altitudeMeters = altitude,
                varioMs = vario,
                windSpeedMs = wind.speedMs,
                windDirectionFromDeg = wind.directionFromDeg
            )
            if (input.isReplay) {
                if (replayInterpolator.shouldReset(sample)) {
                    clearReplayState()
                    storeReset = true
                }
                val expanded = replayInterpolator.expand(sample)
                for (entry in expanded) {
                    if (store.addSample(entry)) {
                        sampleAdded = true
                    }
                }
            } else {
                sampleAdded = store.addSample(
                    sample = sample,
                    minDeltaMillisOverride = resolveLiveMinDeltaMillis(isCircling)
                )
            }
        }

        val circlingChanged = lastRenderCircling?.let { it != isCircling } ?: false
        val windChanged = hasMeaningfulWindChange(
            previous = lastRenderableWind,
            current = wind,
            windDriftEnabled = input.windDriftEnabled,
            isCircling = isCircling
        )
        val invalidationReason = when {
            modeChanged -> TrailRenderInvalidationReason.MODE_CHANGED
            storeReset -> TrailRenderInvalidationReason.STORE_RESET
            sampleAdded -> TrailRenderInvalidationReason.SAMPLE_ADDED
            circlingChanged -> TrailRenderInvalidationReason.CIRCLING_CHANGED
            windChanged -> TrailRenderInvalidationReason.WIND_CHANGED
            else -> null
        }
        val requiresFullRender = invalidationReason != null
        lastRenderCircling = isCircling
        lastRenderableWind = wind

        val renderState = TrailRenderState(
            points = store.snapshot(),
            currentLocation = TrailGeoPoint(lat, lon),
            currentTimeMillis = timestamp,
            windSpeedMs = wind.speedMs,
            windDirectionFromDeg = wind.directionFromDeg,
            isCircling = isCircling,
            isReplay = input.isReplay,
            timeBase = sampleTime.timeBase
        )
        return TrailUpdateResult(
            renderState = renderState,
            sampleAdded = sampleAdded,
            storeReset = storeReset,
            modeChanged = modeChanged,
            requiresFullRender = requiresFullRender,
            invalidationReason = invalidationReason
        )
    }

    private fun resetForModeChange() {
        liveStore.clear()
        clearReplayState()
        liveWindSmoother.reset()
        circlingResolver.reset()
        lastLiveTimeBase = null
        lastReplayRetentionMode = null
        lastRenderCircling = null
        lastRenderableWind = null
    }

    private fun clearReplayState() {
        replayStore.clear()
        syntheticReplayStore.clear()
        replayInterpolator.reset()
        replayWindSmoother.reset()
    }

    private fun resolveSampleTime(input: TrailUpdateInput): SampleTime {
        if (input.isReplay) {
            return SampleTime(
                timestampMillis = input.data.timestamp,
                timeBase = TrailTimeBase.REPLAY_IGC
            )
        }

        val gpsMonotonic = input.data.gps?.monotonicTimestampMillis ?: 0L
        return if (gpsMonotonic > 0L) {
            SampleTime(
                timestampMillis = gpsMonotonic,
                timeBase = TrailTimeBase.LIVE_MONOTONIC
            )
        } else {
            SampleTime(
                timestampMillis = input.data.timestamp,
                timeBase = TrailTimeBase.LIVE_WALL
            )
        }
    }

    private fun resolveReplayWindSample(windState: WindState?): TrailWindSample {
        val vector = windState?.vector
        val hasWind = vector != null &&
            windState.quality > 0 &&
            !windState.stale &&
            vector.speed > WIND_VALID_MIN_SPEED_MS
        if (!hasWind) {
            return TrailWindSample(0.0, 0.0)
        }
        val directionFrom = ((vector!!.directionFromDeg % 360.0) + 360.0) % 360.0
        return TrailWindSample(vector.speed, directionFrom)
    }

    private fun resolveLiveWindSample(
        windState: WindState?,
        airspeedSourceLabel: String
    ): TrailWindSample {
        val hasWind = LiveWindValidityPolicy.isLiveWindUsable(
            windState = windState,
            airspeedSourceLabel = airspeedSourceLabel
        )
        val vector = windState?.vector
        if (!hasWind || vector == null) {
            return TrailWindSample(0.0, 0.0)
        }
        val directionFrom = ((vector.directionFromDeg % 360.0) + 360.0) % 360.0
        return TrailWindSample(vector.speed, directionFrom)
    }

    private fun resolveLiveMinDeltaMillis(isCircling: Boolean): Long =
        if (isCircling) LIVE_CIRCLING_MIN_DELTA_MS else LIVE_MIN_DELTA_MS

    private fun hasMeaningfulWindChange(
        previous: TrailWindSample?,
        current: TrailWindSample,
        windDriftEnabled: Boolean,
        isCircling: Boolean
    ): Boolean {
        if (!windDriftEnabled || !isCircling) {
            return false
        }
        val last = previous ?: return false
        val lastValid = isRenderableWind(last)
        val currentValid = isRenderableWind(current)
        if (lastValid != currentValid) {
            return true
        }
        if (!currentValid) {
            return false
        }
        if (abs(last.speedMs - current.speedMs) >= WIND_SPEED_CHANGE_EPS_MS) {
            return true
        }
        return angularDistanceDeg(last.directionFromDeg, current.directionFromDeg) >= WIND_DIRECTION_CHANGE_EPS_DEG
    }

    private fun isRenderableWind(sample: TrailWindSample): Boolean =
        sample.speedMs > WIND_VALID_MIN_SPEED_MS && sample.directionFromDeg.isFinite()

    private fun angularDistanceDeg(first: Double, second: Double): Double {
        val normalized = ((second - first + 540.0) % 360.0) - 180.0
        return abs(normalized)
    }

    private data class SampleTime(
        val timestampMillis: Long,
        val timeBase: TrailTimeBase
    )

    private companion object {
        private const val LIVE_MIN_DELTA_MS = 2_000L
        private const val LIVE_CIRCLING_MIN_DELTA_MS = 500L
        private const val LIVE_WIND_SMOOTH_MS = 1_000L
        private const val REPLAY_WIND_SMOOTH_MS = 4_000L
        private const val SYNTHETIC_REPLAY_MAX_SIZE = 8_192
        private const val SYNTHETIC_REPLAY_NO_THIN_MS = 12 * 60_000L
        private const val LIVE_WIND_VALID_MIN_SPEED_MS = FlightMetricsConstants.LIVE_WIND_VALID_MIN_SPEED_MS
        private const val REPLAY_WIND_VALID_MIN_SPEED_MS = FlightMetricsConstants.LIVE_WIND_VALID_MIN_SPEED_MS
        private const val WIND_VALID_MIN_SPEED_MS = FlightMetricsConstants.LIVE_WIND_VALID_MIN_SPEED_MS
        private const val WIND_SPEED_CHANGE_EPS_MS = 0.25
        private const val WIND_DIRECTION_CHANGE_EPS_DEG = 5.0
    }
}

