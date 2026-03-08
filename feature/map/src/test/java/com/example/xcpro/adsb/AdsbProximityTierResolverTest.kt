package com.example.xcpro.adsb

import java.util.concurrent.ConcurrentHashMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbProximityTierResolverTest {

    @Test
    fun resolve_withoutOwnshipReference_returnsNeutralAndClearsResolvedTierState() {
        val distanceState = ConcurrentHashMap<Icao24, AdsbProximityTier>()
        val resolvedState = ConcurrentHashMap<Icao24, AdsbProximityTier>()
        val resolver = AdsbProximityTierResolver(distanceState, resolvedState)
        val targetId = Icao24.from("abc123") ?: error("invalid id")

        resolvedState[targetId] = AdsbProximityTier.RED
        val tier = resolver.resolve(
            targetId = targetId,
            distanceMeters = 500.0,
            hasOwnshipReference = false,
            isVerticalNonThreat = false,
            hasFreshTrendSample = true,
            showClosingAlert = true,
            postPassDivergingSampleCount = 0,
            isCirclingEmergencyRedRule = false,
            isEmergencyCollisionRisk = false
        )

        assertEquals(AdsbProximityTier.NEUTRAL, tier)
        assertTrue(resolvedState.isEmpty())
    }

    @Test
    fun resolve_redFreshPostPass_deEscalatesToAmberThenGreen() {
        val distanceState = ConcurrentHashMap<Icao24, AdsbProximityTier>()
        val resolvedState = ConcurrentHashMap<Icao24, AdsbProximityTier>()
        val resolver = AdsbProximityTierResolver(distanceState, resolvedState)
        val targetId = Icao24.from("def456") ?: error("invalid id")

        val closingTier = resolver.resolve(
            targetId = targetId,
            distanceMeters = 1_000.0,
            hasOwnshipReference = true,
            isVerticalNonThreat = false,
            hasFreshTrendSample = true,
            showClosingAlert = true,
            postPassDivergingSampleCount = 0,
            isCirclingEmergencyRedRule = false,
            isEmergencyCollisionRisk = false
        )
        val amberTier = resolver.resolve(
            targetId = targetId,
            distanceMeters = 1_000.0,
            hasOwnshipReference = true,
            isVerticalNonThreat = false,
            hasFreshTrendSample = true,
            showClosingAlert = false,
            postPassDivergingSampleCount = 1,
            isCirclingEmergencyRedRule = false,
            isEmergencyCollisionRisk = false
        )
        val greenTier = resolver.resolve(
            targetId = targetId,
            distanceMeters = 1_000.0,
            hasOwnshipReference = true,
            isVerticalNonThreat = false,
            hasFreshTrendSample = true,
            showClosingAlert = false,
            postPassDivergingSampleCount = 2,
            isCirclingEmergencyRedRule = false,
            isEmergencyCollisionRisk = false
        )

        assertEquals(AdsbProximityTier.RED, closingTier)
        assertEquals(AdsbProximityTier.AMBER, amberTier)
        assertEquals(AdsbProximityTier.GREEN, greenTier)
    }
}
