package com.example.xcpro.skysight

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkysightSettingsScreen(
    drawerState: DrawerState,
    onNavigateBack: () -> Unit = {},
    onNavigateToMap: () -> Unit = {}
) {
    val context = LocalContext.current
    val skysightClient = remember { SkysightClient.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    val isAuthenticated by skysightClient.isAuthenticated.collectAsState()
    val availableRegions by skysightClient.availableRegionsList.collectAsState()
    val selectedRegion by skysightClient.selectedRegion.collectAsState()
    
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var rememberCredentials by remember { mutableStateOf(true) }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                title = { 
                    Text(
                        text = "SkySight",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToMap()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Go to Map"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isAuthenticated) {
                item {
                    SkysightLoginSection(
                        username = username,
                        password = password,
                        passwordVisible = passwordVisible,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onUsernameChange = { username = it },
                        onPasswordChange = { password = it },
                        onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
                        onLogin = {
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
                        }
                    )
                }
            } else {
                item {
                    SkysightAccountSection(
                        onLogout = {
                            skysightClient.logout()
                            username = ""
                            password = ""
                            errorMessage = null
                        }
                    )
                }
                
                item {
                    SkysightRegionSection(
                        availableRegions = availableRegions,
                        selectedRegion = selectedRegion,
                        onRegionSelect = { region ->
                            scope.launch {
                                skysightClient.selectRegion(region)
                            }
                        }
                    )
                }
                
                item {
                    SkysightOverlaySettingsSection(
                        skysightClient = skysightClient
                    )
                }
            }
        }
    }
}

@Composable
fun SkysightLoginSection(
    username: String,
    password: String,
    passwordVisible: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onLogin: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Skysight Account",
                style = MaterialTheme.typography.titleMedium
            )
            
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Username/Email") },
                placeholder = { Text("Enter your username/email") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null)
                }
            )
            
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password") },
                placeholder = { Text("Enter your password") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = onPasswordVisibilityToggle) {
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
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Authentication Error",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (error.contains("401")) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "• Check your Skysight credentials\n• Ensure you have an active subscription\n• Contact support@skysight.io if the issue persists",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            
            Button(
                onClick = { onLogin() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && username.isNotBlank() && password.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Login")
                }
            }
        }
    }
}

@Composable
fun SkysightAccountSection(
    onLogout: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Connected to Skysight",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Weather data available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                OutlinedButton(onClick = onLogout) {
                    Text("Logout")
                }
            }
        }
    }
}

@Composable
fun SkysightRegionSection(
    availableRegions: List<String>,
    selectedRegion: String?,
    onRegionSelect: (String) -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Forecast Region",
                style = MaterialTheme.typography.titleMedium
            )
            
            if (availableRegions.isEmpty()) {
                Text(
                    text = "Loading regions...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                availableRegions.forEach { region ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedRegion == region,
                            onClick = { onRegionSelect(region) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = getRegionDisplayName(region),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Region: $region",
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

@Composable
fun SkysightOverlaySettingsSection(
    skysightClient: SkysightClient
) {
    var showSatellite by remember { mutableStateOf(skysightClient.getShowSatellite()) }
    var showRain by remember { mutableStateOf(skysightClient.getShowRain()) }
    
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Map Overlays",
                style = MaterialTheme.typography.titleMedium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Satellite, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Satellite Imagery",
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = showSatellite,
                    onCheckedChange = { enabled ->
                        showSatellite = enabled
                        skysightClient.setShowSatellite(enabled)
                    }
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Cloud, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Rain Forecast",
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = showRain,
                    onCheckedChange = { enabled ->
                        showRain = enabled
                        skysightClient.setShowRain(enabled)
                    }
                )
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