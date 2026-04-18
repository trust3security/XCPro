// Role: Build line and dot segments for the trail based on style and bounds rules.
// Invariants: Output segments preserve the same ordering and decisions as rendering logic.
package com.trust3.xcpro.map.trail

internal data class SnailTrailLineSegment(
    val start: RenderPoint,
    val end: RenderPoint,
    val colorIndex: Int,
    val width: Float
)

internal data class SnailTrailDotSegment(
    val start: RenderPoint,
    val end: RenderPoint,
    val colorIndex: Int,
    val radius: Float
)

internal data class SnailTrailSegmentLog(
    val kind: String,
    val start: RenderPoint,
    val end: RenderPoint,
    val colorIndex: Int,
    val width: Float?,
    val radius: Float?
)

internal data class SnailTrailSegmentPlan(
    val lineSegments: List<SnailTrailLineSegment>,
    val dotSegments: List<SnailTrailDotSegment>,
    val logEntries: List<SnailTrailSegmentLog>
)

internal class SnailTrailSegmentBuilder(
    private val boundsChecker: BoundsChecker
) {

    interface BoundsChecker {
        fun isInside(point: RenderPoint): Boolean
    }

    fun build(
        points: List<RenderPoint>,
        settings: TrailSettings,
        styleCache: SnailTrailStyleCache,
        skipBoundsCheck: Boolean,
        includeLogs: Boolean
    ): SnailTrailSegmentPlan {
        if (points.isEmpty()) {
            return SnailTrailSegmentPlan(emptyList(), emptyList(), emptyList())
        }

        val lineSegments = ArrayList<SnailTrailLineSegment>(points.size)
        val dotSegments = ArrayList<SnailTrailDotSegment>(points.size)
        val logEntries = if (includeLogs) ArrayList<SnailTrailSegmentLog>(points.size) else null

        var last: RenderPoint? = null
        var lastInside = false

        for (point in points) {
            val inside = if (skipBoundsCheck) true else boundsChecker.isInside(point)
            if (last != null && (skipBoundsCheck || (lastInside && inside))) {
                val colorIndex = if (settings.type == TrailType.ALTITUDE) {
                    SnailTrailMath.altitudeColorIndex(point.altitudeMeters, styleCache.valueMin, styleCache.valueMax)
                } else {
                    SnailTrailMath.varioColorIndex(point.varioMs, styleCache.valueMin, styleCache.valueMax)
                }
                val width = if (styleCache.useScaledLines) styleCache.scaledWidths[colorIndex] else styleCache.minWidth
                val radius = styleCache.scaledWidths[colorIndex]

                when (settings.type) {
                    TrailType.ALTITUDE,
                    TrailType.VARIO_1,
                    TrailType.VARIO_2 -> {
                        lineSegments.add(SnailTrailLineSegment(last, point, colorIndex, width))
                        logEntries?.add(SnailTrailSegmentLog("line", last, point, colorIndex, width, null))
                    }
                    TrailType.VARIO_1_DOTS,
                    TrailType.VARIO_2_DOTS -> {
                        if (point.varioMs < 0) {
                            dotSegments.add(SnailTrailDotSegment(last, point, colorIndex, radius))
                            logEntries?.add(SnailTrailSegmentLog("dot", last, point, colorIndex, null, radius))
                        } else {
                            lineSegments.add(SnailTrailLineSegment(last, point, colorIndex, width))
                            logEntries?.add(SnailTrailSegmentLog("line", last, point, colorIndex, width, null))
                        }
                    }
                    TrailType.VARIO_DOTS_AND_LINES,
                    TrailType.VARIO_EINK -> {
                        if (point.varioMs < 0) {
                            dotSegments.add(SnailTrailDotSegment(last, point, colorIndex, radius))
                            logEntries?.add(SnailTrailSegmentLog("dot", last, point, colorIndex, null, radius))
                        } else {
                            dotSegments.add(SnailTrailDotSegment(last, point, colorIndex, radius))
                            lineSegments.add(SnailTrailLineSegment(last, point, colorIndex, width))
                            logEntries?.add(SnailTrailSegmentLog("dot+line", last, point, colorIndex, width, radius))
                        }
                    }
                }
            }
            last = point
            lastInside = inside
        }

        return SnailTrailSegmentPlan(
            lineSegments = lineSegments,
            dotSegments = dotSegments,
            logEntries = logEntries ?: emptyList()
        )
    }
}
