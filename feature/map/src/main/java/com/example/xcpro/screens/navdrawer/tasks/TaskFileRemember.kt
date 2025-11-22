package com.example.xcpro.screens.navdrawer.tasks

import android.content.Context
import android.net.Uri
import com.example.ui1.screens.AirspaceClassItem
import com.example.xcpro.parseAirspaceClasses

fun rememberAirspaceClasses(
    context: Context,
    files: List<Uri>,
    selectedStates: Map<String, Boolean>
): List<AirspaceClassItem> {
    return parseAirspaceClasses(context, files).map { className ->
        AirspaceClassItem(
            className = className,
            enabled = selectedStates[className] ?: false,
            color = when (className) {
                "A" -> "#FF0000"
                "C" -> "#FF6600"
                "D" -> "#0066FF"
                "R" -> "#FF0000"
                "G" -> "#00AA00"
                "CTR" -> "#9900FF"
                "TMZ" -> "#FFFF00"
                else -> "#888888"
            },
            description = when (className) {
                "A" -> "Controlled - IFR only"
                "C" -> "Controlled - Radio required"
                "D" -> "Controlled - Radio required"
                "R" -> "Restricted airspace"
                "G" -> "General - Uncontrolled"
                "CTR" -> "Control Zone"
                "TMZ" -> "Transponder mandatory"
                else -> "Unknown class"
            }
        )
    }
}
