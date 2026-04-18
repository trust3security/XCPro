package com.trust3.xcpro.map

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun <T> Flow<T>.sampleWithImmediateFirst(
    windowMs: Long,
    scope: CoroutineScope
): Flow<T> {
    // Leading-edge throttle per burst: emit first event immediately, then suppress
    // until the stream has been idle for the configured window.
    return channelFlow {
        var gateOpen = true
        var reopenJob: Job? = null
        val source = this@sampleWithImmediateFirst.distinctUntilChanged()
        val collectorJob = scope.launch {
            source.collect { value ->
                if (gateOpen) {
                    send(value)
                    gateOpen = false
                }
                reopenJob?.cancel()
                reopenJob = launch {
                    delay(windowMs)
                    gateOpen = true
                }
            }
        }
        awaitClose {
            reopenJob?.cancel()
            collectorJob.cancel()
        }
    }
}
