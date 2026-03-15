package com.example.xcpro.hawk

internal class AdaptiveAccelTrust(
    windowSize: Int,
    varianceOkMax: Double,
    weightMax: Double
) {
    private var windowCapacity = windowSize
    private var varianceWindow = RollingVarianceWindow(windowSize)
    private var varianceMax = varianceOkMax
    private var weightLimit = weightMax

    fun updateConfig(windowSize: Int, varianceOkMax: Double, weightMax: Double) {
        if (windowSize != windowCapacity || varianceOkMax != varianceMax || weightMax != weightLimit) {
            windowCapacity = windowSize
            varianceWindow = RollingVarianceWindow(windowSize)
            varianceMax = varianceOkMax
            weightLimit = weightMax
        }
    }

    fun addSample(value: Double) {
        varianceWindow.add(value)
    }

    fun variance(): Double? = varianceWindow.variance()

    fun weight(variance: Double?): Double {
        if (variance == null || varianceMax <= 0.0) return 0.0
        val normalized = (1.0 - (variance / varianceMax)).coerceIn(0.0, 1.0)
        return (normalized * weightLimit).coerceIn(0.0, weightLimit)
    }

    fun reset() {
        varianceWindow.clear()
    }
}
