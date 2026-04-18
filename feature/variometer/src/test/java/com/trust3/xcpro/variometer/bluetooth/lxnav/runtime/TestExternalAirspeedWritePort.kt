package com.trust3.xcpro.variometer.bluetooth.lxnav.runtime

import com.trust3.xcpro.weather.wind.data.ExternalAirspeedWritePort
import com.trust3.xcpro.weather.wind.model.AirspeedSample

internal class TestExternalAirspeedWritePort : ExternalAirspeedWritePort {
    var latestSample: AirspeedSample? = null
        private set

    override fun updateAirspeed(sample: AirspeedSample?) {
        latestSample = sample
    }

    override fun clear() {
        latestSample = null
    }
}
