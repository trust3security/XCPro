package com.trust3.xcpro.simulator.condor

import com.trust3.xcpro.weather.wind.data.ExternalWindWritePort
import com.trust3.xcpro.weather.wind.model.WindVector

internal class TestExternalWindWritePort : ExternalWindWritePort {
    var lastVector: WindVector? = null
        private set
    var lastTimestampMillis: Long? = null
        private set
    var clearCount: Int = 0
        private set

    override fun updateExternalWindVector(vector: WindVector, timestampMillis: Long) {
        lastVector = vector
        lastTimestampMillis = timestampMillis
    }

    override fun clearExternalWind() {
        lastVector = null
        lastTimestampMillis = null
        clearCount += 1
    }
}
