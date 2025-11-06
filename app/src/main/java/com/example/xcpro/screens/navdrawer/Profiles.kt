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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import com.example.xcpro.profiles.ProfileViewModel
import com.example.xcpro.profiles.ProfileExportDialog
import com.example.xcpro.profiles.ProfileImportDialog
import com.example.xcpro.screens.navdrawer.SettingsTopAppBar
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    onLoadConfig: () -> Unit = {},
    onSaveConfig: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val uiState by profileViewModel.uiState.collectAsState()
    
    var configContent by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    
    val navBarInsets = WindowInsets.navigationBars.asPaddingValues()
    val hasNavBar = navBarInsets.calculateBottomPadding() > 0.dp

    fun loadConfigFile() {
        try {
            val file = File(context.filesDir, "configuration.json")
            if (file.exists()) {
                configContent = file.readText()
                errorMessage = null
            } else {
                errorMessage = "configuration.json not found in internal storage"
                configContent = null
            }
        } catch (e: IOException) {
            errorMessage = "Error loading configuration.json: ${e.message}"
            configContent = null
        }
    }

    LaunchedEffect(Unit) {
        loadConfigFile()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            SettingsTopAppBar(
                title = "Profiles",
                onNavigateUp = { navController.popBackStack() },
                onOpenDrawer = {
                    scope.launch {
                        navController.popBackStack("map", inclusive = false)
                        drawerState.open()
                    }
                },
                onNavigateToMap = {
                    scope.launch {
                        drawerState.close()
                        navController.popBackStack("map", inclusive = false)
                    }
                }
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
                        loadConfigFile()
                    }) {
                        Text("Load")
                    }
                    Button(onClick = {
                        onSaveConfig()
                        loadConfigFile()
                    }) {
                        Text("Save")
                    }
                    Button(onClick = {
                        navController.popBackStack("map", inclusive = false)
                    }) {
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
                        text = "Flight Profiles",
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
                                    imageVector = activeProfile.aircraftType.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Active Profile",
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
                        Row {
                            TextButton(onClick = { showImportDialog = true }) {
                                Text("Import")
                            }
                            TextButton(onClick = { showExportDialog = true }) {
                                Text("Export All")
                            }
                        }
                    }
                }
                
                items(uiState.profiles.size) { index ->
                    val profile = uiState.profiles[index]
                    Card(
                        onClick = { navController.navigate("profile_settings/${profile.id}") },
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = profile.aircraftType.icon,
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
                            if (profile.isActive) {
                                Text(
                                    text = "ACTIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                
                // Legacy Config Section (if needed)
                if (configContent != null || errorMessage != null) {
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
                                
                                if (errorMessage != null) {
                                    Text(
                                        text = errorMessage!!,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else if (configContent != null) {
                                    Text(
                                        text = configContent!!,
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
                                loadConfigFile()
                            }) {
                                Text("Load")
                            }
                            Button(onClick = {
                                onSaveConfig()
                                loadConfigFile()
                            }) {
                                Text("Save")
                            }
                            Button(onClick = {
                                navController.popBackStack("map", inclusive = false)
                            }) {
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
            profile = null, // Export all profiles
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
                // Import all profiles (they get new IDs to avoid conflicts)
                importedProfiles.forEach { profile ->
                    profileViewModel.createProfile(
                        com.example.xcpro.profiles.ProfileCreationRequest(
                            name = "${profile.name} (Imported)",
                            aircraftType = profile.aircraftType,
                            aircraftModel = profile.aircraftModel,
                            description = profile.description
                        )
                    )
                }
                showImportDialog = false
                exportMessage = "Successfully imported ${importedProfiles.size} profiles"
            },
            onError = { error ->
                exportMessage = error
                showImportDialog = false
            }
        )
    }
    
    // Show feedback messages
    exportMessage?.let { message ->
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(3000)
            exportMessage = null
        }
    }
}
