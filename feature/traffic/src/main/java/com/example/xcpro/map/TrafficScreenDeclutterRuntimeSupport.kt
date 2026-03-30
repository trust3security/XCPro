package com.example.xcpro.map

import android.graphics.PointF
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

internal data class TrafficProjectionSeed(
    val key: String,
    val latitude: Double,
    val longitude: Double,
    val collisionWidthPx: Float,
    val collisionHeightPx: Float,
    val priorityRank: Int,
    val pinAtOrigin: Boolean = false
)

internal fun resolveTrafficDeclutteredDisplayCoordinates(
    map: MapLibreMap,
    seeds: List<TrafficProjectionSeed>,
    declutterEngine: TrafficScreenDeclutterEngine,
    strengthMultiplier: Float
): Map<String, TrafficDisplayCoordinate> {
    if (seeds.isEmpty()) {
        declutterEngine.clear()
        return emptyMap()
    }

    val projectedTargets = ArrayList<Pair<TrafficProjectionSeed, ProjectedTrafficTarget>>(seeds.size)
    seeds.forEach { seed ->
        val screenPoint = runCatching {
            map.projection.toScreenLocation(LatLng(seed.latitude, seed.longitude))
        }.getOrNull() ?: return@forEach
        if (!screenPoint.x.isFinite() || !screenPoint.y.isFinite()) return@forEach
        projectedTargets += seed to ProjectedTrafficTarget(
            key = seed.key,
            screenX = screenPoint.x,
            screenY = screenPoint.y,
            collisionWidthPx = seed.collisionWidthPx,
            collisionHeightPx = seed.collisionHeightPx,
            priorityRank = seed.priorityRank,
            pinAtOrigin = seed.pinAtOrigin
        )
    }
    if (projectedTargets.isEmpty()) {
        declutterEngine.clear()
        return emptyMap()
    }

    val layout = declutterEngine.layout(
        targets = projectedTargets.map { it.second },
        strengthMultiplier = strengthMultiplier
    )
    val displayCoordinatesByKey = LinkedHashMap<String, TrafficDisplayCoordinate>(projectedTargets.size)
    projectedTargets.forEach { (seed, projectedTarget) ->
        val offset = layout.offsetFor(projectedTarget.key)
        if (offset == TrafficDisplayOffset.Zero) {
            displayCoordinatesByKey[seed.key] = TrafficDisplayCoordinate(
                latitude = seed.latitude,
                longitude = seed.longitude
            )
            return@forEach
        }
        val displayLatLng = runCatching {
            map.projection.fromScreenLocation(
                PointF(
                    projectedTarget.screenX + offset.dxPx,
                    projectedTarget.screenY + offset.dyPx
                )
            )
        }.getOrNull() ?: return@forEach
        if (!displayLatLng.latitude.isFinite() || !displayLatLng.longitude.isFinite()) return@forEach
        displayCoordinatesByKey[seed.key] = TrafficDisplayCoordinate(
            latitude = displayLatLng.latitude,
            longitude = displayLatLng.longitude
        )
    }
    return displayCoordinatesByKey
}
