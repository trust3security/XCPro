package com.trust3.xcpro.hawk

import kotlinx.coroutines.flow.Flow

enum class HawkRuntimeSource {
    LIVE,
    REPLAY
}

data class HawkBaroSample(
    val pressureHpa: Double,
    val monotonicTimestampMillis: Long
)

data class HawkAccelSample(
    val verticalAcceleration: Double,
    val monotonicTimestampMillis: Long,
    val isReliable: Boolean
)

interface HawkSensorStreamPort {
    val baroSamples: Flow<HawkBaroSample>
    val accelSamples: Flow<HawkAccelSample>
}

interface HawkActiveSourcePort {
    val activeSource: Flow<HawkRuntimeSource>
}
