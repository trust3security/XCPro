package com.example.xcpro.ogn

import com.example.xcpro.core.time.FakeClock
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketAddress
import java.nio.charset.StandardCharsets
import kotlin.collections.ArrayDeque
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
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

        advanceTimeBy(1_000L)
        runCurrent()

        val firstLogin = firstSocket.loginLine()
        val secondLogin = secondSocket.loginLine()
        assertTrue(firstLogin?.contains("filter r/0.00000/0.00000/150") == true)
        assertTrue(secondLogin?.contains("filter r/0.50000/0.00000/150") == true)

        repository.stop()
        runCurrent()
    }

    private fun newRepository(dispatcher: kotlinx.coroutines.CoroutineDispatcher): OgnTrafficRepositoryImpl {
        val ddbRepository: OgnDdbRepository = mock()
        val preferencesRepository: OgnTrafficPreferencesRepository = mock()
        whenever(preferencesRepository.ownFlarmHexFlow).thenReturn(MutableStateFlow(null))
        whenever(preferencesRepository.ownIcaoHexFlow).thenReturn(MutableStateFlow(null))
        return OgnTrafficRepositoryImpl(
            parser = OgnAprsLineParser(),
            ddbRepository = ddbRepository,
            preferencesRepository = preferencesRepository,
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )
    }

    private class ScriptedSocket(
        script: String,
        private val onFirstLineDelivered: (() -> Unit)? = null
    ) : Socket() {
        private val outputBytes = ByteArrayOutputStream()
        private val scriptBytes = script.toByteArray(StandardCharsets.ISO_8859_1)
        private var scriptIndex = 0
        private var firstLineDelivered = false

        override fun setTcpNoDelay(on: Boolean) = Unit
        override fun setKeepAlive(on: Boolean) = Unit
        override fun setSoTimeout(timeout: Int) = Unit
        override fun connect(endpoint: SocketAddress?, timeout: Int) = Unit
        override fun close() = Unit

        override fun getOutputStream(): OutputStream = outputBytes

        override fun getInputStream(): InputStream = object : InputStream() {
            override fun read(): Int {
                if (scriptIndex >= scriptBytes.size) return -1
                val value = scriptBytes[scriptIndex++].toInt() and 0xFF
                if (!firstLineDelivered && value == '\n'.code) {
                    firstLineDelivered = true
                    onFirstLineDelivered?.invoke()
                }
                return value
            }
        }

        fun loginLine(): String? {
            return outputBytes
                .toString(StandardCharsets.ISO_8859_1.name())
                .lineSequence()
                .firstOrNull { it.startsWith("user ") }
        }
    }
}
