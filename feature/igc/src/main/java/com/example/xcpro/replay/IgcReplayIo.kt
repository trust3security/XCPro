package com.example.xcpro.replay

import android.content.Context
import android.net.Uri
import android.util.Log
import java.time.Instant

fun Context.loadIgcLog(fileUri: Uri, parser: IgcParser): IgcLog =
    contentResolver.openInputStream(fileUri)?.use { stream ->
        parser.parse(stream)
    } ?: throw IllegalArgumentException("Unable to open IGC file")

fun Context.loadIgcAssetLog(assetPath: String, parser: IgcParser): IgcLog =
    assets.open(assetPath).use { stream ->
        parser.parse(stream)
    }

fun logReplaySessionPrep(
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
        "Prepared replay '${selection.document.displayName ?: selection.document.uri}' " +
            "points=$pointCount duration=${durationSec}s start=$startIso end=$endIso qnh=${"%.1f".format(qnh)}"
    )
}
