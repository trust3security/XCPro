package com.trust3.xcpro.puretrack

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun Call.awaitPureTrackResponse(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            cancel()
        }

        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isActive) return
                    runCatching {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!continuation.isActive) {
                        response.close()
                        return
                    }
                    runCatching {
                        continuation.resume(response) {
                            response.close()
                        }
                    }.onFailure {
                        response.close()
                    }
                }
            }
        )
    }
