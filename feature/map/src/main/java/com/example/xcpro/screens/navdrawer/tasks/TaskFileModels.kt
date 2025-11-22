package com.example.xcpro.screens.navdrawer.tasks

import android.net.Uri
import com.example.ui1.screens.AirspaceClassItem

data class TaskFileEntry(
    val uri: Uri,
    val displayName: String
)

// We reuse AirspaceClassItem from the flightdata module to avoid duplication.
typealias TaskAirspaceClass = AirspaceClassItem
