package com.example.xcpro.profiles

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.xcpro.profiles.ui.ProfileSelectionContent

@Composable
fun ProfileSelectionScreen(
    onProfileSelected: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ProfileSelectionContent(
        state = uiState,
        onSelectProfile = { viewModel.selectProfile(it) },
        onDeleteProfile = { viewModel.deleteProfile(it) },
        onCreateProfile = { viewModel.createProfile(it) },
        onShowCreateDialog = viewModel::showCreateDialog,
        onHideCreateDialog = viewModel::hideCreateDialog,
        onClearError = viewModel::clearError,
        onSkip = onProfileSelected,
        onContinue = onProfileSelected
    )
}
