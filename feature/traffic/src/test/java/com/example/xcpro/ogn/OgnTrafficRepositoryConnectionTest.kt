package com.example.xcpro.ogn

import com.example.xcpro.core.time.Clock
import com.example.xcpro.core.time.FakeClock
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import kotlin.collections.ArrayDeque
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.atMost
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class OgnTrafficRepositoryConnectionTest {

    @Test
    fun connect_writesLoginFilterWithFiveDecimalCoordsAndRadiusKm() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = newRepository(dispatcher)
        val socket = ScriptedSocket(
            script = "# logresp OGNXC1 verified, server GLIDERN1\n"
        )
        repository.socketFactory = { socket }

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()

        val loginLine = socket.loginLine()
        assertTrue(loginLine != null)
        assertTrue(loginLine?.contains("filter r/-33.86880/151.20930/150") == true)

        repository.stop()
        runCurrent()
    }

    @Test
    fun centerMoveBeyondThreshold_reconnectsWithUpdatedFilterCenter() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = newRepository(dispatcher)
        val firstSocket = ScriptedSocket(
            script = "# logresp OGNXC1 verified, server GLIDERN1\n",
            onFirstLineDelivered = {
                repository.updateCenter(latitude = 0.50000, longitude = 0.0)
            }
        )
        val secondSocket = ScriptedSocket(
            script = "# logresp OGNXC1 verified, server GLIDERN1\n"
        )
        val sockets = ArrayDeque<ScriptedSocket>()
        sockets.addLast(firstSocket)
        sockets.addLast(secondSocket)
        repository.socketFactory = { sockets.removeFirst() }

        repository.updateCenter(latitude = 0.0, longitude = 0.0)
        repository.setEnabled(true)
        runCurrent()

        val firstLogin = firstSocket.loginLine()
        val secondLogin = secondSocket.loginLine()
        assertNotNull(secondLogin)
        assertTrue(firstLogin?.contains("filter r/0.00000/0.00000/150") == true)
        assertTrue(secondLogin?.contains("filter r/0.50000/0.00000/150") == true)
        assertTrue(repository.snapshot.value.connectionIssue == null)

        repository.stop()
        runCurrent()
    }

    @Test
    fun changedRadius_isAppliedToLoginFilter() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val radiusFlow = MutableStateFlow(OGN_RECEIVE_RADIUS_DEFAULT_KM)
        val repository = newRepository(
            dispatcher = dispatcher,
            receiveRadiusKmFlow = radiusFlow
        )
        val socket = ScriptedSocket(
            script = "# logresp OGNXC1 verified, server GLIDERN1\n"
        )
        repository.socketFactory = { socket }

        radiusFlow.value = 200
        repository.updateCenter(latitude = 46.0, longitude = 7.0)
        repository.setEnabled(true)
        runCurrent()

        val loginLine = socket.loginLine()
        assertTrue(loginLine != null)
        assertTrue(loginLine?.contains("filter r/46.00000/7.00000/200") == true)

        repository.stop()
        runCurrent()
    }

    @Test
    fun autoRadiusEnabled_usesFlightContextForLoginFilter() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val autoReceiveRadiusEnabledFlow = MutableStateFlow(true)
        val repository = newRepository(
            dispatcher = dispatcher,
            autoReceiveRadiusEnabledFlow = autoReceiveRadiusEnabledFlow
        )
        val socket = ScriptedSocket(
            script = "# logresp OGNXC1 verified, server GLIDERN1\n"
        )
        repository.socketFactory = { socket }

        repository.updateAutoReceiveRadiusContext(
            zoomLevel = 6.0f,
            groundSpeedMs = 40.0,
            isFlying = true
        )
        repository.updateCenter(latitude = 46.0, longitude = 7.0)
        repository.setEnabled(true)
        runCurrent()

        val loginLine = socket.loginLine()
        assertTrue(loginLine != null)
        assertTrue(loginLine?.contains("filter r/46.00000/7.00000/220") == true)

        repository.stop()
        runCurrent()
    }

    @Test
    fun connect_usesPersistedClientCallsignInLogin() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clientCallsignFlow = MutableStateFlow<String?>("XCPA1B2C3")
        val repository = newRepository(
            dispatcher = dispatcher,
            clientCallsignFlow = clientCallsignFlow
        )
        val socket = ScriptedSocket(
            script = "# logresp XCPA1B2C3 verified, server GLIDERN1\n"
        )
        repository.socketFactory = { socket }

        repository.updateCenter(latitude = 46.0, longitude = 7.0)
        repository.setEnabled(true)
        runCurrent()

        val loginLine = socket.loginLine()
        assertTrue(loginLine != null)
        assertTrue(loginLine?.startsWith("user XCPA1B2C3 pass ") == true)

        repository.stop()
        runCurrent()
    }

    @Test
    fun connect_withoutPersistedCallsign_usesGeneratedClientCallsignImmediately() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clientCallsignFlow = MutableStateFlow<String?>(null)
        val repository = newRepository(
            dispatcher = dispatcher,
            clientCallsignFlow = clientCallsignFlow
        )
        val socket = ScriptedSocket(
            script = "# logresp XCPABC123 verified, server GLIDERN1\n"
        )
        repository.socketFactory = { socket }

        repository.updateCenter(latitude = 46.0, longitude = 7.0)
        repository.setEnabled(true)
        runCurrent()

        val loginLine = socket.loginLine()
        assertNotNull(loginLine)
        assertTrue(loginLine?.startsWith("user ") == true)
        assertTrue(
            loginLine
                ?.substringAfter("user ")
                ?.substringBefore(' ')
                ?.matches(Regex("^[A-Z][A-Z0-9]{2,8}$")) == true
        )

        repository.stop()
        runCurrent()
    }

    @Test
    fun timedTarget_thenUntimedRewindCandidate_keepsLatestTimedPosition() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = newRepository(dispatcher = dispatcher)
        val socket = ScriptedSocket(
            script = buildString {
                append("# logresp XCPTEST01 verified, server GLIDERN1\n")
                append("FLRABC123>APRS,qAS,EDER:/114500h5029.86N/00956.98E'342/049/A=005524 id0AABC123\n")
                append("FLRABC123>APRS,qAS,EDER:!5020.00N/00920.00E'342/049/A=005524 id0AABC123\n")
            }
        )
        repository.socketFactory = { socket }

        repository.updateCenter(latitude = 50.49, longitude = 9.95)
        repository.setEnabled(true)
        runCurrent()

        val target = repository.targets.value.firstOrNull()
        assertNotNull(target)
        assertTrue(target?.latitude?.let { kotlin.math.abs(it - 50.497666) < 1e-5 } == true)
        assertTrue(target?.longitude?.let { kotlin.math.abs(it - 9.949666) < 1e-5 } == true)
        assertTrue(repository.snapshot.value.droppedOutOfOrderSourceFrames >= 1L)

        repository.stop()
        runCurrent()
    }

    @Test
    fun timedThenUntimedFallback_thenOlderTimedFrame_keepsUntimedPosition() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = StepClock(
            monoMs = 0L,
            wallMs = 1_730_000_000_000L,
            monoStepMs = 20_000L,
            wallStepMs = 1_000L
        )
        val repository = newRepository(
            dispatcher = dispatcher,
            clock = clock
        )
        val socket = ScriptedSocket(
            script = buildString {
                append("# logresp XCPTEST01 verified, server GLIDERN1\n")
                append("FLRABC123>APRS,qAS,EDER:/114500h5029.86N/00956.98E'342/049/A=005524 id0AABC123\n")
                append("FLRABC123>APRS,qAS,EDER:!5029.88N/00957.00E'342/049/A=005524 id0AABC123\n")
                append("FLRABC123>APRS,qAS,EDER:/114400h5029.91N/00957.03E'342/049/A=005524 id0AABC123\n")
            }
        )
        repository.socketFactory = { socket }

        try {
            repository.updateCenter(latitude = 50.49, longitude = 9.95)
            repository.setEnabled(true)
            runCurrent()

            val target = repository.targets.value.firstOrNull()
            assertNotNull(target)
            assertTrue(target?.latitude?.let { kotlin.math.abs(it - 50.498000) < 1e-5 } == true)
            assertTrue(target?.longitude?.let { kotlin.math.abs(it - 9.950000) < 1e-5 } == true)
            assertTrue(repository.snapshot.value.droppedOutOfOrderSourceFrames >= 1L)
        } finally {
            repository.stop()
            runCurrent()
        }
    }

    @Test
    fun stalledInboundStream_errorsEvenWhenKeepaliveWritesOccur() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = StepClock(
            monoMs = 0L,
            wallMs = 3_700_000L,
            monoStepMs = 30_000L,
            wallStepMs = 0L
        )
        val repository = newRepository(
            dispatcher = dispatcher,
            clock = clock
        )
        val socket = TimeoutSocket()
        repository.socketFactory = { socket }

        repository.updateCenter(latitude = 46.0, longitude = 7.0)
        repository.setEnabled(true)
        runCurrent()

        val snapshot = repository.snapshot.value
        assertTrue(snapshot.connectionState == OgnConnectionState.ERROR)
        assertTrue(snapshot.connectionIssue == OgnConnectionIssue.STALL_TIMEOUT)
        assertTrue(snapshot.lastError == "StreamStalled")
        assertTrue(socket.writtenLines().any { line -> line == "#keepalive" })

        repository.stop()
        runCurrent()
    }

    @Test
    fun connectedSession_rechecksDdbRefreshWhileStreamActive() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = StepClock(
            monoMs = 0L,
            wallMs = 3_700_000L,
            monoStepMs = 70_000L,
            wallStepMs = 3_700_000L
        )
        val ddbRepository: OgnDdbRepository = mock()
        whenever(ddbRepository.refreshIfNeeded()).thenReturn(OgnDdbRefreshResult.Updated)
        val repository = newRepository(
            dispatcher = dispatcher,
            clock = clock,
            ddbRepository = ddbRepository
        )
        val socket = ScriptedSocket(
            script = buildString {
                append("# logresp XCPTEST01 verified, server GLIDERN1\n")
                append("# aprsc 2.1.20\n")
                append("# keepalive from server\n")
                append("# trace line\n")
            }
        )
        repository.socketFactory = { socket }

        try {
            repository.updateCenter(latitude = 46.0, longitude = 7.0)
            repository.setEnabled(true)
            runCurrent()

            verify(ddbRepository, atLeast(1)).refreshIfNeeded()
        } finally {
            repository.stop()
            runCurrent()
        }
    }

    @Test
    fun ddbRefreshFailure_retriesWithinMinutes_notHourly() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = StepClock(
            monoMs = 0L,
            wallMs = 0L,
            monoStepMs = 70_000L,
            wallStepMs = 1_000L
        )
        val ddbRepository: OgnDdbRepository = mock()
        doThrow(IllegalStateException("network"))
            .whenever(ddbRepository)
            .refreshIfNeeded()
        val repository = newRepository(
            dispatcher = dispatcher,
            clock = clock,
            ddbRepository = ddbRepository
        )
        val socket = ScriptedSocket(
            script = buildString {
                append("# logresp XCPTEST01 verified, server GLIDERN1\n")
                append("# aprsc 2.1.20\n")
                append("# keepalive from server\n")
                append("# trace line 1\n")
                append("# trace line 2\n")
                append("# trace line 3\n")
                append("# trace line 4\n")
            }
        )
        repository.socketFactory = { socket }

        try {
            repository.updateCenter(latitude = 46.0, longitude = 7.0)
            repository.setEnabled(true)
            runCurrent()
            advanceTimeBy(10_000L)
            runCurrent()

            verify(ddbRepository, atLeast(2)).refreshIfNeeded()
        } finally {
            repository.stop()
            runCurrent()
        }
    }

    @Test
    fun ddbRefreshNotDue_doesNotRelaunchEveryActiveCheckTick() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = StepClock(
            monoMs = 0L,
            wallMs = 0L,
            monoStepMs = 70_000L,
            wallStepMs = 1_000L
        )
        val ddbRepository: OgnDdbRepository = mock()
        whenever(ddbRepository.refreshIfNeeded()).thenReturn(OgnDdbRefreshResult.NotDue)
        val repository = newRepository(
            dispatcher = dispatcher,
            clock = clock,
            ddbRepository = ddbRepository
        )
        val socket = ScriptedSocket(
            script = buildString {
                append("# logresp XCPTEST01 verified, server GLIDERN1\n")
                append("# line 1\n")
                append("# line 2\n")
                append("# line 3\n")
                append("# line 4\n")
                append("# line 5\n")
                append("# line 6\n")
            }
        )
        repository.socketFactory = { socket }

        try {
            repository.updateCenter(latitude = 46.0, longitude = 7.0)
            repository.setEnabled(true)
            runCurrent()
            advanceTimeBy(10_000L)
            runCurrent()

            verify(ddbRepository, atLeast(1)).refreshIfNeeded()
            verify(ddbRepository, atMost(2)).refreshIfNeeded()
        } finally {
            repository.stop()
            runCurrent()
        }
    }

}
