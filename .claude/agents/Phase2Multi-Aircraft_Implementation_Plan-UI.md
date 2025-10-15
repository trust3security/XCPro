# Phase 2: UI Layer Implementation Plan
## Aircraft Management User Interface

### Document Information
- **Phase**: 2 of 4
- **Duration**: Weeks 3-4
- **Dependencies**: Phase 1 (Data Layer) Complete
- **Status**: Implementation Ready

---

## 🎯 Phase 2 Overview

Phase 2 focuses on creating the user interface components for pilot and aircraft management, including navigation drawer enhancements, aircraft management screens, and the migration wizard.

### Key Deliverables
1. **Enhanced Navigation Drawer** with pilot/aircraft sections
2. **Aircraft Management Screens** (list, add, edit, delete)
3. **Migration Wizard UI** for smooth user transition
4. **Aircraft Quick Switch Component** for easy switching
5. **Updated Profile Management** adapted for pilot concepts

---

## 📱 2.1 Enhanced Navigation Drawer

### Current Navigation Structure Analysis
The current navigation drawer has these main sections:
- Profile section (switch profiles)
- Task section (home waypoint, tasks)
- Map Style section
- Settings section
- Account section

### New Navigation Structure

#### Update Existing Navigation Drawer

**File**: `app/src/main/java/com/example/baseui1/MainActivity.kt` (navigation drawer section)

**Enhanced Navigation Items:**
```kotlin
// Add to existing navigation drawer implementation

@Composable
fun EnhancedNavigationDrawer(
    navController: NavHostController,
    drawerState: DrawerState,
    pilotViewModel: PilotViewModel = viewModel(),
    aircraftViewModel: AircraftViewModel = viewModel()
) {
    val uiState by pilotViewModel.uiState.collectAsState()
    val aircraftState by aircraftViewModel.uiState.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                LazyColumn(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pilot Section (Enhanced)
                    item {
                        PilotSection(
                            activePilot = uiState.activePilot,
                            onSwitchPilot = { navController.navigate("pilot_selection") },
                            onManagePilots = { navController.navigate("pilots") }
                        )
                    }

                    // Aircraft Section (New)
                    item {
                        AircraftSection(
                            activeAircraft = aircraftState.activeAircraft,
                            availableAircraft = aircraftState.aircraftForCurrentPilot,
                            onSwitchAircraft = { aircraft ->
                                aircraftViewModel.setActiveAircraft(aircraft.id)
                            },
                            onManageAircraft = { navController.navigate("aircraft_management") },
                            onAddAircraft = { navController.navigate("add_aircraft") }
                        )
                    }

                    // Divider
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }

                    // Existing sections (Task, Map Style, etc.)
                    // ... keep existing navigation items ...
                }
            }
        }
    ) {
        // Main content
    }
}

@Composable
fun PilotSection(
    activePilot: Pilot?,
    onSwitchPilot: () -> Unit,
    onManagePilots: () -> Unit
) {
    Column {
        Text(
            text = "Pilot",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        activePilot?.let { pilot ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pilot.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${pilot.getActiveAircraftCount()} aircraft",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    TextButton(
                        onClick = onSwitchPilot
                    ) {
                        Text("Switch", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onManagePilots,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Manage Pilots")
        }
    }
}

@Composable
fun AircraftSection(
    activeAircraft: Aircraft?,
    availableAircraft: List<Aircraft>,
    onSwitchAircraft: (Aircraft) -> Unit,
    onManageAircraft: () -> Unit,
    onAddAircraft: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Aircraft",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onAddAircraft) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Aircraft",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Active Aircraft Display
        activeAircraft?.let { aircraft ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = aircraft.type.icon,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = aircraft.getDisplayName(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = aircraft.type.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Quick Switch for Multiple Aircraft
        if (availableAircraft.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))

            AircraftQuickSwitch(
                currentAircraft = activeAircraft,
                availableAircraft = availableAircraft,
                onAircraftSelected = onSwitchAircraft
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onManageAircraft,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Build, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Manage Aircraft")
        }
    }
}
```

### Aircraft Quick Switch Component

**File**: `app/src/main/java/com/example/baseui1/aircraft/components/AircraftQuickSwitch.kt`

