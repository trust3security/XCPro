package com.example.dfcards

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android resource-backed CardStrings implementation.
 */
class AndroidCardStrings(private val context: Context) : CardStrings {
    override val noData: String = context.getString(R.string.card_label_no_data)
    override val noBaro: String = context.getString(R.string.card_label_no_baro)
    override val noWind: String = context.getString(R.string.card_label_no_wind)
    override val noWpt: String = context.getString(R.string.card_label_no_wpt)
    override val noTask: String = context.getString(R.string.card_label_no_task)
    override val noStart: String = context.getString(R.string.card_label_no_start)
    override val noAlt: String = context.getString(R.string.card_label_no_alt)
    override val noAccel: String = context.getString(R.string.card_label_no_accel)
    override val noFlarm: String = context.getString(R.string.card_label_no_flarm)
    override val noSats: String = context.getString(R.string.card_label_no_sats)
    override val noPolar: String = context.getString(R.string.card_label_no_polar)
    override val noMc: String = context.getString(R.string.card_label_no_mc)
    override val noIgc: String = context.getString(R.string.card_label_no_igc)
    override val unknown: String = context.getString(R.string.card_label_unknown)
    override val stale: String = context.getString(R.string.card_label_stale)
    override val invalid: String = context.getString(R.string.card_label_invalid)
    override val prestart: String = context.getString(R.string.card_label_prestart)
    override val live: String = context.getString(R.string.card_label_live)
    override val thermal: String = context.getString(R.string.card_label_thermal)
    override val gps: String = context.getString(R.string.card_label_gps)
    override val est: String = context.getString(R.string.card_label_est)
    override val mag: String = context.getString(R.string.card_label_mag)
    override val static: String = context.getString(R.string.card_label_static)
    override val netto: String = context.getString(R.string.card_label_netto)
    override val calc: String = context.getString(R.string.card_label_calc)
    override val flight: String = context.getString(R.string.card_label_flight)
    override val qnhPrefix: String = context.getString(R.string.card_label_qnh)
    override val degUnit: String = context.getString(R.string.card_label_deg)
    override val realIgc: String = context.getString(R.string.card_label_real_igc)
    override val raw: String = context.getString(R.string.card_label_raw)
    override val comp: String = context.getString(R.string.card_label_comp)
    override val rOptimized: String = context.getString(R.string.card_label_r_optimized)
    override val rLegacy: String = context.getString(R.string.card_label_r_legacy)
    override val excellent: String = context.getString(R.string.card_label_excellent)
    override val good: String = context.getString(R.string.card_label_good)
    override val ok: String = context.getString(R.string.card_label_ok)
    override val weak: String = context.getString(R.string.card_label_weak)
    override val poor: String = context.getString(R.string.card_label_poor)
    override val te: String = context.getString(R.string.card_label_te)
}

/**
 * Compose helper for providing CardStrings from Android resources.
 */
@Composable
fun rememberCardStrings(): CardStrings {
    val context = LocalContext.current
    return remember(context) { AndroidCardStrings(context) }
}
