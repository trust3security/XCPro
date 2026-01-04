package com.example.xcpro

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.dfcards.CardCategory
import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.FlightTemplate
import kotlinx.coroutines.flow.first

// FlightMgmtScreen hosts the flight management UI (airspace, waypoints, templates).

private const val TAG = "FlightMgmt"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightMgmt(
    navController: NavHostController
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cardPreferences = remember { CardPreferences(context) }

    var activeTab by remember { mutableStateOf("screens") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    var selectedFlightMode by remember { mutableStateOf(FlightModeSelection.CRUISE) }
    var allTemplates by remember { mutableStateOf<List<FlightTemplate>>(emptyList()) }
    var selectedTemplate by remember { mutableStateOf<FlightTemplate?>(null) }
    val selectedAirspaceFiles = remember { mutableStateListOf<Uri>() }
    val airspaceCheckedStates = remember { mutableStateOf(mutableMapOf<String, Boolean>()) }
    val selectedWaypointFiles = remember { mutableStateListOf<Uri>() }
    val waypointCheckedStates = remember { mutableStateOf(mutableMapOf<String, Boolean>()) }
    val selectedClasses = remember { mutableStateOf(mutableMapOf<String, Boolean>()) }
    var selectedCategory by remember { mutableStateOf(CardCategory.ESSENTIAL) }
    var uniqueAirspaceClasses by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        val (airspaceFiles, airspaceChecks) = loadAirspaceFiles(context)
        val (waypointFiles, waypointChecks) = loadWaypointFiles(context)
        selectedAirspaceFiles.clear()
        selectedAirspaceFiles.addAll(airspaceFiles)
        airspaceCheckedStates.value = airspaceChecks
        selectedWaypointFiles.clear()
        selectedWaypointFiles.addAll(waypointFiles)
        waypointCheckedStates.value = waypointChecks
        selectedClasses.value = loadSelectedClasses(context) ?: mutableMapOf()
        val templates = cardPreferences.getAllTemplates().first()
        allTemplates = templates
        cardPreferences.saveFlightModeTemplate("CRUISE", "essential")
        cardPreferences.saveFlightModeTemplate("THERMAL", "thermal")
        cardPreferences.saveFlightModeTemplate("FINAL_GLIDE", "cross_country")
        Log.d(TAG, "ƒo. Default mappings set up")
        uniqueAirspaceClasses = updateUniqueAirspaceClasses(
            context,
            selectedAirspaceFiles,
            airspaceCheckedStates.value
        ) { error -> errorMessage = error }
    }

    LaunchedEffect(selectedFlightMode, allTemplates) {
        if (allTemplates.isNotEmpty()) {
            try {
                val savedTemplateId = cardPreferences.getFlightModeTemplate(selectedFlightMode.name).first()
                val template = if (savedTemplateId != null) {
                    allTemplates.find { it.id == savedTemplateId }
                } else {
                    allTemplates.find { it.name == "Essential" }
                }
                selectedTemplate = template
            } catch (e: Exception) {
                Log.e(TAG, "ƒ?O Error loading template: ${e.message}")
                selectedTemplate = null
            }
        }
    }

    val airspaceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val fileName = copyFileToInternalStorage(context, it)
                selectedAirspaceFiles.add(it)
                airspaceCheckedStates.value[fileName] = true
                saveAirspaceFiles(context, selectedAirspaceFiles, airspaceCheckedStates.value)
                uniqueAirspaceClasses = updateUniqueAirspaceClasses(
                    context,
                    selectedAirspaceFiles,
                    airspaceCheckedStates.value
                ) { error -> errorMessage = error }
            } catch (e: Exception) {
                errorMessage = "Error adding airspace file: ${e.message}"
            }
        }
    }

    fun onToggleAirspaceFile(name: String) {
        airspaceCheckedStates.value[name] = !(airspaceCheckedStates.value[name] ?: false)
        saveAirspaceFiles(context, selectedAirspaceFiles, airspaceCheckedStates.value)
        uniqueAirspaceClasses = updateUniqueAirspaceClasses(
            context,
            selectedAirspaceFiles,
            airspaceCheckedStates.value
        ) { error -> errorMessage = error }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flight Management") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = when (activeTab) {
                "screens" -> 0
                "airspace" -> 1
                "waypoints" -> 2
                else -> 0
            }) {
                Tab(selected = activeTab == "screens", onClick = { activeTab = "screens" }) {
                    Text("Screens")
                }
                Tab(selected = activeTab == "airspace", onClick = { activeTab = "airspace" }) {
                    Text("Airspace")
                }
                Tab(selected = activeTab == "waypoints", onClick = { activeTab = "waypoints" }) {
                    Text("Waypoints")
                }
            }

            if (activeTab == "airspace") {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    item {
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )
                        }
                        Button(
                            onClick = { airspaceLauncher.launch("text/plain") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add Airspace File")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        SectionHeader("Airspace Files", "${selectedAirspaceFiles.size} files")
                    }

                    items(selectedAirspaceFiles.map { uri ->
                        val name = uri.lastPathSegment ?: ""
                        val enabled = airspaceCheckedStates.value[name] ?: false
                        val count = 0
                        val status = if (enabled) "Loaded" else "Disabled"
                        FileItem(name, enabled, count, status, uri)
                    }) { file ->
                        FileItemCard(
                            file = file,
                            type = "airspace",
                            onToggle = { onToggleAirspaceFile(file.name) },
                            onDelete = { showDeleteDialog = file.name }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        SectionHeader("Airspace Classes", "${uniqueAirspaceClasses.size} classes")
                    }

                    items(uniqueAirspaceClasses) { className ->
                        val (color, description) = airspaceClassInfo[className] ?: Pair("#FFFFFF", "Unknown class")
                        val enabled = selectedClasses.value[className] ?: true
                        AirspaceClassCard(
                            airspaceClass = AirspaceClassItem(className, enabled, color, description),
                            onToggle = {
                                selectedClasses.value[className] = !enabled
                                saveSelectedClasses(context, selectedClasses.value)
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            } else if (activeTab == "screens") {
                // Existing screens content
            } else if (activeTab == "waypoints") {
                // Existing waypoints content
            }
        }
    }
}
