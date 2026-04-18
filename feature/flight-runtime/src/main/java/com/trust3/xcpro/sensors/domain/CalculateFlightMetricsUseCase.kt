package com.trust3.xcpro.sensors.domain
import com.trust3.xcpro.sensors.DisplayVarioSmoother
import com.trust3.xcpro.sensors.NeedleVarioDynamics
import com.trust3.xcpro.core.flight.calculations.BarometricAltitudeData
import com.trust3.xcpro.core.flight.calculations.ConfidenceLevel
import com.trust3.xcpro.core.flight.filters.ModernVarioResult
import com.trust3.xcpro.glider.StillAirSinkProvider
import com.trust3.xcpro.sensors.CirclingDetector
import com.trust3.xcpro.sensors.FlightCalculationHelpers
import com.trust3.xcpro.sensors.GPSData
import com.trust3.xcpro.weather.wind.model.AirspeedSample
import com.trust3.xcpro.weather.wind.model.WindState
import com.trust3.xcpro.common.flight.FlightMode
import com.trust3.xcpro.sensors.domain.FlightMetricsConstants.DEFAULT_QNH_HPA
import com.trust3.xcpro.sensors.domain.FlightMetricsConstants.DISPLAY_DECAY_FACTOR
import com.trust3.xcpro.sensors.domain.FlightMetricsConstants.DISPLAY_SMOOTH_TIME_S
import com.trust3.xcpro.sensors.domain.FlightMetricsConstants.DISPLAY_VAR_CLAMP
import com.trust3.xcpro.sensors.domain.FlightMetricsConstants.FAST_NEEDLE_T95_SECONDS
import com.trust3.xcpro.sensors.domain.FlightMetricsConstants.GPS_AIRSPEED_FALLBACK_MIN_SPEED_MS
import com.trust3.xcpro.sensors.domain.FlightMetricsConstants.NEEDLE_VAR_CLAMP
import com.trust3.xcpro.sensors.domain.FlightMetricsConstants.NEEDLE_T95_SECONDS
import com.trust3.xcpro.sensors.domain.SensorFrontEnd.SensorSnapshot
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
