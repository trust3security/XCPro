package com.trust3.xcpro.livefollow.data.session

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

internal data class RecordedRequest(
    val path: String,
    val body: String,
    val headers: Map<String, String>
) {
    fun headerValue(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

internal class RecordingInterceptor(
    private val responder: (Request) -> Response
) : Interceptor {
    val requests = mutableListOf<RecordedRequest>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        requests += RecordedRequest(
            path = request.url.encodedPath,
            body = buffer.readUtf8(),
            headers = request.headers.toMultimap().mapValues { it.value.last() }
        )
        return responder(request)
    }
}

internal fun testResponse(
    request: Request,
    body: String,
    code: Int = 200,
    message: String = "OK"
): Response {
    return Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(message)
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()
}