```kotlin
package com.example.xcpro.aircraft.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.xcpro.aircraft.models.Aircraft

@Composable
fun AircraftQuickSwitch(
    currentAircraft: Aircraft?,
    availableAircraft: List<Aircraft>,
    onAircraftSelected: (Aircraft) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(availableAircraft) { aircraft ->
            AircraftQuickSwitchCard(
                aircraft = aircraft,
                isSelected = aircraft.id == currentAircraft?.id,
                onSelected = { onAircraftSelected(aircraft) }
            )
        }
    }
}

@Composable
private fun AircraftQuickSwitchCard(
    aircraft: Aircraft,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(100.dp)
            .clickable { onSelected() },
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        },
        elevation = if (isSelected) {
            CardDefaults.cardElevation(defaultElevation = 4.dp)
        } else {
            CardDefaults.cardElevation(defaultElevation = 1.dp)
        }
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = aircraft.type.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = aircraft.getShortName(),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}
```

---

## 📱 2.2 Aircraft Management Screens

### Aircraft List Screen

**File**: `app/src/main/java/com/example/baseui1/aircraft/screens/AircraftListScreen.kt`

```kotlin
package com.example.xcpro.aircraft.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.xcpro.aircraft.models.Aircraft
import com.example.xcpro.aircraft.viewmodels.AircraftViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftListScreen(
    navController: NavHostController,
    aircraftViewModel: AircraftViewModel = viewModel()
) {
    val uiState by aircraftViewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<Aircraft?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aircraft Management") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("add_aircraft") }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Aircraft")
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Your Aircraft (${uiState.aircraftForCurrentPilot.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(uiState.aircraftForCurrentPilot) { aircraft ->
                AircraftListItem(
                    aircraft = aircraft,
                    isActive = aircraft.id == uiState.activeAircraft?.id,
                    onEdit = { navController.navigate("edit_aircraft/${aircraft.id}") },
                    onDelete = { showDeleteDialog = aircraft },
                    onSetActive = { aircraftViewModel.setActiveAircraft(aircraft.id) }
                )
            }

            if (uiState.aircraftForCurrentPilot.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.AirplanemodeActive,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Aircraft Added",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Add your first aircraft to get started",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { navController.navigate("add_aircraft") }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add Aircraft")
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    showDeleteDialog?.let { aircraft ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Aircraft") },
            text = {
                Text("Are you sure you want to delete \"${aircraft.getDisplayName()}\"? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        aircraftViewModel.deleteAircraft(aircraft.id)
                        showDeleteDialog = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AircraftListItem(
    aircraft: Aircraft,
    isActive: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetActive: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isActive) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = aircraft.type.icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = aircraft.getDisplayName(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = aircraft.type.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (aircraft.registration != null || aircraft.competitionNumber != null) {
                    Text(
                        text = listOfNotNull(aircraft.registration, aircraft.competitionNumber?.let { "CN:$it" })
                            .joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        }
                    )
                }
            }

            // Active Badge
            if (isActive) {
                Text(
                    text = "ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else {
                TextButton(onClick = onSetActive) {
                    Text("Set Active")
                }
            }

            // Menu
            var expanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = if (isActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            expanded = false
                            onEdit()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            expanded = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}
```

### Add Aircraft Screen

**File**: `app/src/main/java/com/example/baseui1/aircraft/screens/AddAircraftScreen.kt`

