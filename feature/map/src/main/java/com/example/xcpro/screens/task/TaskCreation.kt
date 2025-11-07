package com.example.xcpro

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCreation(
    navController: NavHostController,
    drawerState: DrawerState
) {
    var activeTab by remember { mutableStateOf(TaskCreationTab.Turnpoints) }
    var currentTask by remember { mutableStateOf(SoaringTask()) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                title = {
                    Text(
                        text = "Create Task",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                navController.popBackStack()
                                drawerState.open()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back and Open Drawer"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            navController.navigate("map") {
                                popUpTo("map") { inclusive = false }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Map,
                            contentDescription = "Go to Map"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TaskCreationTabs(
                activeTab = activeTab,
                onTabSelected = { activeTab = it }
            )

            when (activeTab) {
                TaskCreationTab.Turnpoints -> TurnPointsTab(
                    task = currentTask,
                    onTaskUpdated = { currentTask = it }
                )

                TaskCreationTab.Rules -> RulesTab(
                    task = currentTask,
                    onTaskUpdated = { currentTask = it }
                )

                TaskCreationTab.Manage -> ManageTab(
                    task = currentTask
                )

                TaskCreationTab.Placeholder -> XXXTab()
            }
        }
    }
}
