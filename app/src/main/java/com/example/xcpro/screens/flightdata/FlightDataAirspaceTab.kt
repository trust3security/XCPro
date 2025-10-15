package com.example.ui1.screens.flightmgmt

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xcpro.copyFileToInternalStorage
import com.example.xcpro.parseAirspaceClasses
import com.example.xcpro.saveAirspaceFiles
import com.example.xcpro.saveSelectedClasses
import com.example.xcpro.validateOpenAirFile
import com.example.ui1.screens.AirspaceClassItem
import com.example.ui1.screens.FileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "FlightAirspaceTab"

@Composable
fun FlightDataAirspaceTab(
    selectedAirspaceFiles: MutableList<Uri>,
    airspaceCheckedStates: MutableState<MutableMap<String, Boolean>>,
    onAirspaceStateChanged: (MutableMap<String, Boolean>) -> Unit,
    airspaceClassStates: MutableState<MutableMap<String, Boolean>>,
    onAirspaceClassStateChanged: (MutableMap<String, Boolean>) -> Unit,
    onShowDeleteDialog: (String) -> Unit,
    onErrorMessage: (String) -> Unit,
    scope: CoroutineScope,
    addFileButton: @Composable (String, () -> Unit) -> Unit,
    sectionHeader: @Composable (String, String) -> Unit,
    fileItemCard: @Composable (FileItem, String, (String) -> Unit, (String) -> Unit) -> Unit,
    airspaceClassCard: @Composable (AirspaceClassItem, (String) -> Unit) -> Unit
) {
    val context = LocalContext.current

    val airspaceFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    Log.d(TAG, "🔍 Starting file processing...")

                    // 🆕 CHECK FILE SIZE BEFORE COPYING
                    val contentResolver = context.contentResolver
                    val fileSize = contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.available().toLong()
                    } ?: 0L

                    val fileSizeMB = fileSize / (1024 * 1024)
                    Log.d(TAG, "📏 File size: ${fileSizeMB}MB (${fileSize} bytes)")

                    if (fileSizeMB > 5) {
                        onErrorMessage("File too large: ${fileSizeMB}MB. Maximum size is 5MB.")
                        Log.e(TAG, "❌ File too large: ${fileSizeMB}MB")
                        return@launch
                    }

                    val fileName = copyFileToInternalStorage(context, it)
                    Log.d(TAG, "📁 File copied: $fileName")

                    if (!fileName.endsWith(".txt", ignoreCase = true)) {
                        onErrorMessage("Only .txt files are supported for airspace files.")
                        return@launch
                    }

                    // 🆕 VALIDATE OPENAIR FORMAT
                    val file = File(context.filesDir, fileName)
                    if (file.exists()) {
                        Log.d(TAG, "📖 Reading file content...")
                        val fileContent = file.readText()
                        Log.d(TAG, "📄 File content length: ${fileContent.length} characters")
                        Log.d(TAG, "📄 First 200 chars: ${fileContent.take(200)}")

                        val (isValid, message) = validateOpenAirFile(fileContent)
                        Log.d(TAG, "🔍 Validation result: isValid=$isValid, message='$message'")

                        if (!isValid) {
                            // Delete the invalid file
                            val deleted = file.delete()
                            Log.e(TAG, "❌ File validation failed: $message")
                            Log.d(TAG, "🗑️ File deleted: $deleted")
                            onErrorMessage("Invalid file format: $message")
                            return@launch
                        }

                        Log.d(TAG, "✅ File validation passed: $message")
                    } else {
                        Log.e(TAG, "❌ File does not exist after copying")
                        onErrorMessage("Error reading uploaded file.")
                        return@launch
                    }

                    // Only proceed if validation passed
                    if (!selectedAirspaceFiles.any { file ->
                            file.lastPathSegment?.substringAfterLast("/") == fileName
                        }) {
                        selectedAirspaceFiles.add(Uri.fromFile(File(context.filesDir, fileName)))
                        val newStates = airspaceCheckedStates.value.toMutableMap().apply {
                            put(fileName, true) // default enabled
                        }
                        onAirspaceStateChanged(newStates)
                        saveAirspaceFiles(context, selectedAirspaceFiles, newStates)
                        Log.d(TAG, "✅ Added valid airspace file: $fileName")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "💥 Exception during file processing: ${e.message}", e)
                    onErrorMessage("Error processing file: ${e.message}")
                }
            }
        } ?: run {
            Log.e(TAG, "❌ No URI received from file picker")
        }
    }

    // ✅ Use derivedStateOf for automatic recomposition
    val airspaceFileItems by remember {
        derivedStateOf {
            selectedAirspaceFiles.map { uri ->
                val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Unknown"
                FileItem(
                    name = fileName,
                    enabled = airspaceCheckedStates.value[fileName] ?: false,
                    count = 45, // placeholder – could parse real zone count later
                    status = if (airspaceCheckedStates.value[fileName] == true) "Loaded" else "Disabled",
                    uri = uri
                )
            }
        }
    }

    // ✅ Use derivedStateOf for automatic recomposition
    val enabledAirspaceFiles by remember {
        derivedStateOf {
            selectedAirspaceFiles.filter { uri ->
                val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: ""
                airspaceCheckedStates.value[fileName] == true
            }
        }
    }

    // ✅ Use derivedStateOf for automatic recomposition
    val airspaceClassItems by remember {
        derivedStateOf {
            if (enabledAirspaceFiles.isNotEmpty()) {
                parseAirspaceClasses(context, enabledAirspaceFiles).map { className ->
                    AirspaceClassItem(
                        className = className,
                        enabled = airspaceClassStates.value[className] ?: true,
                        color = when (className) {
                            "A" -> "#FF0000" // Controlled airspace - IFR only
                            "B" -> "#0000FF" // Controlled airspace - IFR and VFR
                            "C" -> "#FF00FF" // Controlled airspace - IFR and VFR with clearance
                            "D" -> "#0000FF" // Controlled airspace - Radio communication required
                            "E" -> "#FF00FF" // Controlled airspace - IFR controlled, VFR not
                            "F" -> "#808080" // Advisory airspace
                            "G" -> "#FFFFFF" // Uncontrolled airspace
                            "CTR" -> "#0000FF" // Control Zone
                            "RMZ" -> "#00FF00" // Radio Mandatory Zone
                            "RESTRICTED" -> "#FF0000" // Restricted area
                            "DANGER" -> "#FFA500" // Danger area
                            else -> "#8E8E93" // Default gray
                        },
                        description = when (className) {
                            "A" -> "IFR only"
                            "B" -> "IFR and VFR"
                            "C" -> "IFR and VFR with clearance"
                            "D" -> "Radio communication required"
                            "E" -> "IFR controlled, VFR not"
                            "F" -> "Advisory airspace"
                            "G" -> "Uncontrolled airspace"
                            "CTR" -> "Control Zone"
                            "RMZ" -> "Radio Mandatory Zone"
                            "RESTRICTED" -> "Restricted area"
                            "DANGER" -> "Danger area"
                            else -> "Unknown class"
                        }
                    )
                }
            } else emptyList()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Add File Button
        item {
            addFileButton("airspace") {
                Log.d(TAG, "🔍 Opening airspace file picker...")
                airspaceFilePickerLauncher.launch("text/plain")
            }
        }

        // 🆕 CONDITIONAL CONTENT: All possible scenarios handled
        when {
            // Scenario 1: No files added at all
            selectedAirspaceFiles.isEmpty() -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Header with icon
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Welcome to XC Pro",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "Add airspace files to visualize controlled airspace, and flight boundaries on your map.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )

                            // Step-by-step instructions
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "How to get started:",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium
                                )

                                AirspaceInstructionStep(
                                    step = "1",
                                    text = "Tap 'Add Airspace File' above",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                AirspaceInstructionStep(
                                    step = "2",
                                    text = "Select .txt files containing airspace data",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                AirspaceInstructionStep(
                                    step = "3",
                                    text = "Enable files to see available classes",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                AirspaceInstructionStep(
                                    step = "4",
                                    text = "Toggle specific classes (A, C, D, R, G)",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            // 🎯 ANIMATED HIGHLIGHT POINTING UPWARD
                            val infiniteTransition = rememberInfiniteTransition(label = "highlight")
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "alpha"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.15f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Start by adding your first airspace file above",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Scenario 2: Files added but ALL are disabled
            enabledAirspaceFiles.isEmpty() -> {
                // Show file management section FIRST
                item {
                    sectionHeader(
                        "Airspace Files",
                        ""
                    )
                }

                // THEN show the file items
                items(airspaceFileItems) { file ->
                    fileItemCard(
                        file,
                        "airspace",
                        { fileName ->
                            val newStates = airspaceCheckedStates.value.toMutableMap().apply {
                                put(fileName, !(get(fileName) ?: false))
                            }
                            onAirspaceStateChanged(newStates)
                            saveAirspaceFiles(context, selectedAirspaceFiles, newStates)
                        },
                        { fileName ->
                            onShowDeleteDialog(fileName)
                        }
                    )
                }

                // FINALLY show the informative card BELOW the file items
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Files Added But Not Enabled",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "You have ${selectedAirspaceFiles.size} airspace file(s) but none are currently enabled.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )

                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "What to do:",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Medium
                                )

                                AirspaceInstructionStep(
                                    step = "1",
                                    text = "Toggle the switch next to any file above",
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                AirspaceInstructionStep(
                                    step = "2",
                                    text = "Enabled files will show available airspace classes",
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                AirspaceInstructionStep(
                                    step = "3",
                                    text = "Select which classes to display on the map",
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }

                            // Animated highlight pointing UPWARD to file list
                            val infiniteTransition = rememberInfiniteTransition(label = "highlight")
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "alpha"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.15f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Enable files in the list above",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Scenario 3: Files enabled but no airspace classes found (invalid files)
            airspaceClassItems.isEmpty() -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Header with warning icon
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "No Airspace Classes Found",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "The enabled files may be invalid or contain no readable airspace data.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )

                            // Troubleshooting steps
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Troubleshooting steps:",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Medium
                                )

                                AirspaceInstructionStep(
                                    step = "1",
                                    text = "Ensure files are in OpenAir format (.txt)",
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                AirspaceInstructionStep(
                                    step = "2",
                                    text = "Check files contain airspace definitions (AC, AN, AL, AH, etc.)",
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                AirspaceInstructionStep(
                                    step = "3",
                                    text = "Enable files in the list below to reload",
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                AirspaceInstructionStep(
                                    step = "4",
                                    text = "Try downloading files from a different source",
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }

                            // Sample format example
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Expected format example:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )

                                    OpenAirFormatLine("AC D", "Airspace Class D")
                                    OpenAirFormatLine("AN London CTR", "Airspace Name")
                                    OpenAirFormatLine("AL 0ft", "Lower Limit")
                                    OpenAirFormatLine("AH 2500ft", "Upper Limit")
                                    OpenAirFormatLine("DP 51:28:00N 000:27:00W", "Point Definition")
                                }
                            }
                        }
                    }
                }

                // Show file management
                item {
                    sectionHeader(
                        "Airspace Files",
                        "${airspaceFileItems.count { it.enabled }} active"
                    )
                }

                items(airspaceFileItems) { file ->
                    fileItemCard(
                        file,
                        "airspace",
                        { fileName ->
                            val newStates = airspaceCheckedStates.value.toMutableMap().apply {
                                put(fileName, !(get(fileName) ?: false))
                            }
                            onAirspaceStateChanged(newStates)
                            saveAirspaceFiles(context, selectedAirspaceFiles, newStates)
                        },
                        { fileName ->
                            onShowDeleteDialog(fileName)
                        }
                    )
                }
            }

            // Scenario 4: Normal operation - files enabled and classes found
            else -> {
                item {
                    sectionHeader(
                        "Airspace Files",
                        "${airspaceFileItems.count { it.enabled }} active"
                    )
                }

                items(airspaceFileItems) { file ->
                    fileItemCard(
                        file,
                        "airspace",
                        { fileName ->
                            val newStates = airspaceCheckedStates.value.toMutableMap().apply {
                                put(fileName, !(get(fileName) ?: false))
                            }
                            onAirspaceStateChanged(newStates)
                            saveAirspaceFiles(context, selectedAirspaceFiles, newStates)
                        },
                        { fileName ->
                            onShowDeleteDialog(fileName)
                        }
                    )
                }

                // Airspace Classes Section
                if (airspaceClassItems.isNotEmpty()) {
                    item {
                        sectionHeader(
                            "Airspace Classes",
                            "${airspaceClassItems.count { it.enabled }} visible"
                        )
                    }
                    items(airspaceClassItems) { airspaceClass ->
                        airspaceClassCard(airspaceClass) { className ->
                            Log.d(TAG, "🔄 Toggling airspace class: $className")
                            val currentState = airspaceClassStates.value[className] ?: true
                            val newStates = airspaceClassStates.value.toMutableMap().apply {
                                put(className, !currentState)
                            }
                            onAirspaceClassStateChanged(newStates)
                            saveSelectedClasses(context, newStates)
                            Log.d(TAG, "✅ Airspace class $className is now ${!currentState}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AirspaceInstructionStep(
    step: String,
    text: String,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = step,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}

@Composable
private fun OpenAirFormatLine(
    command: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = command,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(140.dp)
        )
        Text(
            text = "→ $description",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f)
        )
    }
}