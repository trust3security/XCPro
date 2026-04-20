package com.trust3.xcpro.simulator.condor

import com.trust3.xcpro.core.time.FakeClock
import com.trust3.xcpro.simulator.CondorLiveDegradedReason
import com.trust3.xcpro.simulator.CondorReconnectState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CondorTcpBridgeTransportTest {

    @Test
    fun reconnect_transitions_waiting_attempting_and_exhausted() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock()
        val tcpServerPort = FakeCondorTcpServerPort().apply {
            keepSessionOpen = true
        }
        val liveSampleRepository = CondorLiveSampleRepository(
            parser = CondorSentenceParser(),
            clock = clock
        )
        val transport = CondorTcpBridgeTransport(
            tcpServerPort = tcpServerPort,
            clock = clock,
            liveSampleRepository = liveSampleRepository,
            dispatcher = dispatcher
        )
        try {
            transport.connect(4_353)
            runCurrent()

            tcpServerPort.mutableConnectionState.value =
                CondorTcpServerState.Connected(port = 4_353, remoteAddress = "192.168.1.50")
            runCurrent()

            tcpServerPort.mutableConnectionState.value =
                CondorTcpServerState.Error(
                    port = 4_353,
                    error = CondorTcpServerError.READ_FAILED,
                    detail = "TCP connection failed while reading data."
                )
            runCurrent()
            assertEquals(CondorReconnectState.WAITING, transport.state.value.reconnect)

            advanceTimeBy(1_000L)
            runCurrent()
            assertEquals(CondorReconnectState.ATTEMPTING, transport.state.value.reconnect)

            tcpServerPort.mutableConnectionState.value =
                CondorTcpServerState.Listening(port = 4_353)
            runCurrent()
            tcpServerPort.mutableConnectionState.value =
                CondorTcpServerState.Error(
                    port = 4_353,
                    error = CondorTcpServerError.READ_FAILED,
                    detail = "TCP connection failed while reading data."
                )
            runCurrent()
            advanceTimeBy(2_000L)
            runCurrent()
            assertEquals(CondorReconnectState.ATTEMPTING, transport.state.value.reconnect)

            tcpServerPort.mutableConnectionState.value =
                CondorTcpServerState.Listening(port = 4_353)
            runCurrent()
            tcpServerPort.mutableConnectionState.value =
                CondorTcpServerState.Error(
                    port = 4_353,
                    error = CondorTcpServerError.READ_FAILED,
                    detail = "TCP connection failed while reading data."
                )
            runCurrent()
            advanceTimeBy(5_000L)
            runCurrent()
            assertEquals(CondorReconnectState.ATTEMPTING, transport.state.value.reconnect)

            tcpServerPort.mutableConnectionState.value =
                CondorTcpServerState.Listening(port = 4_353)
            runCurrent()
            tcpServerPort.mutableConnectionState.value =
                CondorTcpServerState.Error(
                    port = 4_353,
                    error = CondorTcpServerError.READ_FAILED,
                    detail = "TCP connection failed while reading data."
                )
            runCurrent()
            assertEquals(CondorReconnectState.EXHAUSTED, transport.state.value.reconnect)
        } finally {
            transport.shutdown()
        }
    }

    @Test
    fun emitted_chunks_feed_existing_sample_repository() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(wallMs = 10_000L)
        val tcpServerPort = FakeCondorTcpServerPort().apply {
            chunksForNextOpen = listOf(
                chunk(
                    "\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,\n",
                    1_000L
                ),
                chunk(
                    "\$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W\n",
                    1_100L
                ),
                chunk("\$LXWP0,Y,88.4,654.1,1.12\n", 1_200L)
            )
        }
        val liveSampleRepository = CondorLiveSampleRepository(
            parser = CondorSentenceParser(),
            clock = clock
        )
        val transport = CondorTcpBridgeTransport(
            tcpServerPort = tcpServerPort,
            clock = clock,
            liveSampleRepository = liveSampleRepository,
            dispatcher = dispatcher
        )
        try {
            transport.connect(4_353)
            runCurrent()

            assertNotNull(liveSampleRepository.gpsFlow.value)
            assertNotNull(liveSampleRepository.airspeedFlow.value)
        } finally {
            transport.shutdown()
        }
    }

    @Test
    fun bind_failure_surfaces_transport_error_detail() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock()
        val tcpServerPort = FakeCondorTcpServerPort()
        val liveSampleRepository = CondorLiveSampleRepository(
            parser = CondorSentenceParser(),
            clock = clock
        )
        val transport = CondorTcpBridgeTransport(
            tcpServerPort = tcpServerPort,
            clock = clock,
            liveSampleRepository = liveSampleRepository,
            dispatcher = dispatcher
        )
        try {
            transport.connect(4_353)
            runCurrent()

            tcpServerPort.mutableConnectionState.value =
                CondorTcpServerState.Error(
                    port = 4_353,
                    error = CondorTcpServerError.BIND_FAILED,
                    detail = "Could not listen on port 4353."
                )
            runCurrent()

            assertEquals(CondorLiveDegradedReason.TRANSPORT_ERROR, transport.state.value.lastFailure)
            assertEquals("Could not listen on port 4353.", transport.state.value.lastFailureDetail)
        } finally {
            transport.shutdown()
        }
    }

    private fun chunk(text: String, receivedMonoMs: Long): CondorReadChunk =
        CondorReadChunk(
            bytes = text.toByteArray(Charsets.US_ASCII),
            receivedMonoMs = receivedMonoMs
        )
}
