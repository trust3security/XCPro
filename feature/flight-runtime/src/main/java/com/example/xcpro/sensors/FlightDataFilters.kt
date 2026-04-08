package com.example.xcpro.sensors

import com.example.xcpro.core.flight.filters.AdvancedBarometricFilter
import com.example.xcpro.sensors.PressureKalmanFilter

internal class FlightFilters {
    val baroFilter = AdvancedBarometricFilter()  // Legacy 2-state filter (fallback)
    val pressureKalmanFilter = PressureKalmanFilter()
}
