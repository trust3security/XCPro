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
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.xcpro.screens.navdrawer.SettingsTopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import com.example.xcpro.map.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    onShowAirspaceOverlay: () -> Unit
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
            //  Match Look & Feel header style exactly
            SettingsTopAppBar(
                title = "General",
                onNavigateUp = {
                    scope.launch {
                        navController.popBackStack()
                        drawerState.open()
                    }
                },
                onSecondaryNavigate = null,
                onNavigateToMap = {
                    scope.launch {
                        drawerState.close()
                        navController.popBackStack("map", inclusive = false)
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

                    // Row 2b: Orientation (single)
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CategoryItem(
                                title = "Orientation",
                                icon = Icons.Default.Explore,
                                onClick = { navController.navigate("orientation_settings") },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    // Row 2c: Polar | Levo Vario
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
                                title = "Levo Vario",
                                icon = Icons.Default.Speed,
                                onClick = { navController.navigate("levo_vario_settings") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Row 2d: HAWK Vario | Diagnostics
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CategoryItem(
                                title = "HAWK Vario",
                                icon = Icons.Default.Speed,
                                onClick = { navController.navigate("hawk_vario_settings") },
                                modifier = Modifier.weight(1f)
                            )
                            CategoryItem(
                                title = "Diagnostics",
                                icon = Icons.Outlined.Insights,
                                onClick = { navController.navigate("vario_diagnostics") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Row 3: Layouts | ADS-b
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
                                title = "ADS-b",
                                icon = Icons.Default.AirplanemodeActive,
                                onClick = { navController.navigate("adsb_settings") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Row 3b: OGN (single)
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CategoryItem(
                                title = "OGN",
                                icon = Icons.Default.Flight,
                                onClick = { navController.navigate("ogn_settings") },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    // Row 4: Navboxes | IGC Replay
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
                            CategoryItem(
                                title = "IGC Replay",
                                icon = Icons.Filled.PlayArrow,
                                onClick = { navController.navigate("igcReplay") },
                                modifier = Modifier.weight(1f)
                            )
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


