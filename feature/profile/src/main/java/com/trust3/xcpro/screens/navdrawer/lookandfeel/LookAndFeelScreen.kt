package com.trust3.xcpro.screens.navdrawer.lookandfeel

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.trust3.xcpro.profiles.ProfileViewModel
import com.trust3.xcpro.profiles.ProfileIdResolver
import kotlinx.coroutines.launch
import com.trust3.xcpro.screens.navdrawer.lookandfeel.StatusBarStyleApplier
import com.trust3.xcpro.screens.navdrawer.SettingsTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookAndFeelScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    onNavigateUp: (() -> Unit)? = null,
    onSecondaryNavigate: (() -> Unit)? = null,
    onNavigateToMap: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val lookAndFeelViewModel: LookAndFeelViewModel = hiltViewModel()
    val profileUiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val profileId = ProfileIdResolver.canonicalOrDefault(profileUiState.activeProfile?.id)

    val snailTrailViewModel: SnailTrailSettingsViewModel = hiltViewModel()
    val trailSettings by snailTrailViewModel.settings.collectAsStateWithLifecycle()

    LaunchedEffect(profileId) {
        lookAndFeelViewModel.setProfileId(profileId)
    }
    val lookAndFeelUiState by lookAndFeelViewModel.uiState.collectAsStateWithLifecycle()
    val statusBarStyle = remember(lookAndFeelUiState.statusBarStyleId) {
        StatusBarStyle.values().find { it.id == lookAndFeelUiState.statusBarStyleId }
            ?: StatusBarStyle.default
    }
    val cardStyle = remember(lookAndFeelUiState.cardStyleId) {
        CardStyle.values().find { it.id == lookAndFeelUiState.cardStyleId }
            ?: CardStyle.default
    }
    val colorTheme = remember(lookAndFeelUiState.colorThemeId) {
        com.trust3.xcpro.ui.theme.AppColorTheme.values()
            .find { it.id == lookAndFeelUiState.colorThemeId }
            ?: com.trust3.xcpro.ui.theme.AppColorTheme.DEFAULT
    }

    val showStatusSheet = remember { mutableStateOf(false) }
    val showCardSheet = remember { mutableStateOf(false) }
    val showColorSheet = remember { mutableStateOf(false) }
    val showSnailTrailSheet = remember { mutableStateOf(false) }
    val navigateUpAction: () -> Unit = onNavigateUp ?: {
        navController.navigateUp()
        Unit
    }
    val secondaryNavigateAction: () -> Unit = onSecondaryNavigate ?: {
        scope.launch {
            navController.popBackStack("map", inclusive = false)
            drawerState.open()
        }
        Unit
    }
    val navigateToMapAction: () -> Unit = onNavigateToMap ?: {
        scope.launch {
            drawerState.close()
            navController.popBackStack("map", inclusive = false)
        }
        Unit
    }

    val menuOptions = remember(statusBarStyle, cardStyle, trailSettings) {
        LookAndFeelMenuDefaults.defaultMenuOptions(
            statusBarStyle = statusBarStyle,
            cardStyle = cardStyle,
            snailTrailSummary = trailSummary(trailSettings)
        )
    }

    Scaffold(
        topBar = {
            SettingsTopAppBar(
                title = "Look & Feel",
                onNavigateUp = navigateUpAction,
                onSecondaryNavigate = secondaryNavigateAction,
                onNavigateToMap = navigateToMapAction
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
        currentStyle = statusBarStyle,
        onStyleSelected = { style ->
            lookAndFeelViewModel.setStatusBarStyleId(style.id)
            (context as? StatusBarStyleApplier)?.applyUserStatusBarStyle(profileId)
        }
    )

    CardStyleSheet(
        showSheet = showCardSheet,
        currentStyle = cardStyle,
        onStyleSelected = { style ->
            lookAndFeelViewModel.setCardStyleId(style.id)
        }
    )

    ColorThemeSheet(
        showSheet = showColorSheet,
        currentTheme = colorTheme,
        onThemeSelected = { theme ->
            lookAndFeelViewModel.setColorThemeId(theme.id)
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
