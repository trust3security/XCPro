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

class SnailTrailManagerDisplayPoseTest {

    @Test
    fun updateDisplayPose_liveRendersDisplayTrailWhenFlagEnabled() {
        val overlay = mock<SnailTrailOverlay>()
        val manager = createManager(overlay, TrailTimeBase.LIVE_MONOTONIC, isReplay = false)
        clearInvocations(overlay)

        manager.updateDisplayPose(
            displayLocation = LatLng(46.0001, 7.0001),
            displayTimeMillis = 2_100L,
            displayTimeBase = TrailTimeBase.LIVE_MONOTONIC,
            frameId = 10L
        )

        verify(overlay).renderDisplayTrail(
            any(),
            eq(TrailSettings())
        )
    }

    @Test
    fun updateDisplayPose_replayRendersDisplayTrailWhenFlagEnabled() {
        val overlay = mock<SnailTrailOverlay>()
        val manager = createManager(overlay, TrailTimeBase.REPLAY_IGC, isReplay = true)
        clearInvocations(overlay)

        manager.updateDisplayPose(
            displayLocation = LatLng(46.0001, 7.0001),
            displayTimeMillis = 2_100L,
            displayTimeBase = TrailTimeBase.REPLAY_IGC,
            frameId = 10L
        )

        verify(overlay).renderDisplayTrail(
            any(),
            eq(TrailSettings())
        )
    }

    @Test
    fun updateDisplayPose_liveClearsDisplayTrailWhenFlagDisabled() {
        val overlay = mock<SnailTrailOverlay>()
        val manager = createManager(
            overlay = overlay,
            timeBase = TrailTimeBase.LIVE_MONOTONIC,
            isReplay = false,
            useDisplayPoseSnailTrail = false
        )
        clearInvocations(overlay)

        manager.updateDisplayPose(
            displayLocation = LatLng(46.0001, 7.0001),
            displayTimeMillis = 2_100L,
            displayTimeBase = TrailTimeBase.LIVE_MONOTONIC,
            frameId = 10L
        )

        verify(overlay).clearDisplayTrail()
        verify(overlay, never()).renderDisplayTrail(
            any(),
            any()
        )
    }

    @Test
    fun updateDisplayPose_liveRendersConnectorWhenPointIsNotAccepted() {
        val overlay = mock<SnailTrailOverlay>()
        val manager = createManager(overlay, TrailTimeBase.LIVE_MONOTONIC, isReplay = false)
        manager.updateDisplayPose(
            displayLocation = LatLng(46.0001, 7.0001),
            displayTimeMillis = 2_100L,
            displayTimeBase = TrailTimeBase.LIVE_MONOTONIC,
            frameId = 1L
        )
        clearInvocations(overlay)

        manager.updateDisplayPose(
            displayLocation = LatLng(46.0001, 7.0001),
            displayTimeMillis = 2_150L,
            displayTimeBase = TrailTimeBase.LIVE_MONOTONIC,
            frameId = 2L
        )

        verify(overlay, never()).renderDisplayTrail(any(), any())
        verify(overlay).renderDisplayConnector(any(), eq(TrailSettings()), any(), eq(2_150L), eq(10f))
    }

    @Test
    fun updateDisplayPose_replayUsesAcceptedPointCadenceButKeepsConnectorLive() {
        val overlay = mock<SnailTrailOverlay>()
        val manager = createManager(overlay, TrailTimeBase.REPLAY_IGC, isReplay = true)
        manager.updateDisplayPose(
            displayLocation = LatLng(46.0001, 7.0001),
            displayTimeMillis = 2_000L,
            displayTimeBase = TrailTimeBase.REPLAY_IGC,
            frameId = 1L
        )
        clearInvocations(overlay)

        manager.updateDisplayPose(
            displayLocation = LatLng(46.0002, 7.0002),
            displayTimeMillis = 2_179L,
            displayTimeBase = TrailTimeBase.REPLAY_IGC,
            frameId = 2L
        )

        verify(overlay, never()).renderDisplayTrail(any(), any())
        verify(overlay).renderDisplayConnector(any(), eq(TrailSettings()), any(), eq(2_179L), eq(10f))

        clearInvocations(overlay)
        manager.updateDisplayPose(
            displayLocation = LatLng(46.0003, 7.0003),
            displayTimeMillis = 2_180L,
            displayTimeBase = TrailTimeBase.REPLAY_IGC,
            frameId = 3L
        )

        verify(overlay).renderDisplayTrail(any(), eq(TrailSettings()))
        verify(overlay).renderDisplayConnector(any(), eq(TrailSettings()), any(), eq(2_180L), eq(10f))
    }

    private fun createManager(
        overlay: SnailTrailOverlay,
        timeBase: TrailTimeBase,
        isReplay: Boolean,
        useDisplayPoseSnailTrail: Boolean = true
    ): SnailTrailManager {
        val runtimeState = object : SnailTrailRuntimeState {
            override var mapView: MapView? = null
            override var blueLocationOverlay: com.trust3.xcpro.map.BlueLocationOverlay? = null
            override var snailTrailOverlay: SnailTrailOverlay? = overlay
        }
        return SnailTrailManager(
            context = mock<Context>(),
            runtimeState = runtimeState,
            featureFlags = MapFeatureFlags().apply {
                this.useDisplayPoseSnailTrail = useDisplayPoseSnailTrail
            }
        ).also {
            it.updateFromTrailUpdate(updateResult(isReplay = isReplay, timeBase = timeBase), TrailSettings(), 10f)
        }
    }

    private fun updateResult(
        isReplay: Boolean = false,
        timeBase: TrailTimeBase = TrailTimeBase.LIVE_MONOTONIC
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
            isTurnSmoothing = false,
            isReplay = isReplay,
            timeBase = timeBase
        ),
        sampleAdded = true,
        storeReset = false,
        modeChanged = false,
        requiresFullRender = true,
        invalidationReason = TrailRenderInvalidationReason.SAMPLE_ADDED
    )
}
