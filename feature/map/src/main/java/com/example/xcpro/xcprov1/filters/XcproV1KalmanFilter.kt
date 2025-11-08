package com.example.xcpro.xcprov1.filters

import com.example.xcpro.xcprov1.model.DiagnosticsSnapshot
import com.example.xcpro.xcprov1.model.FlightDataV1Snapshot
import com.example.xcpro.xcprov1.model.XcproV1State
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Six-state extended Kalman filter tailored for the XCPro V1 variometer.
 *
 * The filter fuses barometric altitude, vertical acceleration, GPS vertical speed
 * and inferred wind components to deliver actual and potential climb along with
 * air-mass diagnostics.
 */
class XcproV1KalmanFilter(
    private val aeroModel: Js1cAeroModel = Js1cAeroModel
) {

    private var state = XcproV1State(
        altitude = 0.0,
        climbRate = 0.0,
        accelBias = 0.0,
        verticalWind = 0.0,
        windX = 0.0,
        windY = 0.0
    )

    // Covariance matrix (6x6)
    private val P = Array(6) { DoubleArray(6) }

    // Measurement noise
    var baroNoise: Double = 0.25
    var accelNoise: Double = 0.4
    var gpsVerticalNoise: Double = 0.8

    private var lastTimestamp = 0L

    data class UpdateResult(
        val snapshot: FlightDataV1Snapshot,
        val state: XcproV1State
    )

    fun reset() {
        state = XcproV1State(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        for (i in 0 until 6) {
            for (j in 0 until 6) {
                P[i][j] = if (i == j) 10.0 else 0.0
            }
        }
        lastTimestamp = 0L
    }

    fun update(
        timestamp: Long,
        baroAltitude: Double,
        verticalAccel: Double,
        gpsVerticalSpeed: Double?,
        gpsGroundSpeed: Double,
        gpsTrackRad: Double?,
        trueAirspeed: Double,
        airBearingRad: Double?,
        wingLoading: Double = Js1cAeroModel.defaultWingLoading(),
        bankDeg: Double = 0.0
    ): UpdateResult {
        val dt = computeDeltaTime(timestamp)
        predict(dt, verticalAccel)
        baroUpdate(baroAltitude)
        accelUpdate(verticalAccel)
        gpsVerticalSpeed?.let { gpsUpdate(it) }

        val windVector = windModel.update(
            groundSpeed = gpsGroundSpeed,
            groundTrackRad = gpsTrackRad,
            trueAirspeed = trueAirspeed,
            airBearingRad = airBearingRad
        )

        state = state.copy(
            windX = windVector.windX,
            windY = windVector.windY
        )

        val actualClimb = state.climbRate
        val netto = aeroModel.nettoFromVerticals(actualClimb, bankDeg, trueAirspeed, wingLoading)
        val potential = aeroModel.potentialClimb(state.verticalWind, trueAirspeed, bankDeg, wingLoading)

        val confidence = computeConfidence()

        val diagnostics = DiagnosticsSnapshot(
            covarianceTrace = covarianceTrace(),
            baroInnovation = lastBaroInnovation,
            accelInnovation = lastAccelInnovation,
            gpsInnovation = lastGpsInnovation,
            residualRms = residualRms()
        )

        val snapshot = FlightDataV1Snapshot(
            timestampMillis = timestamp,
            actualClimb = actualClimb,
            potentialClimb = potential,
            netto = netto,
            verticalWind = state.verticalWind,
            windX = windVector.windX,
            windY = windVector.windY,
            confidence = confidence,
            climbTrend = trendTracker.update(actualClimb, dt),
            sourceLabel = if (gpsVerticalSpeed != null) "XCProV1 (IMU+GPS)" else "XCProV1 (IMU only)",
            diagnostics = diagnostics
        )

        return UpdateResult(snapshot, state)
    }

    private fun predict(dt: Double, verticalAccel: Double) {
        if (dt <= 0.0) return

        val accel = verticalAccel - state.accelBias
        val newClimb = state.climbRate + accel * dt

        val newAltitude = state.altitude + newClimb * dt

        val fv = state.copy(
            altitude = newAltitude,
            climbRate = newClimb,
            accelBias = state.accelBias,
            verticalWind = state.verticalWind,
            windX = state.windX,
            windY = state.windY
        )

        val F = arrayOf(
            doubleArrayOf(1.0, dt, -0.5 * dt * dt, 0.0, 0.0, 0.0),
            doubleArrayOf(0.0, 1.0, -dt, 0.0, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0, 0.0, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, 0.0, 1.0, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0)
        )

        val Q = processNoise(dt)
        val newP = multiply(multiply(F, P), transpose(F))
        addInPlace(newP, Q)
        copyMatrix(newP, P)

        state = fv
    }

    private fun processNoise(dt: Double): Array<DoubleArray> {
        val accelSigma = 0.8
        val windSigma = 0.2
        val biasSigma = 0.02

        val qAltitude = accelSigma * dt * dt * 0.5
        val qClimb = accelSigma * dt
        val qBias = biasSigma * dt
        val qWind = windSigma * dt

        return arrayOf(
            doubleArrayOf(qAltitude, 0.0, 0.0, 0.0, 0.0, 0.0),
            doubleArrayOf(0.0, qClimb, 0.0, 0.0, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, qBias, 0.0, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, qWind, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, 0.0, qWind, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, qWind)
        )
    }

    private fun baroUpdate(measuredAltitude: Double) {
        val H = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val innovation = measuredAltitude - (H dot stateVector())
        lastBaroInnovation = innovation

        val pTimesH = multiply(P, H)
        val s = baroNoise + (H dot pTimesH)
        if (s <= 0.0) return
        val k = pTimesH.map { it / s }.toDoubleArray()

        val updated = stateVector().zip(k) { value, gain -> value + gain * innovation }.toDoubleArray()
        applyState(updated)

        val KH = outerProduct(k, H)
        val identity = identityMatrix(6)
        val newP = multiply(identityMinus(KH, identity), P)
        copyMatrix(newP, P)
    }

    private fun accelUpdate(verticalAccel: Double) {
        val predictedAccel = (state.climbRate - previousClimbRate) / max(lastDeltaTime, 0.02)
        val innovation = verticalAccel - predictedAccel
        lastAccelInnovation = innovation

        val H = doubleArrayOf(0.0, 0.0, -1.0, 0.0, 0.0, 0.0)
        val pTimesH = multiply(P, H)
        val s = accelNoise + (H dot pTimesH)
        if (s <= 0.0) return
        val k = pTimesH.map { it / s }.toDoubleArray()

        val updated = stateVector().zip(k) { value, gain -> value + gain * innovation }.toDoubleArray()
        applyState(updated)

        val KH = outerProduct(k, H)
        val identity = identityMatrix(6)
        val newP = multiply(identityMinus(KH, identity), P)
        copyMatrix(newP, P)
    }

    private fun gpsUpdate(gpsVertical: Double) {
        val H = doubleArrayOf(0.0, 1.0, 0.0, 0.0, 0.0, 0.0)
        val innovation = gpsVertical - (H dot stateVector())
        lastGpsInnovation = innovation

        val pTimesH = multiply(P, H)
        val s = gpsVerticalNoise + (H dot pTimesH)
        if (s <= 0.0) return
        val k = pTimesH.map { it / s }.toDoubleArray()

        val updated = stateVector().zip(k) { value, gain -> value + gain * innovation }.toDoubleArray()
        applyState(updated)

        val KH = outerProduct(k, H)
        val identity = identityMatrix(6)
        val newP = multiply(identityMinus(KH, identity), P)
        copyMatrix(newP, P)
    }

    private fun computeDeltaTime(timestamp: Long): Double {
        if (lastTimestamp == 0L) {
            lastTimestamp = timestamp
            return 0.02
        }
        val dt = min(0.5, max(0.01, (timestamp - lastTimestamp) / 1000.0))
        lastDeltaTime = dt
        previousClimbRate = state.climbRate
        lastTimestamp = timestamp
        return dt
    }

    private fun covarianceTrace(): Double {
        var trace = 0.0
        for (i in 0 until 6) {
            trace += P[i][i]
        }
        return trace
    }

    private fun computeConfidence(): Double {
        val trace = covarianceTrace()
        val maxTrace = 50.0
        val normalized = 1.0 - (trace / maxTrace).coerceIn(0.0, 1.0)
        return normalized * normalized
    }

    private fun residualRms(): Double {
        val values = doubleArrayOf(
            abs(lastBaroInnovation),
            abs(lastAccelInnovation),
            abs(lastGpsInnovation)
        )
        return values.average()
    }

    private fun stateVector(): DoubleArray =
        doubleArrayOf(
            state.altitude,
            state.climbRate,
            state.accelBias,
            state.verticalWind,
            state.windX,
            state.windY
        )

    private fun applyState(vector: DoubleArray) {
        state = XcproV1State(
            altitude = vector[0],
            climbRate = vector[1],
            accelBias = vector[2],
            verticalWind = vector[3],
            windX = vector[4],
            windY = vector[5]
        )
    }

    private fun identityMatrix(size: Int): Array<DoubleArray> =
        Array(size) { i ->
            DoubleArray(size) { j -> if (i == j) 1.0 else 0.0 }
        }

    private fun identityMinus(a: Array<DoubleArray>, identity: Array<DoubleArray>): Array<DoubleArray> {
        val result = Array(identity.size) { DoubleArray(identity.size) }
        for (i in identity.indices) {
            for (j in identity.indices) {
                result[i][j] = identity[i][j] - a[i][j]
            }
        }
        return result
    }

    private fun multiply(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
        val rows = a.size
        val cols = b[0].size
        val inner = b.size
        val result = Array(rows) { DoubleArray(cols) }
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                var sum = 0.0
                for (k in 0 until inner) {
                    sum += a[i][k] * b[k][j]
                }
                result[i][j] = sum
            }
        }
        return result
    }

    private fun multiply(matrix: Array<DoubleArray>, vector: DoubleArray): DoubleArray {
        val rows = matrix.size
        val result = DoubleArray(rows)
        for (i in 0 until rows) {
            var sum = 0.0
            for (j in matrix[i].indices) {
                sum += matrix[i][j] * vector[j]
            }
            result[i] = sum
        }
        return result
    }

    private fun transpose(matrix: Array<DoubleArray>): Array<DoubleArray> {
        val result = Array(matrix[0].size) { DoubleArray(matrix.size) }
        for (i in matrix.indices) {
            for (j in matrix[0].indices) {
                result[j][i] = matrix[i][j]
            }
        }
        return result
    }

    private fun addInPlace(target: Array<DoubleArray>, other: Array<DoubleArray>) {
        for (i in target.indices) {
            for (j in target[i].indices) {
                target[i][j] += other[i][j]
            }
        }
    }

    private fun copyMatrix(from: Array<DoubleArray>, to: Array<DoubleArray>) {
        for (i in from.indices) {
            for (j in from[i].indices) {
                to[i][j] = from[i][j]
            }
        }
    }

    private infix fun DoubleArray.dot(other: DoubleArray): Double {
        var sum = 0.0
        for (i in indices) {
            sum += this[i] * other[i]
        }
        return sum
    }

    private fun outerProduct(a: DoubleArray, b: DoubleArray): Array<DoubleArray> {
        val result = Array(a.size) { DoubleArray(b.size) }
        for (i in a.indices) {
            for (j in b.indices) {
                result[i][j] = a[i] * b[j]
            }
        }
        return result
    }

    private val windModel = WindStateModel()
    private val trendTracker = TrendTracker()

    private var lastBaroInnovation = 0.0
    private var lastAccelInnovation = 0.0
    private var lastGpsInnovation = 0.0
    private var lastDeltaTime = 0.02
    private var previousClimbRate = 0.0

    private class TrendTracker {
        private var ema = 0.0
        fun update(value: Double, dt: Double): Double {
            val alpha = min(1.0, dt / 2.0)
            ema = (1.0 - alpha) * ema + alpha * value
            return ema
        }
    }
}
