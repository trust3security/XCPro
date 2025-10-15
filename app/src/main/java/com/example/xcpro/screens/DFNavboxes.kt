package com.example.ui1.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.ui1.icons.Reply_all
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
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                title = { Text(text = "Files") },
                navigationIcon = {
                    Row {
                        IconButton(onClick = {
                            navController.popBackStack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        IconButton(onClick = {
                            scope.launch {
                                navController.popBackStack("map", inclusive = false)
                                drawerState.open()
                            }
                        }) {
                            Icon(
                                imageVector = Reply_all,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        navController.popBackStack("map", inclusive = false)
                    }) {
                        Icon(Icons.Default.Map, contentDescription = "Home")
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