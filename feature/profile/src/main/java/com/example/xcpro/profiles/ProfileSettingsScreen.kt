package com.example.xcpro.profiles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    
    Scaffold(
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
                            viewModel.updateProfile(editedProfile)
                            navController.popBackStack()
                        }
                    ) {
                        Text("Save")
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
                ProfilePreferencesSettings(
                    profile = editedProfile,
                    onProfileChanged = { editedProfile = it }
                )
            }
            
            item {
                ProfileActionButtons(
                    profile = profile,
                    onExport = { showExportDialog = true },
                    onImport = { showImportDialog = true },
                    onDelete = {
                        viewModel.deleteProfile(profile.id)
                        navController.popBackStack()
                    }
                )
            }
        }
    }
    
    if (showExportDialog) {
        ProfileExportDialog(
            profile = profile,
            onDismiss = { showExportDialog = false },
            onExport = { message ->
                exportMessage = message
                showExportDialog = false
            }
        )
    }
    
    if (showImportDialog) {
        ProfileImportDialog(
            onDismiss = { showImportDialog = false },
            onImport = { importedProfiles ->
                // Handle imported profiles - could show selection dialog
                showImportDialog = false
            },
            onError = { error ->
                exportMessage = error
                showImportDialog = false
            }
        )
    }
    
    exportMessage?.let { message ->
        LaunchedEffect(message) {
            // Show snackbar or similar feedback
            exportMessage = null
        }
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

@Composable
fun ProfilePreferencesSettings(
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
                text = "Preferences",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            // Unit System
            Column {
                Text(
                    text = "Unit System",
                    style = MaterialTheme.typography.labelLarge
                )
                UnitSystem.values().forEach { unitSystem ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = profile.preferences.units == unitSystem,
                            onClick = {
                                onProfileChanged(
                                    profile.copy(
                                        preferences = profile.preferences.copy(units = unitSystem)
                                    )
                                )
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(unitSystem.displayName)
                    }
                }
            }
            
            // Auto Switch Modes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto Switch Flight Modes",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Automatically switch card layouts based on flight conditions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = profile.preferences.autoSwitchModes,
                    onCheckedChange = {
                        onProfileChanged(
                            profile.copy(
                                preferences = profile.preferences.copy(autoSwitchModes = it)
                            )
                        )
                    }
                )
            }
            
            // Card Animations
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Card Animations",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Enable smooth animations for card transitions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = profile.preferences.cardAnimations,
                    onCheckedChange = {
                        onProfileChanged(
                            profile.copy(
                                preferences = profile.preferences.copy(cardAnimations = it)
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun ProfileActionButtons(
    profile: UserProfile,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export")
                }
                
                OutlinedButton(
                    onClick = onImport,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import")
                }
            }
            
            Button(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Profile")
            }
        }
    }
}
