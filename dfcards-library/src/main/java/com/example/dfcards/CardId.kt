package com.example.dfcards

// Typed wrapper for card identifiers.
// Invariants: raw is the persisted ID string and may be unknown.

/**
 * Strongly-typed card identifier wrapper used at formatting boundaries.
 */
@JvmInline
internal value class CardId(val raw: String) {
    /**
     * Returns true when this ID matches a known catalog card.
     */
    fun isKnown(): Boolean = raw in knownIds

    /**
     * Returns the known ID enum if this ID is recognized.
     */
    fun toKnownOrNull(): KnownCardId? = KnownCardId.fromRaw(raw)

    companion object {
        const val GPS_ALT = "gps_alt"
        const val BARO_ALT = "baro_alt"
        const val AGL = "agl"
        const val VARIO = "vario"
        const val IAS = "ias"
        const val TAS = "tas"
        const val GROUND_SPEED = "ground_speed"

        const val VARIO_OPTIMIZED = "vario_optimized"
        const val VARIO_LEGACY = "vario_legacy"
        const val VARIO_RAW = "vario_raw"
        const val VARIO_GPS = "vario_gps"
        const val VARIO_COMPLEMENTARY = "vario_complementary"
        const val HAWK_VARIO = "hawk_vario"
        const val REAL_IGC_VARIO = "real_igc_vario"

        const val TRACK = "track"
        const val WPT_DIST = "wpt_dist"
        const val WPT_BRG = "wpt_brg"
        const val FINAL_GLD = "final_gld"
        const val WPT_ETA = "wpt_eta"

        const val THERMAL_AVG = "thermal_avg"
        const val THERMAL_TC_AVG = "thermal_tc_avg"
        const val THERMAL_T_AVG = "thermal_t_avg"
        const val THERMAL_TC_GAIN = "thermal_tc_gain"
        const val NETTO = "netto"
        const val NETTO_AVG30 = "netto_avg30"
        const val LEVO_NETTO = "levo_netto"
        const val LD_CURR = "ld_curr"
        const val MC_SPEED = "mc_speed"

        const val WIND_SPD = "wind_spd"
        const val WIND_DIR = "wind_dir"
        const val WIND_ARROW = "wind_arrow"
        const val LOCAL_TIME = "local_time"
        const val FLIGHT_TIME = "flight_time"

        const val TASK_SPD = "task_spd"
        const val TASK_DIST = "task_dist"
        const val START_ALT = "start_alt"

        const val G_FORCE = "g_force"
        const val FLARM = "flarm"
        const val QNH = "qnh"
        // AI-NOTE: Preserve legacy typo to avoid breaking persisted layouts.
        const val SATELITES = "satelites"
        const val GPS_ACCURACY = "gps_accuracy"

        val knownIds: Set<String> = setOf(
            GPS_ALT,
            BARO_ALT,
            AGL,
            VARIO,
            IAS,
            TAS,
            GROUND_SPEED,
            VARIO_OPTIMIZED,
            VARIO_LEGACY,
            VARIO_RAW,
            VARIO_GPS,
            VARIO_COMPLEMENTARY,
            HAWK_VARIO,
            REAL_IGC_VARIO,
            TRACK,
            WPT_DIST,
            WPT_BRG,
            FINAL_GLD,
            WPT_ETA,
            THERMAL_AVG,
            THERMAL_TC_AVG,
            THERMAL_T_AVG,
            THERMAL_TC_GAIN,
            NETTO,
            NETTO_AVG30,
            LEVO_NETTO,
            LD_CURR,
            MC_SPEED,
            WIND_SPD,
            WIND_DIR,
            WIND_ARROW,
            LOCAL_TIME,
            FLIGHT_TIME,
            TASK_SPD,
            TASK_DIST,
            START_ALT,
            G_FORCE,
            FLARM,
            QNH,
            SATELITES,
            GPS_ACCURACY
        )

        fun fromRaw(raw: String): CardId = CardId(raw)
    }
}
