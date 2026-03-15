package com.example.xcpro

import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.orientation.OrientationStationaryHeadingPolicy
import javax.inject.Inject

class MapOrientationHeadingPolicy @Inject constructor(
    private val featureFlags: MapFeatureFlags
) : OrientationStationaryHeadingPolicy {
    override val allowHeadingWhileStationary: Boolean
        get() = featureFlags.allowHeadingWhileStationary
}
