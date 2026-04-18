package com.trust3.xcpro.hawk

import java.util.Locale
import kotlin.math.abs

enum class HawkConfidence(val code: Int, val label: String) {
    UNKNOWN(0, "CONF --"),
    LEVEL1(1, "CONF 1"),
    LEVEL2(2, "CONF 2"),
    LEVEL3(3, "CONF 3"),
    LEVEL4(4, "CONF 4"),
    LEVEL5(5, "CONF 5"),
    LEVEL6(6, "CONF 6");

    val isLow: Boolean
        get() = this == LEVEL1 || this == LEVEL2
}

data class HawkVarioUiState(
    val varioSmoothedMps: Float? = null,
    val varioRawMps: Float? = null,
    val accelOk: Boolean = false,
    val baroOk: Boolean = false,
    val confidence: HawkConfidence = HawkConfidence.UNKNOWN,
    val confidenceScore: Float = 0f,
    val accelVariance: Float? = null,
    val baroInnovationMps: Float? = null,
    val baroHz: Float? = null,
    val lastUpdateElapsedRealtimeMs: Long? = null
) {
    fun formatCenterValue(): String {
        val value = varioSmoothedMps ?: return "--.- m/s"
        val clamped = if (abs(value) < 0.05f) 0f else value
        return String.format(Locale.US, "%+.1f m/s", clamped)
    }

    val accelStatusText: String
        get() = if (accelOk) "ACCEL OK" else "ACCEL UNREL"

    val baroStatusText: String
        get() = if (baroOk) "BARO OK" else "BARO DEG"

    val confText: String
        get() = confidence.label

    val statusLine: String
        get() = "${accelStatusText} ${baroStatusText} ${confText}"
}
