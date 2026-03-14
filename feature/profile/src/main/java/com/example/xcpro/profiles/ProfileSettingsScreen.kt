package com.example.xcpro.profiles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.xcpro.profiles.ui.icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    profileId: String,
    navController: NavHostController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (!uiState.isHydrated) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val profile = uiState.profiles.find { it.id == profileId }
    
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var pendingMutation by remember { mutableStateOf<PendingProfileMutation?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        val message = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearError()
    }
    LaunchedEffect(exportMessage) {
        val message = exportMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        exportMessage = null
    }
    
    if (profile == null) {
        LaunchedEffect(Unit) {
            navController.popBackStack()
        }
        return
    }
    
    var editedProfile by remember(profile.id) { mutableStateOf(profile) }
    LaunchedEffect(profile) {
        editedProfile = profile
    }
    LaunchedEffect(uiState.isLoading, uiState.error, uiState.profiles, pendingMutation) {
        val resolution = resolvePendingProfileMutation(
            pendingMutation = pendingMutation,
            isLoading = uiState.isLoading,
            hasError = uiState.error != null,
            profileExists = uiState.profiles.any { it.id == profileId }
        )
        if (resolution.pendingMutation != pendingMutation) {
            pendingMutation = resolution.pendingMutation
        }
        if (resolution.shouldPopBackStack) {
            navController.popBackStack()
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Profile Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            pendingMutation = PendingProfileMutation(PendingProfileMutationType.SAVE)
                            viewModel.updateProfile(editedProfile)
                        },
                        enabled = !uiState.isLoading
                    ) {
                        Text("Save Changes")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ProfileBasicSettings(
                    profile = editedProfile,
                    onProfileChanged = { editedProfile = it }
                )
            }

            item {
                ProfileActionButtons(
                    onExport = { showExportDialog = true },
                    onImport = { showImportDialog = true },
                    isLoading = uiState.isLoading,
                    canDelete = !ProfileIdResolver.isCanonicalDefault(profile.id),
                    onDelete = {
                        pendingMutation = PendingProfileMutation(PendingProfileMutationType.DELETE)
                        viewModel.deleteProfile(profile.id)
                    }
                )
            }
        }
    }
    
    if (showExportDialog) {
        ProfileExportDialog(
            profile = profile,
            onDismiss = { showExportDialog = false },
            onRequestExportBundle = {
                viewModel.exportBundle(profileIds = setOf(profile.id))
            },
            onExport = { message ->
                exportMessage = message
                showExportDialog = false
            }
        )
    }
    
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
                exportMessage = error
                showImportDialog = false
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

@Composable
fun ProfileBasicSettings(
    profile: UserProfile,
    onProfileChanged: (UserProfile) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Basic Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            OutlinedTextField(
                value = profile.name,
                onValueChange = { onProfileChanged(profile.copy(name = it)) },
                label = { Text("Profile Name") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = profile.aircraftModel ?: "",
                onValueChange = { onProfileChanged(profile.copy(aircraftModel = it.takeIf { it.isNotBlank() })) },
                label = { Text("Aircraft Model (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = profile.description ?: "",
                onValueChange = { onProfileChanged(profile.copy(description = it.takeIf { it.isNotBlank() })) },
                label = { Text("Description (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = profile.aircraftType.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Aircraft Type",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = profile.aircraftType.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
