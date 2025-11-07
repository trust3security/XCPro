package com.example.ui1.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.xcpro.map.R
import com.example.xcpro.skysight.SkysightClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkysightSettingsPanel() {
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
                            trailingIcon = {
                                val visibilityIcon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(visibilityIcon, contentDescription = if (passwordVisible) "Hide Password" else "Show Password")
                                }
                            }
                        )

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Button(
                            onClick = {
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    try {
                                        skysightClient.authenticate(username, password)
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "Login failed"
                                    } finally {
                                        isLoading = false
                                    }
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
                        if (isAuthenticated) {
                            android.util.Log.d("SkysightSettings", "Available regions: ${availableRegionsList.size} - $availableRegionsList")
                            android.util.Log.d("SkysightSettings", "Selected region: $selectedRegion")

                            var expandedRegion by remember { mutableStateOf(false) }

                            ExposedDropdownMenuBox(
                                expanded = expandedRegion,
                                onExpandedChange = { expandedRegion = !expandedRegion },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = selectedRegion?.let { getRegionDisplayName(it) }
                                        ?: if (availableRegionsList.isEmpty()) "Loading regions..." else "Select Region",
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
                                                scope.launch { skysightClient.selectRegion(region) }
                                                expandedRegion = false
                                            }
                                        )
                                    }
                                }
                            }

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
                        ) { Text("Logout") }
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
