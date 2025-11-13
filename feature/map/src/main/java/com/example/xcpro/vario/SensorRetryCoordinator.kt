package com.example.xcpro.vario

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class SensorRetryCoordinator(
    private val scope: CoroutineScope,
    private val retryDelayMs: Long
) {

    private var retryJob: Job? = null

    fun schedule(action: suspend () -> Boolean) {
        if (retryJob?.isActive == true) return
        val job = scope.launch {
            while (isActive) {
                delay(retryDelayMs)
                if (action()) {
                    break
                }
            }
        }
        job.invokeOnCompletion { retryJob = null }
        retryJob = job
    }

    fun cancel() {
        retryJob?.cancel()
        retryJob = null
    }
}
