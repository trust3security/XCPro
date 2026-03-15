package com.example.xcpro.hawk

import kotlin.math.abs

internal data class BaroQcResult(
    val accepted: Boolean,
    val median: Double,
    val deviation: Double,
    val rateOk: Boolean
)

internal class BaroQc(
    windowSize: Int,
    private var outlierThresholdM: Double,
    private var maxRateMps: Double,
    private var rejectionWindow: Int
) {
    private var windowCapacity = windowSize.coerceAtLeast(1)
    private val history = ArrayDeque<Double>(windowCapacity)
    private val acceptance = ArrayDeque<Boolean>(rejectionWindow)

    fun updateConfig(windowSize: Int, outlierThresholdM: Double, maxRateMps: Double, rejectionWindow: Int) {
        val safeWindow = windowSize.coerceAtLeast(1)
        val safeRejectionWindow = rejectionWindow.coerceAtLeast(1)
        if (safeWindow != windowCapacity || safeRejectionWindow != this.rejectionWindow) {
            windowCapacity = safeWindow
            history.clear()
            acceptance.clear()
        }
        this.outlierThresholdM = outlierThresholdM
        this.maxRateMps = maxRateMps
        this.rejectionWindow = safeRejectionWindow
    }

    fun evaluate(altitudeM: Double, baroVarioMps: Double): BaroQcResult {
        val rateOk = baroVarioMps.isFinite() && abs(baroVarioMps) <= maxRateMps
        val median = median(history, altitudeM)
        val deviation = abs(altitudeM - median)
        val withinMedian = deviation <= outlierThresholdM
        val accepted = rateOk && withinMedian
        return BaroQcResult(
            accepted = accepted,
            median = median,
            deviation = deviation,
            rateOk = rateOk
        )
    }

    fun record(accepted: Boolean, altitudeM: Double) {
        if (accepted) {
            if (history.size == windowCapacity) {
                history.removeFirst()
            }
            history.addLast(altitudeM)
        }
        if (acceptance.size == rejectionWindow) {
            acceptance.removeFirst()
        }
        acceptance.addLast(accepted)
    }

    fun rejectionRate(): Double {
        if (acceptance.isEmpty()) return 0.0
        val rejected = acceptance.count { !it }
        return rejected.toDouble() / acceptance.size.toDouble()
    }

    fun reset() {
        history.clear()
        acceptance.clear()
    }

    private fun median(history: ArrayDeque<Double>, fallback: Double): Double {
        if (history.isEmpty()) return fallback
        val values = history.toDoubleArray()
        values.sort()
        val mid = values.size / 2
        return if (values.size % 2 == 0) {
            (values[mid - 1] + values[mid]) / 2.0
        } else {
            values[mid]
        }
    }
}
