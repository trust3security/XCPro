package com.example.xcpro.map

import kotlin.math.hypot

internal fun resolveOgnTrafficScreenDeclutter(
    projectedTargets: List<OgnProjectedTrafficTarget>,
    renderedIconSizePx: Int
): List<OgnTrafficRenderItem> {
    val candidates = projectedTargets
        .filter { projected ->
            projected.screenX.isFinite() &&
                projected.screenY.isFinite() &&
                isValidOgnCoordinate(projected.target.latitude, projected.target.longitude)
        }
        .sortedBy { it.target.canonicalKey }
    if (candidates.isEmpty()) return emptyList()

    val groupingRadiusPx = resolveOgnTrafficGroupingRadiusPx(renderedIconSizePx)
    val visited = BooleanArray(candidates.size)
    val renderItems = ArrayList<OgnTrafficRenderItem>(candidates.size)

    for (startIndex in candidates.indices) {
        if (visited[startIndex]) continue
        visited[startIndex] = true
        val connectedIndices = mutableListOf(startIndex)
        val queue = ArrayDeque<Int>()
        queue.addLast(startIndex)

        while (queue.isNotEmpty()) {
            val currentIndex = queue.removeFirst()
            val current = candidates[currentIndex]
            for (candidateIndex in candidates.indices) {
                if (visited[candidateIndex]) continue
                val candidate = candidates[candidateIndex]
                if (screenDistancePx(current, candidate) > groupingRadiusPx) continue
                visited[candidateIndex] = true
                connectedIndices += candidateIndex
                queue.addLast(candidateIndex)
            }
        }

        val members = connectedIndices
            .map { candidates[it].target }
            .sortedBy { it.canonicalKey }
        if (members.size == 1) {
            renderItems += OgnTrafficRenderItem.Single(members.single())
            continue
        }

        val centerLatitude = members.map { it.latitude }.average()
        val centerLongitude = members.map { it.longitude }.average()
        renderItems += OgnTrafficRenderItem.Cluster(
            clusterKey = resolveOgnTrafficClusterKey(members),
            centerLatitude = centerLatitude,
            centerLongitude = centerLongitude,
            members = members
        )
    }

    return renderItems.sortedBy { renderItemSortKey(it) }
}

internal fun resolveOgnTrafficClusterKey(members: List<OgnTrafficTarget>): String =
    members
        .map { it.canonicalKey }
        .sorted()
        .joinToString(separator = "|", prefix = "cluster:")

private fun renderItemSortKey(item: OgnTrafficRenderItem): String = when (item) {
    is OgnTrafficRenderItem.Single -> item.target.canonicalKey
    is OgnTrafficRenderItem.Cluster -> item.clusterKey
}

private fun screenDistancePx(
    first: OgnProjectedTrafficTarget,
    second: OgnProjectedTrafficTarget
): Double = hypot(first.screenX - second.screenX, first.screenY - second.screenY)

private fun resolveOgnTrafficGroupingRadiusPx(renderedIconSizePx: Int): Double {
    val clampedIconSizePx = clampOgnRenderedIconSizePx(renderedIconSizePx)
    return (clampedIconSizePx * OGN_TRAFFIC_GROUPING_RADIUS_MULTIPLIER)
        .coerceAtLeast(OGN_TRAFFIC_GROUPING_RADIUS_MIN_PX)
}

private const val OGN_TRAFFIC_GROUPING_RADIUS_MULTIPLIER = 0.72
private const val OGN_TRAFFIC_GROUPING_RADIUS_MIN_PX = 30.0
