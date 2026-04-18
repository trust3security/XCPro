package com.trust3.xcpro

import com.trust3.xcpro.map.config.MapFeatureFlags
import com.trust3.xcpro.orientation.OrientationStationaryHeadingPolicy
import javax.inject.Inject

class MapOrientationHeadingPolicy @Inject constructor(
    private val featureFlags: MapFeatureFlags
) : OrientationStationaryHeadingPolicy {
    override val allowHeadingWhileStationary: Boolean
        get() = featureFlags.allowHeadingWhileStationary
}
