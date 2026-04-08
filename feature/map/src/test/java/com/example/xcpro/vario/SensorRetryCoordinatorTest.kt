package com.example.xcpro.vario

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SensorRetryCoordinatorTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    @Test
    fun retriesUntilActionSucceeds() = scope.runTest {
        var attempts = 0
        val coordinator = SensorRetryCoordinator(this, dispatcher, retryDelayMs = 1_000L)

        coordinator.schedule {
            attempts++
            attempts >= 3
        }

        advanceTimeBy(999)
        runCurrent()
        assertEquals(0, attempts)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, attempts)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, attempts)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(3, attempts)
    }

    @Test
    fun cancelStopsFurtherRetries() = scope.runTest {
        var attempts = 0
        val coordinator = SensorRetryCoordinator(this, dispatcher, retryDelayMs = 1_000L)

        coordinator.schedule {
            attempts++
            false
        }

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, attempts)

        coordinator.cancel()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, attempts)
    }
}
