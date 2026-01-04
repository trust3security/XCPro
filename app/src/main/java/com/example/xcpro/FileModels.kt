package com.example.xcpro

import android.net.Uri

// Models and static mappings for the flight management UI.

data class FileItem(
    val name: String,
    val enabled: Boolean,
    val count: Int,
    val status: String,
    val uri: Uri
)

data class AirspaceClassItem(
    val className: String,
    val enabled: Boolean,
    val color: String,
    val description: String
)

val airspaceClassInfo = mapOf(
    "A" to Pair("#FF0000", "Controlled airspace - IFR only"),
    "B" to Pair("#0000FF", "Controlled airspace - IFR and VFR"),
    "C" to Pair("#FF00FF", "Controlled airspace - IFR and VFR with clearance"),
    "D" to Pair("#0000FF", "Controlled airspace - Radio communication required"),
    "E" to Pair("#FF00FF", "Controlled airspace - IFR controlled, VFR not"),
    "F" to Pair("#808080", "Advisory airspace"),
    "G" to Pair("#FFFFFF", "Uncontrolled airspace"),
    "CTR" to Pair("#0000FF", "Control Zone"),
    "RMZ" to Pair("#00FF00", "Radio Mandatory Zone"),
    "RESTRICTED" to Pair("#FF0000", "Restricted area"),
    "DANGER" to Pair("#FFA500", "Danger area")
)