```kotlin
package com.example.xcpro.aircraft.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.xcpro.aircraft.models.Aircraft
import com.example.xcpro.aircraft.viewmodels.AircraftViewModel
import com.example.xcpro.profiles.AircraftType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAircraftScreen(
    navController: NavHostController,
    aircraftViewModel: AircraftViewModel = viewModel()
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AircraftType.SAILPLANE) }
    var model by remember { mutableStateOf("") }
    var registration by remember { mutableStateOf("") }
    var competitionNumber by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Aircraft") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                isLoading = true
                                aircraftViewModel.createAircraft(
                                    name = name.trim(),
                                    type = selectedType,
                                    model = model.trim().takeIf { it.isNotEmpty() },
                                    registration = registration.trim().takeIf { it.isNotEmpty() },
                                    competitionNumber = competitionNumber.trim().takeIf { it.isNotEmpty() }
                                ) { success ->
                                    isLoading = false
                                    if (success) {
                                        navController.navigateUp()
                                    }
                                }
                            }
                        },
                        enabled = name.isNotBlank() && !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Aircraft Type Selection
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Aircraft Type",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    AircraftType.values().forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedType == type,
                                onClick = { selectedType = type }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = type.icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = type.displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "${type.getAvailableFlightModes().size} flight modes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Basic Information
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Aircraft Information",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Aircraft Name *") },
                        placeholder = { Text("e.g., My ASG 29") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words
                        ),
                        isError = name.isBlank()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model") },
                        placeholder = { Text("e.g., ASG 29E") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = registration,
                            onValueChange = { registration = it.uppercase() },
                            label = { Text("Registration") },
                            placeholder = { Text("D-KXXX") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters
                            )
                        )

                        OutlinedTextField(
                            value = competitionNumber,
                            onValueChange = { competitionNumber = it },
                            label = { Text("Comp. Number") },
                            placeholder = { Text("29") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            )
                        )
                    }
                }
            }

            // Preview Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Preview",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = selectedType.icon,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (name.isNotBlank()) {
                                    buildString {
                                        if (model.isNotBlank()) append("$model - ")
                                        append(name)
                                        if (registration.isNotBlank()) append(" - $registration")
                                    }
                                } else {
                                    "Aircraft name required"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (name.isNotBlank()) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                            Text(
                                text = selectedType.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Help Text
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Aircraft Setup Tips",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Each aircraft will have independent screen configurations\n" +
                                        "• Flight modes available depend on aircraft type\n" +
                                        "• You can customize flight data layouts per aircraft later",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// Extension function to get available flight modes for aircraft type
private fun AircraftType.getAvailableFlightModes(): List<String> {
    return when (this) {
        AircraftType.PARAGLIDER, AircraftType.HANG_GLIDER ->
            listOf("Cruise", "Thermal")
        AircraftType.SAILPLANE, AircraftType.GLIDER ->
            listOf("Cruise", "Thermal", "Final Glide")
    }
}
```

---

## 📱 2.3 Migration Wizard UI

### Migration Wizard Flow

**File**: `app/src/main/java/com/example/baseui1/aircraft/migration/MigrationWizardScreen.kt`

```kotlin
package com.example.xcpro.aircraft.migration

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.xcpro.aircraft.viewmodels.MigrationViewModel

@Composable
fun MigrationWizardScreen(
    navController: NavHostController,
    migrationViewModel: MigrationViewModel = viewModel()
) {
    val uiState by migrationViewModel.uiState.collectAsState()

    when (uiState.currentStep) {
        MigrationStep.WELCOME -> {
            MigrationWelcomeStep(
                onContinue = { migrationViewModel.nextStep() },
                onSkip = { navController.navigateUp() }
            )
        }
        MigrationStep.ANALYSIS -> {
            MigrationAnalysisStep(
                profileGroups = uiState.profileGroups,
                onContinue = { migrationViewModel.nextStep() },
                onBack = { migrationViewModel.previousStep() }
            )
        }
        MigrationStep.CONFIRMATION -> {
            MigrationConfirmationStep(
                isProcessing = uiState.isProcessing,
                onConfirm = { migrationViewModel.performMigration() },
                onBack = { migrationViewModel.previousStep() }
            )
        }
        MigrationStep.COMPLETE -> {
            MigrationCompleteStep(
                result = uiState.migrationResult,
                onFinish = { navController.navigate("map") { popUpTo(0) } }
            )
        }
        MigrationStep.ERROR -> {
            MigrationErrorStep(
                error = uiState.error,
                onRetry = { migrationViewModel.retryMigration() },
                onSkip = { navController.navigateUp() }
            )
        }
    }
}

@Composable
private fun MigrationWelcomeStep(
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Flight,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Welcome to Multi-Aircraft Support!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "We've detected you have multiple profiles. Let's consolidate them under your pilot identity for easier management.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "What's New:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "• One pilot identity for all your aircraft\n" +
                            "• Independent screen configurations per aircraft\n" +
                            "• Easy aircraft switching during flights\n" +
                            "• All your existing configurations preserved",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f)
            ) {
                Text("Skip for Now")
            }
            Button(
                onClick = onContinue,
                modifier = Modifier.weight(1f)
            ) {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun MigrationAnalysisStep(
    profileGroups: Map<String, List<String>>,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Migration Preview",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "We'll create the following pilot profiles with their aircraft:",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(profileGroups.entries.toList()) { (pilotName, profileNames) ->
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pilot: $pilotName",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        profileNames.forEach { profileName ->
                            Row(
                                modifier = Modifier.padding(start = 32.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Flight,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Aircraft: $profileName",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }
            Button(
                onClick = onContinue,
                modifier = Modifier.weight(1f)
            ) {
                Text("Continue Migration")
            }
        }
    }
}

@Composable
private fun MigrationConfirmationStep(
    isProcessing: Boolean,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isProcessing) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Migrating your profiles...",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "This may take a moment",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Icon(
                Icons.Default.Backup,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Ready to Migrate",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Before we start:",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "• We'll create a backup of your current profiles\n" +
                                "• All your configurations will be preserved\n" +
                                "• You can undo this migration if needed\n" +
                                "• The migration typically takes less than a minute",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing
                ) {
                    Text("Back")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing
                ) {
                    Text("Start Migration")
                }
            }
        }
    }
}

@Composable
private fun MigrationCompleteStep(
    result: MigrationResult?,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Migration Complete!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        result?.let { migrationResult ->
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Migration Summary:",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "✓ ${migrationResult.pilotsCreated} pilot profile(s) created\n" +
                                "✓ ${migrationResult.aircraftCreated} aircraft added\n" +
                                "✓ ${migrationResult.profilesMigrated} legacy profiles migrated\n" +
                                "✓ Backup saved to: ${migrationResult.backupPath.substringAfterLast("/")}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Flying!")
        }
    }
}

@Composable
private fun MigrationErrorStep(
    error: String?,
    onRetry: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Migration Error",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Error Details:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = error ?: "An unexpected error occurred during migration.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f)
            ) {
                Text("Skip Migration")
            }
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f)
            ) {
                Text("Retry")
            }
        }
    }
}

enum class MigrationStep {
    WELCOME,
    ANALYSIS,
    CONFIRMATION,
    COMPLETE,
    ERROR
}
```

