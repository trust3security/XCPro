package com.example.xcpro.hawk

import com.example.xcpro.core.time.FakeClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HawkVarioRepositoryTest {

    @Test
    fun live_source_emits_output_from_baro_samples() = runTest {
        val ports = FakeHawkSensorStreamPort()
        val activeSource = MutableStateFlow(HawkRuntimeSource.LIVE)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(
            sensorStreamPort = ports,
            activeSourcePort = activeSource,
            dispatcher = dispatcher
        )

        repository.start()
        advanceUntilIdle()

        ports.baro.tryEmit(HawkBaroSample(pressureHpa = 1013.25, monotonicTimestampMillis = 100L))
        advanceUntilIdle()

        assertNotNull(repository.output.value)
    }

    @Test
    fun replay_source_clears_output_and_ignores_new_samples() = runTest {
        val ports = FakeHawkSensorStreamPort()
        val activeSource = MutableStateFlow(HawkRuntimeSource.LIVE)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(
            sensorStreamPort = ports,
            activeSourcePort = activeSource,
            dispatcher = dispatcher
        )

        repository.start()
        advanceUntilIdle()
        ports.baro.tryEmit(HawkBaroSample(pressureHpa = 1013.25, monotonicTimestampMillis = 100L))
        advanceUntilIdle()
        assertNotNull(repository.output.value)

        activeSource.value = HawkRuntimeSource.REPLAY
        advanceUntilIdle()
        assertNull(repository.output.value)

        ports.baro.tryEmit(HawkBaroSample(pressureHpa = 1012.80, monotonicTimestampMillis = 200L))
        advanceUntilIdle()
        assertNull(repository.output.value)
    }

    private fun createRepository(
        sensorStreamPort: HawkSensorStreamPort,
        activeSourcePort: Flow<HawkRuntimeSource>,
        dispatcher: TestDispatcher
    ): HawkVarioRepository = HawkVarioRepository(
        sensorStreamPort = sensorStreamPort,
        activeSourcePort = object : HawkActiveSourcePort {
            override val activeSource: Flow<HawkRuntimeSource> = activeSourcePort
        },
        configRepository = HawkConfigRepository(),
        engine = HawkVarioEngine(),
        clock = FakeClock(),
        dispatcher = dispatcher
    )
}

private class FakeHawkSensorStreamPort : HawkSensorStreamPort {
    val baro = MutableSharedFlow<HawkBaroSample>(extraBufferCapacity = 4)
    val accel = MutableSharedFlow<HawkAccelSample>(extraBufferCapacity = 4)

    override val baroSamples: Flow<HawkBaroSample> = baro
    override val accelSamples: Flow<HawkAccelSample> = accel
}
