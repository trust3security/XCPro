package com.example.xcpro.map

/**
 * Chooses which pose to render for the glider marker.
 * Raw replay pose is preferred only when explicitly enabled.
 */
object DisplayPoseSelector {
    fun selectPose(
        mode: DisplayPoseMode,
        rawPose: DisplayPoseSmoother.DisplayPose?,
        smoothedPose: DisplayPoseSmoother.DisplayPose?
    ): DisplayPoseSmoother.DisplayPose? {
        return when (mode) {
            DisplayPoseMode.RAW_REPLAY -> rawPose ?: smoothedPose
            DisplayPoseMode.SMOOTHED -> smoothedPose ?: rawPose
        }
    }
}
