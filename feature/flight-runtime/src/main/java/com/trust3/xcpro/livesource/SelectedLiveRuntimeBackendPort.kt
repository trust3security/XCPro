package com.trust3.xcpro.livesource

enum class LiveRuntimeStartResult {
    READY,
    STARTING_MANAGER_RETRY,
    STARTING_EXTERNAL_RETRY
}

interface SelectedLiveRuntimeBackendPort {
    suspend fun start(): LiveRuntimeStartResult

    fun stop()

    suspend fun setGpsUpdateIntervalMs(intervalMs: Long)
}
