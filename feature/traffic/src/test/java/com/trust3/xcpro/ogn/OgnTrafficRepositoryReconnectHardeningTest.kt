package com.trust3.xcpro.ogn

import kotlin.collections.ArrayDeque
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OgnTrafficRepositoryReconnectHardeningTest {

    @Test
    fun repeatedCleanEof_escalatesBackoffAndPublishesUnexpectedStreamIssue() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = newRepository(dispatcher = dispatcher)
        val sockets = ArrayDeque<ScriptedSocket>().apply {
            addLast(ScriptedSocket(script = ""))
            addLast(ScriptedSocket(script = ""))
        }
        repository.socketFactory = { sockets.removeFirst() }

        try {
            repository.updateCenter(latitude = 46.0, longitude = 7.0)
            repository.setEnabled(true)
            runCurrent()

            assertEquals(OgnConnectionState.ERROR, repository.snapshot.value.connectionState)
            assertEquals(
                OgnConnectionIssue.UNEXPECTED_STREAM_END,
                repository.snapshot.value.connectionIssue
            )
            assertEquals(1_000L, repository.snapshot.value.reconnectBackoffMs)

            advanceTimeBy(1_000L)
            runCurrent()

            assertEquals(OgnConnectionState.ERROR, repository.snapshot.value.connectionState)
            assertEquals(
                OgnConnectionIssue.UNEXPECTED_STREAM_END,
                repository.snapshot.value.connectionIssue
            )
            assertEquals(2_000L, repository.snapshot.value.reconnectBackoffMs)
        } finally {
            repository.shutdownForTest()
            runCurrent()
        }
    }

    @Test
    fun offlineAtStart_waitsWithoutSocketChurnUntilNetworkReturns() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val networkPort = FakeOgnNetworkAvailabilityPort(initialOnline = false)
        val repository = newRepository(
            dispatcher = dispatcher,
            networkAvailabilityPort = networkPort
        )
        var connectAttempts = 0
        repository.socketFactory = {
            connectAttempts += 1
            ScriptedSocket(script = "# logresp OGNXC1 verified, server GLIDERN1\n")
        }

        try {
            repository.updateCenter(latitude = 46.0, longitude = 7.0)
            repository.setEnabled(true)
            runCurrent()

            assertEquals(0, connectAttempts)
            assertEquals(OgnConnectionState.ERROR, repository.snapshot.value.connectionState)
            assertEquals(OgnConnectionIssue.OFFLINE_WAIT, repository.snapshot.value.connectionIssue)
            assertTrue(!repository.snapshot.value.networkOnline)

            networkPort.setOnline(true)
            runCurrent()

            assertEquals(1, connectAttempts)
        } finally {
            repository.shutdownForTest()
            runCurrent()
        }
    }

    @Test
    fun offlineDuringBackoff_pausesReconnectUntilOnlineReturns() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val networkPort = FakeOgnNetworkAvailabilityPort(initialOnline = true)
        val repository = newRepository(
            dispatcher = dispatcher,
            networkAvailabilityPort = networkPort
        )
        val sockets = ArrayDeque<ScriptedSocket>().apply {
            addLast(ScriptedSocket(script = ""))
            addLast(ScriptedSocket(script = "# logresp OGNXC1 verified, server GLIDERN1\n"))
        }
        var connectAttempts = 0
        repository.socketFactory = {
            connectAttempts += 1
            sockets.removeFirst()
        }

        try {
            repository.updateCenter(latitude = 46.0, longitude = 7.0)
            repository.setEnabled(true)
            runCurrent()

            assertEquals(1, connectAttempts)
            assertEquals(1_000L, repository.snapshot.value.reconnectBackoffMs)

            networkPort.setOnline(false)
            advanceTimeBy(1_000L)
            runCurrent()

            assertEquals(1, connectAttempts)
            assertEquals(OgnConnectionIssue.OFFLINE_WAIT, repository.snapshot.value.connectionIssue)
            assertTrue(!repository.snapshot.value.networkOnline)

            networkPort.setOnline(true)
            runCurrent()

            assertEquals(2, connectAttempts)
        } finally {
            repository.shutdownForTest()
            runCurrent()
        }
    }

    @Test
    fun unverifiedLogin_surfacesStructuredIssue() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = newRepository(dispatcher = dispatcher)
        repository.socketFactory = {
            ScriptedSocket(script = "# logresp XCPTEST01 unverified, server GLIDERN1\n")
        }

        try {
            repository.updateCenter(latitude = 46.0, longitude = 7.0)
            repository.setEnabled(true)
            runCurrent()

            assertEquals(OgnConnectionState.ERROR, repository.snapshot.value.connectionState)
            assertEquals(
                OgnConnectionIssue.LOGIN_UNVERIFIED,
                repository.snapshot.value.connectionIssue
            )
            assertEquals("LoginUnverified", repository.snapshot.value.lastError)
        } finally {
            repository.shutdownForTest()
            runCurrent()
        }
    }
}
