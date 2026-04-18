package com.trust3.xcpro.sensors

import com.trust3.xcpro.core.flight.filters.AdvancedBarometricFilter
import com.trust3.xcpro.sensors.PressureKalmanFilter

internal class FlightFilters {
    val baroFilter = AdvancedBarometricFilter()  // Legacy 2-state filter (fallback)
    val pressureKalmanFilter = PressureKalmanFilter()
}
