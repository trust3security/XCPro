//package com.trust3.xcpro.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MySupport(
    navController: NavHostController,
    drawerState: DrawerState,
    onShowBottomSheet: () -> Unit = {},
    onHideBottomSheet: () -> Unit = {}
) {
    val scaffoldState = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()
    var selectedMap by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(scaffoldState.bottomSheetState.isCollapsed) {
        if (scaffoldState.bottomSheetState.isCollapsed) {
            onHideBottomSheet()
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 0.dp,
        sheetContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Color.LightGray)
                    //.border(2.dp, Color.Red)
                    .padding(bottom = 56.dp)
            ) {
                Text(
                    text = selectedMap ?: "No Map Selected",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Support") },
                navigationIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            navController.popBackStack("map", inclusive = false)
                            drawerState.open()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                backgroundColor = MaterialTheme.colors.surface
            )
        },
        backgroundColor = MaterialTheme.colors.background,
       // modifier = Modifier.border(2.dp, Color(0xFFFFA500))
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                    top = innerPadding.calculateTopPadding(),
                    bottom = 0.dp
                )
        ) {
            // Main content
            Text("Support Screen Content", modifier = Modifier.align(Alignment.Center))

            // Bottom bar manually aligned
            BottomAppBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .height(64.dp)
                    .background(Color.Magenta),
                  //  .border(2.dp, Color.Green),
                elevation = 0.dp,
                contentPadding = PaddingValues(0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    listOf("Map1", "Map2", "Map3").forEach { mapName ->
                        Column(
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                                .clickable {
                                    selectedMap = mapName
                                    scope.launch {
                                        scaffoldState.bottomSheetState.expand()
                                        onShowBottomSheet()
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Map,
                                contentDescription = mapName,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(mapName, style = MaterialTheme.typography.caption)
                        }
                    }
                }
            }
        }
    }
}
