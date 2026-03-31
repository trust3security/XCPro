package com.example.xcpro.map

import android.graphics.PointF
import com.example.xcpro.ogn.OgnAddressType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.android.geometry.LatLngBounds
import org.junit.runner.RunWith
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.VisibleRegion
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Projection
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrafficPackedGroupLabelControlTest {

    private val control = TrafficPackedGroupLabelControl()

    @Test
    fun resolveFullLabelKeys_nonPackedTargets_keepAllLabels() {
        val map = mapOfPoints(
            -35.0 to 149.0 to PointF(10f, 10f),
            -35.1 to 149.1 to PointF(200f, 200f)
        )

        val fullLabelKeys = control.resolveFullLabelKeys(
            map = map,
            seeds = listOf(
                seed("a", -35.0, 149.0, 0),
                seed("b", -35.1, 149.1, 1)
            )
        )

        assertEquals(setOf("a", "b"), fullLabelKeys)
    }

    @Test
    fun resolveFullLabelKeys_packedGroup_keepsOnlyPrimaryPlusSingletons() {
        val map = mapOfPoints(
            -35.0 to 149.0 to PointF(100f, 100f),
            -35.0 to 149.0001 to PointF(105f, 104f),
            -35.2 to 149.2 to PointF(280f, 280f)
        )

        val fullLabelKeys = control.resolveFullLabelKeys(
            map = map,
            seeds = listOf(
                seed("primary", -35.0, 149.0, 0),
                seed("suppressed", -35.0, 149.0001, 1),
                seed("solo", -35.2, 149.2, 2)
            )
        )

        assertEquals(setOf("primary", "solo"), fullLabelKeys)
    }

    @Test
    fun resolveFullLabelKeys_excludesOffscreenTargetFromPrimaryElection() {
        val map = mapOfPoints(
            -35.0 to 149.0 to PointF(195f, 100f),
            -35.0 to 149.0001 to PointF(205f, 100f),
            -35.2 to 149.2 to PointF(50f, 50f),
            viewportBoundsPx = PackedGroupDisplayBounds(
                left = 0f,
                top = 0f,
                right = 200f,
                bottom = 200f
            )
        )

        val fullLabelKeys = control.resolveFullLabelKeys(
            map = map,
            seeds = listOf(
                seed("visible", -35.0, 149.0, 1),
                seed("offscreen", -35.0, 149.0001, 0),
                seed("solo", -35.2, 149.2, 2)
            )
        )

        assertEquals(setOf("visible", "solo"), fullLabelKeys)
    }

    @Test
    fun rankOgnTargetsForPackedGroupLabels_selectedAircraftWinsThenNearest() {
        val near = ognTarget(id = "N1", distanceMeters = 100.0)
        val farSelected = ognTarget(id = "S1", distanceMeters = 800.0)

        val ranks = rankOgnTargetsForPackedGroupLabels(
            targets = listOf(near, farSelected),
            selectedTargetKey = farSelected.canonicalKey
        )

        assertEquals(0, ranks[farSelected.canonicalKey])
        assertEquals(1, ranks[near.canonicalKey])
    }

    @Test
    fun rankOgnTargetsForPackedGroupLabels_tiesBreakByCanonicalKey() {
        val first = ognTarget(id = "A1", distanceMeters = 100.0)
        val second = ognTarget(id = "B1", distanceMeters = 100.0)

        val ranks = rankOgnTargetsForPackedGroupLabels(
            targets = listOf(second, first),
            selectedTargetKey = null
        )

        assertEquals(0, ranks[first.canonicalKey])
        assertEquals(1, ranks[second.canonicalKey])
    }

    @Test
    fun rankAdsbTargetsForPackedGroupLabels_selectedAircraftWinsOverEmergency() {
        val selected = adsbTarget(id = "abc123", distanceMeters = 900.0, emergency = false)
        val emergency = adsbTarget(id = "def456", distanceMeters = 100.0, emergency = true)

        val ranks = rankAdsbTargetsForPackedGroupLabels(
            targets = listOf(emergency, selected),
            selectedTargetId = selected.id
        )

        assertEquals(0, ranks[selected.id.raw])
        assertEquals(1, ranks[emergency.id.raw])
    }

    @Test
    fun rankAdsbTargetsForPackedGroupLabels_emergencyWinsThenNearestThenId() {
        val emergency = adsbTarget(id = "aaa111", distanceMeters = 500.0, emergency = true)
        val nearNeutral = adsbTarget(id = "bbb222", distanceMeters = 100.0, emergency = false)
        val farNeutral = adsbTarget(id = "ccc333", distanceMeters = 300.0, emergency = false)

        val ranks = rankAdsbTargetsForPackedGroupLabels(
            targets = listOf(farNeutral, nearNeutral, emergency),
            selectedTargetId = null
        )

        assertEquals(0, ranks[emergency.id.raw])
        assertEquals(1, ranks[nearNeutral.id.raw])
        assertEquals(2, ranks[farNeutral.id.raw])
    }

    private fun seed(
        key: String,
        latitude: Double,
        longitude: Double,
        priorityRank: Int
    ) = TrafficPackedGroupLabelSeed(
        key = key,
        latitude = latitude,
        longitude = longitude,
        collisionWidthPx = 40f,
        collisionHeightPx = 40f,
        priorityRank = priorityRank
    )

    private fun ognTarget(
        id: String,
        distanceMeters: Double
    ) = OgnTrafficTarget(
        id = id,
        callsign = "CALL$id",
        destination = "APRS",
        latitude = -35.0,
        longitude = 149.0,
        altitudeMeters = 1_000.0,
        trackDegrees = 90.0,
        groundSpeedMps = 30.0,
        verticalSpeedMps = 0.0,
        deviceIdHex = id,
        signalDb = -10.0,
        displayLabel = id,
        identity = null,
        rawComment = null,
        rawLine = "raw-$id",
        timestampMillis = 1_000L,
        lastSeenMillis = 1_000L,
        distanceMeters = distanceMeters,
        addressType = OgnAddressType.FLARM,
        addressHex = id,
        canonicalKey = "FLARM:$id"
    )

    private fun adsbTarget(
        id: String,
        distanceMeters: Double,
        emergency: Boolean
    ) = AdsbTrafficUiModel(
        id = Icao24.from(id) ?: error("invalid ICAO24"),
        callsign = "CALL$id",
        lat = -35.0,
        lon = 149.0,
        altitudeM = 1_000.0,
        speedMps = 40.0,
        trackDeg = 90.0,
        climbMps = 0.0,
        ageSec = 1,
        isStale = false,
        distanceMeters = distanceMeters,
        bearingDegFromUser = 90.0,
        positionSource = 0,
        category = 0,
        lastContactEpochSec = null,
        isEmergencyCollisionRisk = emergency
    )

    private fun mapOfPoints(
        vararg entries: Pair<Pair<Double, Double>, PointF>,
        viewportBoundsPx: PackedGroupDisplayBounds? = null
    ): MapLibreMap {
        val map: MapLibreMap = mock()
        val projection: Projection = mock()
        whenever(map.projection).thenReturn(projection)
        if (viewportBoundsPx != null) {
            val farLeft = LatLng(1.0, 1.0)
            val farRight = LatLng(1.0, 2.0)
            val nearLeft = LatLng(0.0, 1.0)
            val nearRight = LatLng(0.0, 2.0)
            val visibleRegion = VisibleRegion(
                farLeft,
                farRight,
                nearLeft,
                nearRight,
                LatLngBounds.from(
                    1.0,
                    2.0,
                    0.0,
                    1.0
                )
            )
            whenever(projection.visibleRegion).thenReturn(visibleRegion)
            whenever(projection.toScreenLocation(farLeft)).thenReturn(
                PointF(viewportBoundsPx.left, viewportBoundsPx.top)
            )
            whenever(projection.toScreenLocation(farRight)).thenReturn(
                PointF(viewportBoundsPx.right, viewportBoundsPx.top)
            )
            whenever(projection.toScreenLocation(nearLeft)).thenReturn(
                PointF(viewportBoundsPx.left, viewportBoundsPx.bottom)
            )
            whenever(projection.toScreenLocation(nearRight)).thenReturn(
                PointF(viewportBoundsPx.right, viewportBoundsPx.bottom)
            )
        }
        val points = entries.toMap()
        whenever(projection.toScreenLocation(LatLng(0.0, 0.0)))
            .thenReturn(PointF())
        points.forEach { (coordinate, point) ->
            whenever(
                projection.toScreenLocation(
                    LatLng(coordinate.first, coordinate.second)
                )
            ).thenReturn(point)
        }
        return map
    }

    private infix fun Double.to(other: Double): Pair<Double, Double> = Pair(this, other)
}
