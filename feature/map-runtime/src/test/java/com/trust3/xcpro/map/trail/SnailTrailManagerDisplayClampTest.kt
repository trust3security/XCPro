package com.trust3.xcpro.map.trail

import android.content.Context
import com.trust3.xcpro.map.config.MapFeatureFlags
import com.trust3.xcpro.map.trail.domain.TrailRenderInvalidationReason
import com.trust3.xcpro.map.trail.domain.TrailRenderState
import com.trust3.xcpro.map.trail.domain.TrailTimeBase
import com.trust3.xcpro.map.trail.domain.TrailUpdateResult
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.mockito.Mockito.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class SnailTrailManagerDisplayClampTest {

    @Test
    fun updateFromTrailUpdate_liveClampsBodyBeforeDisplayedPose() {
        val overlay = mock<SnailTrailOverlay>()
        val manager = createManager(overlay)
        val first = point(longitude = 7.0, timestampMillis = 1_000L)
        val future = point(longitude = 7.001, timestampMillis = 2_000L)
        val displayLocation = LatLng(46.0, 7.0005)

        manager.updateFromTrailUpdate(
            update = updateResult(
                points = listOf(first, future),
                currentLocation = TrailGeoPoint(46.0, 7.001),
                currentTimeMillis = 2_000L
            ),
            settings = TrailSettings(),
            currentZoom = 10f,
            displayLocation = displayLocation,
            displayTimeMillis = 2_000L,
            displayTimeBase = TrailTimeBase.LIVE_MONOTONIC
        )

        verify(overlay).render(
            points = eq(listOf(first)),
            settings = eq(TrailSettings()),
            currentLocation = eq(displayLocation),
            currentTimeMillis = eq(2_000L),
            windSpeedMs = eq(0.0),
            windDirectionFromDeg = eq(0.0),
            isCircling = eq(false),
            isTurnSmoothing = eq(false),
            currentZoom = eq(10f),
            isReplay = eq(false),
            frameId = eq(null)
        )
    }

    @Test
    fun updateDisplayPose_fullRendersWhenDisplayPoseReachesNextPointSpatially() {
        val overlay = mock<SnailTrailOverlay>()
        val manager = createManager(overlay)
        val first = point(longitude = 7.0, timestampMillis = 1_000L)
        val reached = point(longitude = 7.001, timestampMillis = 2_000L)
        val future = point(longitude = 7.002, timestampMillis = 3_000L)

        manager.updateFromTrailUpdate(
            update = updateResult(
                points = listOf(first, reached, future),
                currentLocation = TrailGeoPoint(46.0, 7.002),
                currentTimeMillis = 3_000L
            ),
            settings = TrailSettings(),
            currentZoom = 10f,
            displayLocation = LatLng(46.0, 7.0005),
            displayTimeMillis = 3_000L,
            displayTimeBase = TrailTimeBase.LIVE_MONOTONIC
        )
        clearInvocations(overlay)

        manager.updateDisplayPose(
            displayLocation = LatLng(46.0, 7.001),
            displayTimeMillis = 3_100L,
            displayTimeBase = TrailTimeBase.LIVE_MONOTONIC,
            frameId = 9L
        )

        verify(overlay).render(
            points = eq(listOf(first, reached)),
            settings = eq(TrailSettings()),
            currentLocation = eq(LatLng(46.0, 7.001)),
            currentTimeMillis = eq(3_100L),
            windSpeedMs = eq(0.0),
            windDirectionFromDeg = eq(0.0),
            isCircling = eq(false),
            isTurnSmoothing = eq(false),
            currentZoom = eq(10f),
            isReplay = eq(false),
            frameId = eq(9L)
        )
    }

    private fun createManager(overlay: SnailTrailOverlay): SnailTrailManager {
        val runtimeState = object : SnailTrailRuntimeState {
            override var mapView: MapView? = null
            override var blueLocationOverlay: com.trust3.xcpro.map.BlueLocationOverlay? = null
            override var snailTrailOverlay: SnailTrailOverlay? = overlay
        }
        return SnailTrailManager(
            context = mock<Context>(),
            runtimeState = runtimeState,
            featureFlags = MapFeatureFlags()
        )
    }

    private fun updateResult(
        points: List<TrailPoint>,
        currentLocation: TrailGeoPoint,
        currentTimeMillis: Long
    ): TrailUpdateResult = TrailUpdateResult(
        renderState = TrailRenderState(
            points = points,
            currentLocation = currentLocation,
            currentTimeMillis = currentTimeMillis,
            windSpeedMs = 0.0,
            windDirectionFromDeg = 0.0,
            isCircling = false,
            isTurnSmoothing = false,
            isReplay = false,
            timeBase = TrailTimeBase.LIVE_MONOTONIC
        ),
        sampleAdded = true,
        storeReset = false,
        modeChanged = false,
        requiresFullRender = true,
        invalidationReason = TrailRenderInvalidationReason.SAMPLE_ADDED
    )

    private fun point(
        longitude: Double,
        timestampMillis: Long
    ): TrailPoint = TrailPoint(
        latitude = 46.0,
        longitude = longitude,
        timestampMillis = timestampMillis,
        altitudeMeters = 1_000.0,
        varioMs = 0.5,
        driftFactor = 0.0,
        windSpeedMs = 0.0,
        windDirectionFromDeg = 0.0
    )
}
