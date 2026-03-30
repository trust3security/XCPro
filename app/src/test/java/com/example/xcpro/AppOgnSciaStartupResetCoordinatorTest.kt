package com.example.xcpro

import com.example.xcpro.ogn.OgnSciaStartupResetState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
class AppOgnSciaStartupResetCoordinatorTest {

    @Test
    fun `startIfNeeded launches async reset and completes`() = runTest {
        val resetter = mock<SciaStartupResetter>()
        val coordinator = AppOgnSciaStartupResetCoordinator(
            sciaStartupResetterProvider = FixedProvider(resetter),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        coordinator.startIfNeeded()

        assertEquals(OgnSciaStartupResetState.RUNNING, coordinator.resetState.value)

        runCurrent()

        verifyBlocking(resetter) { resetForFreshProcessStart() }
        assertEquals(OgnSciaStartupResetState.COMPLETED, coordinator.resetState.value)
    }

    @Test
    fun `startIfNeeded is idempotent`() = runTest {
        val resetter = mock<SciaStartupResetter>()
        val coordinator = AppOgnSciaStartupResetCoordinator(
            sciaStartupResetterProvider = FixedProvider(resetter),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        coordinator.startIfNeeded()
        coordinator.startIfNeeded()
        runCurrent()

        verifyBlocking(resetter, times(1)) { resetForFreshProcessStart() }
    }

    @Test
    fun `startIfNeeded failure keeps failed state`() = runTest {
        val resetter = mock<SciaStartupResetter>()
        runBlocking {
            whenever(resetter.resetForFreshProcessStart()).thenThrow(IllegalStateException("boom"))
        }
        val coordinator = AppOgnSciaStartupResetCoordinator(
            sciaStartupResetterProvider = FixedProvider(resetter),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        coordinator.startIfNeeded()
        runCurrent()

        assertEquals(OgnSciaStartupResetState.FAILED, coordinator.resetState.value)
    }

    private class FixedProvider<T>(
        private val value: T
    ) : Provider<T> {
        override fun get(): T = value
    }
}
