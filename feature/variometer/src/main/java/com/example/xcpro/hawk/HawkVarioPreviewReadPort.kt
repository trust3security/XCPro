package com.example.xcpro.hawk

import kotlinx.coroutines.flow.Flow

interface HawkVarioPreviewReadPort {
    val hawkVarioUiState: Flow<HawkVarioUiState>
}
