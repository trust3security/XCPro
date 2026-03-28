package com.example.xcpro.map

import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.ogn.OgnTrafficIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OgnTrafficOverlayFeatureScreenDeclutterTest {

    @Test
    fun singleRenderItem_writesTargetIdentityAndResolvesTargetHit() {
        val target = target("A1")
        val feature = buildFeatures(
            renderItems = listOf(OgnTrafficRenderItem.Single(target))
        ).single()

        assertEquals(target.canonicalKey, feature.getStringProperty(PROP_TARGET_KEY))
        assertEquals(target.id, feature.getStringProperty(PROP_TARGET_ID))
        assertEquals("", feature.getStringProperty(PROP_CLUSTER_COUNT_LABEL))
        assertFalse(feature.hasProperty(PROP_CLUSTER_KEY))

        val hit = resolveOgnTrafficHitResult(feature) as OgnTrafficHitResult.Target
        assertEquals(target.canonicalKey, hit.targetKey)
    }

    @Test
    fun clusterRenderItem_writesClusterPropsAndResolvesClusterHit() {
        val first = target("A1")
        val second = target("B2")
        val cluster = OgnTrafficRenderItem.Cluster(
            clusterKey = resolveOgnTrafficClusterKey(listOf(first, second)),
            centerLatitude = -35.05,
            centerLongitude = 149.05,
            members = listOf(first, second)
        )

        val feature = buildFeatures(renderItems = listOf(cluster)).single()
        val expectedRepresentativeIcon = resolveOgnStyleImageId(
            icon = iconForOgnAircraftIdentity(
                aircraftTypeCode = first.identity?.aircraftTypeCode,
                competitionNumber = first.identity?.competitionNumber
            ),
            useSatelliteContrastIcons = false
        )

        assertEquals(expectedRepresentativeIcon, feature.getStringProperty(PROP_ICON_ID))
        assertEquals("2", feature.getStringProperty(PROP_CLUSTER_COUNT_LABEL))
        assertEquals(cluster.clusterKey, feature.getStringProperty(PROP_CLUSTER_KEY))
        assertEquals(2, feature.getNumberProperty(PROP_CLUSTER_COUNT).toInt())
        assertEquals(-35.05, feature.getNumberProperty(PROP_CLUSTER_LAT).toDouble(), 0.0)
        assertEquals(149.05, feature.getNumberProperty(PROP_CLUSTER_LON).toDouble(), 0.0)
        assertEquals("", feature.getStringProperty(PROP_TOP_LABEL))
        assertEquals("", feature.getStringProperty(PROP_BOTTOM_LABEL))

        val hit = resolveOgnTrafficHitResult(feature) as OgnTrafficHitResult.Cluster
        assertEquals(cluster.clusterKey, hit.clusterKey)
        assertEquals(2, hit.memberCount)
        assertEquals(-35.05, hit.centerLatitude, 0.0)
        assertEquals(149.05, hit.centerLongitude, 0.0)
    }

    @Test
    fun mixedRenderItems_preserveStableFeatureIdentityOrdering() {
        val single = OgnTrafficRenderItem.Single(target("A1"))
        val cluster = OgnTrafficRenderItem.Cluster(
            clusterKey = "cluster:FLARM:B2|FLARM:C3",
            centerLatitude = -35.02,
            centerLongitude = 149.03,
            members = listOf(target("B2"), target("C3"))
        )

        val features = buildFeatures(renderItems = listOf(single, cluster))

        assertEquals(2, features.size)
        assertTrue(features[0].hasProperty(PROP_TARGET_KEY))
        assertTrue(features[1].hasProperty(PROP_CLUSTER_KEY))
    }

    private fun buildFeatures(
        renderItems: List<OgnTrafficRenderItem>
    ) = buildOgnTrafficOverlayFeatures(
        nowMonoMs = 10_000L,
        renderItems = renderItems,
        ownshipAltitudeMeters = 1_000.0,
        altitudeUnit = AltitudeUnit.METERS,
        useSatelliteContrastIcons = false,
        unitsPreferences = UnitsPreferences(),
        staleVisualAfterMs = 5_000L,
        liveAlpha = 1.0,
        staleAlpha = 0.5
    )

    private fun target(id: String): OgnTrafficTarget = OgnTrafficTarget(
        id = id,
        callsign = "CALL$id",
        destination = "APRS",
        latitude = -35.0,
        longitude = 149.0,
        altitudeMeters = 1_050.0,
        trackDegrees = 90.0,
        groundSpeedMps = 35.0,
        verticalSpeedMps = 1.2,
        deviceIdHex = "HEX$id",
        signalDb = -12.0,
        displayLabel = id,
        identity = OgnTrafficIdentity(
            registration = "VH-$id",
            competitionNumber = id,
            aircraftModel = "Glider",
            tracked = true,
            identified = true,
            aircraftTypeCode = null
        ),
        rawComment = null,
        rawLine = "raw-$id",
        timestampMillis = 10_000L,
        lastSeenMillis = 10_000L,
        distanceMeters = 1_200.0,
        addressType = OgnAddressType.FLARM,
        addressHex = id,
        canonicalKey = "FLARM:$id"
    )
}
