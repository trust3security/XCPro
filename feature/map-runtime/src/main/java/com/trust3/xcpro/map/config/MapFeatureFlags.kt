package com.trust3.xcpro.map.config

import com.trust3.xcpro.map.runtime.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized switches for map feature behavior that needs to differ between
 * production and tests. This is an injected state holder so tests can override
 * behavior without relying on hidden global singletons.
 */
@Singleton
class MapFeatureFlags @Inject constructor() {
    /**
     * Controls whether [MapScreenViewModel] should call
     * [com.trust3.xcpro.tasks.TaskManagerCoordinator.loadSavedTasks] during init.
     * Unit tests can disable this to avoid heavy persistence lookups.
     */
    @Volatile
    var loadSavedTasksOnInit: Boolean = true

    /**
     * Shows the developer-only vario demo FAB on the map screen when true.
     */
    @Volatile
    var showVarioDemoFab: Boolean = BuildConfig.DEBUG

    /**
     * Shows the developer-only racing replay FAB on the map screen when true.
     */
    @Volatile
    var showRacingReplayFab: Boolean = BuildConfig.DEBUG

    /**
     * Pixel threshold for map location jitter suppression.
     */
    @Volatile
    var locationJitterThresholdPx: Float = 0.5f

    /**
     * Window size for glider offset averaging.
     */
    @Volatile
    var locationOffsetHistorySize: Int = 30

    /**
     * Debug override: allow device heading even when stationary (ignores isFlying/speed gate).
     */
    @Volatile
    var allowHeadingWhileStationary: Boolean = false

    /**
     * Use raw replay fixes for the glider marker (no smoothing/prediction).
     * This aligns UI with navigation events during replay/testing.
     */
    @Volatile
    var useRawReplayPose: Boolean = BuildConfig.DEBUG

    /**
     * Force the replay glider icon heading to follow track (ignores device sensors).
     * Useful when replaying on the ground to mimic in-flight heading behavior.
     */
    @Volatile
    var forceReplayTrackHeading: Boolean = false

    /**
     * Max per-frame track bearing change (degrees). Set >= 180 to disable clamping.
     */
    @Volatile
    var maxTrackBearingStepDeg: Double = 5.0

    /**
     * Default live display smoothing profile.
     */
    @Volatile
    var defaultDisplaySmoothingProfile: com.trust3.xcpro.map.DisplaySmoothingProfile =
        if (BuildConfig.DEBUG) {
            com.trust3.xcpro.map.DisplaySmoothingProfile.CADENCE_BRIDGE
        } else {
            com.trust3.xcpro.map.DisplaySmoothingProfile.SMOOTH
        }

    /**
     * Enable adaptive display smoothing based on live speed/accuracy.
     * UI-only; does not affect navigation or SSOT data.
     */
    @Volatile
    var useAdaptiveDisplaySmoothing: Boolean = true

    /**
     * Enable icon heading smoothing (angular velocity clamp + deadband).
     */
    @Volatile
    var useIconHeadingSmoothing: Boolean = true

    /**
     * Use runtime replay interpolation to derive heading per display frame.
     */
    @Volatile
    var useRuntimeReplayHeading: Boolean = false

    /**
     * Drive SIM2 display updates off the MapView render frame callbacks.
     * Keeps camera + aircraft updates in the same render pass.
     */
    @Volatile
    var useRenderFrameSync: Boolean = false

    /**
     * Paint recent live/Condor snail trail body from display-pose frames.
     * UI-only; raw TrailStore remains authoritative.
     */
    @Volatile
    var useDisplayPoseSnailTrail: Boolean = BuildConfig.DEBUG

    /**
     * Show legacy raw TrailStore-based snail trail geometry.
     * Currently ignored by SnailTrailManager so only display-pose geometry paints.
     * UI-only visual layer; the display trail can remain enabled even when false.
     */
    @Volatile
    var showRawSnailTrail: Boolean = false

    /**
     * Debug log interval for SIM2 frame pose logs (ms). Set to 0 to log every frame.
     */
    @Volatile
    var sim2FrameLogIntervalMs: Long = 100L

    /**
     * Minimum speed to enable directional bias (m/s).
     */
    @Volatile
    var mapShiftBiasMinSpeedMs: Double = 8.0

    /**
     * Window size for bias averaging.
     */
    @Volatile
    var mapShiftBiasHistorySize: Int = 30

    /**
     * Cap bias offset to this fraction of the smaller screen dimension.
     */
    @Volatile
    var mapShiftBiasMaxOffsetFraction: Double = 0.35

    /**
     * Hold last valid bias while inputs are invalid.
     */
    @Volatile
    var mapShiftBiasHoldOnInvalid: Boolean = true
}
