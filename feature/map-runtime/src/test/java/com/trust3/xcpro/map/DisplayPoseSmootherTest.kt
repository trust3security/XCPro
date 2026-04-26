package com.trust3.xcpro.map

import com.trust3.xcpro.common.orientation.MapOrientationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayPoseSmootherTest {

    @Test
    fun heading_alpha_decreases_with_poor_bearing_accuracy() {
        val good = DisplayPoseSmoother()
        good.pushRawFix(
            DisplayPoseSmoother.RawFix(
                latitude = 0.0,
                longitude = 0.0,
                speedMs = 10.0,
                trackDeg = 0.0,
                headingDeg = 0.0,
                accuracyM = 5.0,
                bearingAccuracyDeg = 1.0,
                speedAccuracyMs = null,
                timestampMs = 0L,
                orientationMode = MapOrientationMode.NORTH_UP
            )
        )
        good.tick(0L)
        good.pushRawFix(
            DisplayPoseSmoother.RawFix(
                latitude = 0.0,
                longitude = 0.0,
                speedMs = 10.0,
                trackDeg = 90.0,
                headingDeg = 0.0,
                accuracyM = 5.0,
                bearingAccuracyDeg = 1.0,
                speedAccuracyMs = null,
                timestampMs = 0L,
                orientationMode = MapOrientationMode.NORTH_UP
            )
        )
        val goodPose = good.tick(100L)!!

        val poor = DisplayPoseSmoother()
        poor.pushRawFix(
            DisplayPoseSmoother.RawFix(
                latitude = 0.0,
                longitude = 0.0,
                speedMs = 10.0,
                trackDeg = 0.0,
                headingDeg = 0.0,
                accuracyM = 5.0,
                bearingAccuracyDeg = 30.0,
                speedAccuracyMs = null,
                timestampMs = 0L,
                orientationMode = MapOrientationMode.NORTH_UP
            )
        )
        poor.tick(0L)
        poor.pushRawFix(
            DisplayPoseSmoother.RawFix(
                latitude = 0.0,
                longitude = 0.0,
                speedMs = 10.0,
                trackDeg = 90.0,
                headingDeg = 0.0,
                accuracyM = 5.0,
                bearingAccuracyDeg = 30.0,
                speedAccuracyMs = null,
                timestampMs = 0L,
                orientationMode = MapOrientationMode.NORTH_UP
            )
        )
        val poorPose = poor.tick(100L)!!

        val goodDelta = angularDelta(0.0, goodPose.trackDeg)
        val poorDelta = angularDelta(0.0, poorPose.trackDeg)

        assertTrue(goodDelta > poorDelta)
    }

    @Test
    fun outlier_clamp_limits_movement() {
        val smoother = DisplayPoseSmoother(minSpeedForPredictionMs = 0.0)
        smoother.pushRawFix(
            DisplayPoseSmoother.RawFix(
                latitude = 0.0,
                longitude = 0.0,
                speedMs = 0.0,
                trackDeg = 0.0,
                headingDeg = 0.0,
                accuracyM = 5.0,
                bearingAccuracyDeg = null,
                speedAccuracyMs = 5.0,
                timestampMs = 0L,
                orientationMode = MapOrientationMode.NORTH_UP
            )
        )
        val initial = smoother.tick(0L)!!

        smoother.pushRawFix(
            DisplayPoseSmoother.RawFix(
                latitude = 0.1, // ~11km jump
                longitude = 0.1,
                speedMs = 0.0,
                trackDeg = 0.0,
                headingDeg = 0.0,
                accuracyM = 5.0,
                bearingAccuracyDeg = null,
                speedAccuracyMs = 5.0,
                timestampMs = 1000L,
                orientationMode = MapOrientationMode.NORTH_UP
            )
        )
        val pose = smoother.tick(1000L)!!

        val moved = distanceMeters(
            initial.location.latitude,
            initial.location.longitude,
            pose.location.latitude,
            pose.location.longitude
        )
        val allowed = 15.0 // max(5, accuracy(5m) * 3) with poor speed accuracy
        assertTrue(moved <= allowed + 1.0)
    }

    @Test
    fun large_gap_reanchors_to_latest_fix_instead_of_crawling_from_stale_pose() {
        val smoother = DisplayPoseSmoother(minSpeedForPredictionMs = 0.0)
        smoother.pushRawFix(
            DisplayPoseSmoother.RawFix(
                latitude = 0.0,
                longitude = 0.0,
                speedMs = 18.0,
                trackDeg = 0.0,
                headingDeg = 0.0,
                accuracyM = 5.0,
                bearingAccuracyDeg = 5.0,
                speedAccuracyMs = 0.5,
                timestampMs = 0L,
                orientationMode = MapOrientationMode.NORTH_UP
            )
        )
        val initial = smoother.tick(0L)!!

        smoother.pushRawFix(
            DisplayPoseSmoother.RawFix(
                latitude = 0.0045, // ~500 m north
                longitude = 0.0,
                speedMs = 18.0,
                trackDeg = 0.0,
                headingDeg = 0.0,
                accuracyM = 5.0,
                bearingAccuracyDeg = 5.0,
                speedAccuracyMs = 0.5,
                timestampMs = 10_000L,
                orientationMode = MapOrientationMode.NORTH_UP
            )
        )
        val pose = smoother.tick(10_000L)!!

        val moved = distanceMeters(
            initial.location.latitude,
            initial.location.longitude,
            pose.location.latitude,
            pose.location.longitude
        )

        assertEquals(0.0045, pose.location.latitude, 1e-6)
        assertEquals(0.0, pose.location.longitude, 1e-6)
        assertTrue(moved > 400.0)
    }

    @Test
    fun stale_render_gap_reanchors_to_latest_fix_even_when_raw_fix_gap_is_small() {
        val smoother = DisplayPoseSmoother(minSpeedForPredictionMs = 0.0)
        smoother.pushRawFix(
            DisplayPoseSmoother.RawFix(
                latitude = 0.0,
                longitude = 0.0,
                speedMs = 0.0,
                trackDeg = 0.0,
                headingDeg = 0.0,
                accuracyM = 5.0,
                bearingAccuracyDeg = 5.0,
                speedAccuracyMs = 5.0,
                timestampMs = 0L,
                orientationMode = MapOrientationMode.NORTH_UP
            )
        )
        val initial = smoother.tick(100L)!!

        smoother.pushRawFix(
            DisplayPoseSmoother.RawFix(
                latitude = 0.0045, // ~500 m north
                longitude = 0.0,
                speedMs = 0.0,
                trackDeg = 0.0,
                headingDeg = 0.0,
                accuracyM = 5.0,
                bearingAccuracyDeg = 5.0,
                speedAccuracyMs = 5.0,
                timestampMs = 3_500L,
                orientationMode = MapOrientationMode.NORTH_UP
            )
        )
        val pose = smoother.tick(3_500L)!!

        val moved = distanceMeters(
            initial.location.latitude,
            initial.location.longitude,
            pose.location.latitude,
            pose.location.longitude
        )

        assertEquals(0.0045, pose.location.latitude, 1e-6)
        assertEquals(0.0, pose.location.longitude, 1e-6)
        assertTrue(moved > 400.0)
    }

    @Test
    fun short_render_gap_keeps_outlier_clamp_behavior() {
        val smoother = DisplayPoseSmoother(minSpeedForPredictionMs = 0.0)
        smoother.pushRawFix(
            DisplayPoseSmoother.RawFix(
                latitude = 0.0,
                longitude = 0.0,
                speedMs = 0.0,
                trackDeg = 0.0,
                headingDeg = 0.0,
                accuracyM = 5.0,
                bearingAccuracyDeg = null,
                speedAccuracyMs = 5.0,
                timestampMs = 0L,
                orientationMode = MapOrientationMode.NORTH_UP
            )
        )
        val initial = smoother.tick(100L)!!

        smoother.pushRawFix(
            DisplayPoseSmoother.RawFix(
                latitude = 0.1, // ~11 km jump
                longitude = 0.1,
                speedMs = 0.0,
                trackDeg = 0.0,
                headingDeg = 0.0,
                accuracyM = 5.0,
                bearingAccuracyDeg = null,
                speedAccuracyMs = 5.0,
                timestampMs = 1_500L,
                orientationMode = MapOrientationMode.NORTH_UP
            )
        )
        val pose = smoother.tick(1_500L)!!

        val moved = distanceMeters(
            initial.location.latitude,
            initial.location.longitude,
            pose.location.latitude,
            pose.location.longitude
        )
        val allowed = 15.0
        assertTrue(moved <= allowed + 1.0)
    }

    @Test
    fun prediction_disabled_when_speed_accuracy_is_poor() {
        val smoother = DisplayPoseSmoother(minSpeedForPredictionMs = 0.0)
        smoother.pushRawFix(
            DisplayPoseSmoother.RawFix(
                latitude = 0.0,
                longitude = 0.0,
                speedMs = 10.0,
                trackDeg = 90.0,
                headingDeg = 0.0,
                accuracyM = 5.0,
                bearingAccuracyDeg = 5.0,
                speedAccuracyMs = 5.0,
                timestampMs = 0L,
                orientationMode = MapOrientationMode.NORTH_UP
            )
        )
        val pose = smoother.tick(500L)!!
        assertEquals(0.0, pose.location.latitude, 1e-6)
        assertEquals(0.0, pose.location.longitude, 1e-6)
    }

    @Test
    fun prediction_active_when_accuracy_is_good() {
        val smoother = DisplayPoseSmoother(minSpeedForPredictionMs = 0.0)
        smoother.pushRawFix(
            DisplayPoseSmoother.RawFix(
                latitude = 0.0,
                longitude = 0.0,
                speedMs = 10.0,
                trackDeg = 90.0,
                headingDeg = 0.0,
                accuracyM = 5.0,
                bearingAccuracyDeg = 5.0,
                speedAccuracyMs = 0.5,
                timestampMs = 0L,
                orientationMode = MapOrientationMode.NORTH_UP
            )
        )
        val pose = smoother.tick(500L)!!
        val moved = distanceMeters(0.0, 0.0, pose.location.latitude, pose.location.longitude)
        assertTrue(moved > 1.0)
    }

    private fun angularDelta(from: Double, to: Double): Double {
        var delta = (to - from) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        return kotlin.math.abs(delta)
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(rLat1) * kotlin.math.cos(rLat2) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return 6_371_000.0 * c
    }
}
