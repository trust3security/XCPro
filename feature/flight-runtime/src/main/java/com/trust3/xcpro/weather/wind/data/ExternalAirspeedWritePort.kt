package com.trust3.xcpro.weather.wind.data

import com.trust3.xcpro.weather.wind.model.AirspeedSample

/**
 * Write seam for live external airspeed publishers (for example LXNAV Bluetooth).
 *
 * Samples must already be normalized to XCPro canonical SI units before crossing
 * this boundary. TAS-only sources should leave IAS unavailable in the sample
 * contract instead of fabricating it upstream.
 */
interface ExternalAirspeedWritePort {
    fun updateAirspeed(sample: AirspeedSample?)

    fun clear()
}
