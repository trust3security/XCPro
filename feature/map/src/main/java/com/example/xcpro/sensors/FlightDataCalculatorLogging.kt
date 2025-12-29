package com.example.xcpro.sensors

import android.util.Log
import java.util.Locale

internal fun logSlowSnapshot(
    tag: String,
    varioMode: String,
    gpsAltitudeMeters: Double,
    baroAltitudeMeters: Double,
    rawBaroVarioMs: Double,
    levoVarioMs: Double,
    levoSource: String,
    levoValid: Boolean,
    xcSoarVarioMs: Double,
    xcSoarVarioValid: Boolean,
    gpsVarioMs: Double?,
    pressureVarioMs: Double?,
    speedMs: Double,
    aglMeters: Double,
    qnhHpa: Double,
    calibrated: Boolean,
    autoQnhSessionActive: Boolean
) {
    Log.d(
        tag,
        "[SLOW] $varioMode " +
            "GPSalt=${gpsAltitudeMeters.toInt()}m BaroAlt=${baroAltitudeMeters.toInt()}m " +
            "RawBaro=${String.format(Locale.US, "%.2f", rawBaroVarioMs)} " +
            "Levo=${String.format(Locale.US, "%.2f", levoVarioMs)}(src=$levoSource,val=$levoValid) " +
            "XC=${String.format(Locale.US, "%.2f", xcSoarVarioMs)}(val=$xcSoarVarioValid) " +
            "GPSv=${gpsVarioMs?.let { String.format(Locale.US, "%.2f", it) } ?: "--"} " +
            "PressV=${pressureVarioMs?.let { String.format(Locale.US, "%.2f", it) } ?: "--"} " +
            "Spd=${String.format(Locale.US, "%.1f", speedMs)} " +
            "AGL=${aglMeters.toInt()} " +
            "QNH=${String.format(Locale.US, "%.1f", qnhHpa)} cal=$calibrated autoCal=$autoQnhSessionActive"
    )
}
