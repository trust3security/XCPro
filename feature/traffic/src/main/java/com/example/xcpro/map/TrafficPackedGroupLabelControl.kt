package com.example.xcpro.map

import kotlin.math.floor
import kotlin.math.max
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

internal data class TrafficPackedGroupLabelSeed(
    val key: String,
    val latitude: Double,
    val longitude: Double,
    val collisionWidthPx: Float,
    val collisionHeightPx: Float,
    val priorityRank: Int
)

internal data class TrafficPackedGroupLabelControlConfig(
    val collisionPaddingPx: Float = 6f
)

internal data class ProjectedPackedGroupTarget(
    val key: String,
    val latitude: Double,
    val longitude: Double,
    val screenX: Float,
    val screenY: Float,
    val collisionWidthPx: Float,
    val collisionHeightPx: Float,
    val priorityRank: Int
)

internal fun projectPackedGroupTargets(
    map: MapLibreMap,
    seeds: List<TrafficPackedGroupLabelSeed>
): List<ProjectedPackedGroupTarget> {
    if (seeds.isEmpty()) return emptyList()
    val projectedTargets = ArrayList<ProjectedPackedGroupTarget>(seeds.size)
    seeds.forEach { seed ->
        val screenPoint = runCatching {
            map.projection.toScreenLocation(LatLng(seed.latitude, seed.longitude))
        }.getOrNull() ?: return@forEach
        if (!screenPoint.x.isFinite() || !screenPoint.y.isFinite()) return@forEach
        projectedTargets += ProjectedPackedGroupTarget(
            key = seed.key,
            latitude = seed.latitude,
            longitude = seed.longitude,
            screenX = screenPoint.x,
            screenY = screenPoint.y,
            collisionWidthPx = seed.collisionWidthPx,
            collisionHeightPx = seed.collisionHeightPx,
            priorityRank = seed.priorityRank
        )
    }
    return projectedTargets
}

internal fun buildPackedGroupCollisionGroups(
    targets: List<ProjectedPackedGroupTarget>,
    collisionPaddingPx: Float
): List<List<Int>> {
    if (targets.isEmpty()) return emptyList()
    val cellSizePx = targets.fold(1f) { currentMax, target ->
        max(
            currentMax,
            max(target.collisionWidthPx, target.collisionHeightPx) + (collisionPaddingPx * 2f)
        )
    }
    val disjointSet = IntDisjointSet(targets.size)
    val buckets = HashMap<GridCell, MutableList<Int>>()

    targets.forEachIndexed { index, target ->
        val paddedBounds = target.paddedBounds(collisionPaddingPx)
        val minCellX = floor(paddedBounds.left / cellSizePx).toInt()
        val maxCellX = floor(paddedBounds.right / cellSizePx).toInt()
        val minCellY = floor(paddedBounds.top / cellSizePx).toInt()
        val maxCellY = floor(paddedBounds.bottom / cellSizePx).toInt()
        val seenCandidateIndices = HashSet<Int>()
        for (cellX in minCellX..maxCellX) {
            for (cellY in minCellY..maxCellY) {
                val cell = GridCell(cellX, cellY)
                buckets[cell]?.forEach { candidateIndex ->
                    if (!seenCandidateIndices.add(candidateIndex)) return@forEach
                    if (paddedBounds.overlaps(targets[candidateIndex].paddedBounds(collisionPaddingPx))) {
                        disjointSet.union(index, candidateIndex)
                    }
                }
                buckets.getOrPut(cell) { mutableListOf() }.add(index)
            }
        }
    }

    val grouped = linkedMapOf<Int, MutableList<Int>>()
    targets.indices.forEach { index ->
        grouped.getOrPut(disjointSet.find(index)) { mutableListOf() }.add(index)
    }
    return grouped.values.toList()
}

internal fun ProjectedPackedGroupTarget.paddedBounds(
    collisionPaddingPx: Float
): PackedGroupDisplayBounds {
    val halfWidth = (collisionWidthPx / 2f) + collisionPaddingPx
    val halfHeight = (collisionHeightPx / 2f) + collisionPaddingPx
    return PackedGroupDisplayBounds(
        left = screenX - halfWidth,
        top = screenY - halfHeight,
        right = screenX + halfWidth,
        bottom = screenY + halfHeight
    )
}

