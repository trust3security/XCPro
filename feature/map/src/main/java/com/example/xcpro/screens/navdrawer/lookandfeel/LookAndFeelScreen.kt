package com.example.xcpro.screens.navdrawer.lookandfeel

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.xcpro.profiles.ProfileViewModel
import kotlinx.coroutines.launch
import com.example.xcpro.screens.navdrawer.lookandfeel.StatusBarStyleApplier
import com.example.xcpro.screens.navdrawer.SettingsTopAppBar
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookAndFeelScreen(
    navController: NavHostController,
    drawerState: DrawerState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val profileUiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val profileId = profileUiState.activeProfile?.id ?: "default"

    val preferences = remember { LookAndFeelPreferences(context) }
    val snailTrailViewModel: SnailTrailSettingsViewModel = hiltViewModel()
    val trailSettings by snailTrailViewModel.settings.collectAsStateWithLifecycle()

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
    val showSnailTrailSheet = remember { mutableStateOf(false) }

    val menuOptions = remember(statusBarStyle.value, cardStyle.value, trailSettings) {
        LookAndFeelMenuDefaults.defaultMenuOptions(
            statusBarStyle = statusBarStyle.value,
            cardStyle = cardStyle.value,
            snailTrailSummary = trailSummary(trailSettings)
        )
    }

    Scaffold(
        topBar = {
            SettingsTopAppBar(
                title = "Look & Feel",
                onNavigateUp = { navController.navigateUp() },
                onSecondaryNavigate = {
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
                            "snail_trail" -> showSnailTrailSheet.value = true
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

    SnailTrailSheet(
        showSheet = showSnailTrailSheet,
        currentSettings = trailSettings,
        onLengthSelected = { length -> snailTrailViewModel.setLength(length) },
        onTypeSelected = { type -> snailTrailViewModel.setType(type) },
        onWindDriftChanged = { enabled -> snailTrailViewModel.setWindDriftEnabled(enabled) },
        onScalingChanged = { enabled -> snailTrailViewModel.setScalingEnabled(enabled) }
    )
}
