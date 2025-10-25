package com.example.xcpro.map.ballast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BallastModelsTest {

    @Test
    fun `snapshot clamps current kg and ratio`() {
        val snapshot = BallastSnapshot.create(currentKg = 250.0, maxKg = 200.0)

        assertEquals(200.0, snapshot.currentKg, 0.001)
        assertEquals(200.0, snapshot.maxKg, 0.001)
        assertEquals(1f, snapshot.ratio, 0.001f)
    }

    @Test
    fun `snapshot reports fill and drain availability`() {
        val snapshot = BallastSnapshot.create(currentKg = 100.0, maxKg = 200.0)

        assertTrue(snapshot.hasBallast)
        assertTrue(snapshot.canFill)
        assertTrue(snapshot.canDrain)
    }

    @Test
    fun `ui state enables buttons based on mode`() {
        val idleState = BallastUiState(
            snapshot = BallastSnapshot.create(50.0, 200.0)
        )
        assertTrue(idleState.isFillEnabled)
        assertTrue(idleState.isDrainEnabled)

        val fillingState = idleState.withMode(
            mode = BallastMode.Filling,
            durationMillis = 20_000L,
            targetKg = 200.0
        )
        assertFalse(fillingState.isFillEnabled)
        assertTrue(fillingState.isDrainEnabled)

        val drainingState = idleState.withMode(
            mode = BallastMode.Draining,
            durationMillis = 180_000L,
            targetKg = 0.0
        )
        assertTrue(drainingState.isFillEnabled)
        assertFalse(drainingState.isDrainEnabled)
    }
}
