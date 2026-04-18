package com.trust3.xcpro.profiles

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.trust3.xcpro.profiles.ui.ProfileSelectionContent

@Composable
fun ProfileSelectionScreen(
    onProfileSelected: () -> Unit,
    onEditProfile: (UserProfile) -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showImportDialog by remember { mutableStateOf(false) }

    ProfileSelectionContent(
        state = uiState,
        onCompleteFirstLaunch = viewModel::completeFirstLaunch,
        onSelectProfile = { viewModel.selectProfile(it) },
        onDeleteProfile = { viewModel.deleteProfile(it) },
        onCreateProfile = { viewModel.createProfile(it) },
        onShowImportDialog = { showImportDialog = true },
        onShowCreateDialog = viewModel::showCreateDialog,
        onHideCreateDialog = viewModel::hideCreateDialog,
        onRecoverWithDefaultProfile = viewModel::recoverWithDefaultProfile,
        onClearError = viewModel::clearError,
        onContinue = onProfileSelected,
        onEditProfile = onEditProfile,
        storageNamespaceLabel = context.packageName
    )

    if (showImportDialog) {
        ProfileImportDialog(
            canKeepCurrentActive = uiState.activeProfile != null,
            onDismiss = { showImportDialog = false },
            onRequestPreview = viewModel::previewBundle,
            onImportJson = { json, keepCurrentActive, nameCollisionPolicy ->
                viewModel.importBundle(
                    json = json,
                    keepCurrentActive = keepCurrentActive,
                    nameCollisionPolicy = nameCollisionPolicy
                )
                showImportDialog = false
            },
            onError = { error ->
                showImportDialog = false
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            }
        )
    }

    uiState.bundleImportResult?.let { result ->
        ProfileImportResultDialog(
            result = result,
            profiles = uiState.profiles,
            onDismiss = viewModel::clearBundleImportResult
        )
    }
}
