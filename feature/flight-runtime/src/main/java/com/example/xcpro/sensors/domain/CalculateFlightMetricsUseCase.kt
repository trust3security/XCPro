package com.example.xcpro.sensors.domain
import com.example.xcpro.sensors.DisplayVarioSmoother
import com.example.xcpro.sensors.NeedleVarioDynamics
import com.example.dfcards.calculations.BarometricAltitudeData
import com.example.dfcards.calculations.ConfidenceLevel
import com.example.dfcards.filters.ModernVarioResult
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.sensors.CirclingDetector
import com.example.xcpro.sensors.FlightCalculationHelpers
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.weather.wind.model.AirspeedSample
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.sensors.domain.FlightMetricsConstants.DEFAULT_QNH_HPA
import com.example.xcpro.sensors.domain.FlightMetricsConstants.DISPLAY_DECAY_FACTOR
import com.example.xcpro.sensors.domain.FlightMetricsConstants.DISPLAY_SMOOTH_TIME_S
import com.example.xcpro.sensors.domain.FlightMetricsConstants.DISPLAY_VAR_CLAMP
import com.example.xcpro.sensors.domain.FlightMetricsConstants.FAST_NEEDLE_T95_SECONDS
import com.example.xcpro.sensors.domain.FlightMetricsConstants.GPS_AIRSPEED_FALLBACK_MIN_SPEED_MS
import com.example.xcpro.sensors.domain.FlightMetricsConstants.NEEDLE_VAR_CLAMP
import com.example.xcpro.sensors.domain.FlightMetricsConstants.NEEDLE_T95_SECONDS
import com.example.xcpro.sensors.domain.SensorFrontEnd.SensorSnapshot
import kotlin.math.abs



class CalculateFlightMetricsUseCase(
    flightHelpers: FlightCalculationHelpers,
    sinkProvider: StillAirSinkProvider,
    windEstimator: WindEstimator
) {
    private val runtime = CalculateFlightMetricsRuntime(
        flightHelpers = flightHelpers,
        sinkProvider = sinkProvider,
        windEstimator = windEstimator
    )

    var navBaroAltitudeEnabled: Boolean
        get() = runtime.navBaroAltitudeEnabled
        set(value) {
            runtime.navBaroAltitudeEnabled = value
        }

    @Synchronized
    fun execute(request: FlightMetricsRequest): FlightMetricsResult = runtime.execute(request)

    @Synchronized
    fun reset() = runtime.reset()

    @Synchronized
    internal fun windAirspeedDecisionCounts(): Map<WindAirspeedDecisionCode, Long> =
        runtime.windAirspeedDecisionCounts()

    @Synchronized
    internal fun windAirspeedTransitionCounts(): Map<AirspeedSourceTransitionEvent, Long> =
        runtime.windAirspeedTransitionCounts()
}
