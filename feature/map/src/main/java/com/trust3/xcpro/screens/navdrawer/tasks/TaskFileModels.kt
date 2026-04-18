package com.trust3.xcpro.screens.navdrawer.tasks

import com.example.ui1.screens.AirspaceClassItem
import com.trust3.xcpro.common.documents.DocumentRef

data class TaskFileEntry(
    val document: DocumentRef,
    val displayName: String
)

// We reuse AirspaceClassItem from the flightdata module to avoid duplication.
typealias TaskAirspaceClass = AirspaceClassItem
