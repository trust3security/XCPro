package com.example.ui1.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterialApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun MyAbout(
    navController: NavHostController,
    drawerState: DrawerState
) {
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true
    )
    val coroutineScope = rememberCoroutineScope()

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Color.LightGray)
                    .padding(bottom = 56.dp)
            ) {
                Text("Bottom Sheet Content", modifier = Modifier.align(Alignment.Center))
            }
        },
        modifier = Modifier.navigationBarsPadding()
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("About") },
                    navigationIcon = {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                navController.popBackStack("map", inclusive = false)
                                drawerState.open()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            },
            bottomBar = {
                BottomNavigation {
                    BottomNavigationItem(
                        icon = { Icon(Icons.Default.Map, contentDescription = "XXX") },
                        label = { Text("Map") },
                        selected = true,
                        onClick = {
                            coroutineScope.launch { sheetState.show() }
                        }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Text("About Screen Content", modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}