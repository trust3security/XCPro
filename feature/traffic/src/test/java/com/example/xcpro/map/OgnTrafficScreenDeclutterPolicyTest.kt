package com.example.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OgnTrafficScreenDeclutterPolicyTest {

    @Test
    fun nearbyProjectedTargets_groupIntoSingleCluster() {
        val renderItems = resolveOgnTrafficScreenDeclutter(
            projectedTargets = listOf(
                projectedTarget("A", screenX = 100.0, screenY = 100.0),
                projectedTarget("B", screenX = 132.0, screenY = 118.0)
            ),
            renderedIconSizePx = OGN_ICON_SIZE_DEFAULT_PX
        )

        val cluster = renderItems.single() as OgnTrafficRenderItem.Cluster
        assertEquals("cluster:FLARM:A|FLARM:B", cluster.clusterKey)
        assertEquals(2, cluster.memberCount)
        assertEquals(listOf("FLARM:A", "FLARM:B"), cluster.members.map { it.canonicalKey })
    }

    @Test
    fun distantProjectedTargets_remainSingles() {
        val renderItems = resolveOgnTrafficScreenDeclutter(
            projectedTargets = listOf(
                projectedTarget("A", screenX = 100.0, screenY = 100.0),
                projectedTarget("B", screenX = 260.0, screenY = 240.0)
            ),
            renderedIconSizePx = OGN_ICON_SIZE_DEFAULT_PX
        )

        assertEquals(
            listOf("FLARM:A", "FLARM:B"),
            renderItems.map { (it as OgnTrafficRenderItem.Single).target.canonicalKey }
        )
    }

    @Test
    fun clusterIdentity_isStableAcrossInputOrder() {
        val forward = resolveOgnTrafficScreenDeclutter(
            projectedTargets = listOf(
                projectedTarget("A", screenX = 100.0, screenY = 100.0),
                projectedTarget("B", screenX = 120.0, screenY = 112.0),
                projectedTarget("C", screenX = 136.0, screenY = 126.0)
            ),
            renderedIconSizePx = 96
        )
        val reversed = resolveOgnTrafficScreenDeclutter(
            projectedTargets = listOf(
                projectedTarget("C", screenX = 136.0, screenY = 126.0),
                projectedTarget("B", screenX = 120.0, screenY = 112.0),
                projectedTarget("A", screenX = 100.0, screenY = 100.0)
            ),
            renderedIconSizePx = 96
        )

        assertEquals(forward, reversed)
    }

    @Test
    fun invalidScreenPoints_areDroppedWithoutAffectingValidTargets() {
        val renderItems = resolveOgnTrafficScreenDeclutter(
            projectedTargets = listOf(
                projectedTarget("A", screenX = Double.NaN, screenY = 100.0),
                projectedTarget("B", screenX = 120.0, screenY = 100.0)
            ),
            renderedIconSizePx = OGN_ICON_SIZE_DEFAULT_PX
        )

        val single = renderItems.single() as OgnTrafficRenderItem.Single
        assertEquals("FLARM:B", single.target.canonicalKey)
    }

    @Test
    fun grouping_isTransitiveAcrossConnectedTargets() {
        val renderItems = resolveOgnTrafficScreenDeclutter(
            projectedTargets = listOf(
                projectedTarget("A", screenX = 100.0, screenY = 100.0),
                projectedTarget("B", screenX = 145.0, screenY = 100.0),
                projectedTarget("C", screenX = 190.0, screenY = 100.0)
            ),
            renderedIconSizePx = 72
        )

        val cluster = renderItems.single() as OgnTrafficRenderItem.Cluster
        assertTrue(cluster.members.any { it.canonicalKey == "FLARM:A" })
        assertTrue(cluster.members.any { it.canonicalKey == "FLARM:B" })
        assertTrue(cluster.members.any { it.canonicalKey == "FLARM:C" })
    }

    private fun projectedTarget(
        id: String,
        screenX: Double,
        screenY: Double
    ): OgnProjectedTrafficTarget = OgnProjectedTrafficTarget(
        target = target(id),
        screenX = screenX,
        screenY = screenY
    )

    private fun target(id: String): OgnTrafficTarget = OgnTrafficTarget(
        id = id,
        callsign = "CALL$id",
        destination = "APRS",
        latitude = -35.0 + id.first().code / 10_000.0,
        longitude = 149.0 + id.first().code / 10_000.0,
        altitudeMeters = 900.0,
        trackDegrees = 90.0,
        groundSpeedMps = 32.0,
        verticalSpeedMps = 0.8,
        deviceIdHex = "HEX$id",
        signalDb = -10.0,
        displayLabel = id,
        identity = null,
        rawComment = null,
        rawLine = "raw-$id",
        timestampMillis = 1_000L,
        lastSeenMillis = 1_000L,
        distanceMeters = 500.0,
        addressType = OgnAddressType.FLARM,
        addressHex = id,
        canonicalKey = "FLARM:$id"
    )
}
