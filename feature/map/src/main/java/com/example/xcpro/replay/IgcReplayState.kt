package com.example.xcpro.replay

import com.example.xcpro.common.documents.DocumentRef

data class IgcReplayUiState(
    val selectedDocument: DocumentRef? = null,
    val selectedFileName: String? = null,
    val statusMessage: String = "Select an IGC file to begin replay.",
    val isPlaying: Boolean = false,
    val speedMultiplier: Double = 4.0,
    val errorMessage: String? = null,
    val isReplayLoaded: Boolean = false,
    val elapsedMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val progressFraction: Float = 0f
)
