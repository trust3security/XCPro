package com.example.ui1.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.xcpro.skysight.SkysightClient
import com.example.xcpro.screens.navdrawer.SettingsTopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import com.example.xcpro.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    onShowAirspaceOverlay: () -> Unit,
    onPrepareHawkDashboard: () -> Unit,
    onCancelHawkDashboard: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val hasScrolled = remember { derivedStateOf { listState.firstVisibleItemScrollOffset > 0 } }
    val appBarElevation by animateDpAsState(targetValue = if (hasScrolled.value) 4.dp else 0.dp)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            // ✅ Match Look & Feel header style exactly
            SettingsTopAppBar(
                title = "General",
                onNavigateUp = { navController.popBackStack() },
                onOpenDrawer = {
                    scope.launch {
                        navController.popBackStack("map", inclusive = false)
                        drawerState.open()
                        onCancelHawkDashboard()
                    }
                },
                onNavigateToMap = {
                    scope.launch {
                        drawerState.close()
                        navController.popBackStack("map", inclusive = false)
                        onCancelHawkDashboard()
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
                    .wrapContentHeight()
            ) {
                // Align spacing/padding with Flight Data (8dp horizontal, spaced items)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    state = listState
                ) {
                    // Keep SkySight at full width
                    item { SkysightSettingsPanel() }

                    // Row 1: Files | Profiles
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CategoryItem(
                                title = "Files",
                                icon = Icons.Default.Folder,
                                onClick = { navController.navigate("Files") },
                                modifier = Modifier.weight(1f)
                            )
                            CategoryItem(
                                title = "Profiles",
                                icon = Icons.Default.Map,
                                onClick = { navController.navigate("Profiles") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Row 2: Look & Feel | Units
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CategoryItem(
                                title = "Look & Feel",
                                icon = Icons.Outlined.Style,
                                onClick = { navController.navigate("look_and_feel") },
                                modifier = Modifier.weight(1f)
                            )
                            CategoryItem(
                                title = "Units",
                                icon = Icons.Default.Straighten,
                                onClick = { navController.navigate("units_settings") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Row 2b: Polar | XCPro HAWK
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CategoryItem(
                                title = "Polar",
                                icon = Icons.Default.Flight,
                                onClick = { navController.navigate("polar_settings") },
                                modifier = Modifier.weight(1f)
                            )
                            CategoryItem(
                                title = "Vario",
                                icon = Icons.Default.Speed,
                                onClick = {
                                    onPrepareHawkDashboard()
                                    navController.navigate("hawk_dashboard")
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Row 3: Layouts | Airspace
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CategoryItem(
                                title = "Layouts",
                                icon = Icons.Default.GridView,
                                onClick = { navController.navigate("layouts") },
                                modifier = Modifier.weight(1f)
                            )
                            CategoryItem(
                                title = "Airspace",
                                icon = Icons.Default.Cloud,
                                onClick = {
                                    scope.launch {
                                        navController.popBackStack("map", inclusive = false)
                                        onShowAirspaceOverlay()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Row 4: Navboxes | (spacer)
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CategoryItem(
                                title = "Navboxes",
                                icon = Icons.Default.Dashboard,
                                onClick = { navController.navigate("dfnavboxes") },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryItem(title: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Match Flight Data card styling; parent controls grid spacing
    Surface(
        onClick = onClick,
        modifier = modifier
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
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkysightSettingsPanelLegacy() {
    val context = LocalContext.current
    val skysightClient = remember { SkysightClient.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    val isAuthenticated by skysightClient.isAuthenticated.collectAsState()
    val availableRegionsList by skysightClient.availableRegionsList.collectAsState()
    val selectedRegion by skysightClient.selectedRegion.collectAsState()
    
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isExpanded by remember { 
        mutableStateOf(!isAuthenticated) // Auto-collapse when logged in
    }
    
    // Match Flight Data card style: themed surface, subtle border, rounded corners
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.skysight_logo),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "SkySight Weather",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (isAuthenticated) Color.Green else Color.Red,
                                shape = CircleShape
                            )
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
            
            if (!isAuthenticated) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username/Email") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    leadingIcon = {
                        Icon(Icons.Default.Email, contentDescription = null)
                    }
                )
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    }
                )
                
                errorMessage?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            
                            skysightClient.authenticate(username, password)
                                .onSuccess {
                                    errorMessage = null
                                }
                                .onFailure { error ->
                                    errorMessage = error.message
                                }
                            
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && username.isNotBlank() && password.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Login to SkySight")
                    }
                }
            } else {
                // Authenticated state - just show region dropdown
                
                if (isAuthenticated) {
                    android.util.Log.d("SkysightSettings", "Available regions: ${availableRegionsList.size} - $availableRegionsList")
                    android.util.Log.d("SkysightSettings", "Selected region: $selectedRegion")
                    
                    var expandedRegion by remember { mutableStateOf(false) }
                    
                    // Always show dropdown, even if regions are still loading
                    ExposedDropdownMenuBox(
                        expanded = expandedRegion,
                        onExpandedChange = { expandedRegion = !expandedRegion },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedRegion?.let { getRegionDisplayName(it) } ?: 
                                   if (availableRegionsList.isEmpty()) "Loading regions..." else "Select Region",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Region") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRegion)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            enabled = availableRegionsList.isNotEmpty()
                        )
                        
                        ExposedDropdownMenu(
                            expanded = expandedRegion && availableRegionsList.isNotEmpty(),
                            onDismissRequest = { expandedRegion = false }
                        ) {
                            availableRegionsList.forEach { region ->
                                DropdownMenuItem(
                                    text = { Text(getRegionDisplayName(region)) },
                                    onClick = {
                                        android.util.Log.d("SkysightSettings", "Region selected: $region")
                                        scope.launch {
                                            skysightClient.selectRegion(region)
                                        }
                                        expandedRegion = false
                                    }
                                )
                            }
                        }
                    }
                    
                    // Debug info
                    if (availableRegionsList.isEmpty()) {
                        Text(
                            text = "Debug: No regions loaded yet. This could be an API issue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                
                OutlinedButton(
                    onClick = {
                        skysightClient.logout()
                        username = ""
                        password = ""
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Logout")
                }
            }
                }
            }
        }
    }
}

private fun getRegionDisplayName(regionId: String): String {
    return when (regionId) {
        "WEST_US" -> "Western United States"
        "EAST_US" -> "Eastern United States"
        "EUROPE" -> "Europe"
        "EAST_AUS" -> "Eastern Australia"
        "WA" -> "Western Australia"
        "NZ" -> "New Zealand"
        "JAPAN" -> "Japan"
        "ARGENTINA_CHILE" -> "Argentina & Chile"
        "SANEW" -> "South Africa"
        "BRAZIL" -> "Brazil"
        "HRRR" -> "HRRR (High-Resolution)"
        "ICONEU" -> "ICON EU Model"
        else -> regionId
    }
}

