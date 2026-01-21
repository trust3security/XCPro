// Role: Coordinate trail storage and rendering based on live/replay data.
// Invariants: Rendering occurs only when a map style is ready.
package com.example.xcpro.map.trail

import android.content.Context
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.sensors.CirclingDetector
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

class SnailTrailManager(
    private val context: Context,
    private val mapState: MapScreenState
) {
    private val liveStore = TrailStore(minDeltaMillis = 2_000L)
    private val replayStore = TrailStore(minDeltaMillis = 0L)
    private val replayCirclingDetector = CirclingDetector()
    private var lastReplayTimestamp: Long = Long.MIN_VALUE
    private var lastSettings: TrailSettings = TrailSettings()
    private var lastContext: RenderContext? = null
    private var lastIsReplay: Boolean? = null
    private var lastReplaySample: TrailSample? = null
    private var lastReplayTimestampAdjusted: Long? = null
    private var lastReplayStoreTimestamp: Long? = null
    private val replaySampleStepMillis = 250L
    private var lastRenderPoseTimeMs: Long? = null
    private var lastRenderPoseLocation: LatLng? = null
    private var lastRawTailPoint: TrailPoint? = null
    private var lastRenderPoseFrameId: Long? = null
    private val replayWindSmoother = WindSmoother(
        tauMs = REPLAY_WIND_SMOOTH_MS,
        minValidSpeedMs = REPLAY_WIND_VALID_MIN_SPEED_MS
    )

    fun initialize(map: MapLibreMap) {
        val overlay = SnailTrailOverlay(context, map, mapState.mapView)
        overlay.initialize()
        mapState.snailTrailOverlay = overlay
        mapState.blueLocationOverlay?.bringToFront()
        renderLast()
    }

    fun onMapStyleChanged(map: MapLibreMap?) {
        mapState.snailTrailOverlay?.cleanup()
        mapState.snailTrailOverlay = null
        map?.let { initialize(it) }
        renderLast()
    }

    fun updateFromFlightData(
        liveData: RealTimeFlightData?,
        isFlying: Boolean,
        isReplay: Boolean,
        settings: TrailSettings,
        currentZoom: Float,
        displayLocation: LatLng? = null,
        displayTimeMillis: Long? = null
    ) {
        val overlay = mapState.snailTrailOverlay ?: return
        if (liveData == null) {
            overlay.clear()
            lastContext = null
            lastRenderPoseTimeMs = null
            lastRenderPoseLocation = null
            lastRawTailPoint = null
            return
        }

        if (lastIsReplay != null && lastIsReplay != isReplay) {
            liveStore.clear()
            replayStore.clear()
            overlay.clear()
            lastContext = null
            lastReplaySample = null
            lastReplayTimestampAdjusted = null
            replayWindSmoother.reset()
            lastRenderPoseTimeMs = null
            lastRenderPoseLocation = null
            lastRawTailPoint = null
        }
        lastIsReplay = isReplay

        val settingsChanged = settings != lastSettings
        lastSettings = settings

        val lat = liveData.latitude
        val lon = liveData.longitude
        if (!TrailGeo.isValidCoordinate(lat, lon)) {
            overlay.clear()
            return
        }

        val timestamp = liveData.timestamp
        val renderLocation = displayLocation?.takeIf {
            TrailGeo.isValidCoordinate(it.latitude, it.longitude)
        } ?: LatLng(lat, lon)
        val renderTime = displayTimeMillis?.takeIf { it > 0L } ?: timestamp
        val altitude = if (liveData.baroAltitude.isFinite()) {
            liveData.baroAltitude
        } else {
            liveData.gpsAltitude
        }
        val vario = resolveVario(liveData, isReplay)
        val isCircling = resolveCircling(liveData, isReplay)
        val store = activeStore(isReplay)
        val zoomChanged = lastContext?.currentZoom?.let { it != currentZoom } ?: false
        var sampleAdded = false

        val windSample = if (isReplay) {
            replayWindSmoother.update(
                speedMs = liveData.windSpeed.toDouble(),
                directionFromDeg = liveData.windDirection.toDouble(),
                timestampMs = timestamp
            )
        } else {
            WindSample(
                speedMs = liveData.windSpeed.toDouble(),
                directionFromDeg = liveData.windDirection.toDouble()
            )
        }

        if ((isFlying || isReplay) && timestamp > 0L) {
            val sample = TrailSample(
                latitude = lat,
                longitude = lon,
                timestampMillis = timestamp,
                altitudeMeters = altitude,
                varioMs = vario,
                windSpeedMs = windSample.speedMs,
                windDirectionFromDeg = windSample.directionFromDeg
            )
            if (isReplay) {
                if (shouldResetReplayStore(sample)) {
                    resetReplayStore(overlay)
                }
                lastReplayStoreTimestamp = sample.timestampMillis
            }
            sampleAdded = if (isReplay) {
                addReplaySample(sample, store)
            } else {
                store.addSample(sample)
            }
        }

        lastContext = RenderContext(
            currentLocation = renderLocation,
            currentTimeMillis = renderTime,
            windSpeedMs = windSample.speedMs,
            windDirectionFromDeg = windSample.directionFromDeg,
            isCircling = isCircling,
            currentZoom = currentZoom
        )

        if (sampleAdded || settingsChanged || zoomChanged) {
            render(overlay)
        }
    }

    fun updateDisplayPose(
        displayLocation: LatLng?,
        displayTimeMillis: Long?,
        frameId: Long? = null
    ) {
        if (lastIsReplay != true) return
        val overlay = mapState.snailTrailOverlay ?: return
        val context = lastContext ?: return
        val location = displayLocation
            ?.takeIf { TrailGeo.isValidCoordinate(it.latitude, it.longitude) }
            ?: return
        val time = displayTimeMillis?.takeIf { it > 0L } ?: context.currentTimeMillis
        if (time <= 0L) return
        if (time < context.currentTimeMillis) return
        if (frameId != null && lastRenderPoseFrameId == frameId) return

        val prevLocation = lastRenderPoseLocation
        val minStepMs = if (MapFeatureFlags.useRenderFrameSync && lastIsReplay == true) {
            0L
        } else {
            DISPLAY_RENDER_MIN_STEP_MS
        }
        val minDistanceM = if (MapFeatureFlags.useRenderFrameSync && lastIsReplay == true) {
            0.0
        } else {
            DISPLAY_RENDER_MIN_DISTANCE_M
        }
        val movedEnough = if (prevLocation == null) {
            true
        } else {
            TrailGeo.distanceMeters(
                prevLocation.latitude,
                prevLocation.longitude,
                location.latitude,
                location.longitude
            ) >= minDistanceM
        }
        val prevTime = lastRenderPoseTimeMs ?: 0L
        val dt = time - prevTime
        if (dt < minStepMs && !movedEnough) return

        lastRenderPoseTimeMs = time
        lastRenderPoseLocation = location
        if (frameId != null) {
            lastRenderPoseFrameId = frameId
        }
        lastContext = context.copy(
            currentLocation = location,
            currentTimeMillis = time
        )
        overlay.renderTail(
            lastPoint = lastRawTailPoint,
            settings = lastSettings,
            currentLocation = location,
            currentTimeMillis = time,
            windSpeedMs = context.windSpeedMs,
            windDirectionFromDeg = context.windDirectionFromDeg,
            isCircling = context.isCircling,
            currentZoom = context.currentZoom,
            isReplay = lastIsReplay ?: false,
            frameId = frameId
        )
    }

    fun onSettingsChanged(settings: TrailSettings) {
        lastSettings = settings
        renderLast()
    }

    fun onZoomChanged(currentZoom: Float) {
        val existing = lastContext ?: return
        lastContext = existing.copy(currentZoom = currentZoom)
        renderLast()
    }

    private fun renderLast() {
        val overlay = mapState.snailTrailOverlay ?: return
        render(overlay)
    }

    private fun render(overlay: SnailTrailOverlay, frameId: Long? = null) {
        val context = lastContext ?: return
        val isReplay = lastIsReplay ?: false
        val store = activeStore(isReplay)
        val allPoints = store.snapshot()
        val renderPoints = if (context.currentTimeMillis > 0L) {
            val firstTimestamp = allPoints.firstOrNull()?.timestampMillis
            if (firstTimestamp != null && context.currentTimeMillis >= firstTimestamp) {
                val filtered = allPoints.filter { it.timestampMillis <= context.currentTimeMillis }
                if (filtered.isNotEmpty()) filtered else allPoints
            } else {
                allPoints
            }
        } else {
            allPoints
        }
        lastRawTailPoint = renderPoints.lastOrNull()
        overlay.render(
            points = renderPoints,
            settings = lastSettings,
            currentLocation = context.currentLocation,
            currentTimeMillis = context.currentTimeMillis,
            windSpeedMs = context.windSpeedMs,
            windDirectionFromDeg = context.windDirectionFromDeg,
            isCircling = context.isCircling,
            currentZoom = context.currentZoom,
            isReplay = isReplay,
            frameId = frameId
        )
    }

    private fun activeStore(isReplay: Boolean): TrailStore = if (isReplay) replayStore else liveStore

    private fun addReplaySample(sample: TrailSample, store: TrailStore): Boolean {
        val adjusted = adjustReplaySample(sample)
        val previous = lastReplaySample
        var added = false
        if (previous != null && adjusted.timestampMillis > previous.timestampMillis) {
            val dt = adjusted.timestampMillis - previous.timestampMillis
            val distance = TrailGeo.distanceMeters(
                previous.latitude,
                previous.longitude,
                adjusted.latitude,
                adjusted.longitude
            )
            if (distance > 0.5) {
                val steps = (dt / replaySampleStepMillis).coerceAtLeast(1L).toInt()
                if (steps > 1) {
                    val total = steps.toFloat()
                    for (i in 1 until steps) {
                        val t = i / total
                        val ts = previous.timestampMillis + (dt * i / steps)
                        val intermediate = TrailSample(
                            latitude = lerp(previous.latitude, adjusted.latitude, t),
                            longitude = lerp(previous.longitude, adjusted.longitude, t),
                            timestampMillis = ts,
                            altitudeMeters = lerp(previous.altitudeMeters, adjusted.altitudeMeters, t),
                            varioMs = lerp(previous.varioMs, adjusted.varioMs, t),
                            windSpeedMs = lerp(previous.windSpeedMs, adjusted.windSpeedMs, t),
                            windDirectionFromDeg = lerpAngleDeg(
                                previous.windDirectionFromDeg,
                                adjusted.windDirectionFromDeg,
                                t
                            )
                        )
                        if (store.addSample(intermediate)) {
                            added = true
                        }
                    }
                }
            }
        }
        if (store.addSample(adjusted)) {
            added = true
        }
        lastReplaySample = adjusted
        return added
    }

    private fun shouldResetReplayStore(sample: TrailSample): Boolean {
        val last = lastReplaySample ?: return false
        val backstep = last.timestampMillis - sample.timestampMillis
        if (backstep > REPLAY_RESET_TIME_BACKSTEP_MS) return true
        val lastTimestamp = lastReplayStoreTimestamp
        if (lastTimestamp != null) {
            val rawBackstep = lastTimestamp - sample.timestampMillis
            if (rawBackstep > REPLAY_RESET_TIME_BACKSTEP_MS) return true
        }
        val distance = TrailGeo.distanceMeters(
            last.latitude,
            last.longitude,
            sample.latitude,
            sample.longitude
        )
        return distance >= REPLAY_RESET_DISTANCE_M
    }

    private fun resetReplayStore(overlay: SnailTrailOverlay) {
        replayStore.clear()
        overlay.clear()
        lastReplaySample = null
        lastReplayTimestampAdjusted = null
        lastReplayStoreTimestamp = null
        replayWindSmoother.reset()
        lastRenderPoseTimeMs = null
        lastRenderPoseLocation = null
        lastRenderPoseFrameId = null
        lastRawTailPoint = null
    }

    private fun lerp(start: Double, end: Double, t: Float): Double =
        start + (end - start) * t

    private fun lerpAngleDeg(start: Double, end: Double, t: Float): Double {
        if (!start.isFinite() || !end.isFinite()) {
            return if (start.isFinite()) start else end
        }
        val delta = ((end - start + 540.0) % 360.0) - 180.0
        return (start + delta * t + 360.0) % 360.0
    }

    private fun adjustReplaySample(sample: TrailSample): TrailSample {
        val lastAdjusted = lastReplayTimestampAdjusted
        val adjustedTimestamp = if (lastAdjusted == null) {
            sample.timestampMillis
        } else if (sample.timestampMillis <= lastAdjusted) {
            lastAdjusted + replaySampleStepMillis
        } else {
            sample.timestampMillis
        }
        lastReplayTimestampAdjusted = adjustedTimestamp
        return if (adjustedTimestamp == sample.timestampMillis) {
            sample
        } else {
            sample.copy(timestampMillis = adjustedTimestamp)
        }
    }

    private fun resolveVario(liveData: RealTimeFlightData, isReplay: Boolean): Double {
        if (isReplay) {
            val igc = liveData.realIgcVario
            if (igc != null && igc.isFinite()) return igc
            val xcSoar = liveData.xcSoarDisplayVario
            if (liveData.xcSoarVarioValid && xcSoar.isFinite()) return xcSoar
            val displayNetto = liveData.displayNetto
            if (liveData.nettoValid && displayNetto.isFinite()) return displayNetto
        }
        if (liveData.nettoValid && liveData.netto.isFinite()) {
            return liveData.netto.toDouble()
        }
        return liveData.displayVario.takeIf { it.isFinite() } ?: liveData.verticalSpeed
    }

    private fun resolveCircling(liveData: RealTimeFlightData, isReplay: Boolean): Boolean {
        if (!isReplay) return liveData.isCircling
        if (liveData.isCircling) return true
        if (liveData.currentThermalValid || liveData.thermalAverageValid) return true
        val timestamp = liveData.timestamp
        val track = liveData.track
        if (timestamp <= 0L || !track.isFinite()) return false
        if (timestamp < lastReplayTimestamp) {
            replayCirclingDetector.reset()
        }
        lastReplayTimestamp = timestamp
        return replayCirclingDetector.update(
            trackDegrees = track,
            timestampMillis = timestamp,
            isFlying = true
        ).isCircling
    }

    private data class RenderContext(
        val currentLocation: LatLng,
        val currentTimeMillis: Long,
        val windSpeedMs: Double,
        val windDirectionFromDeg: Double,
        val isCircling: Boolean,
        val currentZoom: Float
    )

    private data class WindSample(
        val speedMs: Double,
        val directionFromDeg: Double
    )

    private class WindSmoother(
        private val tauMs: Long,
        private val minValidSpeedMs: Double
    ) {
        private var lastTimeMs: Long? = null
        private var vx: Double = 0.0
        private var vy: Double = 0.0

        fun reset() {
            lastTimeMs = null
            vx = 0.0
            vy = 0.0
        }

        fun update(speedMs: Double, directionFromDeg: Double, timestampMs: Long): WindSample {
            if (timestampMs <= 0L) {
                return WindSample(speedMs, directionFromDeg)
            }
            val valid = speedMs.isFinite() &&
                directionFromDeg.isFinite() &&
                speedMs > minValidSpeedMs
            val windToDeg = if (valid) (directionFromDeg + 180.0) % 360.0 else 0.0
            val windToRad = Math.toRadians(windToDeg)
            val targetVx = if (valid) speedMs * sin(windToRad) else 0.0
            val targetVy = if (valid) speedMs * cos(windToRad) else 0.0

            val last = lastTimeMs
            if (last == null) {
                vx = 0.0
                vy = 0.0
                lastTimeMs = timestampMs
            } else {
                val dtMs = (timestampMs - last).coerceAtLeast(0L)
                val alpha = if (tauMs > 0L) {
                    1.0 - exp(-dtMs.toDouble() / tauMs.toDouble())
                } else {
                    1.0
                }
                vx += (targetVx - vx) * alpha
                vy += (targetVy - vy) * alpha
                lastTimeMs = timestampMs
            }

            val speed = hypot(vx, vy)
            if (speed <= 1e-3) {
                return WindSample(0.0, directionFromDeg.takeIf { it.isFinite() } ?: 0.0)
            }
            val windTo = (Math.toDegrees(atan2(vx, vy)) + 360.0) % 360.0
            val windFrom = (windTo + 180.0) % 360.0
            return WindSample(speed, windFrom)
        }
    }

    private companion object {
        private const val DISPLAY_RENDER_MIN_STEP_MS = 100L
        private const val DISPLAY_RENDER_MIN_DISTANCE_M = 0.5
        private const val REPLAY_RESET_DISTANCE_M = 2_000.0
        private const val REPLAY_RESET_TIME_BACKSTEP_MS = 2_000L
        private const val REPLAY_WIND_SMOOTH_MS = 4_000L
        private const val REPLAY_WIND_VALID_MIN_SPEED_MS = 0.5
    }
}