internal data class PackedGroupDisplayBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun overlaps(other: PackedGroupDisplayBounds): Boolean =
        left < other.right &&
            right > other.left &&
            top < other.bottom &&
            bottom > other.top
}

internal data class GridCell(
    val x: Int,
    val y: Int
)

internal class IntDisjointSet(size: Int) {
    private val parent = IntArray(size) { it }
    private val rank = IntArray(size)

    fun find(index: Int): Int {
        if (parent[index] != index) {
            parent[index] = find(parent[index])
        }
        return parent[index]
    }

    fun union(first: Int, second: Int) {
        val firstRoot = find(first)
        val secondRoot = find(second)
        if (firstRoot == secondRoot) return
        when {
            rank[firstRoot] < rank[secondRoot] -> parent[firstRoot] = secondRoot
            rank[firstRoot] > rank[secondRoot] -> parent[secondRoot] = firstRoot
            else -> {
                parent[secondRoot] = firstRoot
                rank[firstRoot] += 1
            }
        }
    }
}

/**
 * Display-only packed-group detection for label admission.
 * It never mutates authoritative coordinates and never owns persistent state.
 */
internal class TrafficPackedGroupLabelControl(
    private val config: TrafficPackedGroupLabelControlConfig = TrafficPackedGroupLabelControlConfig()
) {
    fun resolveFullLabelKeys(
        map: MapLibreMap,
        seeds: List<TrafficPackedGroupLabelSeed>
    ): Set<String> {
        if (seeds.isEmpty()) return emptySet()

        val fullLabelKeys = LinkedHashSet<String>(seeds.size)
        seeds.forEach { seed -> fullLabelKeys += seed.key }

        val projectedTargets = projectPackedGroupTargets(map = map, seeds = seeds)
        if (projectedTargets.isEmpty()) return fullLabelKeys

        val groups = buildPackedGroupCollisionGroups(
            targets = projectedTargets,
            collisionPaddingPx = config.collisionPaddingPx
        )
        groups.forEach { group ->
            if (group.size <= 1) return@forEach
            val groupedTargets = group.map { index -> projectedTargets[index] }
            val primary = groupedTargets.minWith(
                compareBy<ProjectedPackedGroupTarget> { it.priorityRank }
                    .thenBy { it.key }
            )
            groupedTargets.forEach { target ->
                if (target.key != primary.key) {
                    fullLabelKeys.remove(target.key)
                }
            }
            fullLabelKeys += primary.key
        }

        return fullLabelKeys
    }
}

internal fun rankOgnTargetsForPackedGroupLabels(
    targets: List<OgnTrafficTarget>,
    selectedTargetKey: String?
): Map<String, Int> = targets
    .sortedWith(
        compareBy<OgnTrafficTarget> { if (it.canonicalKey == selectedTargetKey) 0 else 1 }
            .thenBy { if (it.distanceMeters?.isFinite() == true) 0 else 1 }
            .thenBy { it.distanceMeters ?: Double.POSITIVE_INFINITY }
            .thenBy { it.canonicalKey }
    )
    .mapIndexed { index, target -> target.canonicalKey to index }
    .toMap()

internal fun rankAdsbTargetsForPackedGroupLabels(
    targets: List<AdsbTrafficUiModel>,
    selectedTargetId: Icao24?
): Map<String, Int> = targets
    .sortedWith(
        compareBy<AdsbTrafficUiModel> { if (it.id == selectedTargetId) 0 else 1 }
            .thenBy { if (it.isEmergencyCollisionRisk) 0 else 1 }
            .thenBy { if (it.distanceMeters.isFinite()) 0 else 1 }
            .thenBy { if (it.distanceMeters.isFinite()) it.distanceMeters else Double.POSITIVE_INFINITY }
            .thenBy { it.id.raw }
    )
    .mapIndexed { index, target -> target.id.raw to index }
    .toMap()
