package com.example.dfcards

// Canonical list of known card identifiers.
// Invariants: raw values match persisted IDs and CardLibraryCatalog entries.

/**
 * Enumerates all known card IDs used by the card catalog and templates.
 */
internal enum class KnownCardId(val raw: String) {
    GPS_ALT("gps_alt"),
    BARO_ALT("baro_alt"),
    AGL("agl"),
    VARIO("vario"),
    IAS("ias"),
    TAS("tas"),
    GROUND_SPEED("ground_speed"),
    VARIO_OPTIMIZED("vario_optimized"),
    VARIO_LEGACY("vario_legacy"),
    VARIO_RAW("vario_raw"),
    VARIO_GPS("vario_gps"),
    VARIO_COMPLEMENTARY("vario_complementary"),
    HAWK_VARIO("hawk_vario"),
    REAL_IGC_VARIO("real_igc_vario"),
    TRACK("track"),
    WPT_DIST("wpt_dist"),
    WPT_BRG("wpt_brg"),
    FINAL_GLD("final_gld"),
    WPT_ETA("wpt_eta"),
    THERMAL_AVG("thermal_avg"),
    THERMAL_TC_AVG("thermal_tc_avg"),
    THERMAL_T_AVG("thermal_t_avg"),
    THERMAL_TC_GAIN("thermal_tc_gain"),
    NETTO("netto"),
    NETTO_AVG30("netto_avg30"),
    LEVO_NETTO("levo_netto"),
    LD_CURR("ld_curr"),
    POLAR_LD("polar_ld"),
    BEST_LD("best_ld"),
    MC_SPEED("mc_speed"),
    WIND_SPD("wind_spd"),
    WIND_DIR("wind_dir"),
    WIND_ARROW("wind_arrow"),
    LOCAL_TIME("local_time"),
    FLIGHT_TIME("flight_time"),
    TASK_SPD("task_spd"),
    TASK_DIST("task_dist"),
    START_ALT("start_alt"),
    G_FORCE("g_force"),
    FLARM("flarm"),
    QNH("qnh"),
    // AI-NOTE: Preserve legacy typo to avoid breaking persisted layouts.
    SATELITES("satelites"),
    GPS_ACCURACY("gps_accuracy");

    companion object {
        /**
         * Returns a KnownCardId for the raw ID when it is recognized.
         */
        fun fromRaw(raw: String): KnownCardId? =
            values().firstOrNull { it.raw == raw }
    }
}