---

## 📋 Phase 2 Task Summary

### Week 3: Navigation and Core UI Components

#### Day 1-2: Enhanced Navigation Drawer
- [ ] Update navigation drawer structure with pilot/aircraft sections
- [ ] Implement `PilotSection` component with active pilot display
- [ ] Implement `AircraftSection` component with quick switch
- [ ] Add proper navigation routing for new screens

#### Day 3-4: Aircraft Quick Switch
- [ ] Create `AircraftQuickSwitch` component with horizontal scrolling
- [ ] Implement aircraft selection logic with visual feedback
- [ ] Add aircraft switching state management
- [ ] Test quick switch performance with multiple aircraft

#### Day 5: Aircraft List Screen
- [ ] Create `AircraftListScreen` with add/edit/delete actions
- [ ] Implement aircraft list item component with context menu
- [ ] Add empty state for users with no aircraft
- [ ] Implement delete confirmation dialog

### Week 4: Aircraft Management and Migration UI

#### Day 1-2: Add/Edit Aircraft Screens
- [ ] Create `AddAircraftScreen` with form validation
- [ ] Implement aircraft type selection with radio buttons
- [ ] Add aircraft preview card with live updates
- [ ] Create aircraft information form with proper input types

#### Day 3-4: Migration Wizard
- [ ] Implement multi-step migration wizard with progress tracking
- [ ] Create welcome step with feature explanation
- [ ] Add migration analysis step with profile grouping preview
- [ ] Implement confirmation step with processing indicator

#### Day 5: Migration Completion and Error Handling
- [ ] Create migration completion step with success summary
- [ ] Implement error handling step with retry functionality
- [ ] Add proper navigation flow for migration wizard
- [ ] Test migration wizard with various profile scenarios

### Phase 2 Success Criteria

✅ **Navigation Enhanced:**
- Navigation drawer displays pilot and aircraft sections clearly
- Aircraft quick switch allows seamless aircraft selection
- Navigation performance remains smooth with multiple aircraft

✅ **Aircraft Management Complete:**
- Users can add, edit, delete, and manage aircraft
- Aircraft type selection works with proper validation
- Aircraft list displays clearly with status indicators

✅ **Migration Wizard Ready:**
- Migration wizard guides users through profile conversion
- Error handling provides clear feedback and recovery options
- Migration success rate exceeds 95% in testing

✅ **Ready for Phase 3:**
- UI components integrate properly with data layer
- State management handles aircraft switching smoothly
- Migration system ready for screen configuration integration

This completes the UI foundation needed for Phase 3 screen configuration integration.