package com.example.xcpro.common.glider

import com.example.xcpro.common.units.UnitsConverter

data class PolarPoint(
    val speedMs: Double,
    val sinkMs: Double
) {
    val kmh: Double
        get() = UnitsConverter.msToKmh(speedMs)

    companion object {
        fun fromKmh(kmh: Double, sinkMs: Double): PolarPoint =
            PolarPoint(speedMs = UnitsConverter.kmhToMs(kmh), sinkMs = sinkMs)
    }
}

data class PolarCoefficients(
    val a: Double?,
    val b: Double?,
    val c: Double?,
    val minMs: Double = UnitsConverter.kmhToMs(50.0),
    val maxMs: Double = UnitsConverter.kmhToMs(200.0)
) {
    val minKmh: Double
        get() = UnitsConverter.msToKmh(minMs)
    val maxKmh: Double
        get() = UnitsConverter.msToKmh(maxMs)

    companion object {
        fun fromKmh(
            a: Double?,
            b: Double?,
            c: Double?,
            minKmh: Double = 50.0,
            maxKmh: Double = 200.0
        ): PolarCoefficients = PolarCoefficients(
            a = a,
            b = b,
            c = c,
            minMs = UnitsConverter.kmhToMs(minKmh),
            maxMs = UnitsConverter.kmhToMs(maxKmh)
        )
    }
}

data class WaterBallastCapacity(
    val mainWingLiters: Int? = null,
    val tipLiters: Int? = null,
    val fuselageLiters: Int? = null,
    val totalLiters: Int? = null
)

data class SpeedLimits(
    val vneKmh: Int? = null,
    val vraKmh: Int? = null,
    val vaKmh: Int? = null,
    val vwKmh: Int? = null,
    val vtKmh: Int? = null
)

data class FlapLimit(
    val position: String,
    val deflectionDeg: Double,
    val maxSpeedKmh: Int
)

data class StallSpeedsAtWeight(
    val weightKg: Int,
    val flapToSpeedKmh: Map<String, Int>
)

data class GliderModel(
    val id: String,
    val name: String,
    val classLabel: String,
    val wingSpanM: Double? = null,
    val wingAreaM2: Double? = null,
    val aspectRatio: Double? = null,
    val emptyWeightKg: Int? = null,
    val maxWeightKg: Int? = null,
    val bestLD: Double? = null,
    val bestLDSpeedMs: Double? = null,
    val minSinkMs: Double? = null,
    val minSinkSpeedMs: Double? = null,
    val polar: PolarCoefficients? = null,
    val points: List<PolarPoint>? = null,
    val pointsLight: List<PolarPoint>? = null,
    val pointsHeavy: List<PolarPoint>? = null,
    val water: WaterBallastCapacity? = null,
    val speedLimits: SpeedLimits? = null,
    val flapLimits: List<FlapLimit>? = null,
    val stallSpeeds: List<StallSpeedsAtWeight>? = null,
    val minWingLoadingKgM2: Double? = null,
    val maxWingLoadingKgM2: Double? = null
) {
    val bestLDSpeedKmh: Double?
        get() = bestLDSpeedMs?.let(UnitsConverter::msToKmh)
    val minSinkSpeedKmh: Double?
        get() = minSinkSpeedMs?.let(UnitsConverter::msToKmh)
}

