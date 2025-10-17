package com.example.xcpro.common.units

data class UnitsPreferences(
    val altitude: AltitudeUnit = AltitudeUnit.METERS,
    val verticalSpeed: VerticalSpeedUnit = VerticalSpeedUnit.METERS_PER_SECOND,
    val speed: SpeedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
    val distance: DistanceUnit = DistanceUnit.KILOMETERS,
    val pressure: PressureUnit = PressureUnit.HECTOPASCAL,
    val temperature: TemperatureUnit = TemperatureUnit.CELSIUS
)

enum class AltitudeUnit(
    val label: String,
    val abbreviation: String
) {
    METERS("Meters", "m"),
    FEET("Feet", "ft");

    fun fromSi(altitude: AltitudeM): Double = when (this) {
        METERS -> altitude.value
        FEET -> UnitsConverter.metersToFeet(altitude.value)
    }

    fun toSi(value: Double): AltitudeM = when (this) {
        METERS -> AltitudeM(value)
        FEET -> AltitudeM(UnitsConverter.feetToMeters(value))
    }
}

enum class VerticalSpeedUnit(
    val label: String,
    val abbreviation: String
) {
    METERS_PER_SECOND("m/s", "m/s"),
    FEET_PER_MINUTE("ft/min", "ft"),
    KNOTS("kt", "kt");

    fun fromSi(verticalSpeed: VerticalSpeedMs): Double = when (this) {
        METERS_PER_SECOND -> verticalSpeed.value
        FEET_PER_MINUTE -> UnitsConverter.verticalMsToFpm(verticalSpeed.value)
        KNOTS -> UnitsConverter.msToKnots(verticalSpeed.value)
    }

    fun toSi(value: Double): VerticalSpeedMs = when (this) {
        METERS_PER_SECOND -> VerticalSpeedMs(value)
        FEET_PER_MINUTE -> VerticalSpeedMs(UnitsConverter.fpmToVerticalMs(value))
        KNOTS -> VerticalSpeedMs(UnitsConverter.knotsToMs(value))
    }
}

enum class SpeedUnit(
    val label: String,
    val abbreviation: String
) {
    KILOMETERS_PER_HOUR("km/h", "km/h"),
    KNOTS("Knots", "kt"),
    METERS_PER_SECOND("m/s", "m/s"),
    MILES_PER_HOUR("mph", "mph");

    fun fromSi(speed: SpeedMs): Double = when (this) {
        KILOMETERS_PER_HOUR -> UnitsConverter.msToKmh(speed.value)
        KNOTS -> UnitsConverter.msToKnots(speed.value)
        METERS_PER_SECOND -> speed.value
        MILES_PER_HOUR -> UnitsConverter.msToMph(speed.value)
    }

    fun toSi(value: Double): SpeedMs = when (this) {
        KILOMETERS_PER_HOUR -> SpeedMs(UnitsConverter.kmhToMs(value))
        KNOTS -> SpeedMs(UnitsConverter.knotsToMs(value))
        METERS_PER_SECOND -> SpeedMs(value)
        MILES_PER_HOUR -> SpeedMs(UnitsConverter.mphToMs(value))
    }
}

enum class DistanceUnit(
    val label: String,
    val abbreviation: String
) {
    KILOMETERS("km", "km"),
    NAUTICAL_MILES("NM", "NM"),
    STATUTE_MILES("mi", "mi");

    fun fromSi(distance: DistanceM): Double = when (this) {
        KILOMETERS -> UnitsConverter.metersToKilometers(distance.value)
        NAUTICAL_MILES -> UnitsConverter.metersToNauticalMiles(distance.value)
        STATUTE_MILES -> UnitsConverter.metersToStatuteMiles(distance.value)
    }

    fun toSi(value: Double): DistanceM = when (this) {
        KILOMETERS -> DistanceM(UnitsConverter.kilometersToMeters(value))
        NAUTICAL_MILES -> DistanceM(UnitsConverter.nauticalMilesToMeters(value))
        STATUTE_MILES -> DistanceM(UnitsConverter.statuteMilesToMeters(value))
    }
}

enum class PressureUnit(
    val label: String,
    val abbreviation: String
) {
    HECTOPASCAL("hPa", "hPa"),
    INHG("inHg", "inHg");

    fun fromSi(pressure: PressureHpa): Double = when (this) {
        HECTOPASCAL -> pressure.value
        INHG -> UnitsConverter.hpaToInhg(pressure.value)
    }

    fun toSi(value: Double): PressureHpa = when (this) {
        HECTOPASCAL -> PressureHpa(value)
        INHG -> PressureHpa(UnitsConverter.inhgToHpa(value))
    }
}

enum class TemperatureUnit(
    val label: String,
    val abbreviation: String
) {
    CELSIUS("°C", "°C"),
    FAHRENHEIT("°F", "°F");

    fun fromSi(temperature: TemperatureC): Double = when (this) {
        CELSIUS -> temperature.value
        FAHRENHEIT -> UnitsConverter.celsiusToFahrenheit(temperature.value)
    }

    fun toSi(value: Double): TemperatureC = when (this) {
        CELSIUS -> TemperatureC(value)
        FAHRENHEIT -> TemperatureC(UnitsConverter.fahrenheitToCelsius(value))
    }
}
