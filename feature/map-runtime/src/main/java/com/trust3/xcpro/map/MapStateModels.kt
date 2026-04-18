package com.trust3.xcpro.map

data class MapPoint(
    val latitude: Double,
    val longitude: Double
)

data class MapSize(
    val widthPx: Int,
    val heightPx: Int
) {
    companion object {
        val Zero = MapSize(0, 0)
    }
}

data class CameraSnapshot(
    val target: MapPoint,
    val zoom: Double,
    val bearing: Double
)
