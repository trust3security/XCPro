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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class SnailTrailManagerTest {

    @Test
    fun updateFromTrailUpdate_rendersRawTrailBodyByDefault() {
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
            eq(listOf(rawTailPoint())),
            eq(TrailSettings()),
            any(),
            eq(2_000L),
            eq(0.0),
            eq(0.0),
            eq(false),
            eq(false),
            eq(10f),
            eq(false),
            eq(null)
        )
        verify(overlay, never()).clearRawTrail()
    }

    @Test
    fun updateFromTrailUpdate_liveUsesFreshDisplayPoseForRawTailSeed() {
        val overlay = mock<SnailTrailOverlay>()
        val manager = createManager(overlay)
        val displayLocation = LatLng(46.0005, 7.0005)

        manager.updateFromTrailUpdate(
            update = updateResult(
                sampleAdded = true,
                requiresFullRender = true
            ),
            settings = TrailSettings(),
            currentZoom = 10f,
            displayLocation = displayLocation,
            displayTimeMillis = 2_100L,
            displayTimeBase = TrailTimeBase.LIVE_MONOTONIC
        )

        verify(overlay).render(
            eq(listOf(rawTailPoint())),
            eq(TrailSettings()),
            eq(displayLocation),
            eq(2_100L),
            eq(0.0),
            eq(0.0),
            eq(false),
            eq(false),
            eq(10f),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun updateFromTrailUpdate_ignoresStaleDisplayPoseSeed() {
        val overlay = mock<SnailTrailOverlay>()
        val manager = createManager(overlay)
        val staleDisplayLocation = LatLng(46.0005, 7.0005)

        manager.updateFromTrailUpdate(
            update = updateResult(
                sampleAdded = true,
                requiresFullRender = true
            ),
            settings = TrailSettings(),
            currentZoom = 10f,
            displayLocation = staleDisplayLocation,
            displayTimeMillis = 1_900L,
            displayTimeBase = TrailTimeBase.LIVE_MONOTONIC
        )

        verify(overlay).render(
            eq(listOf(rawTailPoint())),
            eq(TrailSettings()),
            any(),
            eq(2_000L),
            eq(0.0),
            eq(0.0),
            eq(false),
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
            anyOrNull(),
            any(),
            anyOrNull(),
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
    fun updateFromTrailUpdate_rendersRawTrailWhenFull() {
        val overlay = mock<SnailTrailOverlay>()
        val manager = createManager(overlay)
        val settings = TrailSettings(length = TrailLength.FULL)

        manager.updateFromTrailUpdate(
            update = updateResult(
                sampleAdded = false,
                requiresFullRender = true,
                invalidationReason = TrailRenderInvalidationReason.CIRCLING_CHANGED
            ),
            settings = settings,
            currentZoom = 10f
        )

        verify(overlay).render(
            eq(listOf(rawTailPoint())),
            eq(settings),
            any(),
            eq(2_000L),
            eq(0.0),
            eq(0.0),
            eq(false),
            eq(false),
            eq(10f),
            eq(false),
            eq(null)
        )
        verify(overlay, never()).clearRawTrail()
    }

    @Test
    fun onZoomChanged_rerendersRawTrailWhenFull() {
        val overlay = mock<SnailTrailOverlay>()
        val manager = createManager(overlay)
        val settings = TrailSettings(length = TrailLength.FULL)
        manager.updateFromTrailUpdate(
            update = updateResult(
                sampleAdded = false,
                requiresFullRender = true
            ),
            settings = settings,
            currentZoom = 10f
        )
        clearInvocations(overlay)

        manager.onZoomChanged(6f)

        verify(overlay).render(
            eq(listOf(rawTailPoint())),
            eq(settings),
            any(),
            eq(2_000L),
            eq(0.0),
            eq(0.0),
            eq(false),
            eq(false),
            eq(6f),
            eq(false),
            eq(null)
        )
    }

    private fun createManager(
        overlay: SnailTrailOverlay,
        featureFlags: MapFeatureFlags = MapFeatureFlags()
    ): SnailTrailManager {
        val runtimeState = object : SnailTrailRuntimeState {
            override var mapView: MapView? = null
            override var blueLocationOverlay: com.trust3.xcpro.map.BlueLocationOverlay? = null
            override var snailTrailOverlay: SnailTrailOverlay? = overlay
        }
        return SnailTrailManager(
            context = mock<Context>(),
            runtimeState = runtimeState,
            featureFlags = featureFlags
        )
    }

    private fun updateResult(
        sampleAdded: Boolean,
        requiresFullRender: Boolean,
        invalidationReason: TrailRenderInvalidationReason? = TrailRenderInvalidationReason.SAMPLE_ADDED,
        isReplay: Boolean = false,
        timeBase: TrailTimeBase = TrailTimeBase.LIVE_MONOTONIC
    ): TrailUpdateResult = TrailUpdateResult(
        renderState = TrailRenderState(
            points = listOf(rawTailPoint()),
            currentLocation = TrailGeoPoint(46.0, 7.0),
            currentTimeMillis = 2_000L,
            windSpeedMs = 0.0,
            windDirectionFromDeg = 0.0,
            isCircling = false,
            isTurnSmoothing = false,
            isReplay = isReplay,
            timeBase = timeBase
        ),
        sampleAdded = sampleAdded,
        storeReset = false,
        modeChanged = false,
        requiresFullRender = requiresFullRender,
        invalidationReason = invalidationReason
    )

    private fun rawTailPoint(): TrailPoint = TrailPoint(
        latitude = 46.0,
        longitude = 7.0,
        timestampMillis = 2_000L,
        altitudeMeters = 1_000.0,
        varioMs = 0.5,
        driftFactor = 0.0,
        windSpeedMs = 0.0,
        windDirectionFromDeg = 0.0
    )
}
