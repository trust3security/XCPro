package com.example.ui1.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.items
import com.example.xcpro.profiles.ProfileViewModel
import com.example.xcpro.profiles.ProfileExportDialog
import com.example.xcpro.profiles.ProfileImportDialog
import com.example.xcpro.profiles.ProfileImportResultDialog
import com.example.xcpro.profiles.ProfileIdResolver
import com.example.xcpro.profiles.UserProfile
import com.example.xcpro.screens.navdrawer.SettingsTopAppBar
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.profiles.ProfilesConfigViewModel
import com.example.xcpro.profiles.ui.CreateProfileDialog
import com.example.xcpro.profiles.ui.icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    onLoadConfig: () -> Unit = {},
    onSaveConfig: () -> Unit = {},
    onNavigateUp: (() -> Unit)? = null,
    onSecondaryNavigate: (() -> Unit)? = null,
    onNavigateToMap: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val configViewModel: ProfilesConfigViewModel = hiltViewModel()
    val uiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val configUiState by configViewModel.uiState.collectAsStateWithLifecycle()
    
    var showExportDialog by remember { mutableStateOf(false) }
    var exportProfile by remember { mutableStateOf<UserProfile?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val navigateUpAction: () -> Unit = onNavigateUp ?: {
        navController.navigateUp()
        Unit
    }
    val secondaryNavigateAction: () -> Unit = onSecondaryNavigate ?: {
        navController.popBackStack()
        Unit
    }
    val navigateToMapAction: () -> Unit = onNavigateToMap ?: {
        scope.launch {
            drawerState.close()
            navController.popBackStack("map", inclusive = false)
        }
        Unit
    }
    val closeAction: () -> Unit = onClose ?: {
        navController.popBackStack("map", inclusive = false)
        Unit
    }
    
    val navBarInsets = WindowInsets.navigationBars.asPaddingValues()
    val hasNavBar = navBarInsets.calculateBottomPadding() > 0.dp

    LaunchedEffect(Unit) {
        configViewModel.loadConfig()
    }

    LaunchedEffect(uiState.error) {
        val message = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        profileViewModel.clearError()
    }

    LaunchedEffect(exportMessage) {
        val message = exportMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        exportMessage = null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            SettingsTopAppBar(
                title = "Profiles",
                onNavigateUp = navigateUpAction,
                onSecondaryNavigate = secondaryNavigateAction,
                onNavigateToMap = navigateToMapAction
            )
        },
        bottomBar = {
            if (!hasNavBar) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = {
                        onLoadConfig()
                        configViewModel.loadConfig()
                    }) {
                        Text("Load Legacy Config")
                    }
                    Button(onClick = {
                        onSaveConfig()
                        configViewModel.loadConfig()
                    }) {
                        Text("Save Legacy Config")
                    }
                    Button(onClick = closeAction) {
                        Text("Close")
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 8.dp)
                    .padding(bottom = if (hasNavBar) navBarInsets.calculateBottomPadding() else 0.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "Aircraft Profiles",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                // Active Profile Card
                uiState.activeProfile?.let { activeProfile ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = activeProfile.aircraftType.icon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Active Aircraft Profile",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = activeProfile.getDisplayName(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                TextButton(
                                    onClick = { navController.navigate("profile_selection") }
                                ) {
                                    Text("Switch", color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                    }
                }
                
                // All Profiles List
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "All Profiles (${uiState.profiles.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { profileViewModel.showCreateDialog() },
                            enabled = !uiState.isLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("New Aircraft Profile")
                        }
                        OutlinedButton(
                            onClick = { showImportDialog = true },
                            enabled = !uiState.isLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Load Profile File")
                        }
                        OutlinedButton(
                            onClick = {
                                exportProfile = null
                                showExportDialog = true
                            },
                            enabled = !uiState.isLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save All Profiles")
                        }
                    }
                }
                
                items(uiState.profiles, key = { it.id }) { profile ->
                    Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = profile.aircraftType.icon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.getDisplayName(),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = profile.aircraftType.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (uiState.activeProfile?.id == profile.id) {
                                    Text(
                                        text = "ACTIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { profileViewModel.selectProfile(profile) },
                                    enabled = !uiState.isLoading && uiState.activeProfile?.id != profile.id,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        if (uiState.activeProfile?.id == profile.id) {
                                            "Active"
                                        } else {
                                            "Activate"
                                        }
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        exportProfile = profile
                                        showExportDialog = true
                                    },
                                    enabled = !uiState.isLoading,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Save Profile File")
                                }
                                TextButton(
                                    onClick = { navController.navigate("profile_settings/${profile.id}") },
                                    enabled = !uiState.isLoading,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Edit")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = { profileViewModel.duplicateProfile(profile) },
                                    enabled = !uiState.isLoading,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Duplicate")
                                }
                                TextButton(
                                    onClick = { profileViewModel.deleteProfile(profile.id) },
                                    enabled = !uiState.isLoading &&
                                        !ProfileIdResolver.isCanonicalDefault(profile.id),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
                
                // Legacy Config Section (if needed)
                if (configUiState.configContent != null || configUiState.errorMessage != null) {
                    item {
                        Card(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Legacy Configuration",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                if (configUiState.errorMessage != null) {
                                    Text(
                                        text = configUiState.errorMessage!!,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else if (configUiState.configContent != null) {
                                    Text(
                                        text = configUiState.configContent!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 10
                                    )
                                }
                            }
                        }
                    }
                }
                if (hasNavBar) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(onClick = {
                                onLoadConfig()
                                configViewModel.loadConfig()
                            }) {
                                Text("Load Legacy Config")
                            }
                            Button(onClick = {
                                onSaveConfig()
                                configViewModel.loadConfig()
                            }) {
                                Text("Save Legacy Config")
                            }
                            Button(onClick = closeAction) {
                                Text("Close")
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Profile Export/Import Dialogs
    if (showExportDialog) {
        ProfileExportDialog(
            profile = exportProfile,
            onDismiss = {
                showExportDialog = false
                exportProfile = null
            },
            onRequestExportJson = {
                val profileIds = exportProfile?.id?.let(::setOf)
                profileViewModel.exportBundle(profileIds)
            },
            onExport = { message ->
                exportMessage = message
                showExportDialog = false
                exportProfile = null
            }
        )
    }
    
    if (showImportDialog) {
        ProfileImportDialog(
            canKeepCurrentActive = uiState.activeProfile != null,
            onDismiss = { showImportDialog = false },
            onRequestPreview = profileViewModel::previewBundle,
            onImportJson = { json, keepCurrentActive, nameCollisionPolicy ->
                profileViewModel.importBundle(
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
            onDismiss = profileViewModel::clearBundleImportResult
        )
    }

    if (uiState.showCreateDialog) {
        CreateProfileDialog(
            onDismiss = profileViewModel::hideCreateDialog,
            onCreate = profileViewModel::createProfile
        )
    }
}

