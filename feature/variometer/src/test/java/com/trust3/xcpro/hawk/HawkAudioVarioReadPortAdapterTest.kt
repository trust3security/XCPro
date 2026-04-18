package com.trust3.xcpro.hawk

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class HawkAudioVarioReadPortAdapterTest {

    @Test
    fun emits_only_finite_audio_vario_samples() = runTest {
        val output = MutableStateFlow<HawkOutput?>(null)
        val repository = mock<HawkVarioRepository> {
            on { this.output } doReturn output
        }
        val adapter = HawkAudioVarioReadPortAdapter(repository)
        val collected = mutableListOf<Double?>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            adapter.audioVarioMps.take(3).toList(collected)
        }

        output.value = hawkOutput(vAudioMps = 1.5)
        output.value = hawkOutput(vAudioMps = Double.NaN)
        advanceUntilIdle()

        assertEquals(listOf(null, 1.5, null), collected)
        job.cancel()
    }

    private fun hawkOutput(vAudioMps: Double?): HawkOutput = HawkOutput(
        vRawMps = null,
        vAudioMps = vAudioMps,
        accelVariance = null,
        baroInnovationMps = null,
        baroHz = null,
        lastUpdateMonoMs = 100L,
        lastBaroSampleMonoMs = 100L,
        lastAccelSampleMonoMs = 100L,
        accelReliable = true,
        baroSampleAccepted = true,
        baroRejectionRate = 0.0,
        lastBaroVarioMps = null
    )
}
