package com.example.xcpro.hawk

/**
 * Tunable configuration for the HAWK vario engine.
 * Keep thresholds centralized here to avoid scattered magic numbers.
 */
data class HawkConfig(
    // Feature flag: gate HAWK processing without touching callers.
    val enabled: Boolean = true,
    // Freshness thresholds (monotonic time).
    val baroStaleMs: Long = 750L,
    val accelStaleMs: Long = 250L,
    // Reset if baro gap exceeds this.
    val maxBaroGapMs: Long = 2_000L,
    // Baro QC thresholds.
    val maxBaroRateMps: Double = 20.0,
    val baroMedianWindow: Int = 5,
    val baroOutlierThresholdM: Double = 6.0,
    val baroInnovationRejectMps: Double = 3.0,
    val baroSupportMinMps: Double = 0.2,
    val baroRejectionWindow: Int = 12,
    val baroRejectionMaxFraction: Double = 0.5,
    // Adaptive accel trust thresholds.
    val accelVarianceWindow: Int = 25,
    val accelVarianceOkMax: Double = 0.6,
    val accelWeightMax: Double = 0.6,
    val accelIntegralClampMps: Double = 10.0,
    val accelMaxGapMs: Long = 250L,
    // Output smoothing.
    val audioTimeConstantMs: Long = 350L,
    val audioDeadbandMps: Double = 0.15,
    val audioClampMps: Double = 30.0,
    // UI staleness ticker.
    val staleCheckIntervalMs: Long = 250L,
    // Debug logging.
    val debugLogging: Boolean = false,
    val debugLogIntervalMs: Long = 1000L
)
