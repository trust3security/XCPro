package com.example.xcpro

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.navigation.NavHostController
import com.example.xcpro.copyFileToInternalStorage
import com.example.xcpro.loadAirspaceFiles
import com.example.xcpro.loadSelectedClasses
import com.example.xcpro.loadWaypointFiles
import com.example.xcpro.parseAirspaceClasses
import com.example.xcpro.saveAirspaceFiles
import com.example.xcpro.saveSelectedClasses
import com.example.xcpro.saveWaypointFiles
import com.example.dfcards.CardCategory
import com.example.dfcards.CardPreferences
import com.example.dfcards.CardsGridSection
import com.example.dfcards.CategoryTabsSection
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.FlightModeSelectionSection
import com.example.dfcards.FlightTemplate
import com.example.dfcards.TemplatesForModeSection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "FlightMgmt"

data class FileItem(
    val name: String,
    val enabled: Boolean,
    val count: Int,
    val status: String,
    val uri: Uri
)

data class AirspaceClassItem(
    val className: String,
    val enabled: Boolean,
    val color: String,
    val description: String
)

val airspaceClassInfo = mapOf(
    "A" to Pair("#FF0000", "Controlled airspace - IFR only"),
    "B" to Pair("#0000FF", "Controlled airspace - IFR and VFR"),
    "C" to Pair("#FF00FF", "Controlled airspace - IFR and VFR with clearance"),
    "D" to Pair("#0000FF", "Controlled airspace - Radio communication required"),
    "E" to Pair("#FF00FF", "Controlled airspace - IFR controlled, VFR not"),
    "F" to Pair("#808080", "Advisory airspace"),
    "G" to Pair("#FFFFFF", "Uncontrolled airspace"),
    "CTR" to Pair("#0000FF", "Control Zone"),
    "RMZ" to Pair("#00FF00", "Radio Mandatory Zone"),
    "RESTRICTED" to Pair("#FF0000", "Restricted area"),
    "DANGER" to Pair("#FFA500", "Danger area")
)

fun extractAirspaceClassesFromFile(context: Context, fileName: String): List<String> {
    try {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return emptyList()
        return file.readLines().filter { it.startsWith("AC ") }.map { it.substring(3).trim() }.distinct()
    } catch (e: Exception) {
        return emptyList()
    }
}

fun updateUniqueAirspaceClasses(
    context: Context,
    files: List<Uri>,
    checkedStates: Map<String, Boolean>,
    onError: (String) -> Unit
): List<String> {
    val enabledFileNames = files.filter { uri ->
        val name = uri.lastPathSegment ?: ""
        checkedStates[name] ?: false
    }.map { uri ->
        uri.lastPathSegment ?: ""
    }

    val classes = try {
        enabledFileNames.flatMap { fileName ->
            extractAirspaceClassesFromFile(context, fileName)
        }.distinct().sorted()
    } catch (e: Exception) {
        onError("Error extracting airspace classes: ${e.message}")
        emptyList()
    }

    return classes
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun FlightMgmt(
    navController: NavHostController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
        Log.d(TAG, "✅ Default mappings set up")
        uniqueAirspaceClasses = updateUniqueAirspaceClasses(
            context,
            selectedAirspaceFiles,
            airspaceCheckedStates.value,
            { error -> errorMessage = error }
        )
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
                Log.e(TAG, "❌ Error loading template: ${e.message}")
                selectedTemplate = null
            }
        }
    }

    val airspaceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val fileName = copyFileToInternalStorage(context, it) // Remove extra fileName argument
                selectedAirspaceFiles.add(it)
                airspaceCheckedStates.value[fileName] = true
                saveAirspaceFiles(context, selectedAirspaceFiles, airspaceCheckedStates.value)
                uniqueAirspaceClasses = updateUniqueAirspaceClasses(
                    context,
                    selectedAirspaceFiles,
                    airspaceCheckedStates.value,
                    { error -> errorMessage = error }
                )
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
            airspaceCheckedStates.value,
            { error -> errorMessage = error }
        )
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
            TabRow(selectedTabIndex = when (activeTab) {
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
                        val count = 0 // Replace with actual count if available
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

@Composable
private fun SectionHeader(
    title: String,
    count: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = count,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FileItemCard(
    file: FileItem,
    type: String,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Surface(
        onClick = { onToggle(file.name) },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (file.enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (file.enabled) "Hide ${file.name}" else "Show ${file.name}",
                modifier = Modifier.size(20.dp),
                tint = if (file.enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (file.name.length > 20) "${file.name.take(20)}..." else file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (type == "airspace") "${file.count} zones" else "${file.count} points",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = if (file.status == "Loaded") MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    1.dp,
                    if (file.status == "Loaded") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(
                    text = file.status,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (file.status == "Loaded") MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete ${file.name}",
                modifier = Modifier
                    .size(20.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        onDelete(file.name)
                    },
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun AirspaceClassCard(
    airspaceClass: AirspaceClassItem,
    onToggle: (String) -> Unit
) {
    Surface(
        onClick = { onToggle(airspaceClass.className) },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        color = Color(airspaceClass.color.toColorInt())
                            .copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        RoundedCornerShape(4.dp)
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Class ${airspaceClass.className}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = airspaceClass.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = if (airspaceClass.enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (airspaceClass.enabled) "Hide class ${airspaceClass.className}" else "Show class ${airspaceClass.className}",
                modifier = Modifier.size(20.dp),
                tint = if (airspaceClass.enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
