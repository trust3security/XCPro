package com.example.ui1.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.xcpro.screens.navdrawer.SettingsTopAppBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DFNavboxes(
    navController: NavHostController,
    drawerState: DrawerState,
    onLoadConfig: () -> Unit = {},
    onSaveConfig: () -> Unit = {},
    onNavigateUp: (() -> Unit)? = null,
    onSecondaryNavigate: (() -> Unit)? = null,
    onNavigateToMap: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
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
    val closeAction: () -> Unit = onClose ?: {
        navController.popBackStack("map", inclusive = false)
        Unit
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            SettingsTopAppBar(
                title = "Navboxes",
                onNavigateUp = navigateUpAction,
                onSecondaryNavigate = secondaryNavigateAction,
                onNavigateToMap = navigateToMapAction
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars), // Add this
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = {
                    onLoadConfig()
                }) {
                    Text("Load")
                }
                Button(onClick = {
                    onSaveConfig()
                }) {
                    Text("Save")
                }
                Button(onClick = closeAction) {
                    Text("Close")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Content can be added here
        }
    }
}
