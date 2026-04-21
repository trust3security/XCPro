package com.trust3.xcpro.map

import com.trust3.xcpro.common.orientation.MapOrientationMode

data class MapFollowCameraCadence(
    val minUpdateIntervalMs: Long,
    val bearingEpsDeg: Double
)

class MapFollowCameraCadencePolicy(
    private val config: Config = Config()
) {
    data class Config(
        val normal: MapFollowCameraCadence = MapFollowCameraCadence(
            minUpdateIntervalMs = 80L,
            bearingEpsDeg = 2.0
        ),
        val closeScale: MapFollowCameraCadence = MapFollowCameraCadence(
            minUpdateIntervalMs = 25L,
            bearingEpsDeg = 0.5
        ),
        val enterCloseScaleWidthMeters: Double = 5_000.0,
        val exitCloseScaleWidthMeters: Double = 7_000.0
    )

    data class Input(
        val timeBase: DisplayClock.TimeBase?,
        val visibleWidthMeters: Double?,
        val orientationMode: MapOrientationMode
    )

    private var closeScaleActive = false

    fun reset() {
        closeScaleActive = false
    }

    fun resolve(input: Input): MapFollowCameraCadence {
        val visibleWidthMeters = input.visibleWidthMeters
            ?.takeIf { it.isFinite() && it > 0.0 }
        if (input.timeBase == DisplayClock.TimeBase.REPLAY || visibleWidthMeters == null) {
            closeScaleActive = false
            return config.normal
        }

        closeScaleActive = when {
            closeScaleActive -> visibleWidthMeters < config.exitCloseScaleWidthMeters
            else -> visibleWidthMeters <= config.enterCloseScaleWidthMeters
        }

        if (!closeScaleActive) return config.normal

        return if (input.orientationMode == MapOrientationMode.NORTH_UP) {
            config.closeScale.copy(bearingEpsDeg = config.normal.bearingEpsDeg)
        } else {
            config.closeScale
        }
    }
}
