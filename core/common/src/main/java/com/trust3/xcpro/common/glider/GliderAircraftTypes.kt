package com.trust3.xcpro.common.glider

object GliderAircraftTypes {
    const val SAILPLANE: String = "SAILPLANE"
    const val PARAGLIDER: String = "PARAGLIDER"
    const val HANG_GLIDER: String = "HANG_GLIDER"

    fun normalize(value: String?): String = when (value?.trim()?.uppercase()) {
        PARAGLIDER -> PARAGLIDER
        HANG_GLIDER, "HANGGLIDER" -> HANG_GLIDER
        "GLIDER", SAILPLANE -> SAILPLANE
        else -> SAILPLANE
    }
}
