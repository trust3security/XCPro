package com.trust3.xcpro.map.config

import com.trust3.xcpro.map.DisplaySmoothingProfile
import com.trust3.xcpro.map.runtime.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

interface MapScreenFeatureFlagPort {
    val loadSavedTasksOnInit: Boolean
    val showVarioDemoFab: Boolean
    val showRacingReplayFab: Boolean
    val defaultDisplaySmoothingProfile: DisplaySmoothingProfile
}

interface MapReplayFeatureFlagPort {
    val useRawReplayPose: Boolean
    var forceReplayTrackHeading: Boolean
    var maxTrackBearingStepDeg: Double
    var useIconHeadingSmoothing: Boolean
    var useRuntimeReplayHeading: Boolean
    var useRenderFrameSync: Boolean
    var sim2FrameLogIntervalMs: Long
}

/**
 * Centralized switches for map feature behavior that needs to differ between
 * production and tests. This is an injected state holder so tests can override
 * behavior without relying on hidden global singletons.
 */
@Singleton
class MapFeatureFlags @Inject constructor() : MapScreenFeatureFlagPort, MapReplayFeatureFlagPort {
    /**
     * Controls whether [MapScreenViewModel] should call
     * [com.trust3.xcpro.tasks.TaskManagerCoordinator.loadSavedTasks] during init.
     * Unit tests can disable this to avoid heavy persistence lookups.
     */
    @Volatile
    override var loadSavedTasksOnInit: Boolean = true

    /**
     * Shows the developer-only vario demo FAB on the map screen when true.
     */
    @Volatile
    override var showVarioDemoFab: Boolean = BuildConfig.DEBUG

    /**
     * Shows the developer-only racing replay FAB on the map screen when true.
     */
    @Volatile
    override var showRacingReplayFab: Boolean = BuildConfig.DEBUG

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
    override var useRawReplayPose: Boolean = BuildConfig.DEBUG

    /**
     * Force the replay glider icon heading to follow track (ignores device sensors).
     * Useful when replaying on the ground to mimic in-flight heading behavior.
     */
    @Volatile
    override var forceReplayTrackHeading: Boolean = false

    /**
     * Max per-frame track bearing change (degrees). Set >= 180 to disable clamping.
     */
    @Volatile
    override var maxTrackBearingStepDeg: Double = 5.0

    /**
     * Default live display smoothing profile.
     */
    @Volatile
    override var defaultDisplaySmoothingProfile: DisplaySmoothingProfile =
        if (BuildConfig.DEBUG) {
            DisplaySmoothingProfile.CADENCE_BRIDGE
        } else {
            DisplaySmoothingProfile.SMOOTH
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
    override var useIconHeadingSmoothing: Boolean = true

    /**
     * Use runtime replay interpolation to derive heading per display frame.
     */
    @Volatile
    override var useRuntimeReplayHeading: Boolean = false

    /**
     * Drive SIM2 display updates off the MapView render frame callbacks.
     * Keeps camera + aircraft updates in the same render pass.
     */
    @Volatile
    override var useRenderFrameSync: Boolean = false

    /**
     * Debug log interval for SIM2 frame pose logs (ms). Set to 0 to log every frame.
     */
    @Volatile
    override var sim2FrameLogIntervalMs: Long = 100L

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
