package com.example.xcpro.livefollow.model

data class LiveFollowTaskSnapshot(
    val taskName: String?,
    val points: List<LiveFollowTaskPoint>
) {
    fun isRenderable(): Boolean = points.size >= 2
}

data class LiveFollowTaskPoint(
    val order: Int,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val radiusMeters: Double?,
    val name: String? = null,
    val type: String? = null
)
