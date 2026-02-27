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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
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

        val firstLogin = firstSocket.loginLine()
        val secondLogin = secondSocket.loginLine()
        assertNotNull(secondLogin)
        assertTrue(firstLogin?.contains("filter r/0.00000/0.00000/150") == true)
        assertTrue(secondLogin?.contains("filter r/0.50000/0.00000/150") == true)

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

    private fun newRepository(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        receiveRadiusKmFlow: MutableStateFlow<Int> = MutableStateFlow(OGN_RECEIVE_RADIUS_DEFAULT_KM),
        autoReceiveRadiusEnabledFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
        clientCallsignFlow: MutableStateFlow<String?> = MutableStateFlow("XCPTEST01")
    ): OgnTrafficRepositoryImpl {
        val ddbRepository: OgnDdbRepository = mock()
        val preferencesRepository: OgnTrafficPreferencesRepository = mock()
        whenever(preferencesRepository.ownFlarmHexFlow).thenReturn(MutableStateFlow(null))
        whenever(preferencesRepository.ownIcaoHexFlow).thenReturn(MutableStateFlow(null))
        whenever(preferencesRepository.receiveRadiusKmFlow).thenReturn(receiveRadiusKmFlow)
        whenever(preferencesRepository.autoReceiveRadiusEnabledFlow).thenReturn(autoReceiveRadiusEnabledFlow)
        whenever(preferencesRepository.clientCallsignFlow).thenReturn(clientCallsignFlow)
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
