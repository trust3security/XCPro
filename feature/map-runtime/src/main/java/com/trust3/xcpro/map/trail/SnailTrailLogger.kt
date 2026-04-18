// Role: Centralize snail trail debug logging for render operations.
package com.trust3.xcpro.map.trail

import com.trust3.xcpro.core.common.logging.AppLogger

internal class SnailTrailLogger(
    private val verboseEnabled: Boolean,
    private val maxSegmentLogs: Int = 400
) {

    fun logRenderStart(
        renderId: Long,
        pointsSize: Int,
        settings: TrailSettings,
        locationLat: Double?,
        locationLon: Double?,
        currentTimeMillis: Long,
        currentZoom: Float,
        isReplay: Boolean,
        windSpeedMs: Double,
        windDirectionFromDeg: Double,
        isCircling: Boolean,
        frameId: Long?
    ) {
        if (!verboseEnabled) return
        AppLogger.d(
            TAG_RENDER,
            "render#$renderId start points=$pointsSize length=${settings.length} type=${settings.type} " +
                "scaling=${settings.scalingEnabled} drift=${settings.windDriftEnabled} " +
                "loc=$locationLat,$locationLon " +
                "time=$currentTimeMillis zoom=$currentZoom replay=$isReplay wind=${"%.2f".format(windSpeedMs)} " +
                "windDir=${"%.1f".format(windDirectionFromDeg)} circling=$isCircling frame=${frameId ?: -1}"
        )
    }

    fun logFiltered(
        renderId: Long,
        filteredCount: Int,
        minDistanceMeters: Double,
        metersPerPixel: Double,
        minTime: Long?
    ) {
        if (!verboseEnabled) return
        AppLogger.d(
            TAG_RENDER,
            "render#$renderId filtered points=$filteredCount minDistanceMeters=${"%.2f".format(minDistanceMeters)} " +
                "metersPerPixel=${"%.6f".format(metersPerPixel)} minTime=${minTime ?: -1}"
        )
    }

    fun logRange(
        renderId: Long,
        valueMin: Double,
        valueMax: Double,
        useScaledLines: Boolean,
        minWidth: Float,
        density: Float
    ) {
        if (!verboseEnabled) return
        AppLogger.d(
            TAG_RENDER,
            "render#$renderId range min=${"%.2f".format(valueMin)} max=${"%.2f".format(valueMax)} " +
                "useScaledLines=$useScaledLines minWidth=${"%.2f".format(minWidth)} density=$density"
        )
    }

    fun logRenderEnd(renderId: Long, lineCount: Int, dotCount: Int) {
        if (!verboseEnabled) return
        AppLogger.d(TAG_RENDER, "render#$renderId end lineFeatures=$lineCount dotFeatures=$dotCount")
    }

    fun logSegment(
        renderId: Long,
        index: Int,
        kind: String,
        start: RenderPoint,
        end: RenderPoint,
        colorIndex: Int,
        type: TrailType,
        width: Float?,
        radius: Float?
    ) {
        if (!verboseEnabled) return
        if (index >= maxSegmentLogs) {
            if (index == maxSegmentLogs) {
                AppLogger.d(TAG_RENDER, "render#$renderId segment logs truncated at $maxSegmentLogs")
            }
            return
        }
        val widthLabel = width?.let { "width=${"%.2f".format(it)}" } ?: "width=-"
        val radiusLabel = radius?.let { "radius=${"%.2f".format(it)}" } ?: "radius=-"
        val colorInt = SnailTrailPalette.colorFor(type, colorIndex)
        AppLogger.d(
            TAG_RENDER,
            "render#$renderId seg=$index kind=$kind idx=$colorIndex color=${SnailTrailPalette.colorHex(colorInt)} " +
                "$widthLabel $radiusLabel " +
                "from=${"%.5f".format(start.latitude)},${"%.5f".format(start.longitude)} " +
                "to=${"%.5f".format(end.latitude)},${"%.5f".format(end.longitude)} " +
                "vario=${"%.2f".format(end.varioMs)} alt=${"%.1f".format(end.altitudeMeters)}"
        )
    }

    private companion object {
        private const val TAG_RENDER = "SnailTrailRender"
    }
}
