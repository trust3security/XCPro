package com.example.xcpro.replay

import android.content.Context
import android.net.Uri
import android.util.Log
import java.time.Instant

internal fun Context.loadIgcLog(fileUri: Uri): IgcLog =
    contentResolver.openInputStream(fileUri)?.use { stream ->
        IgcParser.parse(stream)
    } ?: throw IllegalArgumentException("Unable to open IGC file")

internal fun Context.loadIgcAssetLog(assetPath: String): IgcLog =
    assets.open(assetPath).use { stream ->
        IgcParser.parse(stream)
    }

internal fun logReplaySessionPrep(
    selection: Selection,
    pointCount: Int,
    startMillis: Long,
    endMillis: Long,
    qnh: Double,
    tag: String
) {
    val startIso = Instant.ofEpochMilli(startMillis).toString()
    val endIso = Instant.ofEpochMilli(endMillis).toString()
    val durationSec = (endMillis - startMillis) / 1000
    Log.i(
        tag,
        "Prepared replay '${selection.displayName ?: selection.uri}' " +
            "points=$pointCount duration=${durationSec}s start=$startIso end=$endIso qnh=${"%.1f".format(qnh)}"
    )
}