fun defaultGliderModels(): List<GliderModel> = listOf(
    GliderModel(
        id = "js1c-18",
        name = "JS1-C 18m",
        classLabel = "18m",
        wingSpanM = 18.0,
        wingAreaM2 = 10.93,
        bestLD = 50.0,
        bestLDSpeedMs = UnitsConverter.kmhToMs(105.0),
        minSinkMs = 0.51,
        minSinkSpeedMs = UnitsConverter.kmhToMs(75.0),
        polar = PolarCoefficients.fromKmh(
            a = 0.389,
            b = -0.0137,
            c = 0.00074,
            minKmh = 60.0,
            maxKmh = 180.0
        ),
        points = listOf(
            PolarPoint.fromKmh(60.0, 0.65),
            PolarPoint.fromKmh(70.0, 0.53),
            PolarPoint.fromKmh(75.0, 0.51),
            PolarPoint.fromKmh(80.0, 0.52),
            PolarPoint.fromKmh(90.0, 0.56),
            PolarPoint.fromKmh(100.0, 0.65),
            PolarPoint.fromKmh(105.0, 0.68),
            PolarPoint.fromKmh(110.0, 0.72),
            PolarPoint.fromKmh(120.0, 0.84),
            PolarPoint.fromKmh(130.0, 0.99),
            PolarPoint.fromKmh(140.0, 1.16),
            PolarPoint.fromKmh(150.0, 1.36),
            PolarPoint.fromKmh(160.0, 1.59),
            PolarPoint.fromKmh(170.0, 1.85),
            PolarPoint.fromKmh(180.0, 2.14)
        ),
        water = WaterBallastCapacity(
            mainWingLiters = 192,
            totalLiters = 192
        )
    ),
    GliderModel(
        id = "js1c-21",
        name = "JS1-C 21m",
        classLabel = "21m",
        wingSpanM = 21.0,
        wingAreaM2 = 12.25,
        aspectRatio = 35.9,
        emptyWeightKg = 330,
        maxWeightKg = 720,
        bestLD = 60.0,
        bestLDSpeedMs = UnitsConverter.kmhToMs(120.0),
        minSinkMs = 0.48,
        polar = null,
        pointsLight = listOf(
            PolarPoint.fromKmh(75.0, 0.55),
            PolarPoint.fromKmh(90.0, 0.58),
            PolarPoint.fromKmh(100.0, 0.60),
            PolarPoint.fromKmh(120.0, 0.70),
            PolarPoint.fromKmh(150.0, 1.20),
            PolarPoint.fromKmh(180.0, 1.90)
        ),
        pointsHeavy = listOf(
            PolarPoint.fromKmh(90.0, 0.70),
            PolarPoint.fromKmh(110.0, 0.72),
            PolarPoint.fromKmh(120.0, 0.75),
            PolarPoint.fromKmh(150.0, 1.10),
            PolarPoint.fromKmh(180.0, 1.60),
            PolarPoint.fromKmh(200.0, 2.00),
            PolarPoint.fromKmh(240.0, 3.50)
        ),
        water = WaterBallastCapacity(
            mainWingLiters = 180,
            tipLiters = 34,
            fuselageLiters = 42,
            totalLiters = 268
        ),
        speedLimits = SpeedLimits(
            vneKmh = 270,
            vraKmh = 203,
            vaKmh = 203,
            vwKmh = 140,
            vtKmh = 180
        ),
        flapLimits = listOf(
            FlapLimit(position = "1", deflectionDeg = -3.0, maxSpeedKmh = 270),
            FlapLimit(position = "2", deflectionDeg = 0.0, maxSpeedKmh = 270),
            FlapLimit(position = "3", deflectionDeg = 5.0, maxSpeedKmh = 230),
            FlapLimit(position = "4", deflectionDeg = 13.5, maxSpeedKmh = 170),
            FlapLimit(position = "5", deflectionDeg = 16.6, maxSpeedKmh = 170),
            FlapLimit(position = "L", deflectionDeg = 20.0, maxSpeedKmh = 160)
        ),
        stallSpeeds = listOf(
            StallSpeedsAtWeight(
                weightKg = 500,
                flapToSpeedKmh = mapOf(
                    "L" to 70,
                    "5" to 75,
                    "3" to 82,
                    "1" to 86
                )
            ),
            StallSpeedsAtWeight(
                weightKg = 720,
                flapToSpeedKmh = mapOf(
                    "L" to 87,
                    "5" to 88,
                    "3" to 94,
                    "1" to 101
                )
            )
        ),
        minWingLoadingKgM2 = 32.6,
        maxWingLoadingKgM2 = 58.7
    ),
    GliderModel(
        id = "ASG-29-18",
        name = "ASG-29 (18m)",
        classLabel = "18m",
        polar = null,
        points = null
    ),
    GliderModel(
        id = "ventus3-18",
        name = "Ventus 3 (18m)",
        classLabel = "18m",
        polar = null,
        points = null
    )
)

fun defaultClubFallbackGliderModel(): GliderModel = GliderModel(
    id = "club-default-fallback",
    name = "Club Class (Default)",
    classLabel = "Club",
    wingSpanM = 15.0,
    bestLD = 38.0,
    bestLDSpeedMs = UnitsConverter.kmhToMs(95.0),
    minSinkMs = 0.68,
    minSinkSpeedMs = UnitsConverter.kmhToMs(82.0),
    points = listOf(
        PolarPoint.fromKmh(70.0, 0.78),
        PolarPoint.fromKmh(82.0, 0.68),
        PolarPoint.fromKmh(95.0, 0.72),
        PolarPoint.fromKmh(110.0, 0.86),
        PolarPoint.fromKmh(130.0, 1.15),
        PolarPoint.fromKmh(150.0, 1.52),
        PolarPoint.fromKmh(170.0, 1.96)
    )
)
