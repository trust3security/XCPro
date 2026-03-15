package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.xcpro.weather.ui.WeatherSettingsContentHost
import kotlinx.coroutines.launch

internal const val WEATHER_SETTINGS_SHEET_TAG = "weather_settings_sheet"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherSettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState
) {
    val scope = rememberCoroutineScope()
    WeatherSettingsSheet(
        onDismissRequest = { navController.navigateUp() },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherSettingsSheet(
    onDismissRequest: () -> Unit,
    onNavigateUp: (() -> Unit)?,
    onSecondaryNavigate: (() -> Unit)?,
    onNavigateToMap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = { WeatherSettingsContentHost() }
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag(WEATHER_SETTINGS_SHEET_TAG)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            SettingsTopAppBar(
                title = "RainViewer",
                onNavigateUp = onNavigateUp,
                onSecondaryNavigate = onSecondaryNavigate,
                onNavigateToMap = onNavigateToMap
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                content()
            }
        }
    }
}
