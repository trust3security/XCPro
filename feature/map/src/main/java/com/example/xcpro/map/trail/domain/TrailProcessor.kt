package com.example.xcpro.map.trail.domain

import com.example.xcpro.map.trail.TrailGeo
import com.example.xcpro.map.trail.TrailGeoPoint
import com.example.xcpro.map.trail.TrailSample
import com.example.xcpro.map.trail.TrailStore
import com.example.xcpro.weather.wind.model.WindState

/**
 * Owns trail point storage and transforms sensor data into renderable trail state.
 */
internal class TrailProcessor(
    private val varioResolver: ResolveTrailVarioUseCase = ResolveTrailVarioUseCase(),
    private val circlingResolver: ResolveCirclingUseCase = ResolveCirclingUseCase(),
    private val replayInterpolator: ReplayTrailInterpolator = ReplayTrailInterpolator(),
    private val replayWindSmoother: TrailWindSmoother = TrailWindSmoother(
        tauMs = REPLAY_WIND_SMOOTH_MS,
        minValidSpeedMs = REPLAY_WIND_VALID_MIN_SPEED_MS
    ),
    private val liveStore: TrailStore = TrailStore(minDeltaMillis = LIVE_MIN_DELTA_MS),
    private val replayStore: TrailStore = TrailStore(minDeltaMillis = 0L)
) {
    private var lastIsReplay: Boolean? = null

    fun resetAll() {
        liveStore.clear()
        replayStore.clear()
        replayInterpolator.reset()
        replayWindSmoother.reset()
        circlingResolver.reset()
        lastIsReplay = null
    }

    fun update(input: TrailUpdateInput): TrailUpdateResult? {
        var modeChanged = false
        if (lastIsReplay != null && lastIsReplay != input.isReplay) {
            resetForModeChange()
            modeChanged = true
        }
        lastIsReplay = input.isReplay

        val gps = input.data.gps
        val lat = gps?.position?.latitude ?: 0.0
        val lon = gps?.position?.longitude ?: 0.0
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
        val baseWind = resolveWindSample(input.windState)
        val wind = if (input.isReplay) {
            replayWindSmoother.update(
                speedMs = baseWind.speedMs,
                directionFromDeg = baseWind.directionFromDeg,
                timestampMs = timestamp
            )
        } else {
            baseWind
        }

        val store = if (input.isReplay) replayStore else liveStore
        var storeReset = false
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
                    replayStore.clear()
                    replayInterpolator.reset()
                    replayWindSmoother.reset()
                    storeReset = true
                }
                val expanded = replayInterpolator.expand(sample)
                for (entry in expanded) {
                    if (store.addSample(entry)) {
                        sampleAdded = true
                    }
                }
            } else {
                sampleAdded = store.addSample(sample)
            }
        }

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
            modeChanged = modeChanged
        )
    }

    private fun resetForModeChange() {
        liveStore.clear()
        replayStore.clear()
        replayInterpolator.reset()
        replayWindSmoother.reset()
        circlingResolver.reset()
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

    private fun resolveWindSample(windState: WindState?): TrailWindSample {
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

    private data class SampleTime(
        val timestampMillis: Long,
        val timeBase: TrailTimeBase
    )

    private companion object {
        private const val LIVE_MIN_DELTA_MS = 2_000L
        private const val REPLAY_WIND_SMOOTH_MS = 4_000L
        private const val REPLAY_WIND_VALID_MIN_SPEED_MS = 0.5
        private const val WIND_VALID_MIN_SPEED_MS = 0.5
    }
}

