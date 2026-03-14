package com.example.ui1.screens

import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.example.xcpro.screens.navdrawer.TaskRouteScreen

@Composable
fun Task(
    navController: NavHostController,
    drawerState: DrawerState,
    selectedNavItem: String?,
    onShowBottomSheet: () -> Unit = {},
    onHideBottomSheet: () -> Unit = {}
) {
    TaskRouteScreen(
        navController = navController,
        drawerState = drawerState,
        selectedNavItem = selectedNavItem,
        onShowBottomSheet = onShowBottomSheet,
        onHideBottomSheet = onHideBottomSheet
    )
}


