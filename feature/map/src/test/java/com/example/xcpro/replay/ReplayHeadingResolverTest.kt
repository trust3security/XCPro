package com.example.xcpro.replay

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplayHeadingResolverTest {

    @Test
    fun `resolver reuses previous heading when distance is below threshold`() {
        val resolver = ReplayHeadingResolver()

        val stableHeading = resolver.resolve(
            movement = MovementSnapshot(
                speedMs = 12.0,
                distanceMeters = 12.0,
                east = 0.0,
                north = 1.0
            )
        )

        val reusedHeading = resolver.resolve(
            movement = MovementSnapshot(
                speedMs = 12.0,
                distanceMeters = 1.0,
                east = 1.0,
                north = 0.0
            )
        )

        assertEquals(stableHeading, reusedHeading, 0.0f)
    }
}
