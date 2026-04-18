package com.trust3.xcpro.ogn

/**
 * UI-only OGN overlay refresh cadence.
 * Repository ingest remains live regardless of this setting.
 */
enum class OgnDisplayUpdateMode(
    val storageValue: String,
    val displayLabel: String,
    val renderIntervalMs: Long
) {
    REAL_TIME(
        storageValue = "real_time",
        displayLabel = "Real-time",
        renderIntervalMs = 0L
    ),
    BALANCED(
        storageValue = "balanced",
        displayLabel = "Balanced",
        renderIntervalMs = 1_000L
    ),
    BATTERY(
        storageValue = "battery",
        displayLabel = "Battery",
        renderIntervalMs = 3_000L
    );

    companion object {
        val DEFAULT: OgnDisplayUpdateMode = REAL_TIME
        val sliderModes: List<OgnDisplayUpdateMode> = listOf(REAL_TIME, BALANCED, BATTERY)

        fun fromStorage(value: String?): OgnDisplayUpdateMode =
            entries.firstOrNull { mode -> mode.storageValue == value } ?: DEFAULT

        fun fromSliderIndex(index: Int): OgnDisplayUpdateMode =
            sliderModes.getOrElse(index) { DEFAULT }

        fun toSliderIndex(mode: OgnDisplayUpdateMode): Int =
            sliderModes.indexOf(mode).takeIf { it >= 0 } ?: 0
    }
}
