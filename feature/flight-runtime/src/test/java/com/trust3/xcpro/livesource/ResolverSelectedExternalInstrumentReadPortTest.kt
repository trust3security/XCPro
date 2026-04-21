package com.trust3.xcpro.livesource

import com.trust3.xcpro.external.ExternalInstrumentFlightSnapshot
import com.trust3.xcpro.external.ExternalInstrumentReadPort
import com.trust3.xcpro.external.TimedExternalValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResolverSelectedExternalInstrumentReadPortTest {

    @Test
    fun selected_port_follows_live_source_selection() = runTest {
        val liveSourceStatePort = FakeLiveSourceStatePort()
        val defaultSource = FakeExternalInstrumentReadPort(
            ExternalInstrumentFlightSnapshot(
                pressureAltitudeM = TimedExternalValue(100.0, 1_000L)
            )
        )
        val condorSource = FakeExternalInstrumentReadPort(
            ExternalInstrumentFlightSnapshot(
                pressureAltitudeM = TimedExternalValue(200.0, 2_000L)
            )
        )
        val selected = ResolverSelectedExternalInstrumentReadPort(
            liveSourceStatePort = liveSourceStatePort,
            defaultSource = defaultSource,
            condorSource = condorSource,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        advanceUntilIdle()
        assertEquals(100.0, selected.externalFlightSnapshot.value.pressureAltitudeM?.value ?: Double.NaN, 0.0)

        liveSourceStatePort.setState(
            liveSourceStatePort.state.value.copy(
                selectedExternalInstrumentSource =
                    SelectedLiveExternalInstrumentSource.CONDOR_SIMULATOR
            )
        )
        advanceUntilIdle()

        assertEquals(200.0, selected.externalFlightSnapshot.value.pressureAltitudeM?.value ?: Double.NaN, 0.0)
    }

    private class FakeLiveSourceStatePort : LiveSourceStatePort {
        private val mutableState = MutableStateFlow(ResolvedLiveSourceState())

        override val state: StateFlow<ResolvedLiveSourceState> = mutableState.asStateFlow()

        override fun refreshAndGetState(): ResolvedLiveSourceState = state.value

        fun setState(value: ResolvedLiveSourceState) {
            mutableState.value = value
        }
    }

    private class FakeExternalInstrumentReadPort(
        initialValue: ExternalInstrumentFlightSnapshot
    ) : ExternalInstrumentReadPort {
        private val mutableState = MutableStateFlow(initialValue)

        override val externalFlightSnapshot: StateFlow<ExternalInstrumentFlightSnapshot> =
            mutableState.asStateFlow()
    }
}
