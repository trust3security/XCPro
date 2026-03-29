package com.example.xcpro.map

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal data class TrafficDisplayCoordinate(
    val latitude: Double,
    val longitude: Double
)

internal data class TrafficDisplayOffset(
    val dxPx: Float,
    val dyPx: Float
) {
    companion object {
        val Zero = TrafficDisplayOffset(dxPx = 0f, dyPx = 0f)
    }
}

internal data class ProjectedTrafficTarget(
    val key: String,
    val screenX: Float,
    val screenY: Float,
    val collisionWidthPx: Float,
    val collisionHeightPx: Float,
    val priorityRank: Int,
    val pinAtOrigin: Boolean = false
)

internal data class TrafficDeclutterLayoutResult(
    val offsetsByKey: Map<String, TrafficDisplayOffset>
) {
    fun offsetFor(key: String): TrafficDisplayOffset = offsetsByKey[key] ?: TrafficDisplayOffset.Zero
}

internal data class TrafficScreenDeclutterConfig(
    val collisionPaddingPx: Float = 6f,
    val ringSpacingMultiplier: Float = 0.82f,
    val maxCandidateRings: Int = 6
)

/**
 * Display-only screen-space layout. This is runtime-local and non-authoritative.
 */
internal class TrafficScreenDeclutterEngine(
    private val config: TrafficScreenDeclutterConfig = TrafficScreenDeclutterConfig()
) {
    private var previousOffsetsByKey: Map<String, TrafficDisplayOffset> = emptyMap()

    fun clear() {
        previousOffsetsByKey = emptyMap()
    }

    fun layout(
        targets: List<ProjectedTrafficTarget>,
        strengthMultiplier: Float
    ): TrafficDeclutterLayoutResult {
        if (targets.isEmpty()) {
            clear()
            return TrafficDeclutterLayoutResult(emptyMap())
        }
        val normalizedStrength = strengthMultiplier.coerceIn(0f, 1f)
        if (normalizedStrength <= 0f) {
            val zeroOffsets = targets.associate { it.key to TrafficDisplayOffset.Zero }
            previousOffsetsByKey = zeroOffsets
            return TrafficDeclutterLayoutResult(zeroOffsets)
        }

        val groups = buildCollisionGroups(targets)
        val offsetsByKey = LinkedHashMap<String, TrafficDisplayOffset>(targets.size)
        groups.forEach { group ->
            if (group.size == 1) {
                val target = targets[group.single()]
                offsetsByKey[target.key] = TrafficDisplayOffset.Zero
            } else {
                layoutCollisionGroup(
                    group = group,
                    targets = targets,
                    strengthMultiplier = normalizedStrength,
                    offsetsByKey = offsetsByKey
                )
            }
        }
        targets.forEach { target -> offsetsByKey.putIfAbsent(target.key, TrafficDisplayOffset.Zero) }
        previousOffsetsByKey = offsetsByKey
        return TrafficDeclutterLayoutResult(offsetsByKey)
    }

    private fun buildCollisionGroups(targets: List<ProjectedTrafficTarget>): List<List<Int>> {
        val cellSizePx = targets.fold(1f) { currentMax, target ->
            max(
                currentMax,
                max(target.collisionWidthPx, target.collisionHeightPx) + (config.collisionPaddingPx * 2f)
            )
        }
        val disjointSet = IntDisjointSet(targets.size)
        val buckets = HashMap<GridCell, MutableList<Int>>()

        targets.forEachIndexed { index, target ->
            val paddedBounds = target.paddedBounds()
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
                        if (paddedBounds.overlaps(targets[candidateIndex].paddedBounds())) {
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

    private fun layoutCollisionGroup(
        group: List<Int>,
        targets: List<ProjectedTrafficTarget>,
        strengthMultiplier: Float,
        offsetsByKey: MutableMap<String, TrafficDisplayOffset>
    ) {
        val groupTargets = group.map { targets[it] }
        val anchor = groupTargets.minWith(
            compareByDescending<ProjectedTrafficTarget> { it.pinAtOrigin }
                .thenBy { it.priorityRank }
                .thenBy { it.key }
        )
        val centerX = groupTargets.map { it.screenX.toDouble() }.average().toFloat()
        val centerY = groupTargets.map { it.screenY.toDouble() }.average().toFloat()
        val slotRadiusPx = resolveSlotRadiusPx(groupTargets)
        val occupiedBounds = mutableListOf<DisplayBounds>()

        offsetsByKey[anchor.key] = TrafficDisplayOffset.Zero
        occupiedBounds += anchor.boundsWithOffset(TrafficDisplayOffset.Zero)

        val remainingTargets = groupTargets
            .filterNot { it.key == anchor.key }
            .sortedWith(
                compareBy<ProjectedTrafficTarget> { it.priorityRank }
                    .thenBy { resolvePreferredAngle(it, centerX, centerY) }
                    .thenBy { it.key }
            )

        remainingTargets.forEachIndexed { ordinal, target ->
            val reusedOffset = previousOffsetsByKey[target.key]
                ?.scaled(strengthMultiplier)
                ?.takeIf { it != TrafficDisplayOffset.Zero }
                ?.takeIf { !target.boundsWithOffset(it).overlapsAny(occupiedBounds) }
            if (reusedOffset != null) {
                offsetsByKey[target.key] = reusedOffset
                occupiedBounds += target.boundsWithOffset(reusedOffset)
                return@forEachIndexed
            }

            val desiredAngle = resolvePreferredAngle(
                target = target,
                groupCenterX = centerX,
                groupCenterY = centerY,
                fallbackOrdinal = ordinal,
                fallbackCount = remainingTargets.size.coerceAtLeast(1)
            )
            val candidateOffset = findCandidateOffset(
                target = target,
                desiredAngle = desiredAngle,
                slotRadiusPx = slotRadiusPx,
                strengthMultiplier = strengthMultiplier,
                occupiedBounds = occupiedBounds
            )
            offsetsByKey[target.key] = candidateOffset
            occupiedBounds += target.boundsWithOffset(candidateOffset)
        }
    }

    private fun resolveSlotRadiusPx(groupTargets: List<ProjectedTrafficTarget>): Float {
        val baseDimension = groupTargets.fold(1f) { currentMax, target ->
            max(currentMax, max(target.collisionWidthPx, target.collisionHeightPx))
        }
        return max(12f, (baseDimension * config.ringSpacingMultiplier) + config.collisionPaddingPx)
    }

    private fun resolvePreferredAngle(
        target: ProjectedTrafficTarget,
        groupCenterX: Float,
        groupCenterY: Float,
        fallbackOrdinal: Int = 0,
        fallbackCount: Int = 1
    ): Double {
        previousOffsetsByKey[target.key]
            ?.takeIf { it != TrafficDisplayOffset.Zero }
            ?.let { return normalizeRadians(atan2(it.dyPx.toDouble(), it.dxPx.toDouble())) }

        val dx = target.screenX - groupCenterX
        val dy = target.screenY - groupCenterY
        if (dx != 0f || dy != 0f) {
            return normalizeRadians(atan2(dy.toDouble(), dx.toDouble()))
        }

        val safeCount = max(fallbackCount, 1)
        return normalizeRadians((-PI / 2.0) + ((ordinalFraction(fallbackOrdinal, safeCount)) * PI * 2.0))
    }

    private fun findCandidateOffset(
        target: ProjectedTrafficTarget,
        desiredAngle: Double,
        slotRadiusPx: Float,
        strengthMultiplier: Float,
        occupiedBounds: List<DisplayBounds>
    ): TrafficDisplayOffset {
        val angleOffsetsDeg = intArrayOf(0, 18, -18, 36, -36, 54, -54, 72, -72, 90, -90, 135, -135, 180)
        var fallback = TrafficDisplayOffset.Zero
        for (ringIndex in 1..config.maxCandidateRings) {
            val radiusPx = slotRadiusPx * ringIndex * strengthMultiplier
            for (angleOffsetDeg in angleOffsetsDeg) {
                val angle = desiredAngle + Math.toRadians(angleOffsetDeg.toDouble())
                val candidateOffset = TrafficDisplayOffset(
                    dxPx = (cos(angle) * radiusPx).toFloat(),
                    dyPx = (sin(angle) * radiusPx).toFloat()
                )
                fallback = candidateOffset
                if (!target.boundsWithOffset(candidateOffset).overlapsAny(occupiedBounds)) {
                    return candidateOffset
                }
            }
        }
        return fallback
    }

    private fun ProjectedTrafficTarget.paddedBounds(): DisplayBounds {
        val halfWidth = (collisionWidthPx / 2f) + config.collisionPaddingPx
        val halfHeight = (collisionHeightPx / 2f) + config.collisionPaddingPx
        return DisplayBounds(
            left = screenX - halfWidth,
            top = screenY - halfHeight,
            right = screenX + halfWidth,
            bottom = screenY + halfHeight
        )
    }

    private fun ProjectedTrafficTarget.boundsWithOffset(offset: TrafficDisplayOffset): DisplayBounds {
        val paddedBounds = paddedBounds()
        return DisplayBounds(
            left = paddedBounds.left + offset.dxPx,
            top = paddedBounds.top + offset.dyPx,
            right = paddedBounds.right + offset.dxPx,
            bottom = paddedBounds.bottom + offset.dyPx
        )
    }

    private fun DisplayBounds.overlapsAny(bounds: List<DisplayBounds>): Boolean = bounds.any(::overlaps)

    private fun TrafficDisplayOffset.scaled(strengthMultiplier: Float): TrafficDisplayOffset =
        TrafficDisplayOffset(dxPx = dxPx * strengthMultiplier, dyPx = dyPx * strengthMultiplier)

    private fun normalizeRadians(angle: Double): Double {
        var normalized = angle
        while (normalized <= -PI) normalized += PI * 2.0
        while (normalized > PI) normalized -= PI * 2.0
        return normalized
    }

    private fun ordinalFraction(ordinal: Int, count: Int): Double {
        val safeOrdinal = min(max(ordinal, 0), count - 1)
        return safeOrdinal.toDouble() / count.toDouble()
    }

    private data class GridCell(
        val x: Int,
        val y: Int
    )

    private data class DisplayBounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        fun overlaps(other: DisplayBounds): Boolean =
            left < other.right &&
                right > other.left &&
                top < other.bottom &&
                bottom > other.top
    }

    private class IntDisjointSet(size: Int) {
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
}

internal fun resolveTrafficDeclutterStrengthMultiplier(
    zoomLevel: Float,
    fullStrengthAtOrBelowZoom: Float,
    zeroStrengthAtOrAboveZoom: Float
): Float {
    val zoom = zoomLevel.takeIf { it.isFinite() } ?: zeroStrengthAtOrAboveZoom
    if (fullStrengthAtOrBelowZoom >= zeroStrengthAtOrAboveZoom) return 0f
    return when {
        zoom <= fullStrengthAtOrBelowZoom -> 1f
        zoom >= zeroStrengthAtOrAboveZoom -> 0f
        else -> {
            val progress = (zoom - fullStrengthAtOrBelowZoom) /
                (zeroStrengthAtOrAboveZoom - fullStrengthAtOrBelowZoom)
            (1f - progress).coerceIn(0f, 1f)
        }
    }
}
