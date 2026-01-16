// Role: Coordinate trail storage and rendering based on live/replay data.
// Invariants: Rendering occurs only when a map style is ready.
package com.example.xcpro.map.trail

import android.content.Context
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.sensors.CirclingDetector
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

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
    private val replaySampleStepMillis = 250L

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
        currentZoom: Float
    ) {
        val overlay = mapState.snailTrailOverlay ?: return
        if (liveData == null) {
            overlay.clear()
            lastContext = null
            return
        }

        if (lastIsReplay != null && lastIsReplay != isReplay) {
            liveStore.clear()
            replayStore.clear()
            overlay.clear()
            lastContext = null
            lastReplaySample = null
            lastReplayTimestampAdjusted = null
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

        if ((isFlying || isReplay) && timestamp > 0L) {
            val sample = TrailSample(
                latitude = lat,
                longitude = lon,
                timestampMillis = timestamp,
                altitudeMeters = altitude,
                varioMs = vario
            )
            sampleAdded = if (isReplay) {
                addReplaySample(sample, store)
            } else {
                store.addSample(sample)
            }
        }

        lastContext = RenderContext(
            currentLocation = LatLng(lat, lon),
            currentTimeMillis = timestamp,
            windSpeedMs = liveData.windSpeed.toDouble(),
            windDirectionFromDeg = liveData.windDirection.toDouble(),
            isCircling = isCircling,
            currentZoom = currentZoom
        )

        if (sampleAdded || settingsChanged || zoomChanged) {
            render(overlay)
        }
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

    private fun render(overlay: SnailTrailOverlay) {
        val context = lastContext ?: return
        val isReplay = lastIsReplay ?: false
        val store = activeStore(isReplay)
        overlay.render(
            points = store.snapshot(),
            settings = lastSettings,
            currentLocation = context.currentLocation,
            currentTimeMillis = context.currentTimeMillis,
            windSpeedMs = context.windSpeedMs,
            windDirectionFromDeg = context.windDirectionFromDeg,
            isCircling = context.isCircling,
            currentZoom = context.currentZoom,
            isReplay = isReplay
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
                            varioMs = lerp(previous.varioMs, adjusted.varioMs, t)
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

    private fun lerp(start: Double, end: Double, t: Float): Double =
        start + (end - start) * t

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
}
