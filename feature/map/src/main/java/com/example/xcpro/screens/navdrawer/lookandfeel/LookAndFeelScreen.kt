package com.example.xcpro.screens.navdrawer.lookandfeel

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.xcpro.profiles.ProfileViewModel
import kotlinx.coroutines.launch
import com.example.xcpro.screens.navdrawer.lookandfeel.StatusBarStyleApplier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookAndFeelScreen(
    navController: NavHostController,
    drawerState: DrawerState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val profileUiState by profileViewModel.uiState.collectAsState()
    val profileId = profileUiState.activeProfile?.id ?: "default"

    val preferences = remember { LookAndFeelPreferences(context) }

    var statusBarStyle = remember(profileId) {
        mutableStateOf(preferences.getStatusBarStyle(profileId))
    }
    var cardStyle = remember(profileId) {
        mutableStateOf(preferences.getCardStyle(profileId))
    }
    var colorTheme = remember(profileId) {
        mutableStateOf(preferences.getColorTheme(profileId))
    }

    val showStatusSheet = remember { mutableStateOf(false) }
    val showCardSheet = remember { mutableStateOf(false) }
    val showColorSheet = remember { mutableStateOf(false) }

    val menuOptions = remember(statusBarStyle.value, cardStyle.value) {
        LookAndFeelMenuDefaults.defaultMenuOptions(
            statusBarStyle = statusBarStyle.value,
            cardStyle = cardStyle.value
        )
    }

    Scaffold(
        topBar = {
            LookAndFeelTopBar(
                onBack = {
                    scope.launch {
                        navController.popBackStack()
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
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            items(menuOptions) { option ->
                LookAndFeelMenuItem(
                    option = option,
                    onClick = {
                        when (option.id) {
                            "colors" -> showColorSheet.value = true
                            "status_bar" -> showStatusSheet.value = true
                            "card_style" -> showCardSheet.value = true
                            else -> Unit
                        }
                    }
                )
            }
        }
    }

    StatusBarStyleSheet(
        showSheet = showStatusSheet,
        currentStyle = statusBarStyle.value,
        onStyleSelected = { style ->
            statusBarStyle.value = style
            preferences.setStatusBarStyle(profileId, style)
            (context as? StatusBarStyleApplier)?.applyUserStatusBarStyle(profileId)
        }
    )

    CardStyleSheet(
        showSheet = showCardSheet,
        currentStyle = cardStyle.value,
        onStyleSelected = { style ->
            cardStyle.value = style
            preferences.setCardStyle(profileId, style)
        }
    )

    ColorThemeSheet(
        showSheet = showColorSheet,
        currentTheme = colorTheme.value,
        onThemeSelected = { theme ->
            colorTheme.value = theme
            preferences.setColorTheme(profileId, theme)
        },
        onNavigateToColors = {
            navController.navigate("colors")
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LookAndFeelTopBar(
    onBack: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        title = {
            Text(
                text = "Look & Feel",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            IconButton(onClick = onNavigateToMap) {
                Icon(
                    imageVector = Icons.Filled.Map,
                    contentDescription = "Go to map"
                )
            }
        }
    )
}
