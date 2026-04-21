package com.trust3.xcpro.map

import com.trust3.xcpro.common.orientation.MapOrientationMode
import org.junit.Assert.assertEquals
import org.junit.Test

class MapFollowCameraCadencePolicyTest {

    @Test
    fun resolve_entersCloseProfile_atFiveKmVisibleWidth() {
        val policy = MapFollowCameraCadencePolicy()

        val cadence = policy.resolve(
            input(visibleWidthMeters = 5_000.0)
        )

        assertEquals(25L, cadence.minUpdateIntervalMs)
        assertEquals(0.5, cadence.bearingEpsDeg, 0.0)
    }

    @Test
    fun resolve_exitsCloseProfile_atSevenKmVisibleWidth() {
        val policy = MapFollowCameraCadencePolicy()
        policy.resolve(input(visibleWidthMeters = 5_000.0))

        val cadence = policy.resolve(input(visibleWidthMeters = 7_000.0))

        assertEquals(80L, cadence.minUpdateIntervalMs)
        assertEquals(2.0, cadence.bearingEpsDeg, 0.0)
    }

    @Test
    fun resolve_keepsCloseProfile_insideHysteresisAfterEntry() {
        val policy = MapFollowCameraCadencePolicy()
        policy.resolve(input(visibleWidthMeters = 5_000.0))

        val cadence = policy.resolve(input(visibleWidthMeters = 6_500.0))

        assertEquals(25L, cadence.minUpdateIntervalMs)
        assertEquals(0.5, cadence.bearingEpsDeg, 0.0)
    }

    @Test
    fun resolve_keepsNormalProfile_insideHysteresisBeforeEntry() {
        val policy = MapFollowCameraCadencePolicy()

        val cadence = policy.resolve(input(visibleWidthMeters = 6_500.0))

        assertEquals(80L, cadence.minUpdateIntervalMs)
        assertEquals(2.0, cadence.bearingEpsDeg, 0.0)
    }

    @Test
    fun resolve_invalidScaleFallsBackToNormalProfile() {
        val policy = MapFollowCameraCadencePolicy()
        policy.resolve(input(visibleWidthMeters = 5_000.0))

        val cadence = policy.resolve(input(visibleWidthMeters = null))

        assertEquals(80L, cadence.minUpdateIntervalMs)
        assertEquals(2.0, cadence.bearingEpsDeg, 0.0)
    }

    @Test
    fun resolve_replayFallsBackToNormalProfile() {
        val policy = MapFollowCameraCadencePolicy()

        val cadence = policy.resolve(
            input(
                timeBase = DisplayClock.TimeBase.REPLAY,
                visibleWidthMeters = 5_000.0
            )
        )

        assertEquals(80L, cadence.minUpdateIntervalMs)
        assertEquals(2.0, cadence.bearingEpsDeg, 0.0)
    }

    @Test
    fun resolve_northUpUsesClosePositionCadenceButNormalBearingDeadband() {
        val policy = MapFollowCameraCadencePolicy()

        val cadence = policy.resolve(
            input(
                visibleWidthMeters = 5_000.0,
                orientationMode = MapOrientationMode.NORTH_UP
            )
        )

        assertEquals(25L, cadence.minUpdateIntervalMs)
        assertEquals(2.0, cadence.bearingEpsDeg, 0.0)
    }

    private fun input(
        timeBase: DisplayClock.TimeBase? = DisplayClock.TimeBase.MONOTONIC,
        visibleWidthMeters: Double?,
        orientationMode: MapOrientationMode = MapOrientationMode.TRACK_UP
    ): MapFollowCameraCadencePolicy.Input {
        return MapFollowCameraCadencePolicy.Input(
            timeBase = timeBase,
            visibleWidthMeters = visibleWidthMeters,
            orientationMode = orientationMode
        )
    }
}
