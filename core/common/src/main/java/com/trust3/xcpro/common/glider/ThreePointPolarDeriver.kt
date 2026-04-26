package com.trust3.xcpro.common.glider

object ThreePointPolarDeriver {
    fun fromModel(model: GliderModel?): ThreePointPolar? {
        val sourcePoints = model?.points
            ?: model?.pointsLight
            ?: model?.pointsHeavy
            ?: return null

        val points = sourcePoints
            .asSequence()
            .filter { point ->
                point.speedMs.isFinite() &&
                    point.speedMs > 0.0 &&
                    point.sinkMs.isFinite() &&
                    point.sinkMs > 0.0
            }
            .distinctBy { it.speedMs }
            .sortedBy { it.speedMs }
            .toList()
        if (points.size < 3) return null

        val low = points.first()
        val high = points.last()
        val mid = points
            .drop(1)
            .dropLast(1)
            .maxByOrNull { point -> point.speedMs / point.sinkMs }
            ?: return null

        return ThreePointPolar(
            lowMs = low.speedMs,
            lowSinkMs = low.sinkMs,
            midMs = mid.speedMs,
            midSinkMs = mid.sinkMs,
            highMs = high.speedMs,
            highSinkMs = high.sinkMs
        )
    }
}
