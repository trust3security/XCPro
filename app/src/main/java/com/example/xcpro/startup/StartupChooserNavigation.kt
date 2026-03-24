package com.example.xcpro.startup

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.example.xcpro.livefollow.LiveFollowRoutes

internal const val STARTUP_CHOOSER_ROUTE = "startup/chooser"
internal const val AUTO_START_LIVEFOLLOW_SHARING_ON_MAP =
    "auto_start_livefollow_sharing_on_map"

internal fun ensureMapRoute(navController: NavHostController) {
    val poppedToMap = navController.popBackStack(
        route = LiveFollowRoutes.MAP_ROUTE,
        inclusive = false
    )
    if (!poppedToMap) {
        navController.navigate(LiveFollowRoutes.MAP_ROUTE) {
            popUpTo(STARTUP_CHOOSER_ROUTE) { inclusive = true }
            launchSingleTop = true
        }
    }
}

internal fun navigateFromStartupToFlyingMap(
    navController: NavHostController
) {
    ensureMapRoute(navController)
    runCatching { navController.getBackStackEntry(LiveFollowRoutes.MAP_ROUTE) }
        .getOrNull()
        ?.savedStateHandle
        ?.set(AUTO_START_LIVEFOLLOW_SHARING_ON_MAP, true)
}

internal fun navigateFromStartupToFriendsFlying(
    navController: NavHostController
) {
    ensureMapRoute(navController)
    navController.navigate(LiveFollowRoutes.FRIENDS_FLYING)
}

internal fun consumeAutoStartLiveFollowSharingOnMap(
    entry: NavBackStackEntry
): Boolean {
    val shouldAutoStart = entry.savedStateHandle
        .get<Boolean>(AUTO_START_LIVEFOLLOW_SHARING_ON_MAP) == true
    if (shouldAutoStart) {
        entry.savedStateHandle[AUTO_START_LIVEFOLLOW_SHARING_ON_MAP] = false
    }
    return shouldAutoStart
}
