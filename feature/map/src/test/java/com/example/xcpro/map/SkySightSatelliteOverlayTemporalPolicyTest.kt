package com.example.xcpro.map

import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_FRAME_STEP_MINUTES
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_MAX
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapLibreMap
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SkySightSatelliteOverlayTemporalPolicyTest {

    @Test
    fun buildFrameEpochs_ordersOldestToNewest() {
        val overlay = SkySightSatelliteOverlay(map = mock<MapLibreMap>())
        val frameStepSec = FORECAST_SKYSIGHT_SATELLITE_FRAME_STEP_MINUTES * 60L
        val baseFrameEpochSec = 200_000L
        val frameCount = FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_MAX

        val epochs = invokeBuildFrameEpochs(
            overlay = overlay,
            baseFrameEpochSec = baseFrameEpochSec,
            frameCount = frameCount
        )

        val expected = (0 until frameCount).map { index ->
            baseFrameEpochSec - ((frameCount - 1 - index) * frameStepSec)
        }
        assertEquals(
            expected,
            epochs
        )
        assertTrue(epochs == epochs.sorted())
    }

    @Test
    fun resolveBaseFrameEpochSec_clampsReferenceToLatestAvailableStep() {
        val nowUtcMs = 1_800_000_900_000L
        val overlay = SkySightSatelliteOverlay(
            map = mock(),
            nowUtcMsProvider = { nowUtcMs }
        )
        val frameCount = FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_MAX
        val frameStepMs = FORECAST_SKYSIGHT_SATELLITE_FRAME_STEP_MINUTES * 60_000L
        val latestAvailableUtcMs = nowUtcMs - (15 * 60_000L)
        val expectedLatestSteppedUtcMs = (latestAvailableUtcMs / frameStepMs) * frameStepMs
        val referenceTimeUtcMs = latestAvailableUtcMs + (6 * frameStepMs)

        val resolved = invokeResolveBaseFrameEpochSec(
            overlay = overlay,
            referenceTimeUtcMs = referenceTimeUtcMs,
            frameCount = frameCount
        )

        assertEquals(expectedLatestSteppedUtcMs / 1_000L, resolved)
    }

    @Test
    fun resolveBaseFrameEpochSec_withoutReference_usesNearLiveSteppedTime() {
        val nowUtcMs = 1_800_000_900_000L
        val overlay = SkySightSatelliteOverlay(
            map = mock(),
            nowUtcMsProvider = { nowUtcMs }
        )
        val frameCount = FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_MAX
        val frameStepMs = FORECAST_SKYSIGHT_SATELLITE_FRAME_STEP_MINUTES * 60_000L
        val latestAvailableUtcMs = nowUtcMs - (15 * 60_000L)
        val expectedLatestSteppedUtcMs = (latestAvailableUtcMs / frameStepMs) * frameStepMs

        val resolved = invokeResolveBaseFrameEpochSec(
            overlay = overlay,
            referenceTimeUtcMs = null,
            frameCount = frameCount
        )

        assertEquals(expectedLatestSteppedUtcMs / 1_000L, resolved)
    }

    @Test
    fun resolveBaseFrameEpochSec_clampsReferenceToLowerBoundByHistoryWindow() {
        val nowUtcMs = 1_800_000_900_000L
        val overlay = SkySightSatelliteOverlay(
            map = mock(),
            nowUtcMsProvider = { nowUtcMs }
        )
        val frameCount = FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_MAX
        val frameStepMs = FORECAST_SKYSIGHT_SATELLITE_FRAME_STEP_MINUTES * 60_000L
        val latestAvailableUtcMs = nowUtcMs - (15 * 60_000L)
        val latestSteppedUtcMs = (latestAvailableUtcMs / frameStepMs) * frameStepMs
        val maxHistorySpanMs = (FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_MAX - 1) * frameStepMs
        val renderedHistorySpanMs = (frameCount - 1) * frameStepMs
        val expectedEarliestReferenceUtcMs = (
            latestSteppedUtcMs - maxHistorySpanMs + renderedHistorySpanMs
            ).coerceAtLeast(0L)

        val resolved = invokeResolveBaseFrameEpochSec(
            overlay = overlay,
            referenceTimeUtcMs = 0L,
            frameCount = frameCount
        )

        assertEquals(expectedEarliestReferenceUtcMs / 1_000L, resolved)
    }

    @Test
    fun resolveInitialFrameIndex_nonAnimatedUsesLatestFrame() {
        val overlay = SkySightSatelliteOverlay(map = mock<MapLibreMap>())
        val frameCount = FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_MAX

        val index = invokeResolveInitialFrameIndex(
            overlay = overlay,
            animate = false,
            frameCount = frameCount
        )

        assertEquals(frameCount - 1, index)
    }

    @Test
    fun resolveInitialFrameIndex_animatedUsesOldestFrame() {
        val overlay = SkySightSatelliteOverlay(map = mock<MapLibreMap>())
        val frameCount = FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_MAX

        val index = invokeResolveInitialFrameIndex(
            overlay = overlay,
            animate = true,
            frameCount = frameCount
        )

        assertEquals(0, index)
    }

    @Test
    fun nextFrameIndex_cyclesOldestToNewestThenWraps() {
        val overlay = SkySightSatelliteOverlay(map = mock<MapLibreMap>())
        val frameCount = 6
        val sequence = mutableListOf<Int>()
        var index = 0
        sequence.add(index)

        repeat(frameCount) {
            index = invokeNextFrameIndex(
                overlay = overlay,
                currentFrameIndex = index,
                frameCount = frameCount
            )
            sequence.add(index)
        }

        assertEquals(listOf(0, 1, 2, 3, 4, 5, 0), sequence)
    }

    private fun invokeBuildFrameEpochs(
        overlay: SkySightSatelliteOverlay,
        baseFrameEpochSec: Long,
        frameCount: Int
    ): List<Long> {
        val method = SkySightSatelliteOverlay::class.java.getDeclaredMethod(
            "buildFrameEpochs",
            Long::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(overlay, baseFrameEpochSec, frameCount) as List<Long>
    }

    private fun invokeResolveBaseFrameEpochSec(
        overlay: SkySightSatelliteOverlay,
        referenceTimeUtcMs: Long?,
        frameCount: Int
    ): Long {
        val method = SkySightSatelliteOverlay::class.java.getDeclaredMethod(
            "resolveBaseFrameEpochSec",
            java.lang.Long::class.java,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(overlay, referenceTimeUtcMs, frameCount) as Long
    }

    private fun invokeResolveInitialFrameIndex(
        overlay: SkySightSatelliteOverlay,
        animate: Boolean,
        frameCount: Int
    ): Int {
        val method = SkySightSatelliteOverlay::class.java.getDeclaredMethod(
            "resolveInitialFrameIndex",
            Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(overlay, animate, frameCount) as Int
    }

    private fun invokeNextFrameIndex(
        overlay: SkySightSatelliteOverlay,
        currentFrameIndex: Int,
        frameCount: Int
    ): Int {
        val method = SkySightSatelliteOverlay::class.java.getDeclaredMethod(
            "nextFrameIndex",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(overlay, currentFrameIndex, frameCount) as Int
    }
}
