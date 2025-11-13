package com.example.xcpro.replay

import android.net.Uri

data class IgcReplayUiState(
    val selectedUri: Uri? = null,
    val selectedFileName: String? = null,
    val statusMessage: String = "Select an IGC file to begin replay.",
    val isPlaying: Boolean = false,
    val speedMultiplier: Double = 4.0,
    val errorMessage: String? = null,
    val isReplayLoaded: Boolean = false
)
