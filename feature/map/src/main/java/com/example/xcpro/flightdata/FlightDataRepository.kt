package com.example.xcpro.flightdata

import com.example.xcpro.sensors.CompleteFlightData
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single-source-of-truth holder for the latest CompleteFlightData sample.
 *
 * VarioForegroundService updates this repository, and UI layers observe the exposed StateFlow
 * instead of attaching directly to the FlightDataCalculator.
 */
@Singleton
class FlightDataRepository @Inject constructor() {

    enum class Source { LIVE, REPLAY }

    @Volatile private var activeSource: Source = Source.LIVE

    private val _flightData = MutableStateFlow<CompleteFlightData?>(null)
    val flightData: StateFlow<CompleteFlightData?> = _flightData.asStateFlow()

    fun setActiveSource(source: Source) {
        activeSource = source
    }

    fun update(data: CompleteFlightData?, source: Source = Source.LIVE) {
        if (source != activeSource) return  // AI-NOTE: gate updates so live sensors can't override replay
        _flightData.value = data
    }

    /**
     * Force-clears the cached sample regardless of the current active source.
     * Used by replay teardown so stale samples can't linger when source changes.
     */
    fun clear() {
        _flightData.value = null
    }
}
