package com.example.xcpro.map

import android.graphics.PointF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Projection
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrafficSelectedGroupFanoutLayoutTest {

    private val layout = TrafficSelectedGroupFanoutLayout()

    @Test
    fun resolveDisplayCoordinatesByKey_fansOutOnlyNonPrimaryMembersOfSelectedPackedGroup() {
        val selected = seed("selected", -35.0, 149.0)
        val first = seed("alpha", -35.0001, 149.0001)
        val second = seed("bravo", -35.0002, 149.0002)
        val unrelated = seed("solo", -35.2, 149.2)
        val map = mapOfPoints(
            selected.latitude to selected.longitude to PointF(100f, 100f),
            first.latitude to first.longitude to PointF(104f, 103f),
            second.latitude to second.longitude to PointF(108f, 106f),
            unrelated.latitude to unrelated.longitude to PointF(300f, 300f)
        )

        val displayCoordinates = layout.resolveDisplayCoordinatesByKey(
            map = map,
            seeds = listOf(unrelated, second, selected, first),
            selectedTargetKey = selected.key
        )

        assertEquals(setOf(first.key, second.key), displayCoordinates.keys)
        assertNotEquals(first.latitude, displayCoordinates.getValue(first.key).latitude)
        assertNotEquals(first.longitude, displayCoordinates.getValue(first.key).longitude)
        assertNotEquals(second.latitude, displayCoordinates.getValue(second.key).latitude)
        assertNotEquals(second.longitude, displayCoordinates.getValue(second.key).longitude)
    }

    @Test
    fun resolveDisplayCoordinatesByKey_isDeterministicForStableMembership() {
        val selected = seed("selected", -35.0, 149.0)
        val first = seed("alpha", -35.0001, 149.0001)
        val second = seed("bravo", -35.0002, 149.0002)
        val map = mapOfPoints(
            selected.latitude to selected.longitude to PointF(100f, 100f),
            first.latitude to first.longitude to PointF(104f, 103f),
            second.latitude to second.longitude to PointF(108f, 106f)
        )

        val firstPass = layout.resolveDisplayCoordinatesByKey(
            map = map,
            seeds = listOf(selected, first, second),
            selectedTargetKey = selected.key
        )
        val secondPass = layout.resolveDisplayCoordinatesByKey(
            map = map,
            seeds = listOf(second, selected, first),
            selectedTargetKey = selected.key
        )

        assertEquals(firstPass, secondPass)
    }

    @Test
    fun resolveDisplayCoordinatesByKey_returnsEmptyWhenSelectionMissingOrNotPacked() {
        val selected = seed("selected", -35.0, 149.0)
        val solo = seed("solo", -35.2, 149.2)
        val map = mapOfPoints(
            selected.latitude to selected.longitude to PointF(100f, 100f),
            solo.latitude to solo.longitude to PointF(300f, 300f)
        )

        assertEquals(
            emptyMap<String, TrafficDisplayCoordinate>(),
            layout.resolveDisplayCoordinatesByKey(
                map = map,
                seeds = listOf(selected, solo),
                selectedTargetKey = null
            )
        )
        assertEquals(
            emptyMap<String, TrafficDisplayCoordinate>(),
            layout.resolveDisplayCoordinatesByKey(
                map = map,
                seeds = listOf(selected, solo),
                selectedTargetKey = selected.key
            )
        )
    }

    private fun seed(
        key: String,
        latitude: Double,
        longitude: Double
    ) = TrafficPackedGroupLabelSeed(
        key = key,
        latitude = latitude,
        longitude = longitude,
        collisionWidthPx = 40f,
        collisionHeightPx = 40f,
        priorityRank = 0
    )

    private fun mapOfPoints(
        vararg entries: Pair<Pair<Double, Double>, PointF>
    ): MapLibreMap {
        val map: MapLibreMap = mock()
        val projection: Projection = mock()
        whenever(map.projection).thenReturn(projection)
        val points = entries.toMap()
        whenever(projection.toScreenLocation(any())).thenAnswer { invocation ->
            val latLng = invocation.getArgument<LatLng>(0)
            points[latLng.latitude to latLng.longitude]
                ?: PointF((latLng.longitude * 1000.0).toFloat(), (-latLng.latitude * 1000.0).toFloat())
        }
        whenever(projection.fromScreenLocation(any())).thenAnswer { invocation ->
            val point = invocation.getArgument<PointF>(0)
            LatLng((-point.y / 1000f).toDouble(), (point.x / 1000f).toDouble())
        }
        return map
    }

    private infix fun Double.to(other: Double): Pair<Double, Double> = Pair(this, other)
}
