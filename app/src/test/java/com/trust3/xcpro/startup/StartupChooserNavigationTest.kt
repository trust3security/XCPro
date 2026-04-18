package com.trust3.xcpro.startup

import androidx.navigation.NavHostController
import com.trust3.xcpro.livefollow.LiveFollowRoutes
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class StartupChooserNavigationTest {

    @Test
    fun navigateFromStartupToFriendsFlying_usesNeutralListRouteWithoutSavedStatePayload() {
        val navController: NavHostController = mock()
        whenever(navController.popBackStack(LiveFollowRoutes.MAP_ROUTE, false)).thenReturn(true)

        navigateFromStartupToFriendsFlying(navController)

        verify(navController).popBackStack(LiveFollowRoutes.MAP_ROUTE, false)
        verify(navController).navigate(LiveFollowRoutes.FRIENDS_FLYING)
        verify(navController, never()).getBackStackEntry(LiveFollowRoutes.MAP_ROUTE)
    }
}
