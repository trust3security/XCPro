package com.example.xcpro.adsb
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OkHttpCoroutinesTest {

    @Test
    fun successfulResponse_staysOpenUntilCallerCloses() = runTest {
        val call = FakeCall()
        val deferred = async { call.awaitResponse() }
        runCurrent()

        val body = TrackingResponseBody("ok")
        call.deliverResponse(response(call.request(), body))
        runCurrent()

        val received = deferred.await()
        assertFalse(body.closed)
        received.close()
        assertTrue(body.closed)
    }

    @Test
    fun cancelBeforeCallback_cancelsCallAndClosesLateResponse() = runTest {
        val call = FakeCall()
        val deferred = async { call.awaitResponse() }
        runCurrent()

        deferred.cancel()
        runCurrent()
        assertTrue(call.isCanceled())

        val body = TrackingResponseBody("late")
        call.deliverResponse(response(call.request(), body))
        runCurrent()

        assertTrue(body.closed)
    }

    @Test
    fun cancelAfterCallbackBeforeResumption_closesResponse() = runTest {
        val call = FakeCall()
        val deferred = async { call.awaitResponse() }
        runCurrent()

        val body = TrackingResponseBody("race")
        call.deliverResponse(response(call.request(), body))

        assertFalse(body.closed)
        deferred.cancel()
        runCurrent()

        assertTrue(deferred.isCancelled)
        assertTrue(body.closed)
    }

    @Test
    fun cancelBeforeFailure_ignoresLateFailureCallback() = runTest {
        val call = FakeCall()
        val deferred = async { call.awaitResponse() }
        runCurrent()

        deferred.cancel()
        runCurrent()
        call.deliverFailure(IOException("late failure"))
        runCurrent()

        assertTrue(deferred.isCancelled)
    }

    @Test
    fun failureCallback_propagatesException() = runTest {
        val call = FakeCall()
        val deferred = async { runCatching { call.awaitResponse() } }
        runCurrent()

        call.deliverFailure(IOException("boom"))
        runCurrent()

        assertTrue(deferred.isCompleted)
        val failed = deferred.await().exceptionOrNull()
        assertTrue(failed is IOException)
    }

    private fun response(
        request: Request,
        body: ResponseBody
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(body)
        .build()

    private class TrackingResponseBody(content: String) : ResponseBody() {
        private val delegate = content.toResponseBody()
        var closed: Boolean = false
            private set

        override fun contentType() = delegate.contentType()

        override fun contentLength() = delegate.contentLength()

        override fun source() = delegate.source()

        override fun close() {
            closed = true
            delegate.close()
        }
    }

    private class FakeCall(
        private val request: Request = Request.Builder().url("https://example.com/test").build()
    ) : Call {
        private var callback: Callback? = null
        private var executed: Boolean = false
        private var canceled: Boolean = false

        override fun request(): Request = request

        override fun execute(): Response {
            throw UnsupportedOperationException("Synchronous execute is not used in this test")
        }

        override fun enqueue(responseCallback: Callback) {
            executed = true
            callback = responseCallback
        }

        override fun cancel() {
            canceled = true
        }

        override fun isExecuted(): Boolean = executed

        override fun isCanceled(): Boolean = canceled

        override fun timeout(): Timeout = Timeout.NONE

        override fun clone(): Call = FakeCall(request)

        fun deliverResponse(response: Response) {
            val current = callback ?: error("Callback not registered")
            current.onResponse(this, response)
        }

        fun deliverFailure(error: IOException) {
            val current = callback ?: error("Callback not registered")
            current.onFailure(this, error)
        }

    }
}
