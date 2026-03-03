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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal fun newRepository(
    dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    receiveRadiusKmFlow: MutableStateFlow<Int> = MutableStateFlow(OGN_RECEIVE_RADIUS_DEFAULT_KM),
    autoReceiveRadiusEnabledFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
    clientCallsignFlow: MutableStateFlow<String?> = MutableStateFlow("XCPTEST01"),
    clock: Clock = FakeClock(monoMs = 0L, wallMs = 0L),
    ddbRepository: OgnDdbRepository? = null
): OgnTrafficRepositoryImpl {
    val resolvedDdbRepository = ddbRepository ?: mock<OgnDdbRepository>().also { repository ->
        runBlocking {
            whenever(repository.refreshIfNeeded()).thenReturn(OgnDdbRefreshResult.Updated)
        }
    }
    val preferencesRepository: OgnTrafficPreferencesRepository = mock()
    whenever(preferencesRepository.ownFlarmHexFlow).thenReturn(MutableStateFlow(null))
    whenever(preferencesRepository.ownIcaoHexFlow).thenReturn(MutableStateFlow(null))
    whenever(preferencesRepository.receiveRadiusKmFlow).thenReturn(receiveRadiusKmFlow)
    whenever(preferencesRepository.autoReceiveRadiusEnabledFlow).thenReturn(autoReceiveRadiusEnabledFlow)
    whenever(preferencesRepository.clientCallsignFlow).thenReturn(clientCallsignFlow)
    return OgnTrafficRepositoryImpl(
        parser = OgnAprsLineParser(),
        ddbRepository = resolvedDdbRepository,
        preferencesRepository = preferencesRepository,
        clock = clock,
        dispatcher = dispatcher
    )
}

internal class ScriptedSocket(
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

internal class TimeoutSocket : Socket() {
    private val outputBytes = ByteArrayOutputStream()

    override fun setTcpNoDelay(on: Boolean) = Unit
    override fun setKeepAlive(on: Boolean) = Unit
    override fun setSoTimeout(timeout: Int) = Unit
    override fun connect(endpoint: SocketAddress?, timeout: Int) = Unit
    override fun close() = Unit

    override fun getOutputStream(): OutputStream = outputBytes

    override fun getInputStream(): InputStream = object : InputStream() {
        override fun read(): Int {
            throw SocketTimeoutException("read timeout")
        }
    }

    fun writtenLines(): List<String> =
        outputBytes
            .toString(StandardCharsets.ISO_8859_1.name())
            .lineSequence()
            .toList()
}

internal class StepClock(
    private var monoMs: Long,
    private var wallMs: Long,
    private val monoStepMs: Long,
    private val wallStepMs: Long
) : Clock {
    override fun nowMonoMs(): Long {
        val value = monoMs
        monoMs += monoStepMs
        return value
    }

    override fun nowWallMs(): Long {
        val value = wallMs
        wallMs += wallStepMs
        return value
    }
}
