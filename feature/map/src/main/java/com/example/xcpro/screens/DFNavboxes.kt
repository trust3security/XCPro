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
    onSaveConfig: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            SettingsTopAppBar(
                title = "Navboxes",
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
                Button(onClick = {
                    navController.popBackStack("map", inclusive = false)
                }) {
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
