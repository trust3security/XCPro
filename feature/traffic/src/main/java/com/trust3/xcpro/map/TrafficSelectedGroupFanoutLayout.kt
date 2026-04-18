package com.trust3.xcpro.map

import android.graphics.PointF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.maplibre.android.maps.MapLibreMap

internal data class TrafficDisplayCoordinate(
    val latitude: Double,
    val longitude: Double
)

internal data class TrafficSelectedGroupFanoutLayoutConfig(
    val collisionPaddingPx: Float = 6f,
    val firstRingRadiusMultiplier: Float = 1f,
    val additionalRingRadiusStepMultiplier: Float = 1f,
    val firstRingSlotCount: Int = 6,
    val additionalRingSlotCount: Int = 12
)

/**
 * Selection-driven display-only fan-out for one packed group.
 * It never mutates authoritative traffic coordinates.
 */
internal class TrafficSelectedGroupFanoutLayout(
    private val config: TrafficSelectedGroupFanoutLayoutConfig = TrafficSelectedGroupFanoutLayoutConfig()
) {
    fun resolveDisplayCoordinatesByKey(
        map: MapLibreMap,
        seeds: List<TrafficPackedGroupLabelSeed>,
        selectedTargetKey: String?
    ): Map<String, TrafficDisplayCoordinate> {
        if (selectedTargetKey.isNullOrBlank() || seeds.isEmpty()) return emptyMap()
        val projectedTargets = projectPackedGroupTargets(map = map, seeds = seeds)
        if (projectedTargets.isEmpty()) return emptyMap()

        val selectedTarget = projectedTargets.firstOrNull { it.key == selectedTargetKey } ?: return emptyMap()
        val selectedGroup = buildPackedGroupCollisionGroups(
            targets = projectedTargets,
            collisionPaddingPx = config.collisionPaddingPx
        ).firstOrNull { group ->
            group.size > 1 && group.any { projectedTargets[it].key == selectedTargetKey }
        } ?: return emptyMap()

        val nonPrimaryTargets = selectedGroup
            .map { index -> projectedTargets[index] }
            .filterNot { it.key == selectedTarget.key }
            .sortedBy { it.key }
        if (nonPrimaryTargets.isEmpty()) return emptyMap()
        val packedGroupFootprintPx = resolvePackedGroupFootprintPx(
            targets = selectedGroup.map { index -> projectedTargets[index] }
        )

        val displayCoordinatesByKey = LinkedHashMap<String, TrafficDisplayCoordinate>(nonPrimaryTargets.size)
        nonPrimaryTargets.forEachIndexed { index, target ->
            val slot = resolveSlot(index)
            val angleRadians = (-PI / 2.0) + ((slot.slotIndex.toDouble() / slot.slotCount.toDouble()) * PI * 2.0)
            val radiusPx = resolveRingRadiusPx(
                packedGroupFootprintPx = packedGroupFootprintPx,
                ringIndex = slot.ringIndex
            )
            val displayLatLng = runCatching {
                map.projection.fromScreenLocation(
                    PointF(
                        selectedTarget.screenX + (cos(angleRadians) * radiusPx).toFloat(),
                        selectedTarget.screenY + (sin(angleRadians) * radiusPx).toFloat()
                    )
                )
            }.getOrNull() ?: return@forEachIndexed
            if (!displayLatLng.latitude.isFinite() || !displayLatLng.longitude.isFinite()) return@forEachIndexed
            displayCoordinatesByKey[target.key] = TrafficDisplayCoordinate(
                latitude = displayLatLng.latitude,
                longitude = displayLatLng.longitude
            )
        }
        return displayCoordinatesByKey
    }

    private fun resolveSlot(index: Int): FanoutSlot {
        val firstRingCount = config.firstRingSlotCount
        if (index < firstRingCount) {
            return FanoutSlot(
                ringIndex = 0,
                slotIndex = index,
                slotCount = firstRingCount
            )
        }
        val outerIndex = index - firstRingCount
        val outerCount = config.additionalRingSlotCount
        return FanoutSlot(
            ringIndex = 1 + (outerIndex / outerCount),
            slotIndex = outerIndex % outerCount,
            slotCount = outerCount
        )
    }

    private fun resolvePackedGroupFootprintPx(
        targets: List<ProjectedPackedGroupTarget>
    ): Float = targets
        .maxOf { target -> maxOf(target.collisionWidthPx, target.collisionHeightPx) } +
        (config.collisionPaddingPx * 2f)

    private fun resolveRingRadiusPx(
        packedGroupFootprintPx: Float,
        ringIndex: Int
    ): Float {
        val firstRingRadiusPx = packedGroupFootprintPx * config.firstRingRadiusMultiplier
        val ringStepPx = packedGroupFootprintPx * config.additionalRingRadiusStepMultiplier
        return firstRingRadiusPx + (ringStepPx * ringIndex)
    }

    private data class FanoutSlot(
        val ringIndex: Int,
        val slotIndex: Int,
        val slotCount: Int
    )
}
