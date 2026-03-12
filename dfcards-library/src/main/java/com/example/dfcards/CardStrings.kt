package com.example.dfcards

// Card label strings used by the formatter and UI.
// Invariants: ASCII-only; no vendor-specific wording.

/**
 * Provides user-facing labels for card formatting.
 */
interface CardStrings {
    val noData: String
    val noBaro: String
    val noWind: String
    val noWpt: String
    val noTask: String
    val noStart: String
    val noAlt: String
    val noAccel: String
    val noFlarm: String
    val noSats: String
    val noPolar: String
    val noMc: String
    val noIgc: String
    val unknown: String
    val stale: String
    val invalid: String
    val prestart: String
    val live: String
    val gps: String
    val est: String
    val mag: String
    val static: String
    val netto: String
    val calc: String
    val flight: String
    val qnhPrefix: String
    val degUnit: String
    val realIgc: String
    val raw: String
    val comp: String
    val rOptimized: String
    val rLegacy: String
    val excellent: String
    val good: String
    val ok: String
    val weak: String
    val poor: String
    val te: String
}

// Fallback English strings to keep non-Android tests deterministic.
internal class DefaultCardStrings : CardStrings {
    override val noData: String = "NO DATA"
    override val noBaro: String = "NO BARO"
    override val noWind: String = "NO WIND"
    override val noWpt: String = "NO WPT"
    override val noTask: String = "NO TASK"
    override val noStart: String = "NO START"
    override val noAlt: String = "NO ALT"
    override val noAccel: String = "NO ACCEL"
    override val noFlarm: String = "NO FLARM"
    override val noSats: String = "NO SATS"
    override val noPolar: String = "NO POLAR"
    override val noMc: String = "NO MC"
    override val noIgc: String = "NO IGC"
    override val unknown: String = "UNKNOWN"
    override val stale: String = "STALE"
    override val invalid: String = "INVALID"
    override val prestart: String = "PRESTART"
    override val live: String = "LIVE"
    override val gps: String = "GPS"
    override val est: String = "EST"
    override val mag: String = "MAG"
    override val static: String = "STATIC"
    override val netto: String = "NETTO"
    override val calc: String = "CALC"
    override val flight: String = "FLIGHT"
    override val qnhPrefix: String = "QNH"
    override val degUnit: String = "deg"
    override val realIgc: String = "REAL IGC"
    override val raw: String = "RAW"
    override val comp: String = "COMP"
    override val rOptimized: String = "R=0.5"
    override val rLegacy: String = "R=2.0"
    override val excellent: String = "EXCELLENT"
    override val good: String = "GOOD"
    override val ok: String = "OK"
    override val weak: String = "WEAK"
    override val poor: String = "POOR"
    override val te: String = "TE"
}
