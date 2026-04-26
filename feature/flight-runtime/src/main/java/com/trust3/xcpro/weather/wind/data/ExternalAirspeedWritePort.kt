package com.trust3.xcpro.weather.wind.data

import com.trust3.xcpro.weather.wind.model.AirspeedSample

/**
 * Write seam for live external airspeed publishers (for example LXNAV Bluetooth).
 *
 * Samples must already be normalized to XCPro canonical SI units before crossing
 * this boundary. Partial samples are allowed: publishers may provide IAS-only
 * or TAS-only data and let the canonical flight-runtime owner derive the rest.
 */
interface ExternalAirspeedWritePort {
    fun updateAirspeed(sample: AirspeedSample?)

    fun clear()
}
