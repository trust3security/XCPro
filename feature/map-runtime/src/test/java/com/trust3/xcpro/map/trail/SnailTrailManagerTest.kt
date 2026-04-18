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
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class SnailTrailManagerTest {

    @Test
    fun updateFromTrailUpdate_rendersWhenFullRenderIsRequiredWithoutNewSample() {
        val overlay = mock<SnailTrailOverlay>()
        val manager = createManager(overlay)

        manager.updateFromTrailUpdate(
            update = updateResult(
                sampleAdded = false,
                requiresFullRender = true,
                invalidationReason = TrailRenderInvalidationReason.CIRCLING_CHANGED
            ),
            settings = TrailSettings(),
            currentZoom = 10f
        )

        verify(overlay).render(
            any(),
            eq(TrailSettings()),
            any(),
            eq(2_000L),
            eq(0.0),
            eq(0.0),
            eq(false),
            eq(10f),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun updateDisplayPose_liveSkipsTailRenderWhenTimeBaseDoesNotMatch() {
        val overlay = mock<SnailTrailOverlay>()
        val manager = createManager(overlay)
        manager.updateFromTrailUpdate(
            update = updateResult(
                sampleAdded = true,
                requiresFullRender = true
            ),
            settings = TrailSettings(),
            currentZoom = 10f
        )
        clearInvocations(overlay)

        manager.updateDisplayPose(
            displayLocation = LatLng(46.0001, 7.0001),
            displayTimeMillis = 2_100L,
            displayTimeBase = TrailTimeBase.LIVE_WALL
        )

        verify(overlay, never()).renderTail(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )
    }

    @Test
    fun updateDisplayPose_liveRendersTailWhenTimeBaseMatches() {
        val overlay = mock<SnailTrailOverlay>()
        val manager = createManager(overlay)
        manager.updateFromTrailUpdate(
            update = updateResult(
                sampleAdded = true,
                requiresFullRender = true
            ),
            settings = TrailSettings(),
            currentZoom = 10f
        )
        clearInvocations(overlay)

        manager.updateDisplayPose(
            displayLocation = LatLng(46.0001, 7.0001),
            displayTimeMillis = 2_100L,
            displayTimeBase = TrailTimeBase.LIVE_MONOTONIC
        )

        verify(overlay).renderTail(
            any(),
            eq(TrailSettings()),
            any(),
            eq(2_100L),
            eq(0.0),
            eq(0.0),
            eq(false),
            eq(10f),
            eq(false),
            eq(null)
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
        sampleAdded: Boolean,
        requiresFullRender: Boolean,
        invalidationReason: TrailRenderInvalidationReason? = TrailRenderInvalidationReason.SAMPLE_ADDED
    ): TrailUpdateResult = TrailUpdateResult(
        renderState = TrailRenderState(
            points = listOf(
                TrailPoint(
                    latitude = 46.0,
                    longitude = 7.0,
                    timestampMillis = 2_000L,
                    altitudeMeters = 1_000.0,
                    varioMs = 0.5,
                    driftFactor = 0.0,
                    windSpeedMs = 0.0,
                    windDirectionFromDeg = 0.0
                )
            ),
            currentLocation = TrailGeoPoint(46.0, 7.0),
            currentTimeMillis = 2_000L,
            windSpeedMs = 0.0,
            windDirectionFromDeg = 0.0,
            isCircling = false,
            isReplay = false,
            timeBase = TrailTimeBase.LIVE_MONOTONIC
        ),
        sampleAdded = sampleAdded,
        storeReset = false,
        modeChanged = false,
        requiresFullRender = requiresFullRender,
        invalidationReason = invalidationReason
    )
}
