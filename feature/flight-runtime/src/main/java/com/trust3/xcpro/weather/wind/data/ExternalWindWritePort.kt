package com.trust3.xcpro.weather.wind.data

import com.trust3.xcpro.weather.wind.model.WindVector

/**
 * Write seam for authoritative external wind publishers.
 *
 * Callers must provide canonical XCPro wind vectors. Implementations own any
 * validation, storage, and downstream publication policy.
 */
interface ExternalWindWritePort {
    fun updateExternalWindVector(vector: WindVector, timestampMillis: Long)

    fun clearExternalWind()
}
