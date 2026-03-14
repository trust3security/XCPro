package com.example.xcpro.appshell.navdrawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.BottomAppBar
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                    IconButton(
                        onClick = {
                            scope.launch {
                                navController.popBackStack("map", inclusive = false)
                                drawerState.open()
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                backgroundColor = MaterialTheme.colors.surface
            )
        },
        backgroundColor = MaterialTheme.colors.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Text("Support Screen Content", modifier = Modifier.align(Alignment.Center))
            SupportBottomBar(
                onSelectMap = { mapName ->
                    selectedMap = mapName
                    scope.launch {
                        scaffoldState.bottomSheetState.expand()
                        onShowBottomSheet()
                    }
                }
            )
        }
    }
}

@Composable
private fun BoxScope.SupportBottomBar(
    onSelectMap: (String) -> Unit
) {
    BottomAppBar(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .height(64.dp)
            .background(Color.Magenta),
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
                        .clickable { onSelectMap(mapName) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Map,
                        contentDescription = mapName,
                        modifier = Modifier.height(20.dp)
                    )
                    Text(mapName, style = MaterialTheme.typography.caption)
                }
            }
        }
    }
}
