package com.example.xcpro.map

sealed interface OgnTrafficHitResult {
    data class Target(
        val targetKey: String
    ) : OgnTrafficHitResult

    data class Cluster(
        val clusterKey: String,
        val centerLatitude: Double,
        val centerLongitude: Double,
        val memberCount: Int
    ) : OgnTrafficHitResult
}

internal data class OgnProjectedTrafficTarget(
    val target: OgnTrafficTarget,
    val screenX: Double,
    val screenY: Double
)

internal sealed interface OgnTrafficRenderItem {
    data class Single(
        val target: OgnTrafficTarget
    ) : OgnTrafficRenderItem

    data class Cluster(
        val clusterKey: String,
        val centerLatitude: Double,
        val centerLongitude: Double,
        val members: List<OgnTrafficTarget>
    ) : OgnTrafficRenderItem {
        val memberCount: Int
            get() = members.size
    }
}
